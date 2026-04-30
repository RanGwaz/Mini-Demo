from __future__ import annotations

import hashlib
import json
import math
import os
import re
from datetime import datetime
from functools import lru_cache
from typing import Dict, Iterable, List, Optional, Sequence, Tuple

import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F

EMBEDDING_DIM = 512
TAG_VECTOR_DIM = 64
HISTORY_SUMMARY_DIM = 12
POST_NUMERIC_DIM = 14
CROSS_FEATURE_DIM = 12
MAX_HISTORY_REFERENCE = 50.0
SCENE_FEATURE_DIM = 12
SEQ_ENCODER_DIM = 64
SEQ_EVENT_DIM = 10
SEQ_MAX_LEN = 50
SEQUENCE_MAX_LEN = SEQ_MAX_LEN

POSITIVE_HISTORY_WEIGHTS: Dict[str, float] = {
    "POST_COMMENT": 4.0,
    "POST_FAVORITE": 3.5,
    "POST_LIKE": 3.0,
    "POST_SHARE": 2.6,
    "POST_DETAIL_VIEW": 1.9,
    "POST_CLICK": 1.4,
}

NEGATIVE_HISTORY_WEIGHTS: Dict[str, float] = {
    "NOT_INTERESTED": 3.0,
    "POST_NEGATIVE_FEEDBACK": 2.8,
    "POST_HIDE": 2.5,
}

POSITIVE_LABEL_WEIGHTS: Dict[str, float] = {
    "POST_COMMENT": 5.0,
    "POST_FAVORITE": 4.0,
    "POST_LIKE": 3.0,
    "POST_SHARE": 2.8,
    "POST_DETAIL_VIEW": 2.0,
    "POST_CLICK": 1.5,
}

NEGATIVE_LABEL_WEIGHTS: Dict[str, float] = {
    "NOT_INTERESTED": 3.0,
    "POST_NEGATIVE_FEEDBACK": 2.8,
    "POST_HIDE": 2.5,
}

RECALL_TARGET_EVENT_WEIGHTS: Dict[str, float] = {
    "POST_COMMENT": 5.0,
    "POST_FAVORITE": 4.2,
    "POST_LIKE": 3.4,
    "POST_SHARE": 2.8,
}

HISTORY_EVENT_TYPES: Tuple[str, ...] = tuple(
    sorted(set(POSITIVE_HISTORY_WEIGHTS) | set(NEGATIVE_HISTORY_WEIGHTS))
)
RANK_LABEL_EVENT_TYPES: Tuple[str, ...] = tuple(
    sorted(set(POSITIVE_LABEL_WEIGHTS) | set(NEGATIVE_LABEL_WEIGHTS))
)
RECALL_TARGET_EVENT_TYPES: Tuple[str, ...] = tuple(RECALL_TARGET_EVENT_WEIGHTS.keys())

RANK_MTL_TASK_DIM = 3
TAG_VOCAB_PATH = os.getenv(
    "TAG_VOCAB_PATH",
    os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "models", "tag_vocabulary.json")),
)
_TEXT_SPLITTER = re.compile(r"[,|/\\>\s_\-:;，。！？、]+")

_SEQ_EVENT_ORDER = [
    "POST_COMMENT",
    "POST_FAVORITE",
    "POST_LIKE",
    "POST_SHARE",
    "POST_DETAIL_VIEW",
    "POST_CLICK",
]


HistoryEvent = Tuple[int, str, datetime]


def candidate_input_dim() -> int:
    return EMBEDDING_DIM * 3 + HISTORY_SUMMARY_DIM + TAG_VECTOR_DIM


def candidate_item_input_dim() -> int:
    return EMBEDDING_DIM + POST_NUMERIC_DIM + TAG_VECTOR_DIM + 2


def rank_feature_dim() -> int:
    return HISTORY_SUMMARY_DIM + POST_NUMERIC_DIM + CROSS_FEATURE_DIM + SCENE_FEATURE_DIM + SEQ_ENCODER_DIM


def _as_float(value: object, default: float = 0.0) -> float:
    if value is None:
        return default
    if isinstance(value, (int, float)):
        return float(value)
    try:
        return float(str(value))
    except Exception:
        return default


def _as_int(value: object, default: int = 0) -> int:
    if value is None:
        return default
    if isinstance(value, int):
        return value
    try:
        return int(str(value))
    except Exception:
        return default


def _to_datetime(value: object) -> Optional[datetime]:
    if value is None:
        return None
    if isinstance(value, datetime):
        return value
    text = str(value).strip()
    if not text:
        return None
    try:
        return datetime.fromisoformat(text.replace("Z", "+00:00"))
    except Exception:
        return None


def build_sequence_features(
    positive_history: Sequence[HistoryEvent],
    post_meta_map: Dict[int, dict],
    now: Optional[datetime],
    max_len: int = SEQ_MAX_LEN,
) -> Tuple[np.ndarray, int]:
    if now is None:
        now = datetime.now()
    safe_max_len = max(1, int(max_len))
    parsed_events = []
    for raw_event in positive_history:
        dwell_ms = 0.0
        rank_position = 0.0
        if isinstance(raw_event, dict):
            post_id = _as_int(raw_event.get("post_id") or raw_event.get("target_id"), 0)
            event_type = str(raw_event.get("event_type", "UNKNOWN")).strip().upper()
            event_ts = _to_datetime(raw_event.get("event_ts") or raw_event.get("created_at")) or now
            dwell_ms = max(0.0, _as_float(raw_event.get("dwell_ms"), 0.0))
            rank_position = max(0.0, _as_float(raw_event.get("rank_position"), 0.0))
        else:
            post_id, event_type, ts = raw_event
            event_ts = ts if isinstance(ts, datetime) else now
        if int(post_id) <= 0:
            continue
        parsed_events.append((int(post_id), str(event_type).strip().upper(), event_ts, dwell_ms, rank_position))
    events = sorted(parsed_events, key=lambda event: event[2], reverse=True)[:safe_max_len]
    events = sorted(events, key=lambda event: event[2])
    seq = np.zeros((safe_max_len, SEQ_EVENT_DIM), dtype=np.float32)
    actual_len = len(events)
    offset = safe_max_len - actual_len
    for index, (post_id, event_type, ts, dwell_ms, rank_position) in enumerate(events):
        pos = offset + index
        if event_type in _SEQ_EVENT_ORDER:
            seq[pos, _SEQ_EVENT_ORDER.index(event_type)] = 1.0
        event_ts = ts if isinstance(ts, datetime) else now
        elapsed_hours = max(0.0, (now - event_ts).total_seconds() / 3600.0)
        dwell_signal = min(math.log1p(dwell_ms) / math.log1p(60000.0), 1.5) if dwell_ms > 0.0 else 0.0
        rank_signal = 0.0 if rank_position <= 0.0 else 1.0 / (1.0 + rank_position / 12.0)
        seq[pos, 6] = math.exp(-elapsed_hours / 72.0)
        meta = post_meta_map.get(int(post_id), {})
        seq[pos, 7] = min(_as_float(meta.get("hot_score")) / 20.0 + dwell_signal * 0.2, 2.0)
        seq[pos, 8] = min(_as_float(meta.get("quality_score")) + rank_signal * 0.15, 1.5)
        seq[pos, 9] = 1.0 / (1.0 + age_hours(meta.get("created_at"), now) / 24.0)
    return seq, actual_len


def build_candidate_embedding(post_id: int, emb_map: Dict[int, np.ndarray]) -> np.ndarray:
    return l2_normalize(emb_map.get(post_id), dim=EMBEDDING_DIM)


def split_tags(value: Optional[object]) -> List[str]:
    if value is None:
        return []
    if isinstance(value, str):
        parts = value.split(",")
    elif isinstance(value, Iterable):
        parts = [str(item) for item in value]
    else:
        parts = [str(value)]
    return [part.strip().lower() for part in parts if str(part).strip()]


def split_topic_path(value: Optional[object]) -> List[str]:
    if value is None:
        return []
    text = str(value).strip().lower()
    if not text:
        return []
    normalized = (
        text.replace(">", "/")
        .replace("|", "/")
        .replace("_", "-")
    )
    parts = [part.strip() for part in normalized.split("/") if part.strip()]
    return parts


def split_text_terms(value: Optional[object], limit: int = 12) -> List[str]:
    if value is None or limit <= 0:
        return []
    text = str(value).strip().lower()
    if not text:
        return []
    terms: List[str] = []
    for token in _TEXT_SPLITTER.split(text):
        cleaned = token.strip()
        if len(cleaned) < 2:
            continue
        if cleaned.isdigit():
            continue
        terms.append(cleaned)
        if len(terms) >= limit:
            break
    return terms


def _normalize_text(value: Optional[object]) -> str:
    if value is None:
        return ""
    return str(value).strip().lower()


def _hash_to_unit(value: str, bucket: int = 997) -> float:
    if not value:
        return 0.0
    digest = hashlib.md5(value.encode("utf-8")).hexdigest()
    return (int(digest, 16) % bucket) / float(max(1, bucket - 1))


def collect_semantic_terms(post_meta: Optional[dict]) -> List[str]:
    if not post_meta:
        return []
    terms: List[str] = []
    terms.extend(split_tags(post_meta.get("tags")))
    terms.extend(split_tags(post_meta.get("semantic_tags")))
    terms.extend(split_tags(post_meta.get("style_tags")))
    terms.extend(split_topic_path(post_meta.get("topic_path")))
    terms.extend(split_text_terms(post_meta.get("topic_cluster_key"), limit=4))
    terms.extend(split_text_terms(post_meta.get("subtopic_cluster_key"), limit=4))
    terms.extend(split_text_terms(post_meta.get("title"), limit=8))
    terms.extend(split_text_terms(post_meta.get("content"), limit=10))
    return [term for term in terms if term]


@lru_cache(maxsize=4)
def _load_tag_vocab(path: str = TAG_VOCAB_PATH, dim: int = TAG_VECTOR_DIM) -> Dict[str, int]:
    if not path:
        return {}
    try:
        with open(os.path.abspath(path), "r", encoding="utf-8") as file_obj:
            payload = json.load(file_obj)
    except Exception:
        return {}

    raw_items: Iterable[Tuple[object, object]]
    if isinstance(payload, dict):
        raw_vocab = payload.get("tag_to_id") if isinstance(payload.get("tag_to_id"), dict) else payload
        raw_items = raw_vocab.items()
    elif isinstance(payload, list):
        raw_items = [(item, index) for index, item in enumerate(payload)]
    else:
        return {}

    result: Dict[str, int] = {}
    for key, value in raw_items:
        term = str(key).strip().lower()
        if not term:
            continue
        try:
            index = int(value)
        except Exception:
            continue
        if 0 <= index < dim:
            result[term] = index
    return result


def hashed_tag_vector(tags: Sequence[str], dim: int = TAG_VECTOR_DIM) -> np.ndarray:
    vector = np.zeros(dim, dtype=np.float32)
    vocab = _load_tag_vocab(dim=dim)
    for tag in tags:
        if not tag:
            continue
        normalized = str(tag).strip().lower()
        if not normalized:
            continue
        index = vocab.get(normalized)
        if index is None:
            digest = hashlib.md5(normalized.encode("utf-8")).hexdigest()
            index = int(digest, 16) % dim
        vector[index] += 1.0
    total = float(vector.sum())
    if total > 0:
        vector /= total
    return vector


def cosine_similarity(left: Optional[np.ndarray], right: Optional[np.ndarray]) -> float:
    if left is None or right is None:
        return 0.0
    left_norm = float(np.linalg.norm(left))
    right_norm = float(np.linalg.norm(right))
    if left_norm <= 1e-8 or right_norm <= 1e-8:
        return 0.0
    return float(np.dot(left / left_norm, right / right_norm))


def l2_normalize(vector: Optional[np.ndarray], dim: int = EMBEDDING_DIM) -> np.ndarray:
    if vector is None:
        return np.zeros(dim, dtype=np.float32)
    norm = float(np.linalg.norm(vector))
    if norm <= 1e-8:
        return np.zeros(dim, dtype=np.float32)
    return (vector / norm).astype(np.float32)


def age_hours(value: object, now: Optional[datetime]) -> float:
    if now is None:
        now = datetime.now()
    created_at = _to_datetime(value)
    if created_at is None:
        return 24.0
    try:
        delta = now - created_at
        return max(0.0, delta.total_seconds() / 3600.0)
    except Exception:
        return 24.0


def _cyclical_pair(value: int, period: int) -> List[float]:
    if period <= 0:
        return [0.0, 0.0]
    radians = 2.0 * math.pi * (value % period) / float(period)
    return [math.sin(radians), math.cos(radians)]


def aggregate_history_embedding(
    history: Sequence[HistoryEvent],
    emb_map: Dict[int, np.ndarray],
    weight_map: Dict[str, float],
    dim: int = EMBEDDING_DIM,
) -> np.ndarray:
    if not history:
        return np.zeros(dim, dtype=np.float32)

    weighted_vectors: List[np.ndarray] = []
    for rank, (post_id, event_type, _) in enumerate(history):
        base_vector = emb_map.get(post_id)
        if base_vector is None:
            continue
        base_weight = weight_map.get(event_type, 0.0)
        if base_weight <= 0:
            continue
        decay = 1.0 / (1.0 + rank * 0.10)
        weighted_vectors.append(base_vector.astype(np.float32) * float(base_weight * decay))

    if not weighted_vectors:
        return np.zeros(dim, dtype=np.float32)

    return l2_normalize(np.vstack(weighted_vectors).sum(axis=0), dim=dim)


def effective_user_embedding(
    positive_history: Sequence[HistoryEvent],
    negative_history: Sequence[HistoryEvent],
    emb_map: Dict[int, np.ndarray],
    negative_alpha: float = 0.35,
) -> np.ndarray:
    positive_vector = aggregate_history_embedding(positive_history, emb_map, POSITIVE_HISTORY_WEIGHTS)
    negative_vector = aggregate_history_embedding(negative_history, emb_map, NEGATIVE_HISTORY_WEIGHTS)
    if float(np.linalg.norm(positive_vector)) <= 1e-8 and float(np.linalg.norm(negative_vector)) <= 1e-8:
        return np.zeros(EMBEDDING_DIM, dtype=np.float32)
    combined = positive_vector - negative_vector * negative_alpha
    return l2_normalize(combined, dim=EMBEDDING_DIM)


def _collect_history_tags(history: Sequence[HistoryEvent], post_meta_map: Dict[int, dict]) -> List[str]:
    tags: List[str] = []
    for post_id, _, _ in history:
        tags.extend(collect_semantic_terms(post_meta_map.get(post_id, {})))
    return tags


def build_history_summary_features(
    positive_history: Sequence[HistoryEvent],
    negative_history: Sequence[HistoryEvent],
    post_meta_map: Dict[int, dict],
    now: Optional[datetime],
) -> np.ndarray:
    if now is None:
        now = datetime.now()

    def summarize(history: Sequence[HistoryEvent], weight_map: Dict[str, float]) -> List[float]:
        if not history:
            return [0.0] * 6

        total_weight = 0.0
        unique_posts = set()
        unique_authors = set()
        unique_tags = set()
        most_recent_ts = history[0][2]

        for post_id, event_type, _ in history:
            total_weight += weight_map.get(event_type, 0.0)
            unique_posts.add(post_id)
            meta = post_meta_map.get(post_id, {})
            author_id = meta.get("author_id")
            if author_id is not None:
                unique_authors.add(author_id)
            unique_tags.update(collect_semantic_terms(meta))

        recency_feature = 1.0 / (1.0 + age_hours(most_recent_ts, now) / 24.0)
        return [
            min(len(history) / MAX_HISTORY_REFERENCE, 1.0),
            min(total_weight / 20.0, 1.0),
            min(len(unique_posts) / MAX_HISTORY_REFERENCE, 1.0),
            min(len(unique_authors) / 20.0, 1.0),
            min(len(unique_tags) / 30.0, 1.0),
            recency_feature,
        ]

    positive_summary = summarize(positive_history, POSITIVE_HISTORY_WEIGHTS)
    negative_summary = summarize(negative_history, NEGATIVE_HISTORY_WEIGHTS)
    return np.array(positive_summary + negative_summary, dtype=np.float32)


def build_tag_preference_vector(
    positive_history: Sequence[HistoryEvent],
    post_meta_map: Dict[int, dict],
    dim: int = TAG_VECTOR_DIM,
) -> np.ndarray:
    weighted_counter: Dict[str, float] = {}
    for rank, (post_id, event_type, _) in enumerate(positive_history):
        tags = collect_semantic_terms(post_meta_map.get(post_id, {}))
        if not tags:
            continue
        base_weight = POSITIVE_HISTORY_WEIGHTS.get(event_type, 0.0)
        if base_weight <= 0:
            continue
        decay = 1.0 / (1.0 + rank * 0.12)
        for tag in tags:
            weighted_counter[tag] = weighted_counter.get(tag, 0.0) + base_weight * decay

    vector = np.zeros(dim, dtype=np.float32)
    vocab = _load_tag_vocab(dim=dim)
    for tag, weight in weighted_counter.items():
        normalized = str(tag).strip().lower()
        index = vocab.get(normalized)
        if index is None:
            digest = hashlib.md5(normalized.encode("utf-8")).hexdigest()
            index = int(digest, 16) % dim
        vector[index] += float(weight)

    total = float(vector.sum())
    if total > 0:
        vector /= total
    return vector


def build_candidate_input(
    positive_history: Sequence[HistoryEvent],
    negative_history: Sequence[HistoryEvent],
    post_meta_map: Dict[int, dict],
    emb_map: Dict[int, np.ndarray],
    now: Optional[datetime],
) -> np.ndarray:
    positive_vector = aggregate_history_embedding(positive_history, emb_map, POSITIVE_HISTORY_WEIGHTS)
    negative_vector = aggregate_history_embedding(negative_history, emb_map, NEGATIVE_HISTORY_WEIGHTS)
    effective_vector = effective_user_embedding(positive_history, negative_history, emb_map)
    history_summary = build_history_summary_features(positive_history, negative_history, post_meta_map, now)
    tag_vector = build_tag_preference_vector(positive_history, post_meta_map)
    return np.concatenate(
        [positive_vector, negative_vector, effective_vector, history_summary, tag_vector],
        axis=0,
    ).astype(np.float32)


def build_candidate_item_input(
    post_id: int,
    post_meta: dict,
    emb_map: Dict[int, np.ndarray],
    now: Optional[datetime],
) -> np.ndarray:
    post_vector = l2_normalize(emb_map.get(post_id), dim=EMBEDDING_DIM)
    post_numeric = build_post_numeric_features(post_meta, now)
    semantic_vector = hashed_tag_vector(collect_semantic_terms(post_meta), dim=TAG_VECTOR_DIM)
    author_hash = _hash_to_unit(str(post_meta.get("author_id") or ""))
    topic_hash = _hash_to_unit("|".join(split_topic_path(post_meta.get("topic_path"))))
    meta_hashes = np.array([author_hash, topic_hash], dtype=np.float32)
    return np.concatenate([post_vector, post_numeric, semantic_vector, meta_hashes], axis=0).astype(np.float32)


def build_post_numeric_features(post_meta: dict, now: Optional[datetime]) -> np.ndarray:
    created_at = _to_datetime(post_meta.get("created_at"))
    hot_score = max(0.0, _as_float(post_meta.get("hot_score")))
    realtime = post_meta.get("realtime_metrics") if isinstance(post_meta.get("realtime_metrics"), dict) else {}
    if realtime:
        exposure_1h = max(0.0, _as_float(realtime.get("exposure_1h")))
        exposure_24h = max(0.0, _as_float(realtime.get("exposure_24h")))
        confidence_1h = 0.0 if exposure_1h <= 0.0 else min(1.0, math.log1p(exposure_1h) / math.log1p(80.0))
        confidence_24h = 0.0 if exposure_24h <= 0.0 else min(1.0, math.log1p(exposure_24h) / math.log1p(420.0))
        realtime_boost = (
            (_as_float(realtime.get("ctr_1h")) * 12.0 + _as_float(realtime.get("positive_rate_1h")) * 18.0) * confidence_1h
            + (_as_float(realtime.get("ctr_24h")) * 8.0 + _as_float(realtime.get("positive_rate_24h")) * 10.0) * confidence_24h
            - _as_float(realtime.get("negative_rate_24h")) * 12.0 * confidence_24h
        )
        hot_score += max(-8.0, min(10.0, realtime_boost))
    like_count = max(0.0, _as_float(post_meta.get("like_count") or post_meta.get("like")))
    favorite_count = max(0.0, _as_float(post_meta.get("favorite_count") or post_meta.get("fav")))
    comment_count = max(0.0, _as_float(post_meta.get("comment_count") or post_meta.get("comment")))
    view_count = max(0.0, _as_float(post_meta.get("view_count") or post_meta.get("view")))
    quality_score = max(0.0, _as_float(post_meta.get("quality_score")))
    aesthetic_score = max(0.0, _as_float(post_meta.get("aesthetic_score")))
    safety_score = max(0.0, _as_float(post_meta.get("safety_score"), 1.0))
    item_age_hours = age_hours(created_at, now)

    hour = created_at.hour if created_at is not None else 0
    weekday = created_at.weekday() if created_at is not None else 0
    hour_sin, hour_cos = _cyclical_pair(hour, 24)
    day_sin, day_cos = _cyclical_pair(weekday, 7)

    return np.array(
        [
            min(hot_score / 20.0, 2.0),
            min(math.log1p(like_count) / 8.0, 1.5),
            min(math.log1p(favorite_count) / 8.0, 1.5),
            min(math.log1p(comment_count) / 8.0, 1.5),
            min(math.log1p(view_count) / 12.0, 1.5),
            1.0 / (1.0 + item_age_hours / 24.0),
            min(math.log1p(item_age_hours) / 6.0, 1.5),
            min(quality_score, 1.5),
            min(aesthetic_score, 1.5),
            min(safety_score, 1.0),
            hour_sin,
            hour_cos,
            day_sin,
            day_cos,
        ],
        dtype=np.float32,
    )


def build_cross_features(
    post_id: int,
    post_meta: dict,
    post_meta_map: Dict[int, dict],
    positive_history: Sequence[HistoryEvent],
    negative_history: Sequence[HistoryEvent],
    emb_map: Dict[int, np.ndarray],
    generated_user_vector: Optional[np.ndarray] = None,
) -> np.ndarray:
    positive_vector = aggregate_history_embedding(positive_history, emb_map, POSITIVE_HISTORY_WEIGHTS)
    negative_vector = aggregate_history_embedding(negative_history, emb_map, NEGATIVE_HISTORY_WEIGHTS)
    effective_vector = (
        l2_normalize(generated_user_vector, dim=EMBEDDING_DIM)
        if generated_user_vector is not None
        else effective_user_embedding(positive_history, negative_history, emb_map)
    )
    post_vector = emb_map.get(post_id)

    current_tags = set(collect_semantic_terms(post_meta))
    positive_tags = set(_collect_history_tags(positive_history, post_meta_map))
    negative_tags = set(_collect_history_tags(negative_history, post_meta_map))
    overlap = len(current_tags & positive_tags)
    union = len(current_tags | positive_tags)
    current_topics = set(split_topic_path(post_meta.get("topic_path")))
    positive_topics = set()
    positive_styles = set()
    negative_styles = set()
    for history_post_id, _, _ in positive_history:
        meta = post_meta_map.get(history_post_id, {})
        positive_topics.update(split_topic_path(meta.get("topic_path")))
        positive_styles.update(split_tags(meta.get("style_tags")))
    for history_post_id, _, _ in negative_history:
        negative_styles.update(split_tags(post_meta_map.get(history_post_id, {}).get("style_tags")))
    current_styles = set(split_tags(post_meta.get("style_tags")))

    author_id = post_meta.get("author_id")
    same_author_positive = 0
    same_author_negative = 0
    target_seen_positive = 0.0
    target_seen_negative = 0.0

    for history_post_id, _, _ in positive_history:
        history_author = post_meta_map.get(history_post_id, {}).get("author_id")
        if author_id is not None and history_author == author_id:
            same_author_positive += 1
        if history_post_id == post_id:
            target_seen_positive = 1.0

    for history_post_id, _, _ in negative_history:
        history_author = post_meta_map.get(history_post_id, {}).get("author_id")
        if author_id is not None and history_author == author_id:
            same_author_negative += 1
        if history_post_id == post_id:
            target_seen_negative = 1.0

    negative_tag_hit = 1.0 if current_tags and current_tags & negative_tags else 0.0
    topic_hit = 1.0 if current_topics and current_topics & positive_topics else 0.0
    style_hit = 1.0 if current_styles and current_styles & positive_styles else 0.0

    return np.array(
        [
            cosine_similarity(effective_vector, post_vector),
            cosine_similarity(positive_vector, post_vector),
            cosine_similarity(negative_vector, post_vector),
            overlap / max(1, len(current_tags)),
            overlap / max(1, union),
            same_author_positive / max(1, len(positive_history)),
            same_author_negative / max(1, len(negative_history)),
            target_seen_positive,
            target_seen_negative,
            negative_tag_hit,
            topic_hit,
            style_hit,
        ],
        dtype=np.float32,
    )


def build_rank_features(
    post_id: int,
    post_meta: dict,
    post_meta_map: Dict[int, dict],
    positive_history: Sequence[HistoryEvent],
    negative_history: Sequence[HistoryEvent],
    emb_map: Dict[int, np.ndarray],
    now: Optional[datetime],
    scene_context: Optional[dict] = None,
    generated_user_vector: Optional[np.ndarray] = None,
    seq_vec: Optional[np.ndarray] = None,
) -> np.ndarray:
    history_summary = build_history_summary_features(positive_history, negative_history, post_meta_map, now)
    post_numeric = build_post_numeric_features(post_meta, now)
    cross_features = build_cross_features(
        post_id=post_id,
        post_meta=post_meta,
        post_meta_map=post_meta_map,
        positive_history=positive_history,
        negative_history=negative_history,
        emb_map=emb_map,
        generated_user_vector=generated_user_vector,
    )
    enriched_scene_context = dict(scene_context or {})
    enriched_scene_context.setdefault("post_terms", collect_semantic_terms(post_meta))
    scene_features = build_scene_features(enriched_scene_context)
    sequence_vector = (
        np.asarray(seq_vec, dtype=np.float32)
        if seq_vec is not None
        else np.zeros(SEQ_ENCODER_DIM, dtype=np.float32)
    )
    if sequence_vector.shape != (SEQ_ENCODER_DIM,):
        fixed_sequence_vector = np.zeros(SEQ_ENCODER_DIM, dtype=np.float32)
        fixed_sequence_vector[: min(SEQ_ENCODER_DIM, sequence_vector.size)] = sequence_vector.reshape(-1)[:SEQ_ENCODER_DIM]
        sequence_vector = fixed_sequence_vector
    return np.concatenate(
        [history_summary, post_numeric, cross_features, scene_features, sequence_vector],
        axis=0,
    ).astype(np.float32)


def build_scene_features(scene_context: Optional[dict]) -> np.ndarray:
    context = scene_context or {}
    surface = _normalize_text(context.get("surface") or context.get("scene"))
    recall_source = _normalize_text(context.get("recall_source"))
    device_type = _normalize_text(context.get("device_type"))

    rank_position = max(0, _as_int(context.get("rank_position"), 0))
    page_no = max(1, _as_int(context.get("page_no"), 1))

    is_home = 1.0 if "home" in surface else 0.0
    is_detail = 1.0 if "detail" in surface else 0.0
    is_similar = 1.0 if "similar" in surface else 0.0
    is_search = 1.0 if "search" in surface else 0.0
    is_unknown_surface = 1.0 if (is_home + is_detail + is_similar + is_search) <= 0.0 else 0.0

    rank_bias = 0.0 if rank_position <= 0 else 1.0 / (1.0 + rank_position / 12.0)
    page_bias = 1.0 / (1.0 + max(0, page_no - 1) / 4.0)

    is_mobile = 1.0 if any(token in device_type for token in ("mobile", "android", "ios", "app")) else 0.0
    is_desktop = 1.0 if any(token in device_type for token in ("desktop", "web", "windows", "mac")) else 0.0

    source_hash = _hash_to_unit(recall_source)
    realtime_terms = {str(term).strip().lower() for term in (context.get("realtime_interest_terms") or []) if str(term).strip()}
    post_terms = {str(term).strip().lower() for term in (context.get("post_terms") or []) if str(term).strip()}
    realtime_hits = realtime_terms & post_terms
    realtime_hit = 1.0 if realtime_hits else 0.0
    realtime_overlap = min(1.0, len(realtime_hits) / float(max(1, len(realtime_terms)))) if realtime_terms else 0.0

    return np.array(
        [
            is_home,
            is_detail,
            is_similar,
            is_search,
            is_unknown_surface,
            rank_bias,
            page_bias,
            is_mobile,
            is_desktop,
            source_hash,
            realtime_hit,
            realtime_overlap,
        ],
        dtype=np.float32,
    )


def fallback_rank_score(features: np.ndarray) -> float:
    post_offset = HISTORY_SUMMARY_DIM
    cross_offset = HISTORY_SUMMARY_DIM + POST_NUMERIC_DIM
    scene_offset = HISTORY_SUMMARY_DIM + POST_NUMERIC_DIM + CROSS_FEATURE_DIM
    effective_similarity = float(features[cross_offset])
    hot_score = float(features[post_offset])
    freshness = float(features[post_offset + 5])
    quality = float(features[post_offset + 7])
    aesthetic = float(features[post_offset + 8])
    safety = float(features[post_offset + 9])
    tag_overlap = float(features[cross_offset + 3])
    negative_tag_hit = float(features[cross_offset + 9])
    topic_hit = float(features[cross_offset + 10])
    style_hit = float(features[cross_offset + 11])
    scene_home = float(features[scene_offset])
    scene_rank_bias = float(features[scene_offset + 5])
    scene_page_bias = float(features[scene_offset + 6])
    scene_mobile = float(features[scene_offset + 7])
    return (
        effective_similarity * 0.48
        + hot_score * 0.08
        + freshness * 0.14
        + quality * 0.08
        + aesthetic * 0.10
        + safety * 0.05
        + tag_overlap * 0.12
        + topic_hit * 0.10
        + style_hit * 0.08
        + scene_rank_bias * 0.08
        + scene_page_bias * 0.04
        + scene_home * 0.03
        + scene_mobile * 0.01
        - negative_tag_hit * 0.16
    )


class CandidateGeneratorModel(nn.Module):
    def __init__(self, input_dim: int = candidate_input_dim(), output_dim: int = EMBEDDING_DIM):
        super().__init__()
        self.user_tower = nn.Sequential(
            nn.Linear(input_dim, 1024),
            nn.LayerNorm(1024),
            nn.GELU(),
            nn.Dropout(0.12),
            nn.Linear(1024, 768),
            nn.GELU(),
            nn.Dropout(0.10),
            nn.Linear(768, 512),
            nn.GELU(),
            nn.Linear(512, output_dim),
        )
        self.item_tower = nn.Sequential(
            nn.Linear(candidate_item_input_dim(), 768),
            nn.LayerNorm(768),
            nn.GELU(),
            nn.Dropout(0.10),
            nn.Linear(768, 512),
            nn.GELU(),
            nn.Linear(512, output_dim),
        )

    def encode_user(self, features: torch.Tensor) -> torch.Tensor:
        output = self.user_tower(features)
        return F.normalize(output, dim=-1)

    def encode_item(self, features: torch.Tensor) -> torch.Tensor:
        output = self.item_tower(features)
        return F.normalize(output, dim=-1)

    def forward(self, features: torch.Tensor) -> torch.Tensor:
        return self.encode_user(features)


class SASRecSequenceEncoder(nn.Module):
    def __init__(
        self,
        event_dim: int = SEQ_EVENT_DIM,
        hidden_dim: int = SEQ_ENCODER_DIM,
        num_layers: int = 2,
        num_heads: int = 4,
        dropout: float = 0.15,
        max_len: int = SEQUENCE_MAX_LEN,
    ):
        super().__init__()
        self.max_len = int(max_len)
        self.input_projection = nn.Linear(event_dim, hidden_dim)
        self.position_embedding = nn.Embedding(max_len, hidden_dim)
        encoder_layer = nn.TransformerEncoderLayer(
            d_model=hidden_dim,
            nhead=num_heads,
            dim_feedforward=hidden_dim * 4,
            dropout=dropout,
            activation="gelu",
            batch_first=True,
            norm_first=True,
        )
        self.encoder = nn.TransformerEncoder(encoder_layer, num_layers=num_layers)
        self.norm = nn.LayerNorm(hidden_dim)
        self.dropout = nn.Dropout(dropout)

    def forward(self, seq: torch.Tensor, lengths: torch.Tensor) -> torch.Tensor:
        batch_size = seq.size(0)
        seq_len = seq.size(1)
        safe_lengths = lengths.clamp(min=1, max=seq_len)
        positions = torch.arange(seq_len, device=seq.device).unsqueeze(0).expand(batch_size, seq_len)
        valid_start = (seq_len - safe_lengths).unsqueeze(1)
        padding_mask = positions < valid_start
        causal_mask = torch.triu(
            torch.ones((seq_len, seq_len), dtype=torch.bool, device=seq.device),
            diagonal=1,
        )
        hidden = self.input_projection(seq) + self.position_embedding(positions.clamp(max=self.max_len - 1))
        hidden = self.dropout(hidden)
        encoded = self.encoder(hidden, mask=causal_mask, src_key_padding_mask=padding_mask)
        last_indices = torch.full((batch_size, 1, 1), seq_len - 1, dtype=torch.long, device=seq.device)
        last_indices = last_indices.expand(batch_size, 1, encoded.size(-1))
        pooled = encoded.gather(1, last_indices).squeeze(1)
        return self.dropout(self.norm(pooled))


UserSequenceEncoder = SASRecSequenceEncoder


class CrossLayer(nn.Module):
    def __init__(self, dim: int):
        super().__init__()
        self.W = nn.Linear(dim, dim, bias=True)

    def forward(self, x0: torch.Tensor, x: torch.Tensor) -> torch.Tensor:
        return x0 * self.W(x) + x


class DCNv2CrossLayer(nn.Module):
    def __init__(self, dim: int, low_rank: int = 64, experts: int = 4):
        super().__init__()
        safe_rank = max(8, min(low_rank, dim))
        safe_experts = max(1, experts)
        self.U = nn.Parameter(torch.empty(safe_experts, safe_rank, dim))
        self.V = nn.Parameter(torch.empty(safe_experts, dim, safe_rank))
        self.C = nn.Parameter(torch.empty(safe_experts, safe_rank, safe_rank))
        self.bias = nn.Parameter(torch.zeros(safe_experts, dim))
        self.gating = nn.Linear(dim, safe_experts)
        self.reset_parameters()

    def reset_parameters(self) -> None:
        for param in (self.U, self.V, self.C):
            nn.init.xavier_uniform_(param)
        nn.init.zeros_(self.bias)

    def forward(self, x0: torch.Tensor, x: torch.Tensor) -> torch.Tensor:
        gates = torch.softmax(self.gating(x), dim=-1)
        expert_outputs = torch.jit.annotate(List[torch.Tensor], [])
        for expert_idx in range(self.U.size(0)):
            projected = torch.matmul(x, self.V[expert_idx])
            projected = F.gelu(torch.matmul(projected, self.C[expert_idx]))
            crossed = torch.matmul(projected, self.U[expert_idx])
            expert_outputs.append(x0 * (crossed + self.bias[expert_idx]) + x)
        stacked = torch.stack(expert_outputs, dim=1)
        return torch.sum(stacked * gates.unsqueeze(-1), dim=1)


class DCNv2CrossNetwork(nn.Module):
    def __init__(self, dim: int, num_layers: int = 3, low_rank: int = 64, experts: int = 4):
        super().__init__()
        self.layers = nn.ModuleList([
            DCNv2CrossLayer(dim, low_rank=low_rank, experts=experts)
            for _ in range(num_layers)
        ])

    def forward(self, x0: torch.Tensor) -> torch.Tensor:
        x = x0
        for layer in self.layers:
            x = layer(x0, x)
        return x


class MTLRankModel(nn.Module):
    def __init__(self, input_dim: int = rank_feature_dim()):
        super().__init__()
        self.register_buffer("task_weights", torch.tensor([0.40, 0.42, 0.18], dtype=torch.float32))
        self.log_task_uncertainty = nn.Parameter(torch.zeros(RANK_MTL_TASK_DIM, dtype=torch.float32))
        self.cross_network = DCNv2CrossNetwork(input_dim, num_layers=3, low_rank=64, experts=4)
        self.shared_dnn = nn.Sequential(
            nn.Linear(input_dim * 2, 512),
            nn.LayerNorm(512),
            nn.ReLU(),
            nn.Dropout(0.15),
            nn.Linear(512, 256),
            nn.ReLU(),
            nn.Dropout(0.10),
        )
        self.ctr_head = nn.Sequential(nn.Linear(256, 64), nn.ReLU(), nn.Linear(64, 1))
        self.cvr_head = nn.Sequential(nn.Linear(256, 64), nn.ReLU(), nn.Linear(64, 1))
        self.quality_head = nn.Sequential(nn.Linear(256, 32), nn.ReLU(), nn.Linear(32, 1))

    def forward_all(self, x: torch.Tensor) -> Tuple[torch.Tensor, torch.Tensor, torch.Tensor]:
        x0 = x
        xc = self.cross_network(x0)
        shared = self.shared_dnn(torch.cat([x0, xc], dim=-1))
        return (
            self.ctr_head(shared).squeeze(-1),
            self.cvr_head(shared).squeeze(-1),
            self.quality_head(shared).squeeze(-1),
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        ctr, cvr, quality = self.forward_all(x)
        learned_weights = torch.softmax(-self.log_task_uncertainty, dim=0)
        return ctr * learned_weights[0] + cvr * learned_weights[1] + quality * learned_weights[2]

    def multi_task_loss(self, task_losses: torch.Tensor) -> torch.Tensor:
        precision = torch.exp(-self.log_task_uncertainty)
        return torch.sum(precision * task_losses + self.log_task_uncertainty)


RankModel = MTLRankModel
