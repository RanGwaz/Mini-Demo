from __future__ import annotations

import json
import math
import os
import shutil
import tempfile
from datetime import datetime
from typing import Dict, List, Optional, Tuple

import numpy as np
import pymysql
import redis as _redis
import torch
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from pymilvus import Collection, connections

from reco_features import (
    HISTORY_EVENT_TYPES,
    NEGATIVE_LABEL_WEIGHTS,
    POSITIVE_LABEL_WEIGHTS,
    SEQ_ENCODER_DIM,
    SEQUENCE_MAX_LEN,
    build_candidate_input,
    build_rank_features,
    build_sequence_features,
    effective_user_embedding,
    l2_normalize,
)

DEVICE = torch.device("cuda" if torch.cuda.is_available() else "cpu")
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
LOCAL_MODEL_DIR = os.path.abspath(os.path.join(SCRIPT_DIR, "..", "models"))

MYSQL_HOST = os.getenv("MYSQL_HOST", "localhost")
MYSQL_PORT = int(os.getenv("MYSQL_PORT", "3306"))
MYSQL_USER = os.getenv("MYSQL_USER", "root")
MYSQL_PASSWORD = os.getenv("MYSQL_PASSWORD", "root123456")
MYSQL_DATABASE = os.getenv("MYSQL_DATABASE", "image_social")

MILVUS_HOST = os.getenv("MILVUS_HOST", "localhost")
MILVUS_PORT = os.getenv("MILVUS_PORT", "19530")
MILVUS_COLLECTION = os.getenv("MILVUS_COLLECTION", "post_embeddings")
USER_COLLECTION_NAME = os.getenv("MILVUS_USER_COLLECTION", "user_embeddings")
REDIS_HOST = os.getenv("REDIS_HOST", "localhost")
REDIS_PORT = int(os.getenv("REDIS_PORT", "6379"))
REDIS_DB = int(os.getenv("REDIS_DB", "0"))
REDIS_PWD = os.getenv("REDIS_PASSWORD") or None

def _default_model_path(filename: str) -> str:
    local_path = os.path.join(LOCAL_MODEL_DIR, filename)
    if os.path.exists(local_path):
        return local_path
    return os.path.join(os.sep, "models", filename)


RANK_MODEL_PATH = os.getenv("RANK_MODEL_PATH", _default_model_path("deep_rank.pt"))
CANDIDATE_MODEL_PATH = os.getenv("CANDIDATE_MODEL_PATH", _default_model_path("candidate_generator.pt"))
SEQ_ENCODER_PATH = os.getenv("SEQ_ENCODER_PATH", _default_model_path("seq_encoder.pt"))
RANK_CALIBRATION_PATH = os.getenv("RANK_CALIBRATION_PATH", _default_model_path("deep_rank_calibration.json"))
MAX_USER_HISTORY = int(os.getenv("MAX_USER_HISTORY", "50"))
RECALL_NPROBE = int(os.getenv("RECALL_NPROBE", "16"))

app = FastAPI(title="deep-rank-service", version="2.0.0")


class Candidate(BaseModel):
    post_id: int
    author_id: Optional[int] = 0
    title: Optional[str] = ""
    content: Optional[str] = ""
    cover_url: Optional[str] = ""
    thumb_url: Optional[str] = ""
    topic_cluster_key: Optional[str] = ""
    subtopic_cluster_key: Optional[str] = ""
    hot_score: Optional[float] = 0.0
    like_count: Optional[int] = 0
    favorite_count: Optional[int] = 0
    comment_count: Optional[int] = 0
    view_count: Optional[int] = 0
    tags: Optional[str] = ""
    topic_path: Optional[str] = ""
    semantic_tags: Optional[str] = ""
    style_tags: Optional[str] = ""
    quality_score: Optional[float] = 0.0
    aesthetic_score: Optional[float] = 0.0
    safety_score: Optional[float] = 1.0
    created_at: Optional[str] = ""
    realtime_metrics: Optional[Dict[str, float]] = None


class RankRequest(BaseModel):
    user_id: Optional[int] = None
    scene: Optional[str] = "home_feed"
    page_no: Optional[int] = 1
    device_type: Optional[str] = ""
    experiment_id: Optional[str] = ""
    behavior_sequence: List[dict]
    candidates: List[Candidate]


class ScoreItem(BaseModel):
    post_id: int
    score: float


class RankResponse(BaseModel):
    scores: List[ScoreItem]


class RecallRequest(BaseModel):
    user_id: int
    limit: int = 50
    exclude_post_ids: List[int] = []


class SimilarRecallRequest(BaseModel):
    post_id: int
    limit: int = 50
    exclude_post_ids: List[int] = []


class RecallResponse(BaseModel):
    post_ids: List[int]


def _mysql_conn():
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


def _load_milvus_collection() -> Collection:
    connections.connect(host=MILVUS_HOST, port=MILVUS_PORT)
    collection = Collection(MILVUS_COLLECTION)
    collection.load()
    return collection


def _maybe_load_model(path: str):
    if not path:
        return None
    normalized = os.path.abspath(path)
    if not os.path.exists(normalized):
        return None
    try:
        return torch.jit.load(normalized, map_location=DEVICE).eval()
    except Exception:
        # Work around occasional non-ASCII path issues on Windows when loading TorchScript.
        temp_path = os.path.join(tempfile.gettempdir(), os.path.basename(normalized))
        shutil.copyfile(normalized, temp_path)
        return torch.jit.load(temp_path, map_location=DEVICE).eval()


def _maybe_load_calibration(path: str) -> Optional[dict]:
    if not path:
        return None
    normalized = os.path.abspath(path)
    if not os.path.exists(normalized):
        return None
    try:
        with open(normalized, "r", encoding="utf-8") as file_obj:
            payload = json.load(file_obj)
        if isinstance(payload, dict):
            if payload.get("enabled"):
                scale = float(payload.get("scale", 1.0))
                bias = float(payload.get("bias", 0.0))
                if not math.isfinite(scale) or not math.isfinite(bias) or scale <= 0.0:
                    return {
                        "enabled": False,
                        "scale": 1.0,
                        "bias": 0.0,
                        "iterations": int(payload.get("iterations", 0) or 0),
                        "status": "invalid_non_finite",
                    }
            return payload
    except Exception:
        return None
    return None


def _apply_rank_calibration(logits: np.ndarray, calibration: Optional[dict]) -> np.ndarray:
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


rank_model = _maybe_load_model(RANK_MODEL_PATH)
candidate_model = _maybe_load_model(CANDIDATE_MODEL_PATH)
seq_encoder = _maybe_load_model(SEQ_ENCODER_PATH)
rank_calibration = _maybe_load_calibration(RANK_CALIBRATION_PATH)
collection: Optional[Collection] = None
user_collection: Optional[Collection] = None
_redis_client = None

if rank_model is None:
    raise RuntimeError(
        f"rank model is required but not found: {os.path.abspath(RANK_MODEL_PATH)}"
    )
if seq_encoder is None:
    raise RuntimeError(
        f"sequence encoder is required but not found: {os.path.abspath(SEQ_ENCODER_PATH)}"
    )


def _get_collection() -> Optional[Collection]:
    global collection
    if collection is None:
        try:
            collection = _load_milvus_collection()
        except Exception:
            return None
    return collection


def _get_user_collection() -> Optional[Collection]:
    global user_collection
    if user_collection is None:
        try:
            connections.connect(host=MILVUS_HOST, port=MILVUS_PORT)
            user_collection = Collection(USER_COLLECTION_NAME)
            user_collection.load()
        except Exception:
            return None
    return user_collection


def _get_redis():
    global _redis_client
    if _redis_client is None:
        try:
            _redis_client = _redis.Redis(
                host=REDIS_HOST,
                port=REDIS_PORT,
                db=REDIS_DB,
                password=REDIS_PWD,
                decode_responses=True,
                socket_timeout=0.1,
            )
        except Exception:
            return None
    return _redis_client


def _get_realtime_interest_terms(user_id: Optional[int], top_n: int = 16, window: str = "1h") -> List[str]:
    if user_id is None:
        return []
    redis_client = _get_redis()
    if redis_client is None:
        return []
    for win in (window, "6h", "24h"):
        try:
            raw = redis_client.hgetall(f"user:{int(user_id)}:interests:{win}")
        except Exception:
            raw = {}
        if raw:
            sorted_tags = sorted(raw.items(), key=lambda item: float(item[1]), reverse=True)
            return [str(tag) for tag, _ in sorted_tags[:top_n]]
    return []


@app.get("/healthz")
def healthz():
    return {
        "status": "ok",
        "rank_model": bool(rank_model),
        "seq_encoder": bool(seq_encoder),
        "candidate_model": bool(candidate_model),
        "rank_calibration": bool(rank_calibration and rank_calibration.get("enabled")),
    }


def _fetch_recent_user_event_rows(user_id: int, limit: int) -> Tuple[List[Tuple[int, str, datetime]], List[Tuple[int, str, datetime]], List[dict]]:
    placeholders = ",".join(["%s"] * len(HISTORY_EVENT_TYPES))
    conn = _mysql_conn()
    try:
        with conn.cursor() as cur:
            cur.execute(
                f"""
                SELECT target_id, event_type, created_at
                       , COALESCE(dwell_ms, 0) AS dwell_ms
                       , COALESCE(rank_position, 0) AS rank_position
                       , COALESCE(surface, '') AS surface
                       , COALESCE(page_no, 0) AS page_no
                       , COALESCE(device_type, '') AS device_type
                       , COALESCE(recall_source, '') AS recall_source
                FROM user_events
                WHERE user_id=%s
                  AND target_type='POST'
                  AND target_id IS NOT NULL
                  AND event_type IN ({placeholders})
                ORDER BY created_at DESC
                LIMIT %s
                """,
                (user_id, *HISTORY_EVENT_TYPES, max(limit * 4, SEQUENCE_MAX_LEN)),
            )
            rows = cur.fetchall()
    finally:
        conn.close()

    positive_rows: List[Tuple[int, str, datetime]] = []
    negative_rows: List[Tuple[int, str, datetime]] = []
    sequence_rows: List[dict] = []
    for row in rows:
        event_type = str(row["event_type"]).strip().upper()
        event_ts = row["created_at"]
        post_id = int(row["target_id"])
        item = (post_id, event_type, event_ts)
        if len(sequence_rows) < SEQUENCE_MAX_LEN:
            sequence_rows.append(
                {
                    "post_id": post_id,
                    "event_type": event_type,
                    "event_ts": event_ts,
                    "dwell_ms": int(row.get("dwell_ms") or 0),
                    "rank_position": int(row.get("rank_position") or 0),
                    "surface": row.get("surface") or "",
                    "page_no": int(row.get("page_no") or 0),
                    "device_type": row.get("device_type") or "",
                    "recall_source": row.get("recall_source") or "",
                }
            )
        if item[1] in POSITIVE_LABEL_WEIGHTS and len(positive_rows) < limit:
            positive_rows.append(item)
        elif item[1] in NEGATIVE_LABEL_WEIGHTS and len(negative_rows) < limit:
            negative_rows.append(item)
        if len(sequence_rows) >= SEQUENCE_MAX_LEN and len(positive_rows) >= limit and len(negative_rows) >= limit:
            break
    return positive_rows, negative_rows, sequence_rows


def _fetch_post_meta_rows(post_ids: List[int]) -> Dict[int, dict]:
    unique_ids = sorted({int(post_id) for post_id in post_ids if post_id is not None})
    if not unique_ids:
        return {}

    placeholders = ",".join(["%s"] * len(unique_ids))
    conn = _mysql_conn()
    try:
        with conn.cursor() as cur:
            cur.execute(
                f"""
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
                WHERE id IN ({placeholders})
                """,
                unique_ids,
            )
            rows = cur.fetchall()
    finally:
        conn.close()

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


def _query_embeddings(post_ids: List[int]) -> Dict[int, np.ndarray]:
    unique_ids = sorted({int(post_id) for post_id in post_ids if post_id is not None})
    if not unique_ids:
        return {}

    collection_obj = _get_collection()
    if collection_obj is None:
        return {}

    result: Dict[int, np.ndarray] = {}
    chunk_size = 200
    for start in range(0, len(unique_ids), chunk_size):
        chunk = unique_ids[start:start + chunk_size]
        rows = collection_obj.query(
            expr=f"post_id in {chunk}",
            output_fields=["post_id", "embedding"],
        )
        for row in rows:
            result[int(row["post_id"])] = l2_normalize(np.array(row["embedding"], dtype=np.float32))
    return result


def _candidate_user_vector(
    user_id: Optional[int],
    positive_history: List[Tuple[int, str, datetime]],
    negative_history: List[Tuple[int, str, datetime]],
    history_post_meta: Dict[int, dict],
    history_emb_map: Dict[int, np.ndarray],
) -> np.ndarray:
    if user_id is None:
        return np.zeros(512, dtype=np.float32)
    if candidate_model is None:
        return effective_user_embedding(positive_history, negative_history, history_emb_map)

    features = build_candidate_input(
        positive_history=positive_history,
        negative_history=negative_history,
        post_meta_map=history_post_meta,
        emb_map=history_emb_map,
        now=datetime.now(),
    )
    try:
        with torch.no_grad():
            output = candidate_model(
                torch.tensor(features[None, :], dtype=torch.float32, device=DEVICE)
            )
        return l2_normalize(output.detach().cpu().numpy()[0])
    except Exception:
        return effective_user_embedding(positive_history, negative_history, history_emb_map)


def _compute_seq_vec(
    positive_history: List[Tuple[int, str, datetime]],
    post_meta_map: Dict[int, dict],
    now: datetime,
    sequence_history: Optional[List[dict]] = None,
) -> np.ndarray:
    seq_arr, actual_len = build_sequence_features(
        positive_history=sequence_history or positive_history,
        post_meta_map=post_meta_map,
        now=now,
    )
    if actual_len <= 0:
        return np.zeros(SEQ_ENCODER_DIM, dtype=np.float32)
    with torch.no_grad():
        seq_tensor = torch.tensor(seq_arr[None, :, :], dtype=torch.float32, device=DEVICE)
        length_tensor = torch.tensor([actual_len], dtype=torch.long, device=DEVICE)
        output = seq_encoder(seq_tensor, length_tensor)
    return output.detach().cpu().numpy()[0].astype(np.float32)


def _normalize_candidate_meta(candidate: Candidate) -> dict:
    return {
        "id": int(candidate.post_id),
        "author_id": int(candidate.author_id or 0),
        "title": candidate.title or "",
        "content": candidate.content or "",
        "cover_url": candidate.cover_url or "",
        "thumb_url": candidate.thumb_url or "",
        "tags": candidate.tags or "",
        "topic_path": candidate.topic_path or "",
        "topic_cluster_key": candidate.topic_cluster_key or "",
        "subtopic_cluster_key": candidate.subtopic_cluster_key or "",
        "semantic_tags": candidate.semantic_tags or "",
        "style_tags": candidate.style_tags or "",
        "hot_score": float(candidate.hot_score or 0.0),
        "like_count": int(candidate.like_count or 0),
        "favorite_count": int(candidate.favorite_count or 0),
        "comment_count": int(candidate.comment_count or 0),
        "view_count": int(candidate.view_count or 0),
        "quality_score": float(candidate.quality_score or 0.0),
        "aesthetic_score": float(candidate.aesthetic_score or 0.0),
        "safety_score": float(candidate.safety_score or 1.0),
        "created_at": candidate.created_at or "",
        "realtime_metrics": candidate.realtime_metrics or {},
    }


def _recall_by_vector(vector: np.ndarray, limit: int, exclude_post_ids: List[int]) -> List[int]:
    if float(np.linalg.norm(vector)) <= 1e-8:
        return []

    collection_obj = _get_collection()
    if collection_obj is None:
        return []

    safe_limit = max(1, min(limit, 500))
    excluded = set(exclude_post_ids or [])
    search_param = {"metric_type": "IP", "params": {"nprobe": RECALL_NPROBE}}

    try:
        result = collection_obj.search(
            data=[vector.tolist()],
            anns_field="embedding",
            param=search_param,
            limit=safe_limit + len(excluded) + 20,
            output_fields=["post_id"],
        )
    except Exception:
        return []

    output: List[int] = []
    for hit in result[0]:
        post_id = int(hit.entity.get("post_id"))
        if post_id in excluded:
            continue
        output.append(post_id)
        if len(output) >= safe_limit:
            break
    return output


@app.post("/infer/rank", response_model=RankResponse)
def infer_rank(request: RankRequest):
    if not request.candidates:
        return RankResponse(scores=[])
    sequence_history = list(request.behavior_sequence)[:SEQUENCE_MAX_LEN]
    positive_history: List[Tuple[int, str, datetime]] = []
    negative_history: List[Tuple[int, str, datetime]] = []
    for event in sequence_history:
        post_id = int(event.get("post_id", event.get("target_id", 0)) or 0)
        event_type = str(event.get("event_type", "UNKNOWN")).strip().upper()
        event_ts = event.get("event_ts", event.get("created_at"))
        if post_id <= 0:
            continue
        if isinstance(event_ts, str):
            try:
                event_dt = datetime.fromisoformat(event_ts.replace("Z", "+00:00"))
            except Exception:
                event_dt = datetime.now()
        elif isinstance(event_ts, (int, float)):
            try:
                event_dt = datetime.fromtimestamp(float(event_ts) / 1000.0)
            except Exception:
                event_dt = datetime.now()
        elif isinstance(event_ts, datetime):
            event_dt = event_ts
        else:
            event_dt = datetime.now()
        event_tuple = (post_id, event_type, event_dt)
        if event_type in POSITIVE_LABEL_WEIGHTS and len(positive_history) < MAX_USER_HISTORY:
            positive_history.append(event_tuple)
        elif event_type in NEGATIVE_LABEL_WEIGHTS and len(negative_history) < MAX_USER_HISTORY:
            negative_history.append(event_tuple)

    history_post_ids = [int(event.get("post_id", event.get("target_id", 0)) or 0) for event in sequence_history]
    history_post_ids = [post_id for post_id in history_post_ids if post_id > 0]
    candidate_post_ids = [candidate.post_id for candidate in request.candidates]

    history_post_meta = _fetch_post_meta_rows(history_post_ids)
    history_emb_map = _query_embeddings(history_post_ids)
    generated_user_vector = _candidate_user_vector(
        request.user_id,
        positive_history,
        negative_history,
        history_post_meta,
        history_emb_map,
    )

    candidate_post_meta = {candidate.post_id: _normalize_candidate_meta(candidate) for candidate in request.candidates}
    combined_post_meta = {**history_post_meta, **candidate_post_meta}
    candidate_emb_map = _query_embeddings(candidate_post_ids)
    all_emb_map = {**history_emb_map, **candidate_emb_map}
    now = datetime.now()
    seq_vec = _compute_seq_vec(positive_history, history_post_meta, now, sequence_history)
    realtime_terms = _get_realtime_interest_terms(request.user_id, top_n=16)

    feature_rows: List[np.ndarray] = []
    for index, candidate in enumerate(request.candidates):
        scene_context = {
            "surface": request.scene or "home_feed",
            "page_no": int(request.page_no or 1),
            "rank_position": index + 1,
            "recall_source": "",
            "device_type": request.device_type or "",
            "experiment_id": request.experiment_id or "",
            "realtime_interest_terms": realtime_terms,
        }
        feature_rows.append(
            build_rank_features(
                post_id=candidate.post_id,
                post_meta=candidate_post_meta[candidate.post_id],
                post_meta_map=combined_post_meta,
                positive_history=positive_history,
                negative_history=negative_history,
                emb_map=all_emb_map,
                now=now,
                scene_context=scene_context,
                generated_user_vector=generated_user_vector,
                seq_vec=seq_vec,
            )
        )

    try:
        feature_tensor = torch.tensor(np.vstack(feature_rows), dtype=torch.float32, device=DEVICE)
        with torch.no_grad():
            logits = rank_model(feature_tensor)
            logits_np = logits.detach().cpu().numpy().astype(np.float32)
            scores = _apply_rank_calibration(logits_np, rank_calibration).tolist()
    except Exception as exception:
        raise HTTPException(status_code=500, detail=f"rank inference failed: {exception}") from exception

    return RankResponse(
        scores=[
            ScoreItem(post_id=int(candidate.post_id), score=float(score))
            for candidate, score in zip(request.candidates, scores)
        ]
    )


@app.post("/infer/recall", response_model=RecallResponse)
def infer_recall(request: RecallRequest):
    user_collection_obj = _get_user_collection()
    if user_collection_obj is not None:
        try:
            rows = user_collection_obj.query(
                expr=f"user_id == {int(request.user_id)}",
                output_fields=["embedding"],
            )
        except Exception:
            rows = []
        if rows:
            cached_vector = l2_normalize(np.array(rows[0]["embedding"], dtype=np.float32))
            return RecallResponse(post_ids=_recall_by_vector(cached_vector, request.limit, request.exclude_post_ids))

    positive_history, negative_history, _ = _fetch_recent_user_event_rows(request.user_id, MAX_USER_HISTORY)
    history_post_ids = [post_id for post_id, _, _ in positive_history + negative_history]
    history_post_meta = _fetch_post_meta_rows(history_post_ids)
    history_emb_map = _query_embeddings(history_post_ids)

    user_vector = _candidate_user_vector(
        request.user_id,
        positive_history,
        negative_history,
        history_post_meta,
        history_emb_map,
    )
    return RecallResponse(post_ids=_recall_by_vector(user_vector, request.limit, request.exclude_post_ids))


@app.post("/infer/recall/similar", response_model=RecallResponse)
def infer_similar_recall(request: SimilarRecallRequest):
    post_emb_map = _query_embeddings([request.post_id])
    base_vector = post_emb_map.get(request.post_id)
    if base_vector is None:
        return RecallResponse(post_ids=[])
    excluded = list(dict.fromkeys([request.post_id, *(request.exclude_post_ids or [])]))
    return RecallResponse(post_ids=_recall_by_vector(base_vector, request.limit, excluded))
