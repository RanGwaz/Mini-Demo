#!/usr/bin/env python3
# -*- coding: utf-8 -*-

from __future__ import annotations

import os
import shutil
import tempfile
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Tuple

import numpy as np
import pymysql
import torch
from pymilvus import Collection, CollectionSchema, DataType, FieldSchema, connections, utility

from reco_features import (
    EMBEDDING_DIM,
    HISTORY_EVENT_TYPES,
    NEGATIVE_LABEL_WEIGHTS,
    POSITIVE_LABEL_WEIGHTS,
    build_candidate_input,
    effective_user_embedding,
    l2_normalize,
)

MYSQL_HOST = os.getenv("MYSQL_HOST", "127.0.0.1")
MYSQL_PORT = int(os.getenv("MYSQL_PORT", "3306"))
MYSQL_USER = os.getenv("MYSQL_USER", "root")
MYSQL_PASSWORD = os.getenv("MYSQL_PASSWORD", "root123456")
MYSQL_DATABASE = os.getenv("MYSQL_DATABASE", "image_social")

MILVUS_HOST = os.getenv("MILVUS_HOST", "127.0.0.1")
MILVUS_PORT = os.getenv("MILVUS_PORT", "19530")
USER_COLLECTION = os.getenv("MILVUS_USER_COLLECTION", "user_embeddings")
POST_COLLECTION = os.getenv("MILVUS_COLLECTION", "post_embeddings")
ACTIVE_HOURS = int(os.getenv("ACTIVE_USER_HOURS", "6"))
BATCH_SIZE = int(os.getenv("USER_EMBEDDING_BATCH_SIZE", "256"))
USER_EMBEDDING_SCOPE = os.getenv("USER_EMBEDDING_SCOPE", "auto").strip().lower()
MAX_USER_EMBEDDING_USERS = int(os.getenv("MAX_USER_EMBEDDING_USERS", "0"))
POST_EMBED_QUERY_BATCH_SIZE = int(os.getenv("POST_EMBED_QUERY_BATCH_SIZE", "500"))

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
MODEL_DIR = os.path.abspath(os.path.join(SCRIPT_DIR, "..", "models"))
CANDIDATE_MODEL_PATH = os.getenv("CANDIDATE_MODEL_PATH", os.path.join(MODEL_DIR, "candidate_generator.pt"))
DEVICE = torch.device("cuda" if torch.cuda.is_available() else "cpu")
_post_collection: Optional[Collection] = None


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


def load_scripted_model(path: str):
    normalized = os.path.abspath(path)
    try:
        return torch.jit.load(normalized, map_location=DEVICE).eval()
    except Exception:
        temp_path = os.path.join(tempfile.gettempdir(), os.path.basename(normalized))
        shutil.copyfile(normalized, temp_path)
        return torch.jit.load(temp_path, map_location=DEVICE).eval()


def init_user_collection() -> Collection:
    connections.connect(host=MILVUS_HOST, port=MILVUS_PORT)
    if utility.has_collection(USER_COLLECTION):
        collection = Collection(USER_COLLECTION)
        collection.load()
        return collection

    fields = [
        FieldSchema(name="user_id", dtype=DataType.INT64, is_primary=True),
        FieldSchema(name="embedding", dtype=DataType.FLOAT_VECTOR, dim=EMBEDDING_DIM),
    ]
    collection = Collection(USER_COLLECTION, CollectionSchema(fields, description="personalized user embeddings"))
    collection.create_index(
        "embedding",
        {
            "metric_type": "IP",
            "index_type": "IVF_FLAT",
            "params": {"nlist": 256},
        },
    )
    collection.load()
    return collection


def get_post_collection() -> Collection:
    global _post_collection
    if _post_collection is not None:
        return _post_collection
    connections.connect(host=MILVUS_HOST, port=MILVUS_PORT)
    if not utility.has_collection(POST_COLLECTION):
        raise RuntimeError(f"Milvus post embedding collection not found: {POST_COLLECTION}")
    _post_collection = Collection(POST_COLLECTION)
    _post_collection.load()
    return _post_collection


def fetch_active_user_ids(conn, hours: int) -> List[int]:
    cutoff = datetime.now() - timedelta(hours=max(1, hours))
    with conn.cursor() as cursor:
        cursor.execute(
            """
            SELECT DISTINCT user_id
            FROM user_events
            WHERE created_at >= %s
              AND user_id IS NOT NULL
            """,
            (cutoff,),
        )
        return [int(row["user_id"]) for row in cursor.fetchall()]


def fetch_historical_user_ids(conn, limit: int = 0) -> List[int]:
    limit_clause = ""
    params: Tuple[int, ...] = ()
    if limit > 0:
        limit_clause = " LIMIT %s"
        params = (limit,)
    with conn.cursor() as cursor:
        cursor.execute(
            f"""
            SELECT user_id
            FROM user_events
            WHERE user_id IS NOT NULL
              AND target_type='POST'
              AND target_id IS NOT NULL
              AND event_type IN ({",".join(["%s"] * len(HISTORY_EVENT_TYPES))})
            GROUP BY user_id
            ORDER BY MAX(created_at) DESC
            {limit_clause}
            """,
            (*HISTORY_EVENT_TYPES, *params),
        )
        return [int(row["user_id"]) for row in cursor.fetchall()]


def select_embedding_user_ids(conn) -> Tuple[List[int], int, str]:
    scope = USER_EMBEDDING_SCOPE if USER_EMBEDDING_SCOPE in {"active", "all", "auto"} else "auto"
    active_ids = fetch_active_user_ids(conn, ACTIVE_HOURS)
    if scope == "active":
        return active_ids, len(active_ids), "active"
    if scope == "all":
        return fetch_historical_user_ids(conn, MAX_USER_EMBEDDING_USERS), len(active_ids), "all"
    if active_ids:
        return active_ids, len(active_ids), "active"
    return fetch_historical_user_ids(conn, MAX_USER_EMBEDDING_USERS), 0, "all"


def fetch_recent_user_events(conn, user_id: int, limit: int = 50) -> Tuple[List[Tuple[int, str, datetime]], List[Tuple[int, str, datetime]]]:
    placeholders = ",".join(["%s"] * len(HISTORY_EVENT_TYPES))
    with conn.cursor() as cursor:
        cursor.execute(
            f"""
            SELECT target_id, event_type, created_at
            FROM user_events
            WHERE user_id=%s
              AND target_type='POST'
              AND target_id IS NOT NULL
              AND event_type IN ({placeholders})
            ORDER BY created_at DESC
            LIMIT %s
            """,
            (user_id, *HISTORY_EVENT_TYPES, limit * 3),
        )
        rows = cursor.fetchall()

    positive: List[Tuple[int, str, datetime]] = []
    negative: List[Tuple[int, str, datetime]] = []
    for row in rows:
        event_type = str(row["event_type"]).strip().upper()
        event = (int(row["target_id"]), event_type, row["created_at"])
        if event_type in POSITIVE_LABEL_WEIGHTS and len(positive) < limit:
            positive.append(event)
        elif event_type in NEGATIVE_LABEL_WEIGHTS and len(negative) < limit:
            negative.append(event)
        if len(positive) >= limit and len(negative) >= limit:
            break
    return positive, negative


def fetch_post_meta(conn, post_ids: List[int], cache: Optional[Dict[int, dict]] = None) -> Dict[int, dict]:
    unique_ids = sorted({int(post_id) for post_id in post_ids if post_id})
    if not unique_ids:
        return {}
    cache = cache if cache is not None else {}
    missing_ids = [post_id for post_id in unique_ids if post_id not in cache]
    if not missing_ids:
        return {post_id: cache[post_id] for post_id in unique_ids if post_id in cache}
    placeholders = ",".join(["%s"] * len(missing_ids))
    with conn.cursor() as cursor:
        cursor.execute(
            f"""
            SELECT id, author_id, title, content, tags, topic_path,
                   topic_cluster_key, subtopic_cluster_key,
                   semantic_tags, style_tags,
                   hot_score, like_count, favorite_count, comment_count, view_count,
                   quality_score, aesthetic_score, safety_score, created_at
            FROM posts
            WHERE id IN ({placeholders})
            """,
            missing_ids,
        )
        rows = cursor.fetchall()
    for row in rows:
        cache[int(row["id"])] = row
    return {post_id: cache[post_id] for post_id in unique_ids if post_id in cache}


def query_post_embeddings(
    post_ids: List[int],
    collection: Collection,
    cache: Optional[Dict[int, np.ndarray]] = None,
) -> Dict[int, np.ndarray]:
    unique_ids = sorted({int(post_id) for post_id in post_ids if post_id})
    if not unique_ids:
        return {}
    cache = cache if cache is not None else {}
    missing_ids = [post_id for post_id in unique_ids if post_id not in cache]
    for start in range(0, len(missing_ids), POST_EMBED_QUERY_BATCH_SIZE):
        chunk = missing_ids[start:start + POST_EMBED_QUERY_BATCH_SIZE]
        rows = collection.query(expr=f"post_id in {chunk}", output_fields=["post_id", "embedding"])
        for row in rows:
            cache[int(row["post_id"])] = l2_normalize(np.array(row["embedding"], dtype=np.float32))
    return {post_id: cache[post_id] for post_id in unique_ids if post_id in cache}


def compute_user_vector(
    conn,
    user_id: int,
    candidate_model,
    post_collection: Collection,
    post_meta_cache: Dict[int, dict],
    post_embedding_cache: Dict[int, np.ndarray],
) -> np.ndarray:
    positive_history, negative_history = fetch_recent_user_events(conn, user_id)
    history_post_ids = [post_id for post_id, _, _ in positive_history + negative_history]
    post_meta = fetch_post_meta(conn, history_post_ids, post_meta_cache)
    emb_map = query_post_embeddings(history_post_ids, post_collection, post_embedding_cache)
    if candidate_model is None:
        return effective_user_embedding(positive_history, negative_history, emb_map)
    features = build_candidate_input(
        positive_history=positive_history,
        negative_history=negative_history,
        post_meta_map=post_meta,
        emb_map=emb_map,
        now=datetime.now(),
    )
    with torch.no_grad():
        output = candidate_model(torch.tensor(features[None, :], dtype=torch.float32, device=DEVICE))
    return l2_normalize(output.detach().cpu().numpy()[0])


def upsert_user_embeddings(collection: Collection, user_ids: List[int], candidate_model) -> int:
    conn = mysql_conn()
    post_collection = get_post_collection()
    post_meta_cache: Dict[int, dict] = {}
    post_embedding_cache: Dict[int, np.ndarray] = {}
    total = 0
    batch_ids: List[int] = []
    batch_vectors: List[List[float]] = []
    try:
        for user_id in user_ids:
            vector = compute_user_vector(
                conn,
                user_id,
                candidate_model,
                post_collection,
                post_meta_cache,
                post_embedding_cache,
            )
            if not np.all(np.isfinite(vector)):
                continue
            if float(np.linalg.norm(vector)) <= 1e-8:
                continue
            batch_ids.append(int(user_id))
            batch_vectors.append(vector.astype(np.float32).tolist())
            if len(batch_ids) >= BATCH_SIZE:
                collection.upsert([batch_ids, batch_vectors])
                collection.flush()
                total += len(batch_ids)
                batch_ids, batch_vectors = [], []
        if batch_ids:
            collection.upsert([batch_ids, batch_vectors])
            collection.flush()
            total += len(batch_ids)
    finally:
        conn.close()
    return total


def main() -> None:
    candidate_model = load_scripted_model(CANDIDATE_MODEL_PATH)
    collection = init_user_collection()
    conn = mysql_conn()
    try:
        user_ids, active_count, selected_scope = select_embedding_user_ids(conn)
    finally:
        conn.close()
    updated = upsert_user_embeddings(collection, user_ids, candidate_model)
    print(
        f"[user-emb] scope={selected_scope} active_users={active_count} "
        f"selected_users={len(user_ids)} updated={updated} collection={USER_COLLECTION}"
    )


if __name__ == "__main__":
    main()
