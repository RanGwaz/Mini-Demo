#!/usr/bin/env python3
# -*- coding: utf-8 -*-

from __future__ import annotations

import copy
import json
import math
import os
import pickle
import random
import shutil
import tempfile
import time
from bisect import bisect_left, bisect_right
from collections import defaultdict
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Sequence, Set, Tuple

import numpy as np
import pymysql
import torch
import torch.nn.functional as F
from pymilvus import Collection, connections

from reco_features import (
    HISTORY_EVENT_TYPES,
    NEGATIVE_LABEL_WEIGHTS,
    POSITIVE_LABEL_WEIGHTS,
    RECALL_TARGET_EVENT_TYPES,
    RECALL_TARGET_EVENT_WEIGHTS,
    CandidateGeneratorModel,
    RankModel,
    SEQ_ENCODER_DIM,
    SEQ_EVENT_DIM,
    SEQUENCE_MAX_LEN,
    UserSequenceEncoder,
    build_candidate_input,
    build_candidate_item_input,
    build_rank_features,
    build_sequence_features,
    l2_normalize,
    rank_feature_dim,
)

MYSQL_HOST = os.getenv("MYSQL_HOST", "127.0.0.1")
MYSQL_PORT = int(os.getenv("MYSQL_PORT", "3306"))
MYSQL_USER = os.getenv("MYSQL_USER", "root")
MYSQL_PASSWORD = os.getenv("MYSQL_PASSWORD", "root123456")
MYSQL_DATABASE = os.getenv("MYSQL_DATABASE", "image_social")

MILVUS_HOST = os.getenv("MILVUS_HOST", "127.0.0.1")
MILVUS_PORT = os.getenv("MILVUS_PORT", "19530")
MILVUS_COLLECTION = os.getenv("MILVUS_COLLECTION", "post_embeddings")

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, "../../../backend", ".."))
MODEL_DIR = os.path.join(PROJECT_ROOT, "infra", "docker", "models")

DEFAULT_RANK_MODEL_OUT = os.path.join(MODEL_DIR, "deep_rank.pt")
DEFAULT_CANDIDATE_MODEL_OUT = os.path.join(MODEL_DIR, "candidate_generator.pt")
DEFAULT_SEQ_ENCODER_OUT = os.path.join(MODEL_DIR, "seq_encoder.pt")
DEFAULT_REPORT_OUT = os.path.join(MODEL_DIR, "reco_training_report.json")
DEFAULT_DASHBOARD_OUT = os.path.join(MODEL_DIR, "reco_training_dashboard.html")
DEFAULT_CALIBRATION_OUT = os.path.join(MODEL_DIR, "deep_rank_calibration.json")

RANK_MODEL_OUT = os.getenv("RANK_MODEL_OUT", DEFAULT_RANK_MODEL_OUT)
CANDIDATE_MODEL_OUT = os.getenv("CANDIDATE_MODEL_OUT", DEFAULT_CANDIDATE_MODEL_OUT)
SEQ_ENCODER_OUT = os.getenv("SEQ_ENCODER_OUT", DEFAULT_SEQ_ENCODER_OUT)
TRAIN_REPORT_OUT = os.getenv("TRAIN_REPORT_OUT", DEFAULT_REPORT_OUT)
TRAIN_DASHBOARD_OUT = os.getenv("TRAIN_DASHBOARD_OUT", DEFAULT_DASHBOARD_OUT)
RANK_CALIBRATION_OUT = os.getenv("RANK_CALIBRATION_OUT", DEFAULT_CALIBRATION_OUT)

MAX_RANK_SAMPLES = int(os.getenv("TRAIN_MAX_SAMPLES", "240000"))
MAX_RECALL_SAMPLES = int(os.getenv("TRAIN_MAX_RECALL_SAMPLES", "180000"))
MAX_HISTORY_EVENTS = int(os.getenv("MAX_USER_HISTORY", "50"))
MAX_SEQUENCE_EVENTS = int(os.getenv("MAX_SEQUENCE_EVENTS", str(SEQUENCE_MAX_LEN)))
HISTORY_LOOKBACK_DAYS = int(os.getenv("TRAIN_HISTORY_LOOKBACK_DAYS", "180"))
MAX_HISTORY_EVENT_ROWS = int(os.getenv("TRAIN_MAX_HISTORY_EVENT_ROWS", "420000"))
RANK_LABEL_WINDOW_HOURS = int(os.getenv("RANK_LABEL_WINDOW_HOURS", "48"))
EXPOSURE_DEDUP_HOURS = int(os.getenv("EXPOSURE_DEDUP_HOURS", "6"))
RANK_NEGATIVE_SAMPLE_RATE = float(os.getenv("RANK_NEGATIVE_SAMPLE_RATE", "0.25"))
NO_ACTION_SAMPLE_WEIGHT = float(os.getenv("NO_ACTION_SAMPLE_WEIGHT", "0.03"))
HARD_NEGATIVES_PER_POS = int(os.getenv("RANK_HARD_NEGATIVES_PER_POS", "4"))
HARD_NEGATIVE_WEIGHT = float(os.getenv("RANK_HARD_NEGATIVE_WEIGHT", "2.2"))
RANK_SAMPLE_HALF_LIFE_HOURS = float(os.getenv("RANK_SAMPLE_HALF_LIFE_HOURS", "72.0"))
RANK_SAMPLE_MIN_DECAY = float(os.getenv("RANK_SAMPLE_MIN_DECAY", "0.25"))
RECALL_SAMPLE_HALF_LIFE_HOURS = float(os.getenv("RECALL_SAMPLE_HALF_LIFE_HOURS", "96.0"))
TRAIN_SPLIT_RATIO = float(os.getenv("TRAIN_SPLIT_RATIO", "0.80"))
MIN_RECALL_POSITIVE_HISTORY = int(os.getenv("MIN_RECALL_POSITIVE_HISTORY", "1"))

CANDIDATE_EPOCHS = int(os.getenv("CANDIDATE_EPOCHS", "256"))
RANK_EPOCHS = int(os.getenv("TRAIN_EPOCHS", "256"))
BATCH_SIZE = int(os.getenv("TRAIN_BATCH_SIZE", "512"))
LR = float(os.getenv("TRAIN_LR", "0.001"))
CANDIDATE_TEMPERATURE = float(os.getenv("CANDIDATE_TEMPERATURE", "0.08"))
CANDIDATE_RANDOM_NEGATIVES = int(os.getenv("CANDIDATE_RANDOM_NEGATIVES", "256"))
CANDIDATE_HARD_NEGATIVES_PER_SAMPLE = int(os.getenv("CANDIDATE_HARD_NEGATIVES_PER_SAMPLE", "8"))
CANDIDATE_HARD_NEGATIVE_BANK_SIZE = int(os.getenv("CANDIDATE_HARD_NEGATIVE_BANK_SIZE", "160"))
WEIGHT_DECAY = float(os.getenv("TRAIN_WEIGHT_DECAY", "0.0001"))
RANK_POSITIVE_BATCH_RATIO = float(os.getenv("RANK_POSITIVE_BATCH_RATIO", "0.25"))
RANK_FOCAL_GAMMA = float(os.getenv("RANK_FOCAL_GAMMA", "1.5"))
ENABLE_CALIBRATION = os.getenv("RANK_CALIBRATION_ENABLED", "true").strip().lower() not in {"0", "false", "off", "no"}
TOPK = int(os.getenv("EVAL_K", "10"))
EVAL_K_LIST = os.getenv("EVAL_K_LIST", "10,20,50")
SAMPLE_EXPORT_DIR = os.getenv("TRAIN_SAMPLE_EXPORT_DIR", os.path.join(MODEL_DIR, "dataset_snapshots"))
SOURCE_CACHE_DIR = os.getenv("TRAIN_SOURCE_CACHE_DIR", os.path.join(MODEL_DIR, "source_cache"))
SOURCE_CACHE_ENABLED = os.getenv("TRAIN_SOURCE_CACHE_ENABLED", "true").strip().lower() not in {"0", "false", "off", "no"}
SOURCE_CACHE_TTL_MINUTES = int(os.getenv("TRAIN_SOURCE_CACHE_TTL_MINUTES", "240"))
SEED = int(os.getenv("TRAIN_SEED", "20260410"))

DEVICE = torch.device("cuda" if torch.cuda.is_available() else "cpu")


HistoryEvent = Tuple[int, str, datetime]
ExposureRow = Tuple[int, int, datetime, dict]
RawHistoryEventRow = Tuple[int, int, str, datetime, int, int]
SEQUENCE_HISTORY_EVENT_TYPES: Tuple[str, ...] = tuple(sorted(set(HISTORY_EVENT_TYPES) | {"FEED_EXPOSURE"}))


def set_seed(seed: int) -> None:
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(seed)


def parse_eval_k_values(raw: str) -> List[int]:
    values: List[int] = []
    for token in (raw or "").split(","):
        token = token.strip()
        if not token:
            continue
        try:
            value = int(token)
        except Exception:
            continue
        if value > 0 and value not in values:
            values.append(value)
    if not values:
        values = [TOPK]
    return values


def recency_decay_weight(event_time: datetime,
                         reference_time: Optional[datetime],
                         half_life_hours: float,
                         min_decay: float = 0.2) -> float:
    if reference_time is None or half_life_hours <= 0:
        return 1.0
    elapsed = max(0.0, (reference_time - event_time).total_seconds() / 3600.0)
    decay = math.pow(0.5, elapsed / max(half_life_hours, 1e-6))
    return float(max(min_decay, min(1.0, decay)))


def _normalize_token(raw: object) -> str:
    if raw is None:
        return ""
    token = str(raw).strip().lower()
    if not token:
        return ""
    token = token.replace("|", " ").replace("/", " ").replace(">", " ").replace("_", " ").replace("-", " ")
    token = " ".join(token.split())
    return token


def _split_tokens(raw: object) -> List[str]:
    normalized = _normalize_token(raw)
    if not normalized:
        return []
    values = [item.strip() for item in normalized.replace(",", " ").split(" ") if item.strip()]
    return values


def _meta_topic_terms(meta: Optional[dict]) -> List[str]:
    if not meta:
        return []
    terms: List[str] = []
    terms.extend(_split_tokens(meta.get("topic_path")))
    terms.extend(_split_tokens(meta.get("topic_cluster_key")))
    terms.extend(_split_tokens(meta.get("subtopic_cluster_key")))
    return [term for term in terms if len(term) >= 2]


def _meta_style_terms(meta: Optional[dict]) -> List[str]:
    if not meta:
        return []
    terms: List[str] = []
    terms.extend(_split_tokens(meta.get("style_tags")))
    terms.extend(_split_tokens(meta.get("semantic_tags")))
    terms.extend(_split_tokens(meta.get("tags")))
    return [term for term in terms if len(term) >= 2]


def build_hard_negative_indexes(post_meta: Dict[int, dict]) -> Tuple[Dict[str, List[int]], Dict[str, List[int]]]:
    topic_index: Dict[str, List[int]] = defaultdict(list)
    style_index: Dict[str, List[int]] = defaultdict(list)
    for post_id, meta in post_meta.items():
        for term in _meta_topic_terms(meta)[:4]:
            topic_index[term].append(int(post_id))
        for term in _meta_style_terms(meta)[:6]:
            style_index[term].append(int(post_id))
    return topic_index, style_index


def sample_hard_negative_post_ids(post_id: int,
                                  user_seen_post_ids: Set[int],
                                  post_meta: Dict[int, dict],
                                  topic_index: Dict[str, List[int]],
                                  style_index: Dict[str, List[int]],
                                  rng: np.random.Generator,
                                  limit: int) -> List[int]:
    if limit <= 0:
        return []
    anchor_meta = post_meta.get(post_id)
    if anchor_meta is None:
        return []

    candidates: Set[int] = set()
    for term in _meta_topic_terms(anchor_meta)[:3]:
        candidates.update(topic_index.get(term, []))
    for term in _meta_style_terms(anchor_meta)[:4]:
        candidates.update(style_index.get(term, []))

    blocked = set(user_seen_post_ids)
    blocked.add(int(post_id))
    pool = [candidate for candidate in candidates if candidate not in blocked and candidate in post_meta]
    if not pool:
        return []
    rng.shuffle(pool)
    return pool[:limit]


def build_pair_exposure_timestamps(exposures: Sequence[ExposureRow]) -> Dict[Tuple[int, int], List[datetime]]:
    exposure_timestamps: Dict[Tuple[int, int], List[datetime]] = defaultdict(list)
    for user_id, post_id, exposure_time, _ in exposures:
        exposure_timestamps[(int(user_id), int(post_id))].append(exposure_time)
    return exposure_timestamps


def is_finite_number(value: object) -> bool:
    try:
        return math.isfinite(float(value))
    except Exception:
        return False


def make_json_safe(value):
    if isinstance(value, dict):
        return {key: make_json_safe(item) for key, item in value.items()}
    if isinstance(value, list):
        return [make_json_safe(item) for item in value]
    if isinstance(value, tuple):
        return [make_json_safe(item) for item in value]
    if isinstance(value, np.integer):
        return int(value)
    if isinstance(value, np.floating):
        value = float(value)
    if isinstance(value, float):
        return value if math.isfinite(value) else None
    return value


def mysql_conn():
    return pymysql.connect(
        host=MYSQL_HOST,
        port=MYSQL_PORT,
        user=MYSQL_USER,
        password=MYSQL_PASSWORD,
        database=MYSQL_DATABASE,
        charset="utf8mb4",
        autocommit=True,
        cursorclass=pymysql.cursors.DictCursor,
    )


def source_cache_path() -> str:
    slug = (
        f"{MYSQL_HOST}_{MYSQL_PORT}_{MYSQL_DATABASE}"
        f"_rank{MAX_RANK_SAMPLES}_recall{MAX_RECALL_SAMPLES}"
        f"_hist{MAX_HISTORY_EVENT_ROWS}_lookback{HISTORY_LOOKBACK_DAYS}"
    )
    safe_slug = "".join(ch if ch.isalnum() else "_" for ch in slug)
    return os.path.join(SOURCE_CACHE_DIR, f"{safe_slug}.pkl")


def load_source_data_cache() -> Optional[dict]:
    if not SOURCE_CACHE_ENABLED:
        return None
    cache_path = source_cache_path()
    if not os.path.exists(cache_path):
        return None
    if SOURCE_CACHE_TTL_MINUTES > 0:
        max_age_seconds = max(60, SOURCE_CACHE_TTL_MINUTES * 60)
        age_seconds = max(0.0, datetime.now().timestamp() - os.path.getmtime(cache_path))
        if age_seconds > max_age_seconds:
            return None
    try:
        with open(cache_path, "rb") as file_obj:
            payload = pickle.load(file_obj)
        required = {"post_meta", "exposures", "history_events", "recall_positive_events"}
        if not isinstance(payload, dict) or not required.issubset(payload.keys()):
            return None
        return payload
    except Exception as exc:
        print(f"[train] warning: failed to read source cache, rebuilding: {exc}")
        return None


def save_source_data_cache(payload: dict) -> None:
    if not SOURCE_CACHE_ENABLED:
        return
    cache_path = source_cache_path()
    os.makedirs(os.path.dirname(os.path.abspath(cache_path)), exist_ok=True)
    temp_path = f"{cache_path}.tmp"
    try:
        with open(temp_path, "wb") as file_obj:
            pickle.dump(payload, file_obj, protocol=pickle.HIGHEST_PROTOCOL)
        os.replace(temp_path, cache_path)
    except Exception as exc:
        print(f"[train] warning: failed to save source cache: {exc}")
        try:
            if os.path.exists(temp_path):
                os.remove(temp_path)
        except Exception:
            pass


def load_post_meta(conn) -> Dict[int, dict]:
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT id,
                   author_id,
                   title,
                   content,
                   tags,
                   topic_path,
                   topic_cluster_key,
                   subtopic_cluster_key,
                   semantic_tags,
                   style_tags,
                   hot_score,
                   like_count,
                   favorite_count,
                   comment_count,
                   view_count,
                   quality_score,
                   aesthetic_score,
                   safety_score,
                   created_at
            FROM posts
            WHERE deleted=0
            """
        )
        rows = cur.fetchall()

    result: Dict[int, dict] = {}
    for row in rows:
        result[int(row["id"])] = {
            "id": int(row["id"]),
            "author_id": int(row["author_id"]) if row["author_id"] is not None else None,
            "title": row.get("title") or "",
            "content": row.get("content") or "",
            "tags": row.get("tags") or "",
            "topic_path": row.get("topic_path") or "",
            "topic_cluster_key": row.get("topic_cluster_key") or "",
            "subtopic_cluster_key": row.get("subtopic_cluster_key") or "",
            "semantic_tags": row.get("semantic_tags") or "",
            "style_tags": row.get("style_tags") or "",
            "hot_score": float(row.get("hot_score") or 0.0),
            "like_count": int(row.get("like_count") or 0),
            "favorite_count": int(row.get("favorite_count") or 0),
            "comment_count": int(row.get("comment_count") or 0),
            "view_count": int(row.get("view_count") or 0),
            "quality_score": float(row.get("quality_score") or 0.0),
            "aesthetic_score": float(row.get("aesthetic_score") or 0.0),
            "safety_score": float(row.get("safety_score") or 1.0),
            "created_at": row.get("created_at"),
        }
    return result


def load_exposures(conn, limit: int) -> List[ExposureRow]:
    fetch_limit = max(limit * 3, limit)
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT user_id,
                   target_id,
                   created_at,
                   surface,
                   page_no,
                   rank_position,
                   recall_source,
                   device_type,
                   experiment_id
            FROM user_events
            WHERE target_type='POST'
              AND event_type='FEED_EXPOSURE'
              AND user_id IS NOT NULL
              AND target_id IS NOT NULL
            ORDER BY created_at DESC
            LIMIT %s
            """,
            (fetch_limit,),
        )
        rows = cur.fetchall()
    rows.reverse()
    return [
        (
            int(row["user_id"]),
            int(row["target_id"]),
            row["created_at"],
            {
                "surface": row.get("surface") or "home_feed",
                "page_no": int(row.get("page_no") or 1),
                "rank_position": int(row.get("rank_position") or 0),
                "recall_source": row.get("recall_source") or "",
                "device_type": row.get("device_type") or "",
                "experiment_id": row.get("experiment_id") or "",
            },
        )
        for row in rows
        if row.get("created_at") is not None
    ]


def load_history_events(conn) -> List[RawHistoryEventRow]:
    placeholders = ",".join(["%s"] * len(SEQUENCE_HISTORY_EVENT_TYPES))
    lookback_days = max(7, HISTORY_LOOKBACK_DAYS)
    row_limit = max(10_000, MAX_HISTORY_EVENT_ROWS)
    with conn.cursor() as cur:
        cur.execute(
            f"""
            SELECT user_id, target_id, event_type, created_at, dwell_ms, rank_position
            FROM user_events
            WHERE target_type='POST'
              AND user_id IS NOT NULL
              AND target_id IS NOT NULL
              AND event_type IN ({placeholders})
              AND created_at >= DATE_SUB(NOW(), INTERVAL %s DAY)
            ORDER BY created_at DESC
            LIMIT %s
            """,
            (*SEQUENCE_HISTORY_EVENT_TYPES, lookback_days, row_limit),
        )
        rows = cur.fetchall()
    rows.sort(
        key=lambda row: (
            int(row["user_id"]) if row.get("user_id") is not None else -1,
            row.get("created_at") or datetime.min,
        )
    )
    return [
        (
            int(row["user_id"]),
            int(row["target_id"]),
            str(row["event_type"]),
            row["created_at"],
            int(row.get("dwell_ms") or 0),
            int(row.get("rank_position") or 0),
        )
        for row in rows
        if row.get("created_at") is not None
    ]


def load_recall_positive_events(conn, limit: int) -> List[Tuple[int, int, str, datetime]]:
    placeholders = ",".join(["%s"] * len(RECALL_TARGET_EVENT_TYPES))
    fetch_limit = max(limit * 2, limit)
    with conn.cursor() as cur:
        cur.execute(
            f"""
            SELECT user_id, target_id, event_type, created_at
            FROM user_events
            WHERE target_type='POST'
              AND user_id IS NOT NULL
              AND target_id IS NOT NULL
              AND event_type IN ({placeholders})
            ORDER BY created_at DESC
            LIMIT %s
            """,
            (*RECALL_TARGET_EVENT_TYPES, fetch_limit),
        )
        rows = cur.fetchall()
    rows.reverse()
    return [
        (int(row["user_id"]), int(row["target_id"]), str(row["event_type"]), row["created_at"])
        for row in rows
        if row.get("created_at") is not None
    ]


def load_embeddings(post_ids: Sequence[int]) -> Dict[int, np.ndarray]:
    unique_ids = sorted({int(post_id) for post_id in post_ids if post_id is not None})
    if not unique_ids:
        return {}

    try:
        connections.connect(host=MILVUS_HOST, port=MILVUS_PORT)
        collection = Collection(MILVUS_COLLECTION)
        collection.load()
    except Exception as exc:
        print(f"[train] warning: failed to connect Milvus, fallback to zero vectors: {exc}")
        return {}

    result: Dict[int, np.ndarray] = {}
    chunk_size = 200
    for start in range(0, len(unique_ids), chunk_size):
        chunk = unique_ids[start:start + chunk_size]
        rows = collection.query(
            expr=f"post_id in {chunk}",
            output_fields=["post_id", "embedding"],
        )
        for row in rows:
            result[int(row["post_id"])] = l2_normalize(np.array(row["embedding"], dtype=np.float32))
    return result


def build_user_event_indexes(
    rows: Sequence[RawHistoryEventRow]
) -> Tuple[Dict[int, List[dict]], Dict[int, List[datetime]], Dict[Tuple[int, int], List[Tuple[str, datetime]]], Dict[Tuple[int, int], List[datetime]]]:
    user_events: Dict[int, List[dict]] = defaultdict(list)
    user_timestamps: Dict[int, List[datetime]] = defaultdict(list)
    user_post_events: Dict[Tuple[int, int], List[Tuple[str, datetime]]] = defaultdict(list)
    user_post_timestamps: Dict[Tuple[int, int], List[datetime]] = defaultdict(list)

    for row in rows:
        if not isinstance(row, (tuple, list)) or len(row) < 4:
            continue
        user_id = int(row[0])
        post_id = int(row[1])
        event_type = str(row[2])
        created_at = row[3]
        dwell_ms = int(row[4]) if len(row) > 4 and row[4] is not None else 0
        rank_position = int(row[5]) if len(row) > 5 and row[5] is not None else 0
        if created_at is None:
            continue
        event = {
            "post_id": int(post_id),
            "event_type": str(event_type),
            "created_at": created_at,
            "dwell_ms": int(dwell_ms or 0),
            "rank_position": int(rank_position or 0),
        }
        user_events[user_id].append(event)
        user_timestamps[user_id].append(created_at)
        key = (user_id, post_id)
        user_post_events[key].append((str(event_type), created_at))
        user_post_timestamps[key].append(created_at)

    return user_events, user_timestamps, user_post_events, user_post_timestamps


def collect_histories(
    user_id: int,
    event_time: datetime,
    user_events: Dict[int, List[dict]],
    user_timestamps: Dict[int, List[datetime]],
) -> Tuple[List[HistoryEvent], List[HistoryEvent], List[dict]]:
    timestamps = user_timestamps.get(user_id)
    if not timestamps:
        return [], [], []

    index = bisect_left(timestamps, event_time)
    if index <= 0:
        return [], [], []

    positive_history: List[HistoryEvent] = []
    negative_history: List[HistoryEvent] = []
    sequence_history: List[dict] = []
    events = user_events[user_id]

    for cursor in range(index - 1, -1, -1):
        event = events[cursor]
        post_id = int(event["post_id"])
        event_type = str(event["event_type"])
        created_at = event["created_at"]
        if len(sequence_history) < MAX_SEQUENCE_EVENTS:
            sequence_history.append(
                {
                    "post_id": post_id,
                    "event_type": event_type,
                    "event_ts": created_at,
                    "dwell_ms": int(event.get("dwell_ms", 0)),
                    "rank_position": int(event.get("rank_position", 0)),
                }
            )
        if event_type in POSITIVE_LABEL_WEIGHTS:
            if len(positive_history) < MAX_HISTORY_EVENTS:
                positive_history.append((post_id, event_type, created_at))
        elif event_type in NEGATIVE_LABEL_WEIGHTS:
            if len(negative_history) < MAX_HISTORY_EVENTS:
                negative_history.append((post_id, event_type, created_at))
        if (
            len(sequence_history) >= MAX_SEQUENCE_EVENTS
            and len(positive_history) >= MAX_HISTORY_EVENTS
            and len(negative_history) >= MAX_HISTORY_EVENTS
        ):
            break

    return positive_history, negative_history, sequence_history


def resolve_rank_labels_mtl(
    user_id: int,
    post_id: int,
    exposure_time: datetime,
    user_post_events: Dict[Tuple[int, int], List[Tuple[str, datetime]]],
    user_post_timestamps: Dict[Tuple[int, int], List[datetime]],
    pair_exposure_timestamps: Dict[Tuple[int, int], List[datetime]],
) -> Tuple[float, float, float, float, str]:
    key = (user_id, post_id)
    timestamps = user_post_timestamps.get(key)
    if not timestamps:
        return 0.0, 0.0, 0.0, NO_ACTION_SAMPLE_WEIGHT, "NO_ACTION"

    events = user_post_events[key]
    start_index = bisect_right(timestamps, exposure_time)
    cutoff = exposure_time + timedelta(hours=RANK_LABEL_WINDOW_HOURS)
    pair_exposures = pair_exposure_timestamps.get(key) or []
    next_exposure_index = bisect_right(pair_exposures, exposure_time)
    next_exposure_time = pair_exposures[next_exposure_index] if next_exposure_index < len(pair_exposures) else None
    effective_cutoff = min(cutoff, next_exposure_time) if next_exposure_time is not None else cutoff

    event_types: Set[str] = set()
    for cursor in range(start_index, len(events)):
        event_type, created_at = events[cursor]
        if created_at >= effective_cutoff:
            break
        event_types.add(str(event_type).strip().upper())

    click_types = {"POST_CLICK", "POST_DETAIL_VIEW"}
    cvr_types = {"POST_LIKE", "POST_COMMENT", "POST_FAVORITE", "POST_SHARE"}
    quality_pos = {"POST_FAVORITE", "POST_COMMENT"}
    quality_neg = {"NOT_INTERESTED", "POST_NEGATIVE_FEEDBACK", "POST_HIDE"}

    label_ctr = 1.0 if event_types & click_types else 0.0
    label_cvr = 1.0 if event_types & cvr_types else 0.0
    label_quality = 1.0 if event_types & quality_pos else (-1.0 if event_types & quality_neg else 0.0)

    if event_types & quality_neg:
        weight = 1.8
        label_type = sorted(event_types & quality_neg)[0]
    elif event_types & quality_pos:
        weight = 2.2
        label_type = sorted(event_types & quality_pos)[0]
    elif event_types & cvr_types:
        weight = 1.6
        label_type = sorted(event_types & cvr_types)[0]
    elif event_types & click_types:
        weight = 1.0
        label_type = sorted(event_types & click_types)[0]
    else:
        weight = NO_ACTION_SAMPLE_WEIGHT
        label_type = "NO_ACTION"

    return label_ctr, label_cvr, label_quality, float(weight), label_type


def build_rank_samples(
    exposures: Sequence[ExposureRow],
    post_meta: Dict[int, dict],
    emb_map: Dict[int, np.ndarray],
    user_events: Dict[int, List[dict]],
    user_timestamps: Dict[int, List[datetime]],
    user_post_events: Dict[Tuple[int, int], List[Tuple[str, datetime]]],
    user_post_timestamps: Dict[Tuple[int, int], List[datetime]],
    rng: np.random.Generator,
) -> Tuple[List[dict], dict]:
    samples: List[dict] = []
    last_exposure_by_pair: Dict[Tuple[int, int], datetime] = {}
    dedup_delta = timedelta(hours=EXPOSURE_DEDUP_HOURS)
    pair_exposure_timestamps = build_pair_exposure_timestamps(exposures)
    stats = {
        "total_exposures": int(len(exposures)),
        "skipped_missing_post_meta": 0,
        "skipped_exposure_dedup": 0,
        "skipped_negative_sampling": 0,
        "skipped_no_history": 0,
        "generated_samples": 0,
        "generated_positive_samples": 0,
        "generated_negative_samples": 0,
        "generated_hard_negative_samples": 0,
    }
    reference_time = exposures[-1][2] if exposures else None
    topic_index, style_index = build_hard_negative_indexes(post_meta)

    for user_id, post_id, exposure_time, scene_context in exposures:
        meta = post_meta.get(post_id)
        if meta is None:
            stats["skipped_missing_post_meta"] += 1
            continue

        pair_key = (user_id, post_id)
        last_exposure_time = last_exposure_by_pair.get(pair_key)
        if last_exposure_time is not None and exposure_time - last_exposure_time < dedup_delta:
            stats["skipped_exposure_dedup"] += 1
            continue
        last_exposure_by_pair[pair_key] = exposure_time

        label_ctr, label_cvr, label_quality, weight, label_type = resolve_rank_labels_mtl(
            user_id=user_id,
            post_id=post_id,
            exposure_time=exposure_time,
            user_post_events=user_post_events,
            user_post_timestamps=user_post_timestamps,
            pair_exposure_timestamps=pair_exposure_timestamps,
        )
        label = 1.0 if (label_ctr > 0.0 or label_cvr > 0.0) else 0.0
        if label == 0.0 and label_type == "NO_ACTION" and rng.random() > RANK_NEGATIVE_SAMPLE_RATE:
            stats["skipped_negative_sampling"] += 1
            continue

        positive_history, negative_history, sequence_history = collect_histories(
            user_id=user_id,
            event_time=exposure_time,
            user_events=user_events,
            user_timestamps=user_timestamps,
        )
        if label == 0.0 and label_type == "NO_ACTION" and not positive_history and not negative_history:
            stats["skipped_no_history"] += 1
            continue
        decay = recency_decay_weight(
            exposure_time,
            reference_time=reference_time,
            half_life_hours=RANK_SAMPLE_HALF_LIFE_HOURS,
            min_decay=RANK_SAMPLE_MIN_DECAY,
        )
        sample_weight = float(weight) * decay
        seq_arr, actual_len = build_sequence_features(
            positive_history=sequence_history or positive_history,
            post_meta_map=post_meta,
            now=exposure_time,
            max_len=SEQUENCE_MAX_LEN,
        )
        features = build_rank_features(
            post_id=post_id,
            post_meta=meta,
            post_meta_map=post_meta,
            positive_history=positive_history,
            negative_history=negative_history,
            emb_map=emb_map,
            now=exposure_time,
            scene_context=scene_context,
        )
        static_features = features[:-SEQ_ENCODER_DIM]
        samples.append(
            {
                "ts": exposure_time,
                "user_id": user_id,
                "post_id": post_id,
                "label": float(label),
                "label_ctr": float(label_ctr),
                "label_cvr": float(label_cvr),
                "label_quality": float(label_quality),
                "weight": float(sample_weight),
                "label_type": label_type,
                "features": static_features.astype(np.float32),
                "seq_arr": seq_arr.astype(np.float32),
                "actual_len": int(actual_len),
            }
        )
        stats["generated_samples"] += 1
        if label >= 1.0:
            stats["generated_positive_samples"] += 1
        else:
            stats["generated_negative_samples"] += 1

        if label >= 1.0 and HARD_NEGATIVES_PER_POS > 0 and len(samples) < MAX_RANK_SAMPLES:
            user_seen_post_ids = {history_post_id for history_post_id, _, _ in positive_history}
            user_seen_post_ids.update(history_post_id for history_post_id, _, _ in negative_history)
            hard_negative_ids = sample_hard_negative_post_ids(
                post_id=post_id,
                user_seen_post_ids=user_seen_post_ids,
                post_meta=post_meta,
                topic_index=topic_index,
                style_index=style_index,
                rng=rng,
                limit=HARD_NEGATIVES_PER_POS,
            )
            for hard_post_id in hard_negative_ids:
                hard_meta = post_meta.get(hard_post_id)
                if hard_meta is None:
                    continue
                hard_scene_context = dict(scene_context or {})
                hard_scene_context["recall_source"] = hard_scene_context.get("recall_source") or "hard_negative"
                hard_scene_context["rank_position"] = max(0, int(hard_scene_context.get("rank_position") or 0)) + 1
                hard_features = build_rank_features(
                    post_id=hard_post_id,
                    post_meta=hard_meta,
                    post_meta_map=post_meta,
                    positive_history=positive_history,
                    negative_history=negative_history,
                    emb_map=emb_map,
                    now=exposure_time,
                    scene_context=hard_scene_context,
                )
                hard_static_features = hard_features[:-SEQ_ENCODER_DIM]
                samples.append(
                    {
                        "ts": exposure_time,
                        "user_id": user_id,
                        "post_id": hard_post_id,
                        "label": 0.0,
                        "label_ctr": 0.0,
                        "label_cvr": 0.0,
                        "label_quality": 0.0,
                        "weight": float(max(0.05, sample_weight * HARD_NEGATIVE_WEIGHT)),
                        "label_type": "HARD_NEGATIVE",
                        "features": hard_static_features.astype(np.float32),
                        "seq_arr": seq_arr.astype(np.float32),
                        "actual_len": int(actual_len),
                    }
                )
                stats["generated_samples"] += 1
                stats["generated_negative_samples"] += 1
                stats["generated_hard_negative_samples"] += 1
                if len(samples) >= MAX_RANK_SAMPLES:
                    break
        if len(samples) >= MAX_RANK_SAMPLES:
            break

    return samples, stats


def build_recall_samples(
    positive_events: Sequence[Tuple[int, int, str, datetime]],
    post_meta: Dict[int, dict],
    emb_map: Dict[int, np.ndarray],
    user_events: Dict[int, List[dict]],
    user_timestamps: Dict[int, List[datetime]],
    rng: np.random.Generator,
    item_feature_pool: Dict[int, np.ndarray],
) -> Tuple[List[dict], dict]:
    samples: List[dict] = []
    reference_time = positive_events[-1][3] if positive_events else None
    topic_index, style_index = build_hard_negative_indexes(post_meta)
    stats = {
        "total_positive_events": int(len(positive_events)),
        "skipped_missing_meta_or_embedding": 0,
        "skipped_short_history": 0,
        "skipped_duplicate_user_post": 0,
        "generated_samples": 0,
    }
    seen_user_post_pairs: Set[Tuple[int, int]] = set()

    for user_id, post_id, event_type, event_time in positive_events:
        if post_id not in post_meta or post_id not in emb_map or post_id not in item_feature_pool:
            stats["skipped_missing_meta_or_embedding"] += 1
            continue
        pair_key = (int(user_id), int(post_id))
        if pair_key in seen_user_post_pairs:
            stats["skipped_duplicate_user_post"] += 1
            continue
        seen_user_post_pairs.add(pair_key)
        positive_history, negative_history, _ = collect_histories(
            user_id=user_id,
            event_time=event_time,
            user_events=user_events,
            user_timestamps=user_timestamps,
        )
        if len(positive_history) < MIN_RECALL_POSITIVE_HISTORY:
            stats["skipped_short_history"] += 1
            continue

        user_seen_post_ids = {history_post_id for history_post_id, _, _ in positive_history}
        user_seen_post_ids.update(history_post_id for history_post_id, _, _ in negative_history)
        user_seen_post_ids.add(post_id)
        hard_negative_ids = [
            hard_post_id
            for hard_post_id in sample_hard_negative_post_ids(
                post_id=post_id,
                user_seen_post_ids=user_seen_post_ids,
                post_meta=post_meta,
                topic_index=topic_index,
                style_index=style_index,
                rng=rng,
                limit=CANDIDATE_HARD_NEGATIVES_PER_SAMPLE,
            )
            if hard_post_id in item_feature_pool
        ]

        features = build_candidate_input(
            positive_history=positive_history,
            negative_history=negative_history,
            post_meta_map=post_meta,
            emb_map=emb_map,
            now=event_time,
        )
        samples.append(
            {
                "ts": event_time,
                "user_id": user_id,
                "post_id": post_id,
                "event_type": event_type,
                "weight": float(
                    RECALL_TARGET_EVENT_WEIGHTS.get(event_type, 1.0)
                    * recency_decay_weight(
                        event_time,
                        reference_time=reference_time,
                        half_life_hours=RECALL_SAMPLE_HALF_LIFE_HOURS,
                        min_decay=0.35,
                    )
                ),
                "features": features.astype(np.float32),
                "item_features": item_feature_pool[post_id].astype(np.float32),
                "hard_negative_post_ids": hard_negative_ids,
                "target_embedding": l2_normalize(emb_map[post_id]),
            }
        )
        stats["generated_samples"] += 1
        if len(samples) >= MAX_RECALL_SAMPLES:
            break

    return samples, stats


def build_candidate_item_feature_pool(
    post_meta: Dict[int, dict],
    emb_map: Dict[int, np.ndarray],
    reference_time: Optional[datetime],
) -> Dict[int, np.ndarray]:
    item_features: Dict[int, np.ndarray] = {}
    for post_id, meta in post_meta.items():
        if post_id not in emb_map:
            continue
        item_features[int(post_id)] = build_candidate_item_input(
            post_id=post_id,
            post_meta=meta,
            emb_map=emb_map,
            now=reference_time,
        ).astype(np.float32)
    return item_features


def split_samples_by_time(samples: Sequence[dict], ratio: float) -> Tuple[List[dict], List[dict]]:
    if not samples:
        return [], []
    ordered = sorted(samples, key=lambda item: item["ts"])
    split_index = int(len(ordered) * ratio)
    split_index = min(max(split_index, 1), len(ordered) - 1) if len(ordered) > 1 else len(ordered)
    if split_index >= len(ordered):
        return ordered, []
    return ordered[:split_index], ordered[split_index:]


def candidate_eval_metrics(
    model: CandidateGeneratorModel,
    samples: Sequence[dict],
    item_feature_pool: Dict[int, np.ndarray],
) -> dict:
    if not samples:
        return {
            "samples": 0,
            "loss": 0.0,
            "hit@10": 0.0,
            "hit@50": 0.0,
            "mrr": 0.0,
        }

    _, pool_feature_matrix, pool_index_map = prepare_candidate_item_pool(item_feature_pool)
    eligible_samples = [sample for sample in samples if int(sample["post_id"]) in pool_index_map]
    if not eligible_samples or pool_feature_matrix.size == 0:
        return {
            "samples": 0,
            "loss": 0.0,
            "hit@10": 0.0,
            "hit@50": 0.0,
            "mrr": 0.0,
        }

    total_loss = 0.0
    total_weight = 0.0
    hit10_values: List[float] = []
    hit50_values: List[float] = []
    mrr_values: List[float] = []
    all_target_vectors = encode_candidate_items(model, pool_feature_matrix)

    model.eval()
    with torch.no_grad():
        for start in range(0, len(eligible_samples), BATCH_SIZE):
            batch = eligible_samples[start:start + BATCH_SIZE]
            xb = torch.tensor(np.vstack([item["features"] for item in batch]), dtype=torch.float32, device=DEVICE)
            wb = torch.tensor([item["weight"] for item in batch], dtype=torch.float32, device=DEVICE)
            target = torch.tensor(
                [pool_index_map[int(item["post_id"])] for item in batch],
                dtype=torch.long,
                device=DEVICE,
            )

            user_vectors = model(xb)
            logits = torch.matmul(user_vectors, all_target_vectors.T) / CANDIDATE_TEMPERATURE
            loss_vector = F.cross_entropy(logits, target, reduction="none")
            total_loss += float((loss_vector * wb).sum().item())
            total_weight += float(wb.sum().item())

            ranking = torch.argsort(logits, dim=1, descending=True)
            target_rank = torch.argmax((ranking == target.unsqueeze(1)).int(), dim=1) + 1
            top10 = ranking[:, : min(10, ranking.shape[1])].eq(target.unsqueeze(1)).any(dim=1).float()
            top50 = ranking[:, : min(50, ranking.shape[1])].eq(target.unsqueeze(1)).any(dim=1).float()

            hit10_values.extend(top10.detach().cpu().tolist())
            hit50_values.extend(top50.detach().cpu().tolist())
            mrr_values.extend((1.0 / target_rank.float()).detach().cpu().tolist())

    return {
        "samples": int(len(eligible_samples)),
        "loss": float(total_loss / max(total_weight, 1e-6)),
        "hit@10": float(np.mean(hit10_values)) if hit10_values else 0.0,
        "hit@50": float(np.mean(hit50_values)) if hit50_values else 0.0,
        "mrr": float(np.mean(mrr_values)) if mrr_values else 0.0,
    }


def count_by_key(samples: Sequence[dict], key: str) -> Dict[str, int]:
    counter: Dict[str, int] = defaultdict(int)
    for sample in samples:
        counter[str(sample.get(key, "UNKNOWN"))] += 1
    return dict(sorted(counter.items(), key=lambda item: (-item[1], item[0])))


def rebalance_binary_sample_weights(samples: Sequence[dict]) -> List[dict]:
    if not samples:
        return []
    positive_total = float(sum(sample.get("weight", 1.0) for sample in samples if float(sample.get("label", 0.0)) >= 1.0))
    negative_total = float(sum(sample.get("weight", 1.0) for sample in samples if float(sample.get("label", 0.0)) < 1.0))
    if positive_total <= 1e-8 or negative_total <= 1e-8:
        return [dict(sample) for sample in samples]

    target_total = (positive_total + negative_total) / 2.0
    positive_scale = min(6.0, max(0.35, target_total / positive_total))
    negative_scale = min(6.0, max(0.20, target_total / negative_total))

    adjusted: List[dict] = []
    for sample in samples:
        copied = dict(sample)
        copied["weight"] = float(copied.get("weight", 1.0)) * (
            positive_scale if float(copied.get("label", 0.0)) >= 1.0 else negative_scale
        )
        adjusted.append(copied)
    return adjusted


def rebalance_recall_event_weights(samples: Sequence[dict]) -> List[dict]:
    if not samples:
        return []
    counts = count_by_key(samples, "event_type")
    if len(counts) <= 1:
        return [dict(sample) for sample in samples]

    positive_counts = [value for value in counts.values() if value > 0]
    if not positive_counts:
        return [dict(sample) for sample in samples]
    target = float(sum(positive_counts)) / float(len(positive_counts))

    scale_by_event: Dict[str, float] = {}
    for event_type, count in counts.items():
        if count <= 0:
            continue
        scale_by_event[event_type] = min(2.8, max(0.55, target / float(count)))

    adjusted: List[dict] = []
    for sample in samples:
        copied = dict(sample)
        event_type = str(copied.get("event_type", "UNKNOWN"))
        scale = scale_by_event.get(event_type, 1.0)
        copied["weight"] = float(copied.get("weight", 1.0)) * scale
        adjusted.append(copied)
    return adjusted


def iter_rank_training_batches(samples: Sequence[dict], batch_size: int) -> List[np.ndarray]:
    if not samples:
        return []
    positive_indices = np.array([index for index, sample in enumerate(samples) if float(sample.get("label", 0.0)) >= 1.0], dtype=np.int64)
    negative_indices = np.array([index for index, sample in enumerate(samples) if float(sample.get("label", 0.0)) < 1.0], dtype=np.int64)
    if len(positive_indices) == 0 or len(negative_indices) == 0:
        permutation = np.random.permutation(len(samples))
        return [permutation[start:start + batch_size] for start in range(0, len(permutation), batch_size)]

    pos_per_batch = min(max(1, int(round(batch_size * RANK_POSITIVE_BATCH_RATIO))), max(1, batch_size - 1))
    neg_per_batch = max(1, batch_size - pos_per_batch)
    steps = max(
        int(math.ceil(len(positive_indices) / max(1, pos_per_batch))),
        int(math.ceil(len(negative_indices) / max(1, neg_per_batch))),
    )

    shuffled_pos = np.random.permutation(positive_indices)
    shuffled_neg = np.random.permutation(negative_indices)
    pos_cursor = 0
    neg_cursor = 0
    batches: List[np.ndarray] = []

    for _ in range(steps):
        if pos_cursor + pos_per_batch > len(shuffled_pos):
            shuffled_pos = np.random.permutation(positive_indices)
            pos_cursor = 0
        if neg_cursor + neg_per_batch > len(shuffled_neg):
            shuffled_neg = np.random.permutation(negative_indices)
            neg_cursor = 0

        batch_indices = np.concatenate(
            [
                shuffled_pos[pos_cursor:pos_cursor + pos_per_batch],
                shuffled_neg[neg_cursor:neg_cursor + neg_per_batch],
            ]
        )
        pos_cursor += pos_per_batch
        neg_cursor += neg_per_batch
        np.random.shuffle(batch_indices)
        batches.append(batch_indices)

    return batches


def _sample_array(sample: dict, key: str, shape: Tuple[int, ...], dtype: np.dtype) -> np.ndarray:
    value = sample.get(key)
    if isinstance(value, np.ndarray):
        array = value
    elif value is None:
        array = np.zeros(shape, dtype=dtype)
    else:
        array = np.array(value, dtype=dtype)
    if tuple(array.shape) != tuple(shape):
        reshaped = np.zeros(shape, dtype=dtype)
        slices = tuple(slice(0, min(shape[index], array.shape[index] if index < array.ndim else 0)) for index in range(len(shape)))
        if array.ndim == len(shape):
            reshaped[slices] = array[slices]
        array = reshaped
    return array.astype(dtype, copy=False)


def build_rank_batch_tensors(
    batch: Sequence[dict],
) -> Tuple[torch.Tensor, torch.Tensor, torch.Tensor, torch.Tensor, torch.Tensor, torch.Tensor, torch.Tensor]:
    static_dim = rank_feature_dim() - SEQ_ENCODER_DIM
    static_features = np.vstack([_sample_array(sample, "features", (static_dim,), np.float32) for sample in batch]).astype(np.float32)
    seq_batch = np.stack(
        [_sample_array(sample, "seq_arr", (SEQUENCE_MAX_LEN, SEQ_EVENT_DIM), np.float32) for sample in batch]
    ).astype(np.float32)
    lengths = np.array([max(0, int(sample.get("actual_len", 0))) for sample in batch], dtype=np.int64)
    labels_ctr = np.array([float(sample.get("label_ctr", 0.0)) for sample in batch], dtype=np.float32)
    labels_cvr = np.array([float(sample.get("label_cvr", 0.0)) for sample in batch], dtype=np.float32)
    labels_quality = np.array([float(sample.get("label_quality", 0.0)) for sample in batch], dtype=np.float32)
    weights = np.array([float(sample.get("weight", 1.0)) for sample in batch], dtype=np.float32)

    return (
        torch.tensor(static_features, dtype=torch.float32, device=DEVICE),
        torch.tensor(seq_batch, dtype=torch.float32, device=DEVICE),
        torch.tensor(lengths, dtype=torch.long, device=DEVICE),
        torch.tensor(labels_ctr, dtype=torch.float32, device=DEVICE),
        torch.tensor(labels_cvr, dtype=torch.float32, device=DEVICE),
        torch.tensor(labels_quality, dtype=torch.float32, device=DEVICE),
        torch.tensor(weights, dtype=torch.float32, device=DEVICE),
    )


def build_mtl_feature_tensor(seq_encoder: UserSequenceEncoder, batch: Sequence[dict]) -> Tuple[torch.Tensor, torch.Tensor, torch.Tensor, torch.Tensor, torch.Tensor]:
    static_features, seq_batch, lengths, labels_ctr, labels_cvr, labels_quality, weights = build_rank_batch_tensors(batch)
    seq_vec = seq_encoder(seq_batch, lengths)
    features = torch.cat([static_features, seq_vec], dim=-1)
    return features, labels_ctr, labels_cvr, labels_quality, weights


def weighted_focal_bce_with_logits(
    logits: torch.Tensor,
    targets: torch.Tensor,
    weights: torch.Tensor,
    gamma: float,
) -> torch.Tensor:
    bce = F.binary_cross_entropy_with_logits(logits, targets, reduction="none")
    if gamma <= 1e-6:
        return (bce * weights).sum() / weights.sum().clamp_min(1e-6)
    probabilities = torch.sigmoid(logits)
    pt = torch.where(targets >= 0.5, probabilities, 1.0 - probabilities)
    focal_factor = torch.pow((1.0 - pt).clamp_min(1e-6), gamma)
    return (bce * focal_factor * weights).sum() / weights.sum().clamp_min(1e-6)


def prepare_candidate_item_pool(
    item_feature_pool: Dict[int, np.ndarray],
) -> Tuple[np.ndarray, np.ndarray, Dict[int, int]]:
    ordered_post_ids = np.array(sorted(int(post_id) for post_id in item_feature_pool.keys()), dtype=np.int64)
    if len(ordered_post_ids) == 0:
        return ordered_post_ids, np.zeros((0, 0), dtype=np.float32), {}
    feature_matrix = np.vstack([item_feature_pool[int(post_id)] for post_id in ordered_post_ids]).astype(np.float32)
    index_map = {int(post_id): index for index, post_id in enumerate(ordered_post_ids.tolist())}
    return ordered_post_ids, feature_matrix, index_map


def encode_candidate_items(model: CandidateGeneratorModel, feature_matrix: np.ndarray) -> torch.Tensor:
    if feature_matrix.size == 0:
        return torch.zeros((0, 512), dtype=torch.float32, device=DEVICE)
    vectors: List[torch.Tensor] = []
    model.eval()
    with torch.no_grad():
        for start in range(0, len(feature_matrix), BATCH_SIZE):
            item_tensor = torch.tensor(feature_matrix[start:start + BATCH_SIZE], dtype=torch.float32, device=DEVICE)
            vectors.append(model.encode_item(item_tensor))
    return torch.cat(vectors, dim=0) if vectors else torch.zeros((0, 512), dtype=torch.float32, device=DEVICE)


def collect_candidate_negative_post_ids(
    batch: Sequence[dict],
    pool_post_ids: np.ndarray,
    item_feature_pool: Dict[int, np.ndarray],
) -> List[int]:
    selected: List[int] = []
    seen = {int(item["post_id"]) for item in batch}

    for item in batch:
        for post_id in item.get("hard_negative_post_ids", []):
            safe_post_id = int(post_id)
            if safe_post_id in seen or safe_post_id not in item_feature_pool:
                continue
            seen.add(safe_post_id)
            selected.append(safe_post_id)
            if len(selected) >= CANDIDATE_HARD_NEGATIVE_BANK_SIZE:
                break
        if len(selected) >= CANDIDATE_HARD_NEGATIVE_BANK_SIZE:
            break

    adaptive_random_cap = min(CANDIDATE_RANDOM_NEGATIVES, max(96, len(batch) * 2))
    random_budget = max(0, adaptive_random_cap - len(selected))
    if random_budget <= 0 or len(pool_post_ids) == 0:
        return selected

    attempts = 0
    max_attempts = max(random_budget * 10, len(pool_post_ids) * 2)
    while random_budget > 0 and attempts < max_attempts:
        candidate_post_id = int(pool_post_ids[np.random.randint(0, len(pool_post_ids))])
        attempts += 1
        if candidate_post_id in seen or candidate_post_id not in item_feature_pool:
            continue
        seen.add(candidate_post_id)
        selected.append(candidate_post_id)
        random_budget -= 1

    return selected


def summarize_rank_split(samples: Sequence[dict]) -> dict:
    positives = int(sum(sample["label"] for sample in samples))
    negatives = int(len(samples) - positives)
    return {
        "samples": int(len(samples)),
        "positives": positives,
        "negatives": negatives,
        "positive_rate": float(positives / len(samples)) if samples else 0.0,
        "ctr_positive_rate": float(sum(float(sample.get("label_ctr", 0.0)) for sample in samples) / len(samples)) if samples else 0.0,
        "cvr_positive_rate": float(sum(float(sample.get("label_cvr", 0.0)) for sample in samples) / len(samples)) if samples else 0.0,
        "quality_positive_rate": float(sum(1.0 for sample in samples if float(sample.get("label_quality", 0.0)) > 0.0) / len(samples)) if samples else 0.0,
        "quality_negative_rate": float(sum(1.0 for sample in samples if float(sample.get("label_quality", 0.0)) < 0.0) / len(samples)) if samples else 0.0,
        "label_type_distribution": count_by_key(samples, "label_type"),
    }


def summarize_recall_split(samples: Sequence[dict]) -> dict:
    return {
        "samples": int(len(samples)),
        "event_type_distribution": count_by_key(samples, "event_type"),
    }


def export_dataset_snapshot(rank_train: Sequence[dict],
                            rank_valid: Sequence[dict],
                            recall_train: Sequence[dict],
                            recall_valid: Sequence[dict]) -> dict:
    os.makedirs(SAMPLE_EXPORT_DIR, exist_ok=True)

    def write_jsonl(path: str, rows: Sequence[dict], fields: Sequence[str], max_rows: int = 80000) -> int:
        count = 0
        with open(path, "w", encoding="utf-8") as file_obj:
            for row in rows:
                payload = {key: row.get(key) for key in fields}
                payload["ts"] = row.get("ts").isoformat() if isinstance(row.get("ts"), datetime) else row.get("ts")
                file_obj.write(json.dumps(payload, ensure_ascii=False) + "\n")
                count += 1
                if count >= max_rows:
                    break
        return count

    rank_train_path = os.path.join(SAMPLE_EXPORT_DIR, "rank_train_samples.jsonl")
    rank_valid_path = os.path.join(SAMPLE_EXPORT_DIR, "rank_valid_samples.jsonl")
    recall_train_path = os.path.join(SAMPLE_EXPORT_DIR, "recall_train_samples.jsonl")
    recall_valid_path = os.path.join(SAMPLE_EXPORT_DIR, "recall_valid_samples.jsonl")

    rank_fields = ("user_id", "post_id", "label", "weight", "label_type")
    recall_fields = ("user_id", "post_id", "event_type", "weight", "hard_negative_post_ids")

    rank_train_count = write_jsonl(rank_train_path, rank_train, rank_fields)
    rank_valid_count = write_jsonl(rank_valid_path, rank_valid, rank_fields)
    recall_train_count = write_jsonl(recall_train_path, recall_train, recall_fields)
    recall_valid_count = write_jsonl(recall_valid_path, recall_valid, recall_fields)

    return {
        "directory": os.path.abspath(SAMPLE_EXPORT_DIR),
        "rank_train_samples": rank_train_count,
        "rank_valid_samples": rank_valid_count,
        "recall_train_samples": recall_train_count,
        "recall_valid_samples": recall_valid_count,
        "files": {
            "rank_train": os.path.abspath(rank_train_path),
            "rank_valid": os.path.abspath(rank_valid_path),
            "recall_train": os.path.abspath(recall_train_path),
            "recall_valid": os.path.abspath(recall_valid_path),
        },
    }


def train_candidate_generator(
    train_samples: Sequence[dict],
    valid_samples: Sequence[dict],
    item_feature_pool: Dict[int, np.ndarray],
) -> Tuple[Optional[CandidateGeneratorModel], dict]:
    if not train_samples or not item_feature_pool:
        return None, {"samples": 0}

    model = CandidateGeneratorModel().to(DEVICE)
    optimizer = torch.optim.AdamW(model.parameters(), lr=LR, weight_decay=WEIGHT_DECAY)
    pool_post_ids, _, _ = prepare_candidate_item_pool(item_feature_pool)
    best_state = copy.deepcopy(model.state_dict())
    best_metrics = {"hit@50": -1.0}
    best_epoch = 0

    for epoch in range(1, CANDIDATE_EPOCHS + 1):
        model.train()
        permutation = np.random.permutation(len(train_samples))
        running_loss = 0.0
        running_weight = 0.0
        running_target_size = 0.0
        running_batches = 0

        for start in range(0, len(permutation), BATCH_SIZE):
            batch_indices = permutation[start:start + BATCH_SIZE]
            batch = [train_samples[int(index)] for index in batch_indices]
            xb = torch.tensor(np.vstack([item["features"] for item in batch]), dtype=torch.float32, device=DEVICE)
            positive_item_features = torch.tensor(
                np.vstack([item["item_features"] for item in batch]),
                dtype=torch.float32,
                device=DEVICE,
            )
            wb = torch.tensor([item["weight"] for item in batch], dtype=torch.float32, device=DEVICE)

            optimizer.zero_grad()
            user_vectors = model(xb)
            positive_vectors = model.encode_item(positive_item_features)
            negative_post_ids = collect_candidate_negative_post_ids(batch, pool_post_ids, item_feature_pool)
            if negative_post_ids:
                negative_item_features = torch.tensor(
                    np.vstack([item_feature_pool[post_id] for post_id in negative_post_ids]),
                    dtype=torch.float32,
                    device=DEVICE,
                )
                negative_vectors = model.encode_item(negative_item_features)
                target_vectors = torch.cat([positive_vectors, negative_vectors], dim=0)
            else:
                target_vectors = positive_vectors

            logits = torch.matmul(user_vectors, target_vectors.T) / CANDIDATE_TEMPERATURE
            target = torch.arange(len(batch), device=DEVICE)
            loss_vector = F.cross_entropy(logits, target, reduction="none")
            loss = (loss_vector * wb).sum() / wb.sum().clamp_min(1e-6)
            loss.backward()
            optimizer.step()

            running_loss += float((loss_vector.detach() * wb).sum().item())
            running_weight += float(wb.sum().item())
            running_target_size += float(target_vectors.shape[0])
            running_batches += 1

        train_loss = float(running_loss / max(running_weight, 1e-6))
        average_target_size = max(2.0, running_target_size / max(running_batches, 1))
        random_baseline_ce = float(math.log(average_target_size))
        normalized_train_loss = float(train_loss / max(random_baseline_ce, 1e-6))
        valid_metrics = candidate_eval_metrics(model, valid_samples, item_feature_pool)
        print(
            f"[candidate] epoch={epoch}/{CANDIDATE_EPOCHS} "
            f"train_loss={train_loss:.6f} norm_ce={normalized_train_loss:.4f} "
            f"val_hit@50={valid_metrics['hit@50']:.6f}"
        )

        if valid_metrics["hit@50"] >= best_metrics["hit@50"]:
            best_metrics = dict(valid_metrics)
            best_metrics["train_loss"] = train_loss
            best_metrics["train_loss_normalized"] = normalized_train_loss
            best_metrics["random_baseline_ce"] = random_baseline_ce
            best_epoch = epoch
            best_state = copy.deepcopy(model.state_dict())

    model.load_state_dict(best_state)
    return model, {
        "train_samples": int(len(train_samples)),
        "validation_samples": int(len(valid_samples)),
        "best_epoch": int(best_epoch),
        "validation": best_metrics,
    }


def binary_auc(y_true: np.ndarray, y_score: np.ndarray) -> float:
    if len(y_true) == 0:
        return 0.5
    positive = y_true == 1
    n_pos = int(positive.sum())
    n_neg = int(len(y_true) - n_pos)
    if n_pos == 0 or n_neg == 0:
        return 0.5
    order = np.argsort(y_score)
    ranks = np.empty_like(order)
    ranks[order] = np.arange(len(y_score)) + 1
    rank_sum_pos = float(ranks[positive].sum())
    auc = (rank_sum_pos - n_pos * (n_pos + 1) / 2.0) / (n_pos * n_neg)
    return float(auc)


def safe_logloss(y_true: np.ndarray, y_prob: np.ndarray) -> float:
    if len(y_true) == 0:
        return 0.0
    eps = 1e-7
    y_prob = np.nan_to_num(y_prob.astype(np.float64), nan=0.5, posinf=1.0 - eps, neginf=eps)
    clipped = np.clip(y_prob, eps, 1.0 - eps)
    loss = -(y_true * np.log(clipped) + (1.0 - y_true) * np.log(1.0 - clipped))
    return float(loss.mean())


def dcg_at_k(labels: Sequence[int], k: int) -> float:
    total = 0.0
    for index, label in enumerate(labels[:k], start=1):
        total += (2 ** int(label) - 1) / math.log2(index + 1)
    return total


def evaluate_topk(samples: Sequence[dict], probabilities: np.ndarray, k_values: Sequence[int]) -> dict:
    by_user_candidates: Dict[int, Dict[int, Tuple[int, float]]] = defaultdict(dict)
    for sample, probability in zip(samples, probabilities.tolist()):
        user_id = int(sample["user_id"])
        post_id = int(sample["post_id"])
        label = int(sample["label"])
        score = float(probability) if is_finite_number(probability) else 0.0
        previous = by_user_candidates[user_id].get(post_id)
        if previous is None:
            by_user_candidates[user_id][post_id] = (label, score)
        else:
            by_user_candidates[user_id][post_id] = (max(previous[0], label), max(previous[1], score))

    by_user: Dict[int, List[Tuple[int, int, float]]] = {
        user_id: [(post_id, label, score) for post_id, (label, score) in candidates.items()]
        for user_id, candidates in by_user_candidates.items()
    }
    unique_candidate_ids = {post_id for candidates in by_user.values() for post_id, _, _ in candidates}
    relevant_counts = [
        sum(1 for _, label, _ in items if label == 1)
        for items in by_user.values()
    ]
    evaluated_user_count = sum(1 for count in relevant_counts if count > 0)

    metrics: Dict[str, float] = {}
    for k in k_values:
        precision_values: List[float] = []
        recall_values: List[float] = []
        ndcg_values: List[float] = []
        ap_values: List[float] = []

        for items in by_user.values():
            items.sort(key=lambda item: item[2], reverse=True)
            top_items = items[:k]
            relevant_total = sum(1 for _, label, _ in items if label == 1)
            if relevant_total == 0:
                continue

            top_labels = [label for _, label, _ in top_items]
            hits = sum(top_labels)
            precision_values.append(hits / max(1, len(top_items)))
            recall_values.append(hits / max(1, relevant_total))

            ideal_labels = sorted((label for _, label, _ in items), reverse=True)
            ndcg_values.append(dcg_at_k(top_labels, k) / max(1e-9, dcg_at_k(ideal_labels, k)))

            running_hits = 0
            ap_sum = 0.0
            for index, label in enumerate(top_labels, start=1):
                if label == 1:
                    running_hits += 1
                    ap_sum += running_hits / index
            ap_values.append(ap_sum / max(1, min(relevant_total, k)))

        metrics[f"precision@{k}"] = float(np.mean(precision_values)) if precision_values else 0.0
        metrics[f"recall@{k}"] = float(np.mean(recall_values)) if recall_values else 0.0
        metrics[f"ndcg@{k}"] = float(np.mean(ndcg_values)) if ndcg_values else 0.0
        metrics[f"map@{k}"] = float(np.mean(ap_values)) if ap_values else 0.0

    metrics["users"] = int(len(by_user))
    metrics["evaluated_users"] = int(evaluated_user_count)
    metrics["skipped_no_positive_users"] = int(len(by_user) - evaluated_user_count)
    metrics["unique_candidate_coverage"] = int(len(unique_candidate_ids))
    metrics["candidate_count"] = float(np.mean([len(items) for items in by_user.values()])) if by_user else 0.0
    metrics["relevant_count"] = float(np.mean([count for count in relevant_counts if count > 0])) if evaluated_user_count else 0.0
    return metrics


def predict_rank_logits(
    model: RankModel,
    seq_encoder: UserSequenceEncoder,
    samples: Sequence[dict],
) -> np.ndarray:
    if not samples:
        return np.zeros((0,), dtype=np.float32)
    model.eval()
    seq_encoder.eval()
    logits_values: List[float] = []
    with torch.no_grad():
        for start in range(0, len(samples), BATCH_SIZE):
            batch = samples[start:start + BATCH_SIZE]
            features, _, _, _, _ = build_mtl_feature_tensor(seq_encoder, batch)
            logits = model(features)
            logits_values.extend(logits.detach().cpu().tolist())
    return np.array(logits_values, dtype=np.float32)


def predict_rank_task_logits(
    model: RankModel,
    seq_encoder: UserSequenceEncoder,
    samples: Sequence[dict],
) -> Tuple[np.ndarray, np.ndarray, np.ndarray]:
    if not samples:
        empty = np.zeros((0,), dtype=np.float32)
        return empty, empty, empty
    model.eval()
    seq_encoder.eval()
    ctr_values: List[float] = []
    cvr_values: List[float] = []
    quality_values: List[float] = []
    with torch.no_grad():
        for start in range(0, len(samples), BATCH_SIZE):
            batch = samples[start:start + BATCH_SIZE]
            features, _, _, _, _ = build_mtl_feature_tensor(seq_encoder, batch)
            ctr_logits, cvr_logits, quality_logits = model.forward_all(features)
            ctr_values.extend(ctr_logits.detach().cpu().tolist())
            cvr_values.extend(cvr_logits.detach().cpu().tolist())
            quality_values.extend(quality_logits.detach().cpu().tolist())
    return (
        np.array(ctr_values, dtype=np.float32),
        np.array(cvr_values, dtype=np.float32),
        np.array(quality_values, dtype=np.float32),
    )


def apply_sigmoid_calibration(logits: np.ndarray, calibration: Optional[dict]) -> np.ndarray:
    if logits.size == 0:
        return np.zeros((0,), dtype=np.float32)
    logits = np.nan_to_num(logits.astype(np.float32), nan=0.0, posinf=24.0, neginf=-24.0)
    if not calibration or not calibration.get("enabled"):
        clipped = np.clip(logits, -24.0, 24.0)
        return 1.0 / (1.0 + np.exp(-clipped))
    scale = float(calibration.get("scale", 1.0))
    bias = float(calibration.get("bias", 0.0))
    if not math.isfinite(scale) or not math.isfinite(bias) or scale <= 0.0:
        clipped = np.clip(logits, -24.0, 24.0)
        return 1.0 / (1.0 + np.exp(-clipped))
    adjusted = logits * scale + bias
    adjusted = np.clip(adjusted, -24.0, 24.0)
    return 1.0 / (1.0 + np.exp(-adjusted))


def fit_sigmoid_calibration(logits: np.ndarray,
                            labels: np.ndarray,
                            weights: Optional[np.ndarray]) -> dict:
    if not ENABLE_CALIBRATION or logits.size == 0:
        return {
            "enabled": False,
            "scale": 1.0,
            "bias": 0.0,
            "iterations": 0,
            "status": "disabled_or_empty",
        }

    finite_mask = np.isfinite(logits) & np.isfinite(labels)
    if weights is not None:
        finite_mask &= np.isfinite(weights)
    logits = logits[finite_mask]
    labels = labels[finite_mask]
    weights = weights[finite_mask] if weights is not None else None

    if logits.size == 0:
        return {
            "enabled": False,
            "scale": 1.0,
            "bias": 0.0,
            "iterations": 0,
            "status": "non_finite_inputs",
        }

    logits = np.clip(np.nan_to_num(logits.astype(np.float32), nan=0.0, posinf=24.0, neginf=-24.0), -24.0, 24.0)
    y_true = labels.astype(np.float32)
    if y_true.sum() <= 0 or y_true.sum() >= len(y_true):
        return {
            "enabled": False,
            "scale": 1.0,
            "bias": 0.0,
            "iterations": 0,
            "status": "single_class",
        }

    sample_weights = np.ones_like(y_true, dtype=np.float32) if weights is None else weights.astype(np.float32)
    sample_weights = np.clip(sample_weights, 1e-4, None)

    x = torch.tensor(logits.astype(np.float32), dtype=torch.float32, device=DEVICE)
    y = torch.tensor(y_true, dtype=torch.float32, device=DEVICE)
    w = torch.tensor(sample_weights, dtype=torch.float32, device=DEVICE)

    scale = torch.tensor(1.0, dtype=torch.float32, device=DEVICE, requires_grad=True)
    bias = torch.tensor(0.0, dtype=torch.float32, device=DEVICE, requires_grad=True)
    optimizer = torch.optim.Adam([scale, bias], lr=0.04)

    steps = 240
    for _ in range(steps):
        optimizer.zero_grad()
        calibrated_logits = x * scale + bias
        loss_vector = F.binary_cross_entropy_with_logits(calibrated_logits, y, reduction="none")
        loss = (loss_vector * w).sum() / w.sum().clamp_min(1e-6)
        if not torch.isfinite(loss):
            return {
                "enabled": False,
                "scale": 1.0,
                "bias": 0.0,
                "iterations": 0,
                "status": "non_finite_loss",
            }
        loss.backward()
        optimizer.step()
        with torch.no_grad():
            scale.clamp_(0.05, 20.0)
            bias.clamp_(-20.0, 20.0)

    fitted_scale = float(scale.detach().cpu().item())
    fitted_bias = float(bias.detach().cpu().item())
    if not math.isfinite(fitted_scale) or not math.isfinite(fitted_bias):
        return {
            "enabled": False,
            "scale": 1.0,
            "bias": 0.0,
            "iterations": steps,
            "status": "non_finite_parameters",
        }

    return {
        "enabled": True,
        "scale": fitted_scale,
        "bias": fitted_bias,
        "iterations": steps,
        "status": "ok",
    }


def evaluate_rank_model(model: RankModel,
                        seq_encoder: UserSequenceEncoder,
                        samples: Sequence[dict],
                        k_values: Sequence[int],
                        calibration: Optional[dict] = None) -> dict:
    if not samples:
        safe_k_values = list(k_values) if k_values else [TOPK]
        return {
            "samples": 0,
            "positives": 0,
            "negatives": 0,
            "auc": 0.5,
            "logloss": 0.0,
            **{f"precision@{k}": 0.0 for k in safe_k_values},
            **{f"recall@{k}": 0.0 for k in safe_k_values},
            **{f"ndcg@{k}": 0.0 for k in safe_k_values},
            **{f"map@{k}": 0.0 for k in safe_k_values},
            "users": 0,
        }

    logits = predict_rank_logits(model, seq_encoder, samples)
    y_true = np.array([item["label"] for item in samples], dtype=np.float32)
    y_prob = apply_sigmoid_calibration(logits, calibration)
    ctr_logits, cvr_logits, quality_logits = predict_rank_task_logits(model, seq_encoder, samples)
    ctr_labels = np.array([item.get("label_ctr", 0.0) for item in samples], dtype=np.float32)
    cvr_labels = np.array([item.get("label_cvr", 0.0) for item in samples], dtype=np.float32)
    quality_labels = np.array([1.0 if float(item.get("label_quality", 0.0)) > 0.0 else 0.0 for item in samples], dtype=np.float32)
    ctr_prob = apply_sigmoid_calibration(ctr_logits, None)
    cvr_prob = apply_sigmoid_calibration(cvr_logits, None)
    quality_prob = apply_sigmoid_calibration(quality_logits, None)

    metrics = {
        "samples": int(len(samples)),
        "positives": int(y_true.sum()),
        "negatives": int(len(y_true) - y_true.sum()),
        "auc": binary_auc(y_true, y_prob),
        "auc_ctr": binary_auc(ctr_labels, ctr_prob),
        "auc_cvr": binary_auc(cvr_labels, cvr_prob),
        "auc_quality": binary_auc(quality_labels, quality_prob),
        "logloss": safe_logloss(y_true, y_prob),
    }
    metrics.update(evaluate_topk(samples, y_prob, k_values))
    if calibration and calibration.get("enabled"):
        metrics["calibrated"] = True
    return metrics


def train_rank_model(train_samples: Sequence[dict],
                     valid_samples: Sequence[dict],
                     k_values: Sequence[int],
                     ) -> Tuple[Optional[RankModel], Optional[UserSequenceEncoder], dict, dict]:
    if not train_samples:
        return None, None, {"samples": 0}, {
            "enabled": False,
            "scale": 1.0,
            "bias": 0.0,
            "iterations": 0,
            "status": "no_train_samples",
        }

    model = RankModel().to(DEVICE)
    seq_encoder = UserSequenceEncoder().to(DEVICE)
    optimizer = torch.optim.AdamW(
        list(model.parameters()) + list(seq_encoder.parameters()),
        lr=LR,
        weight_decay=WEIGHT_DECAY,
    )
    safe_k_values = list(k_values) if k_values else [TOPK]
    metric_k = safe_k_values[0]
    metric_key = f"ndcg@{metric_k}"
    best_state = copy.deepcopy(model.state_dict())
    best_seq_state = copy.deepcopy(seq_encoder.state_dict())
    best_metrics = {metric_key: -1.0}
    best_epoch = 0
    focal_gamma = max(0.0, RANK_FOCAL_GAMMA)

    log_every = max(1, int(os.getenv("TRAIN_LOG_EVERY", "50")))
    for epoch in range(1, RANK_EPOCHS + 1):
        model.train()
        seq_encoder.train()
        running_loss = 0.0
        running_weight = 0.0

        step = 0
        epoch_start = time.monotonic()
        for batch_indices in iter_rank_training_batches(train_samples, BATCH_SIZE):
            batch = [train_samples[int(index)] for index in batch_indices]
            features, labels_ctr, labels_cvr, labels_quality, weights = build_mtl_feature_tensor(seq_encoder, batch)

            optimizer.zero_grad()
            ctr_logits, cvr_logits, quality_logits = model.forward_all(features)
            quality_targets = (labels_quality > 0.0).float()
            quality_weights = weights * torch.where(labels_quality < 0.0, torch.full_like(weights, 3.0), torch.ones_like(weights))
            task_losses = torch.stack(
                [
                    weighted_focal_bce_with_logits(ctr_logits, labels_ctr, weights, focal_gamma),
                    weighted_focal_bce_with_logits(cvr_logits, labels_cvr, weights, focal_gamma),
                    weighted_focal_bce_with_logits(quality_logits, quality_targets, quality_weights, focal_gamma),
                ]
            )
            if hasattr(model, "multi_task_loss"):
                loss = model.multi_task_loss(task_losses)
            else:
                loss = task_losses[0] * 0.40 + task_losses[1] * 0.42 + task_losses[2] * 0.18
            loss.backward()
            optimizer.step()

            running_loss += float(loss.detach().item() * weights.sum().item())
            running_weight += float(weights.sum().item())

            step += 1
            if step % log_every == 0:
                elapsed = max(1e-6, time.monotonic() - epoch_start)
                avg_step = elapsed / float(step)
                print(
                    f"[rank] epoch={epoch}/{RANK_EPOCHS} "
                    f"step={step} "
                    f"avg_step={avg_step:.3f}s "
                    f"running_loss={float(running_loss / max(running_weight, 1e-6)):.6f}"
                )

        train_loss = float(running_loss / max(running_weight, 1e-6))
        valid_metrics = evaluate_rank_model(model, seq_encoder, valid_samples, safe_k_values)
        print(
            f"[rank] epoch={epoch}/{RANK_EPOCHS} "
            f"train_loss={train_loss:.6f} val_ndcg@{metric_k}={valid_metrics.get(metric_key, 0.0):.6f}"
        )

        if valid_metrics.get(metric_key, 0.0) >= best_metrics.get(metric_key, -1.0):
            best_metrics = dict(valid_metrics)
            best_metrics["train_loss"] = train_loss
            best_epoch = epoch
            best_state = copy.deepcopy(model.state_dict())
            best_seq_state = copy.deepcopy(seq_encoder.state_dict())

    model.load_state_dict(best_state)
    seq_encoder.load_state_dict(best_seq_state)
    valid_logits = predict_rank_logits(model, seq_encoder, valid_samples)
    valid_labels = np.array([item["label"] for item in valid_samples], dtype=np.float32)
    valid_weights = np.array([item.get("weight", 1.0) for item in valid_samples], dtype=np.float32)
    calibration = fit_sigmoid_calibration(valid_logits, valid_labels, valid_weights)
    calibrated_metrics = evaluate_rank_model(model, seq_encoder, valid_samples, safe_k_values, calibration=calibration)
    calibrated_metrics["train_loss"] = best_metrics.get("train_loss", 0.0)

    return model, seq_encoder, {
        "train_samples": int(len(train_samples)),
        "validation_samples": int(len(valid_samples)),
        "best_epoch": int(best_epoch),
        "validation": calibrated_metrics,
    }, calibration


def save_scripted_model(model: torch.nn.Module, path: str) -> None:
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    temp_path = os.path.join(tempfile.gettempdir(), os.path.basename(path))
    scripted = torch.jit.script(model.cpu().eval())
    scripted.save(temp_path)
    shutil.copyfile(temp_path, os.path.abspath(path))


def render_training_dashboard(report: dict) -> str:
    rank_validation = report.get("rank", {}).get("validation", {}) if isinstance(report.get("rank"), dict) else {}
    candidate_validation = report.get("candidate", {}).get("validation", {}) if isinstance(report.get("candidate"), dict) else {}
    dataset = report.get("dataset", {})
    rank_train = dataset.get("rank_train", {})
    rank_valid = dataset.get("rank_validation", {})

    metric_rows = []
    for key in sorted(rank_validation.keys()):
        if key in {"samples", "positives", "negatives", "users", "logloss", "auc", "train_loss"} or "@" in key:
            metric_rows.append(
                f"<tr><td>{key}</td><td>{rank_validation.get(key)}</td></tr>"
            )
    candidate_rows = []
    for key in sorted(candidate_validation.keys()):
        if key in {"samples", "loss", "hit@10", "hit@50", "mrr", "train_loss"}:
            candidate_rows.append(
                f"<tr><td>{key}</td><td>{candidate_validation.get(key)}</td></tr>"
            )

    return f"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Recommendation Offline Dashboard</title>
  <style>
    :root {{ color-scheme: light; }}
    body {{ margin: 0; font-family: "Segoe UI", Arial, sans-serif; background: #f4f6fb; color: #111827; }}
    .container {{ max-width: 1040px; margin: 0 auto; padding: 24px; }}
    .title {{ font-size: 26px; font-weight: 700; margin: 0 0 6px; }}
    .subtitle {{ color: #4b5563; margin: 0 0 20px; }}
    .grid {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 10px; margin-bottom: 16px; }}
    .card {{ background: #fff; border: 1px solid #e5e7eb; border-radius: 12px; padding: 14px; }}
    .k {{ color: #6b7280; font-size: 12px; margin-bottom: 5px; }}
    .v {{ font-size: 22px; font-weight: 700; }}
    h2 {{ margin: 22px 0 10px; font-size: 18px; }}
    table {{ width: 100%; border-collapse: collapse; background: #fff; border: 1px solid #e5e7eb; border-radius: 12px; overflow: hidden; }}
    th, td {{ border-bottom: 1px solid #f1f5f9; padding: 10px 12px; text-align: left; font-size: 14px; }}
    th {{ background: #f8fafc; color: #374151; }}
    tr:last-child td {{ border-bottom: none; }}
    .footer {{ margin-top: 12px; color: #6b7280; font-size: 12px; }}
  </style>
</head>
<body>
  <div class="container">
    <h1 class="title">Offline Evaluation Dashboard</h1>
    <p class="subtitle">NDCG / Recall@K / AUC validation snapshot</p>

    <div class="grid">
      <div class="card"><div class="k">AUC</div><div class="v">{rank_validation.get("auc", 0.0)}</div></div>
      <div class="card"><div class="k">NDCG@10</div><div class="v">{rank_validation.get("ndcg@10", 0.0)}</div></div>
      <div class="card"><div class="k">Recall@10</div><div class="v">{rank_validation.get("recall@10", 0.0)}</div></div>
      <div class="card"><div class="k">Validation Samples</div><div class="v">{rank_valid.get("samples", 0)}</div></div>
    </div>

    <h2>Rank Validation Metrics</h2>
    <table>
      <thead><tr><th>Metric</th><th>Value</th></tr></thead>
      <tbody>
        {"".join(metric_rows)}
      </tbody>
    </table>

    <h2>Candidate Validation Metrics</h2>
    <table>
      <thead><tr><th>Metric</th><th>Value</th></tr></thead>
      <tbody>
        {"".join(candidate_rows)}
      </tbody>
    </table>

    <h2>Dataset Snapshot</h2>
    <table>
      <thead><tr><th>Split</th><th>Samples</th><th>Positive Rate</th></tr></thead>
      <tbody>
        <tr><td>rank_train</td><td>{rank_train.get("samples", 0)}</td><td>{rank_train.get("positive_rate", 0.0)}</td></tr>
        <tr><td>rank_validation</td><td>{rank_valid.get("samples", 0)}</td><td>{rank_valid.get("positive_rate", 0.0)}</td></tr>
      </tbody>
    </table>

    <p class="footer">Generated at: {report.get("generated_at", "")}</p>
  </div>
</body>
</html>
"""


def build_training_datasets() -> dict:
    print("[train] loading source data...")
    source_cache = load_source_data_cache()
    if source_cache is not None:
        post_meta = source_cache["post_meta"]
        exposures = source_cache["exposures"]
        history_events = source_cache["history_events"]
        recall_positive_events = source_cache["recall_positive_events"]
        print(f"[train] source cache hit -> {os.path.abspath(source_cache_path())}")
    else:
        conn = mysql_conn()
        try:
            post_meta = load_post_meta(conn)
            exposures = load_exposures(conn, MAX_RANK_SAMPLES)
            history_events = load_history_events(conn)
            recall_positive_events = load_recall_positive_events(conn, MAX_RECALL_SAMPLES)
        finally:
            conn.close()
        save_source_data_cache({
            "post_meta": post_meta,
            "exposures": exposures,
            "history_events": history_events,
            "recall_positive_events": recall_positive_events,
            "generated_at": datetime.now().isoformat(),
        })
        if SOURCE_CACHE_ENABLED:
            print(f"[train] source cache saved -> {os.path.abspath(source_cache_path())}")

    user_events, user_timestamps, user_post_events, user_post_timestamps = build_user_event_indexes(history_events)

    involved_post_ids: List[int] = [int(post_id) for _, post_id, _, _ in exposures]
    involved_post_ids.extend(
        int(row[1])
        for row in history_events
        if isinstance(row, (tuple, list)) and len(row) >= 2 and row[1] is not None
    )
    involved_post_ids.extend(int(post_id) for _, post_id, _, _ in recall_positive_events)
    emb_map = load_embeddings(involved_post_ids)
    reference_times = [row[2] for row in exposures]
    reference_times.extend(row[3] for row in recall_positive_events)
    reference_time = max(reference_times) if reference_times else None
    candidate_item_features = build_candidate_item_feature_pool(post_meta, emb_map, reference_time)

    rng = np.random.default_rng(SEED)
    rank_samples, rank_sampling_stats = build_rank_samples(
        exposures=exposures,
        post_meta=post_meta,
        emb_map=emb_map,
        user_events=user_events,
        user_timestamps=user_timestamps,
        user_post_events=user_post_events,
        user_post_timestamps=user_post_timestamps,
        rng=rng,
    )
    recall_samples, recall_sampling_stats = build_recall_samples(
        positive_events=recall_positive_events,
        post_meta=post_meta,
        emb_map=emb_map,
        user_events=user_events,
        user_timestamps=user_timestamps,
        rng=rng,
        item_feature_pool=candidate_item_features,
    )

    return {
        "post_meta": post_meta,
        "emb_map": emb_map,
        "candidate_item_features": candidate_item_features,
        "rank_samples": rank_samples,
        "recall_samples": recall_samples,
        "rank_sampling_stats": rank_sampling_stats,
        "recall_sampling_stats": recall_sampling_stats,
        "exposures": len(exposures),
        "history_events": len(history_events),
        "recall_events": len(recall_positive_events),
        "source_cache_hit": bool(source_cache is not None),
        "source_cache_path": os.path.abspath(source_cache_path()) if SOURCE_CACHE_ENABLED else "",
    }


def main() -> None:
    set_seed(SEED)
    eval_k_values = parse_eval_k_values(EVAL_K_LIST)
    artifacts = build_training_datasets()
    rank_samples = artifacts["rank_samples"]
    recall_samples = artifacts["recall_samples"]

    print(
        f"[train] rank_samples={len(rank_samples)} "
        f"recall_samples={len(recall_samples)} "
        f"embeddings={len(artifacts['emb_map'])}"
    )

    recall_train, recall_valid = split_samples_by_time(recall_samples, TRAIN_SPLIT_RATIO)
    rank_train, rank_valid = split_samples_by_time(rank_samples, TRAIN_SPLIT_RATIO)
    recall_train = rebalance_recall_event_weights(recall_train)
    rank_train = rebalance_binary_sample_weights(rank_train)

    candidate_model, candidate_metrics = train_candidate_generator(
        recall_train,
        recall_valid,
        artifacts["candidate_item_features"],
    )
    rank_model, seq_encoder, rank_metrics, rank_calibration = train_rank_model(
        rank_train,
        rank_valid,
        eval_k_values,
    )
    sample_snapshot = export_dataset_snapshot(rank_train, rank_valid, recall_train, recall_valid)

    if candidate_model is not None:
        save_scripted_model(candidate_model, CANDIDATE_MODEL_OUT)
        print(f"[train] candidate model exported -> {os.path.abspath(CANDIDATE_MODEL_OUT)}")

    if rank_model is not None:
        save_scripted_model(rank_model, RANK_MODEL_OUT)
        print(f"[train] rank model exported -> {os.path.abspath(RANK_MODEL_OUT)}")
    if seq_encoder is not None:
        save_scripted_model(seq_encoder, SEQ_ENCODER_OUT)
        print(f"[train] sequence encoder exported -> {os.path.abspath(SEQ_ENCODER_OUT)}")

    os.makedirs(os.path.dirname(os.path.abspath(RANK_CALIBRATION_OUT)), exist_ok=True)
    with open(RANK_CALIBRATION_OUT, "w", encoding="utf-8") as file_obj:
        json.dump(make_json_safe(rank_calibration), file_obj, ensure_ascii=False, indent=2, allow_nan=False)
    print(f"[train] calibration saved -> {os.path.abspath(RANK_CALIBRATION_OUT)}")

    report = {
        "generated_at": datetime.now().isoformat(),
        "seed": SEED,
        "device": str(DEVICE),
        "source": {
            "exposures": int(artifacts["exposures"]),
            "history_events": int(artifacts["history_events"]),
            "recall_events": int(artifacts["recall_events"]),
            "embeddings": int(len(artifacts["emb_map"])),
            "source_cache_hit": bool(artifacts.get("source_cache_hit", False)),
            "source_cache_path": artifacts.get("source_cache_path", ""),
        },
        "sample_reconstruction": {
            "rank_sampling": artifacts.get("rank_sampling_stats", {}),
            "recall_sampling": artifacts.get("recall_sampling_stats", {}),
            "snapshot": sample_snapshot,
        },
        "dataset": {
            "candidate_train": summarize_recall_split(recall_train),
            "candidate_validation": summarize_recall_split(recall_valid),
            "rank_train": summarize_rank_split(rank_train),
            "rank_validation": summarize_rank_split(rank_valid),
        },
        "evaluation": {
            "k_values": eval_k_values,
            "primary_k": eval_k_values[0] if eval_k_values else TOPK,
        },
        "calibration": rank_calibration,
        "candidate": candidate_metrics,
        "rank": rank_metrics,
    }

    os.makedirs(os.path.dirname(os.path.abspath(TRAIN_REPORT_OUT)), exist_ok=True)
    safe_report = make_json_safe(report)
    with open(TRAIN_REPORT_OUT, "w", encoding="utf-8") as file_obj:
        json.dump(safe_report, file_obj, ensure_ascii=False, indent=2, allow_nan=False)

    os.makedirs(os.path.dirname(os.path.abspath(TRAIN_DASHBOARD_OUT)), exist_ok=True)
    with open(TRAIN_DASHBOARD_OUT, "w", encoding="utf-8") as file_obj:
        file_obj.write(render_training_dashboard(safe_report))

    print(json.dumps(safe_report, ensure_ascii=False, indent=2, allow_nan=False))
    print(f"[train] report saved -> {os.path.abspath(TRAIN_REPORT_OUT)}")
    print(f"[train] dashboard saved -> {os.path.abspath(TRAIN_DASHBOARD_OUT)}")


if __name__ == "__main__":
    main()
