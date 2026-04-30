#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Offline feature engineering for recommendation.

This job refreshes:
1. MySQL user_features
2. MySQL post_features
3. Redis recent behavior sequences used by Java FeatureService
"""

import json
import logging
import os
import sys
from datetime import datetime, timedelta

import pandas as pd
from sqlalchemy import create_engine, text

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[logging.StreamHandler(sys.stdout)],
)
log = logging.getLogger(__name__)


def _env(name, default):
    return os.getenv(name, default)


def _env_int(name, default):
    value = os.getenv(name)
    return int(value) if value not in (None, "") else default


MYSQL_HOST = _env("MYSQL_HOST", "127.0.0.1")
MYSQL_PORT = _env_int("MYSQL_PORT", 3306)
MYSQL_USER = _env("MYSQL_USER", "root")
MYSQL_PASSWORD = _env("MYSQL_PASSWORD", "root123456")
MYSQL_DATABASE = _env("MYSQL_DATABASE", "image_social")

REDIS_HOST = _env("REDIS_HOST", "127.0.0.1")
REDIS_PORT = _env_int("REDIS_PORT", 6379)
REDIS_DB = _env_int("REDIS_DB", 0)
REDIS_PASSWORD = os.getenv("REDIS_PASSWORD")

RECENT_DAYS = _env_int("RECENT_DAYS", 7)
TOP_N_TAGS = _env_int("TOP_N_TAGS", 5)
SEQ_MAX_LEN = _env_int("SEQ_MAX_LEN", 20)
BEHAVIOR_SEQ_MAX_LEN = _env_int("BEHAVIOR_SEQ_MAX_LEN", 200)
REDIS_EXPIRE_SEC = _env_int("REDIS_EXPIRE_SEC", 86400 * 2)
POSITIVE_EVENT_WEIGHTS = {
    "POST_COMMENT": 4.0,
    "POST_FAVORITE": 3.5,
    "POST_LIKE": 3.0,
    "POST_SHARE": 2.5,
    "POST_DETAIL_VIEW": 1.8,
    "POST_CLICK": 1.2,
}
REALTIME_WINDOWS = {"1h": 3600, "6h": 21600, "24h": 86400}
MANUAL_INTEREST_DEFAULT_WEIGHT = 2.5


def get_engine():
    url = (
        f"mysql+pymysql://{MYSQL_USER}:{MYSQL_PASSWORD}"
        f"@{MYSQL_HOST}:{MYSQL_PORT}/{MYSQL_DATABASE}?charset=utf8mb4"
    )
    return create_engine(url, pool_pre_ping=True)


def read_sql_frame(engine, sql, params=None):
    connection = engine.raw_connection()
    try:
        cursor = connection.cursor()
        try:
            if params is None:
                cursor.execute(sql)
            else:
                cursor.execute(sql, params)
            rows = cursor.fetchall()
            columns = [desc[0] for desc in (cursor.description or [])]
            return pd.DataFrame(list(rows), columns=columns)
        finally:
            cursor.close()
    finally:
        connection.close()


def frame_records(df: pd.DataFrame):
    return df.astype(object).where(pd.notna(df), None).to_dict(orient="records")


def _safe_int(value, default=0):
    if value is None:
        return default
    if isinstance(value, float) and pd.isna(value):
        return default
    try:
        return int(value)
    except Exception:
        return default


def _safe_str(value, default=""):
    if value is None:
        return default
    if isinstance(value, float) and pd.isna(value):
        return default
    text = str(value).strip()
    return text if text else default


def get_redis_client():
    import redis

    return redis.Redis(
        host=REDIS_HOST,
        port=REDIS_PORT,
        db=REDIS_DB,
        password=REDIS_PASSWORD,
        decode_responses=True,
    )


def normalize_interest_terms(tags):
    terms = []
    for raw in tags or []:
        if raw is None:
            continue
        for part in str(raw).replace("/", ",").replace(">", ",").replace("|", ",").split(","):
            term = part.strip().lower()
            if term:
                terms.append(term)
    return list(dict.fromkeys(terms))


def record_realtime_interest(redis_client, user_id: int, tags: list, event_type: str) -> None:
    weight = POSITIVE_EVENT_WEIGHTS.get(str(event_type).strip().upper(), 0.0)
    terms = normalize_interest_terms(tags)
    if weight <= 0 or not terms:
        return
    pipe = redis_client.pipeline()
    for window, ttl in REALTIME_WINDOWS.items():
        key = f"user:{int(user_id)}:interests:{window}"
        for tag in terms[:12]:
            pipe.hincrbyfloat(key, tag, weight)
        pipe.expire(key, ttl)
    pipe.execute()


def get_realtime_interest_terms(redis_client, user_id: int, top_n: int = 16, window: str = "1h") -> list:
    for win in (window, "6h", "24h"):
        key = f"user:{int(user_id)}:interests:{win}"
        raw = redis_client.hgetall(key)
        if raw:
            sorted_tags = sorted(raw.items(), key=lambda item: float(item[1]), reverse=True)
            return [tag for tag, _ in sorted_tags[:top_n]]
    return []


def _weighted_top_terms(df: pd.DataFrame, column: str, output_column: str) -> pd.DataFrame:
    if df.empty or column not in df.columns:
        return pd.DataFrame(columns=["id", output_column])
    working = df[["user_id", "weight", column]].copy()
    working[column] = working[column].fillna("")
    working["term_list"] = working[column].astype(str).str.split(",")
    exploded = working.explode("term_list")
    exploded["term"] = exploded["term_list"].astype(str).str.strip().str.lower()
    exploded = exploded[(exploded["term"] != "") & exploded["term"].notna()]
    if exploded.empty:
        return pd.DataFrame(columns=["id", output_column])

    term_scores = (
        exploded.groupby(["user_id", "term"], as_index=False)["weight"]
        .sum()
        .sort_values(["user_id", "weight", "term"], ascending=[True, False, True])
    )
    return (
        term_scores.groupby("user_id")
        .head(TOP_N_TAGS)
        .groupby("user_id")["term"]
        .apply(lambda values: ",".join(values))
        .reset_index()
        .rename(columns={"user_id": "id", "term": output_column})
    )


def _compute_interest_profiles(engine) -> pd.DataFrame:
    placeholders = ",".join([f"'{event_type}'" for event_type in POSITIVE_EVENT_WEIGHTS.keys()])
    events = read_sql_frame(
        engine,
        "SELECT ue.user_id, ue.event_type, p.tags, p.topic_path, p.style_tags "
        "FROM user_events ue "
        "JOIN posts p ON p.id = ue.target_id "
        "WHERE ue.target_type='POST' "
        f"  AND ue.event_type IN ({placeholders}) "
        "  AND ue.user_id IS NOT NULL "
        "  AND p.deleted=0",
    )
    if events.empty:
        return pd.DataFrame(columns=["id", "top_interest_tags", "top_interest_topics", "preferred_styles", "feature_json"])

    events["weight"] = events["event_type"].map(POSITIVE_EVENT_WEIGHTS).fillna(1.0)
    events["topic_terms"] = (
        events["topic_path"]
        .fillna("")
        .astype(str)
        .str.replace("/", ",", regex=False)
        .str.replace(">", ",", regex=False)
    )
    tag_df = _weighted_top_terms(events, "tags", "top_interest_tags")
    topic_df = _weighted_top_terms(events, "topic_terms", "top_interest_topics")
    style_df = _weighted_top_terms(events, "style_tags", "preferred_styles")

    feature_json = (
        events.groupby("user_id")
        .agg(
            positive_event_count=("event_type", "count"),
            unique_topics=("topic_path", lambda values: int(pd.Series(values).fillna("").astype(str).str.len().gt(0).sum())),
            unique_styles=("style_tags", lambda values: int(pd.Series(values).fillna("").astype(str).str.len().gt(0).sum())),
        )
        .reset_index()
    )
    feature_json["feature_json"] = feature_json.apply(
        lambda row: json.dumps(
            {
                "positive_event_count": int(row["positive_event_count"]),
                "non_empty_topic_events": int(row["unique_topics"]),
                "non_empty_style_events": int(row["unique_styles"]),
            },
            ensure_ascii=False,
        ),
        axis=1,
    )
    feature_json = feature_json.rename(columns={"user_id": "id"})[["id", "feature_json"]]

    merged = tag_df.merge(topic_df, on="id", how="outer")
    merged = merged.merge(style_df, on="id", how="outer")
    merged = merged.merge(feature_json, on="id", how="outer")
    return merged


def _manual_interest_profiles(engine) -> pd.DataFrame:
    try:
        manual_df = read_sql_frame(
            engine,
            "SELECT user_id, facet_type, facet_key, weight "
            "FROM user_interest_subscriptions "
            "WHERE status='ACTIVE'"
        )
    except Exception:
        return pd.DataFrame(columns=["id", "manual_interest_tags", "manual_interest_topics", "manual_interest_styles", "manual_interest_json"])

    if manual_df.empty:
        return pd.DataFrame(columns=["id", "manual_interest_tags", "manual_interest_topics", "manual_interest_styles", "manual_interest_json"])

    manual_df["facet_type"] = manual_df["facet_type"].fillna("TOPIC").astype(str).str.upper().str.strip()
    manual_df["facet_key"] = manual_df["facet_key"].fillna("").astype(str).str.lower().str.strip()
    manual_df["weight"] = manual_df["weight"].fillna(MANUAL_INTEREST_DEFAULT_WEIGHT).astype(float)
    manual_df = manual_df[manual_df["facet_key"].str.len() > 0]
    if manual_df.empty:
        return pd.DataFrame(columns=["id", "manual_interest_tags", "manual_interest_topics", "manual_interest_styles", "manual_interest_json"])

    def top_terms(frame: pd.DataFrame, type_names, output_column: str) -> pd.DataFrame:
        selected = frame[frame["facet_type"].isin(type_names)]
        if selected.empty:
            return pd.DataFrame(columns=["id", output_column])
        weighted = (
            selected.groupby(["user_id", "facet_key"], as_index=False)["weight"]
            .sum()
            .sort_values(["user_id", "weight", "facet_key"], ascending=[True, False, True])
        )
        return (
            weighted.groupby("user_id")
            .head(TOP_N_TAGS)
            .groupby("user_id")["facet_key"]
            .apply(lambda values: ",".join(values))
            .reset_index()
            .rename(columns={"user_id": "id", "facet_key": output_column})
        )

    tag_df = top_terms(manual_df, {"TAG"}, "manual_interest_tags")
    topic_df = top_terms(manual_df, {"TOPIC", "SUBTOPIC"}, "manual_interest_topics")
    style_df = top_terms(manual_df, {"STYLE"}, "manual_interest_styles")
    profile_json_df = (
        manual_df.groupby("user_id")
        .agg(
            manual_interest_count=("facet_key", "count"),
            manual_interest_weight=("weight", "sum"),
        )
        .reset_index()
    )
    profile_json_df["manual_interest_json"] = profile_json_df.apply(
        lambda row: json.dumps(
            {
                "manual_interest_count": int(row["manual_interest_count"]),
                "manual_interest_weight": round(float(row["manual_interest_weight"]), 4),
            },
            ensure_ascii=False,
        ),
        axis=1,
    )
    profile_json_df = profile_json_df.rename(columns={"user_id": "id"})[["id", "manual_interest_json"]]

    merged = tag_df.merge(topic_df, on="id", how="outer")
    merged = merged.merge(style_df, on="id", how="outer")
    merged = merged.merge(profile_json_df, on="id", how="outer")
    return merged


def _merge_csv_terms(event_terms: str, manual_terms: str, max_terms: int = TOP_N_TAGS) -> str:
    merged = []
    seen = set()
    for raw in [manual_terms, event_terms]:
        if not raw:
            continue
        for token in str(raw).split(","):
            cleaned = token.strip().lower()
            if not cleaned or cleaned in seen:
                continue
            merged.append(cleaned)
            seen.add(cleaned)
            if len(merged) >= max_terms:
                return ",".join(merged)
    return ",".join(merged)


def _safe_json_object(raw) -> dict:
    if raw is None:
        return {}
    if isinstance(raw, dict):
        return raw
    if isinstance(raw, float) and pd.isna(raw):
        return {}
    if not isinstance(raw, str):
        raw = str(raw)
    raw = raw.strip()
    if not raw:
        return {}
    try:
        value = json.loads(raw)
        return value if isinstance(value, dict) else {}
    except Exception:
        return {}


def compute_user_features(engine):
    log.info("[user_features] loading users...")
    users = read_sql_frame(engine, "SELECT id, created_at FROM users WHERE deleted=0")
    if users.empty:
        log.warning("[user_features] users table is empty")
        return pd.DataFrame()

    today = pd.Timestamp(datetime.now().date())
    users["register_days"] = (
        today - pd.to_datetime(users["created_at"])
    ).dt.days.fillna(0).astype(int)

    metrics = [
        (
            "SELECT author_id AS id, COUNT(*) AS total_posts "
            "FROM posts WHERE deleted=0 GROUP BY author_id",
            "total_posts",
        ),
        (
            "SELECT user_id AS id, COUNT(*) AS total_likes_given "
            "FROM post_likes WHERE deleted=0 GROUP BY user_id",
            "total_likes_given",
        ),
        (
            "SELECT user_id AS id, COUNT(*) AS total_favorites_given "
            "FROM post_favorites WHERE deleted=0 GROUP BY user_id",
            "total_favorites_given",
        ),
        (
            "SELECT user_id AS id, COUNT(*) AS total_comments_given "
            "FROM post_comments WHERE deleted=0 GROUP BY user_id",
            "total_comments_given",
        ),
        (
            "SELECT followed_id AS id, COUNT(*) AS total_followers "
            "FROM user_follows WHERE deleted=0 GROUP BY followed_id",
            "total_followers",
        ),
        (
            "SELECT follower_id AS id, COUNT(*) AS total_following "
            "FROM user_follows WHERE deleted=0 GROUP BY follower_id",
            "total_following",
        ),
    ]

    for sql, column in metrics:
        metric_df = read_sql_frame(engine, sql)
        users = users.merge(metric_df, on="id", how="left")
        users[column] = users[column].fillna(0).astype(int)

    cutoff = (datetime.now() - timedelta(days=RECENT_DAYS)).strftime("%Y-%m-%d %H:%M:%S")
    recent_likes = read_sql_frame(
        engine,
        "SELECT user_id AS id, COUNT(*) AS recent_likes "
        "FROM user_events "
        "WHERE event_type='POST_LIKE' AND created_at >= %s "
        "GROUP BY user_id",
        params=[cutoff],
    )
    users = users.merge(recent_likes, on="id", how="left")
    users["avg_weekly_likes"] = (users["recent_likes"].fillna(0) / RECENT_DAYS).round(2)

    recent_favorites = read_sql_frame(
        engine,
        "SELECT user_id AS id, COUNT(*) AS recent_favs "
        "FROM user_events "
        "WHERE event_type='POST_FAVORITE' AND created_at >= %s "
        "GROUP BY user_id",
        params=[cutoff],
    )
    users = users.merge(recent_favorites, on="id", how="left")
    users["avg_weekly_favorites"] = (users["recent_favs"].fillna(0) / RECENT_DAYS).round(2)

    interest_profiles = _compute_interest_profiles(engine)
    users = users.merge(interest_profiles, on="id", how="left")
    manual_profiles = _manual_interest_profiles(engine)
    users = users.merge(manual_profiles, on="id", how="left")

    users["top_interest_tags"] = users.apply(
        lambda row: _merge_csv_terms(row.get("top_interest_tags"), row.get("manual_interest_tags")),
        axis=1,
    )
    users["top_interest_topics"] = users.apply(
        lambda row: _merge_csv_terms(row.get("top_interest_topics"), row.get("manual_interest_topics")),
        axis=1,
    )
    users["preferred_styles"] = users.apply(
        lambda row: _merge_csv_terms(row.get("preferred_styles"), row.get("manual_interest_styles")),
        axis=1,
    )

    users["feature_json"] = users.apply(
        lambda row: json.dumps(
            {
                **_safe_json_object(row.get("feature_json")),
                **_safe_json_object(row.get("manual_interest_json")),
            },
            ensure_ascii=False,
        ),
        axis=1,
    )

    users["top_interest_tags"] = users["top_interest_tags"].fillna("")
    users["top_interest_topics"] = users["top_interest_topics"].fillna("")
    users["preferred_styles"] = users["preferred_styles"].fillna("")
    users["feature_json"] = users["feature_json"].fillna("{}")
    users["updated_at"] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    return users[
        [
            "id",
            "register_days",
            "total_posts",
            "total_likes_given",
            "total_favorites_given",
            "total_comments_given",
            "total_followers",
            "total_following",
            "avg_weekly_likes",
            "avg_weekly_favorites",
            "top_interest_tags",
            "top_interest_topics",
            "preferred_styles",
            "feature_json",
            "updated_at",
        ]
    ].rename(columns={"id": "user_id"})


def write_user_features(df, engine):
    if df.empty:
        log.warning("[user_features] nothing to write")
        return
    with engine.begin() as conn:
        conn.execute(text("TRUNCATE TABLE user_features"))
        conn.execute(
            text(
                "INSERT INTO user_features ("
                "user_id, register_days, total_posts, total_likes_given, total_favorites_given, "
                "total_comments_given, total_followers, total_following, avg_weekly_likes, "
                "avg_weekly_favorites, top_interest_tags, top_interest_topics, preferred_styles, "
                "feature_json, updated_at"
                ") VALUES ("
                ":user_id, :register_days, :total_posts, :total_likes_given, :total_favorites_given, "
                ":total_comments_given, :total_followers, :total_following, :avg_weekly_likes, "
                ":avg_weekly_favorites, :top_interest_tags, :top_interest_topics, :preferred_styles, "
                ":feature_json, :updated_at"
                ")"
            ),
            frame_records(df),
        )
    log.info("[user_features] wrote %s rows", len(df))


def compute_post_features(engine):
    log.info("[post_features] loading posts...")
    posts = read_sql_frame(
        engine,
        "SELECT id, author_id, created_at, hot_score, view_count, like_count, "
        "favorite_count, comment_count, tags, topic_path, semantic_tags, style_tags, "
        "quality_score, aesthetic_score, safety_score "
        "FROM posts WHERE deleted=0",
    )
    if posts.empty:
        log.warning("[post_features] posts table is empty")
        return pd.DataFrame()

    posts["created_at"] = pd.to_datetime(posts["created_at"])
    posts["publish_hour"] = posts["created_at"].dt.hour
    posts["publish_day_of_week"] = posts["created_at"].dt.dayofweek

    fans_df = read_sql_frame(
        engine,
        "SELECT followed_id AS author_id, COUNT(*) AS author_fans_count "
        "FROM user_follows WHERE deleted=0 GROUP BY followed_id",
    )
    posts = posts.merge(fans_df, on="author_id", how="left")
    posts["author_fans_count"] = posts["author_fans_count"].fillna(0).astype(int)

    author_avg = (
        posts.groupby("author_id")["hot_score"]
        .mean()
        .reset_index()
        .rename(columns={"hot_score": "author_avg_hot_score"})
    )
    posts = posts.merge(author_avg, on="author_id", how="left")
    posts["author_avg_hot_score"] = posts["author_avg_hot_score"].fillna(0).round(2)
    posts["topic_path"] = posts["topic_path"].fillna("")
    posts["semantic_tags"] = posts["semantic_tags"].fillna("")
    posts["style_tags"] = posts["style_tags"].fillna("")
    posts["quality_score"] = posts["quality_score"].fillna(0).round(4)
    posts["aesthetic_score"] = posts["aesthetic_score"].fillna(0).round(4)
    posts["safety_score"] = posts["safety_score"].fillna(1).round(4)
    posts["updated_at"] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    return posts[
        [
            "id",
            "author_id",
            "publish_hour",
            "publish_day_of_week",
            "hot_score",
            "view_count",
            "like_count",
            "favorite_count",
            "comment_count",
            "tags",
            "topic_path",
            "semantic_tags",
            "style_tags",
            "author_fans_count",
            "author_avg_hot_score",
            "quality_score",
            "aesthetic_score",
            "safety_score",
            "updated_at",
        ]
    ].rename(columns={"id": "post_id"})


def write_post_features(df, engine):
    if df.empty:
        log.warning("[post_features] nothing to write")
        return
    with engine.begin() as conn:
        conn.execute(text("TRUNCATE TABLE post_features"))
        conn.execute(
            text(
                "INSERT INTO post_features ("
                "post_id, author_id, publish_hour, publish_day_of_week, hot_score, view_count, "
                "like_count, favorite_count, comment_count, tags, topic_path, semantic_tags, style_tags, "
                "author_fans_count, author_avg_hot_score, quality_score, aesthetic_score, safety_score, updated_at"
                ") VALUES ("
                ":post_id, :author_id, :publish_hour, :publish_day_of_week, :hot_score, :view_count, "
                ":like_count, :favorite_count, :comment_count, :tags, :topic_path, :semantic_tags, :style_tags, "
                ":author_fans_count, :author_avg_hot_score, :quality_score, :aesthetic_score, :safety_score, :updated_at"
                ")"
            ),
            frame_records(df),
        )
    log.info("[post_features] wrote %s rows", len(df))


def update_redis_sequences(engine, redis_client):
    log.info("[redis] rebuilding user behavior sequences...")

    def _write_seq(df, key_prefix):
        if df.empty:
            return
        grouped = (
            df.sort_values("created_at", ascending=False)
            .groupby("user_id")["target_id"]
            .apply(lambda values: list(values.head(SEQ_MAX_LEN)))
        )
        pipe = redis_client.pipeline()
        count = 0
        for user_id, target_ids in grouped.items():
            key = f"{key_prefix}{user_id}"
            pipe.delete(key)
            pipe.rpush(key, *[str(target_id) for target_id in target_ids])
            pipe.expire(key, REDIS_EXPIRE_SEC)
            count += 1
            if count % 200 == 0:
                pipe.execute()
                pipe = redis_client.pipeline()
        pipe.execute()
        log.info("[redis] %s* synced for %s users", key_prefix, len(grouped))

    sequence_specs = [
        ("user:recent_view:", "POST", "FEED_EXPOSURE"),
        ("user:recent_click:", "POST", "POST_CLICK"),
        ("user:recent_detail_view:", "POST", "POST_DETAIL_VIEW"),
        ("user:recent_like:", "POST", "POST_LIKE"),
        ("user:recent_fav:", "POST", "POST_FAVORITE"),
        ("user:recent_comment:", "POST", "POST_COMMENT"),
        ("user:recent_share:", "POST", "POST_SHARE"),
        ("user:recent_negative_feedback:", "POST", "NOT_INTERESTED"),
        ("user:recent_negative_feedback:", "POST", "POST_NEGATIVE_FEEDBACK"),
        ("user:recent_hidden_post:", "POST", "POST_HIDE"),
        ("user:recent_follow:", "USER", "USER_FOLLOW"),
    ]

    for key_prefix, target_type, event_type in sequence_specs:
        df = read_sql_frame(
            engine,
            "SELECT user_id, target_id, created_at "
            "FROM user_events "
            "WHERE event_type=%s "
            "  AND target_type=%s "
            "  AND user_id IS NOT NULL "
            "  AND target_id IS NOT NULL",
            params=[event_type, target_type],
        )
        _write_seq(df, key_prefix)

    behavior_event_types = [
        "FEED_EXPOSURE",
        "POST_CLICK",
        "POST_DETAIL_VIEW",
        "POST_LIKE",
        "POST_FAVORITE",
        "POST_COMMENT",
        "POST_SHARE",
        "NOT_INTERESTED",
        "POST_NEGATIVE_FEEDBACK",
        "POST_HIDE",
    ]
    placeholders = ",".join(["%s"] * len(behavior_event_types))
    behavior_df = read_sql_frame(
        engine,
        "SELECT user_id, target_id, event_type, created_at, dwell_ms, rank_position, surface, page_no, device_type, recall_source "
        "FROM user_events "
        "WHERE target_type='POST' "
        "  AND user_id IS NOT NULL "
        "  AND target_id IS NOT NULL "
        f"  AND event_type IN ({placeholders})",
        params=behavior_event_types,
    )
    if not behavior_df.empty:
        behavior_df["created_at"] = pd.to_datetime(behavior_df["created_at"], errors="coerce")
        behavior_df = behavior_df.dropna(subset=["created_at"])
        grouped = (
            behavior_df.sort_values("created_at", ascending=False)
            .groupby("user_id")
            .head(BEHAVIOR_SEQ_MAX_LEN)
            .groupby("user_id")
        )
        pipe = redis_client.pipeline()
        count = 0
        for user_id, frame in grouped:
            key = f"user:behavior_sequence:{int(user_id)}"
            pipe.delete(key)
            rows = []
            for _, row in frame.iterrows():
                created_at = pd.to_datetime(row["created_at"], errors="coerce")
                if pd.isna(created_at):
                    continue
                event_payload = {
                    "target_id": _safe_int(row.get("target_id"), 0),
                    "event_type": _safe_str(row.get("event_type"), "").upper(),
                    "event_ts": int(created_at.value // 1_000_000),
                    "dwell_ms": _safe_int(row.get("dwell_ms"), 0),
                    "rank_position": _safe_int(row.get("rank_position"), 0),
                    "surface": _safe_str(row.get("surface"), ""),
                    "page_no": _safe_int(row.get("page_no"), 0),
                    "device_type": _safe_str(row.get("device_type"), ""),
                    "recall_source": _safe_str(row.get("recall_source"), ""),
                }
                rows.append(json.dumps(event_payload, ensure_ascii=False))
            if rows:
                pipe.rpush(key, *rows)
                pipe.expire(key, REDIS_EXPIRE_SEC)
            count += 1
            if count % 200 == 0:
                pipe.execute()
                pipe = redis_client.pipeline()
        pipe.execute()
        log.info("[redis] user:behavior_sequence:* synced for %s users", len(grouped))


def main():
    log.info("===== feature engineering job started =====")
    engine = get_engine()

    log.info("--- step 1: user features ---")
    user_df = compute_user_features(engine)
    write_user_features(user_df, engine)

    log.info("--- step 2: post features ---")
    post_df = compute_post_features(engine)
    write_post_features(post_df, engine)

    log.info("--- step 3: redis sequences ---")
    try:
        redis_client = get_redis_client()
        redis_client.ping()
        update_redis_sequences(engine, redis_client)
    except Exception as exc:
        log.warning("[redis] skipped because Redis is unavailable: %s", exc)

    log.info("===== feature engineering job finished =====")


if __name__ == "__main__":
    main()
