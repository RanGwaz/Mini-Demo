#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Build item-to-item collaborative-filtering neighbors from user behavior events.

Run:
  python backend/scripts/build_i2i_neighbors.py

Env:
  MYSQL_HOST / MYSQL_PORT / MYSQL_USER / MYSQL_PASSWORD / MYSQL_DATABASE
  I2I_LOOKBACK_DAYS=30
  I2I_MAX_EVENTS_PER_USER=80
  I2I_TOP_N=100
"""

import logging
import math
import os
import sys
from collections import defaultdict
from datetime import datetime, timedelta

import pandas as pd
from sqlalchemy import bindparam, create_engine, text

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

LOOKBACK_DAYS = _env_int("I2I_LOOKBACK_DAYS", 30)
MAX_EVENTS_PER_USER = _env_int("I2I_MAX_EVENTS_PER_USER", 80)
TOP_N = _env_int("I2I_TOP_N", 100)
PAIR_WINDOW = _env_int("I2I_PAIR_WINDOW", 24)
HALF_LIFE_HOURS = _env_int("I2I_HALF_LIFE_HOURS", 72)

EVENT_WEIGHTS = {
    "FEED_EXPOSURE": 0.15,
    "POST_CLICK": 1.0,
    "POST_DETAIL_VIEW": 1.4,
    "POST_LIKE": 2.0,
    "POST_FAVORITE": 2.6,
    "POST_COMMENT": 2.4,
    "POST_SHARE": 2.2,
}


def get_engine():
    url = (
        f"mysql+pymysql://{MYSQL_USER}:{MYSQL_PASSWORD}"
        f"@{MYSQL_HOST}:{MYSQL_PORT}/{MYSQL_DATABASE}?charset=utf8mb4"
    )
    return create_engine(url, pool_pre_ping=True)


def load_events(engine):
    since = datetime.now() - timedelta(days=LOOKBACK_DAYS)
    sql = text(
        """
        SELECT user_id, target_id AS post_id, event_type, created_at
        FROM user_events
        WHERE target_type = 'POST'
          AND target_id IS NOT NULL
          AND user_id IS NOT NULL
          AND event_type IN :event_types
          AND created_at >= :since
        ORDER BY user_id ASC, created_at DESC
        """
    ).bindparams(bindparam("event_types", expanding=True))
    with engine.connect() as conn:
        result = conn.execute(sql, {"event_types": tuple(EVENT_WEIGHTS.keys()), "since": since})
        return pd.DataFrame(result.mappings().all())


def build_pairs(events: pd.DataFrame):
    pair_stats = defaultdict(lambda: {
        "score": 0.0,
        "co_view_count": 0,
        "co_click_count": 0,
        "co_detail_count": 0,
        "co_like_count": 0,
        "co_favorite_count": 0,
        "co_comment_count": 0,
        "co_share_count": 0,
    })

    if events.empty:
        return []

    events["event_type"] = events["event_type"].astype(str).str.upper()
    events["weight"] = events["event_type"].map(EVENT_WEIGHTS).fillna(0.0)
    events = events[events["weight"] > 0.0]

    grouped = events.groupby("user_id", sort=False)
    for _, group in grouped:
        group = group.head(MAX_EVENTS_PER_USER)
        rows = group[["post_id", "event_type", "created_at", "weight"]].to_dict("records")
        seen_order = []
        seen_posts = set()
        for row in rows:
            post_id = int(row["post_id"])
            if post_id in seen_posts:
                continue
            seen_posts.add(post_id)
            seen_order.append(row)

        for i, left in enumerate(seen_order):
            for right in seen_order[i + 1:i + 1 + PAIR_WINDOW]:
                left_post = int(left["post_id"])
                right_post = int(right["post_id"])
                if left_post == right_post:
                    continue
                delta_hours = abs((left["created_at"] - right["created_at"]).total_seconds()) / 3600.0
                decay = math.pow(0.5, delta_hours / max(1, HALF_LIFE_HOURS))
                score = math.sqrt(float(left["weight"]) * float(right["weight"])) * decay
                update_pair(pair_stats[(left_post, right_post)], score, left["event_type"], right["event_type"])
                update_pair(pair_stats[(right_post, left_post)], score, right["event_type"], left["event_type"])

    by_post = defaultdict(list)
    for (post_id, neighbor_post_id), stats in pair_stats.items():
        by_post[post_id].append((neighbor_post_id, stats))

    rows = []
    for post_id, neighbors in by_post.items():
        neighbors.sort(key=lambda item: item[1]["score"], reverse=True)
        for neighbor_post_id, stats in neighbors[:TOP_N]:
            rows.append({
                "post_id": post_id,
                "neighbor_post_id": neighbor_post_id,
                **stats,
            })
    return rows


def update_pair(stats, score, left_event, right_event):
    stats["score"] += float(score)
    for event_type in (left_event, right_event):
        if event_type == "FEED_EXPOSURE":
            stats["co_view_count"] += 1
        elif event_type == "POST_CLICK":
            stats["co_click_count"] += 1
        elif event_type == "POST_DETAIL_VIEW":
            stats["co_detail_count"] += 1
        elif event_type == "POST_LIKE":
            stats["co_like_count"] += 1
        elif event_type == "POST_FAVORITE":
            stats["co_favorite_count"] += 1
        elif event_type == "POST_COMMENT":
            stats["co_comment_count"] += 1
        elif event_type == "POST_SHARE":
            stats["co_share_count"] += 1


def write_pairs(engine, rows):
    with engine.begin() as conn:
        conn.execute(text(
            """
            CREATE TABLE IF NOT EXISTS post_i2i_neighbors (
                post_id BIGINT NOT NULL,
                neighbor_post_id BIGINT NOT NULL,
                score DOUBLE NOT NULL DEFAULT 0,
                co_view_count INT NOT NULL DEFAULT 0,
                co_click_count INT NOT NULL DEFAULT 0,
                co_detail_count INT NOT NULL DEFAULT 0,
                co_like_count INT NOT NULL DEFAULT 0,
                co_favorite_count INT NOT NULL DEFAULT 0,
                co_comment_count INT NOT NULL DEFAULT 0,
                co_share_count INT NOT NULL DEFAULT 0,
                updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                PRIMARY KEY (post_id, neighbor_post_id),
                KEY idx_post_i2i_score (post_id, score DESC),
                KEY idx_post_i2i_neighbor (neighbor_post_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """
        ))
        conn.execute(text("TRUNCATE TABLE post_i2i_neighbors"))
        if not rows:
            log.warning("[i2i] no pairs generated")
            return
        sql = text(
            """
            INSERT INTO post_i2i_neighbors (
                post_id, neighbor_post_id, score,
                co_view_count, co_click_count, co_detail_count,
                co_like_count, co_favorite_count, co_comment_count, co_share_count,
                updated_at
            ) VALUES (
                :post_id, :neighbor_post_id, :score,
                :co_view_count, :co_click_count, :co_detail_count,
                :co_like_count, :co_favorite_count, :co_comment_count, :co_share_count,
                NOW()
            )
            """
        )
        for start in range(0, len(rows), 1000):
            conn.execute(sql, rows[start:start + 1000])
        log.info("[i2i] wrote %s neighbor rows", len(rows))


def main():
    engine = get_engine()
    log.info("[i2i] loading behavior events from last %s days", LOOKBACK_DAYS)
    events = load_events(engine)
    log.info("[i2i] loaded %s events", len(events))
    rows = build_pairs(events)
    log.info("[i2i] generated %s neighbor rows", len(rows))
    write_pairs(engine, rows)


if __name__ == "__main__":
    main()
