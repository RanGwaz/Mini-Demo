#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Build semantic taxonomy for posts.

The job combines image embeddings from Milvus with lightweight text features to
produce:
1. hierarchical topic clusters
2. multi-label semantic tags
3. style tags
4. quality / aesthetic / safety scores

Results are written back to:
- posts.topic_path / semantic_tags / style_tags / taxonomy_json
- posts.topic_cluster_key / subtopic_cluster_key
- posts.quality_score / aesthetic_score / safety_score
- topic_clusters table
"""

from __future__ import annotations

import argparse
import hashlib
import json
import logging
import math
import os
import re
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass
from datetime import datetime
from typing import Dict, Iterable, List, Sequence, Tuple

import numpy as np
import pymysql

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[logging.StreamHandler(sys.stdout)],
)
log = logging.getLogger(__name__)

MYSQL_HOST = os.getenv("MYSQL_HOST", "127.0.0.1")
MYSQL_PORT = int(os.getenv("MYSQL_PORT", "3306"))
MYSQL_USER = os.getenv("MYSQL_USER", "root")
MYSQL_PASSWORD = os.getenv("MYSQL_PASSWORD", "root123456")
MYSQL_DATABASE = os.getenv("MYSQL_DATABASE", "image_social")

MILVUS_HOST = os.getenv("MILVUS_HOST", "127.0.0.1")
MILVUS_PORT = os.getenv("MILVUS_PORT", "19530")
MILVUS_COLLECTION = os.getenv("MILVUS_COLLECTION", "post_embeddings")

HASHED_TEXT_DIM = int(os.getenv("HASHED_TEXT_DIM", "128"))
TOP_CLUSTER_MIN = int(os.getenv("TOP_CLUSTER_MIN", "10"))
TOP_CLUSTER_MAX = int(os.getenv("TOP_CLUSTER_MAX", "28"))
SUB_CLUSTER_MAX = int(os.getenv("SUB_CLUSTER_MAX", "8"))
MIN_SUB_CLUSTER_SIZE = int(os.getenv("MIN_SUB_CLUSTER_SIZE", "45"))
KMEANS_ITERS = int(os.getenv("KMEANS_ITERS", "18"))
DEFAULT_SEED = int(os.getenv("SEMANTIC_SEED", "20260411"))
DEFAULT_EMBEDDING_VERSION = os.getenv("EMBEDDING_VERSION", "clip-vit-base-patch32")

TOKEN_RE = re.compile(r"[a-z0-9]+(?:'[a-z0-9]+)?")
STOPWORDS = {
    "and", "for", "with", "the", "this", "that", "from", "into", "over", "under", "after", "before",
    "around", "about", "photo", "image", "picture", "unsplash", "high",
    "quality", "beautiful", "nice", "view", "views", "vertical", "horizontal", "color", "colour",
    "hd", "4k", "of", "in", "on", "to",
}
PRIMARY_CATEGORY_RULES = {
    "avatar": {"avatar", "profile", "headshot", "face", "selfie", "portrait", "icon", "pfp"},
    "wallpaper": {"wallpaper", "background", "lockscreen", "homescreen", "desktop", "iphone", "android", "ipad"},
    "outfit": {"fashion", "outfit", "streetwear", "style", "lookbook", "wardrobe", "ootd", "clothing"},
    "anime": {"anime", "manga", "cartoon", "chibi", "illustration", "kawaii"},
    "nature": {"nature", "forest", "mountain", "ocean", "beach", "flower", "sky", "sunset", "lake"},
    "food": {"food", "coffee", "drink", "dessert", "meal", "restaurant", "kitchen"},
    "interior": {"interior", "room", "decor", "apartment", "living", "bedroom", "workspace"},
    "architecture": {"architecture", "building", "city", "street", "urban", "house", "bridge"},
    "animal": {"animal", "dog", "cat", "pet", "bird", "wildlife", "horse"},
    "vehicle": {"car", "vehicle", "bike", "motorcycle", "truck", "racing"},
    "people": {"people", "man", "woman", "girl", "boy", "couple", "model"},
    "abstract": {"abstract", "pattern", "gradient", "texture", "geometry", "minimal"},
}
STYLE_HINT_RULES = {
    "minimal": {"minimal", "clean", "simple"},
    "dark": {"dark", "night", "moody", "black"},
    "bright": {"bright", "sunny", "light", "glow"},
    "vintage": {"vintage", "retro", "film"},
    "cute": {"cute", "soft", "sweet", "pastel"},
    "real-person": {"portrait", "selfie", "face", "model", "people", "woman", "man"},
    "illustration": {"illustration", "draw", "drawing", "anime", "cartoon", "graphic"},
    "aesthetic": {"aesthetic", "vibe", "mood", "dreamy"},
}
UNSAFE_HINTS = {"nsfw", "nude", "naked", "erotic", "adult", "sex", "lingerie"}


@dataclass
class PostRow:
    post_id: int
    author_id: int
    title: str
    content: str
    tags: str
    hot_score: float
    like_count: int
    favorite_count: int
    comment_count: int
    view_count: int
    width: int
    height: int
    created_at: object


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build post topic clusters and semantic tags")
    parser.add_argument("--limit", type=int, default=0, help="Only process the latest N posts, 0 means all")
    parser.add_argument("--seed", type=int, default=DEFAULT_SEED)
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--taxonomy-version", default=f"taxonomy-{datetime.now().strftime('%Y%m%d%H%M%S')}")
    parser.add_argument("--embedding-version", default=DEFAULT_EMBEDDING_VERSION)
    return parser.parse_args()


def mysql_conn():
    return pymysql.connect(
        host=MYSQL_HOST,
        port=MYSQL_PORT,
        user=MYSQL_USER,
        password=MYSQL_PASSWORD,
        database=MYSQL_DATABASE,
        charset="utf8mb4",
        autocommit=False,
        cursorclass=pymysql.cursors.DictCursor,
    )


def load_posts(conn, limit: int) -> List[PostRow]:
    limit_sql = "LIMIT %s" if limit and limit > 0 else ""
    params: Tuple[object, ...] = (limit,) if limit and limit > 0 else tuple()
    sql = f"""
        SELECT p.id,
               p.author_id,
               COALESCE(p.title, '') AS title,
               COALESCE(p.content, '') AS content,
               COALESCE(p.tags, '') AS tags,
               COALESCE(p.hot_score, 0) AS hot_score,
               COALESCE(p.like_count, 0) AS like_count,
               COALESCE(p.favorite_count, 0) AS favorite_count,
               COALESCE(p.comment_count, 0) AS comment_count,
               COALESCE(p.view_count, 0) AS view_count,
               COALESCE(asset.width, 0) AS width,
               COALESCE(asset.height, 0) AS height,
               p.created_at
        FROM posts p
        LEFT JOIN (
            SELECT post_id, MAX(width) AS width, MAX(height) AS height
            FROM post_assets
            WHERE deleted=0
            GROUP BY post_id
        ) asset ON asset.post_id = p.id
        WHERE p.deleted=0
        ORDER BY p.created_at DESC
        {limit_sql}
    """
    with conn.cursor() as cur:
        cur.execute(sql, params)
        rows = cur.fetchall()
    return [
        PostRow(
            post_id=int(row["id"]),
            author_id=int(row["author_id"]),
            title=row["title"],
            content=row["content"],
            tags=row["tags"],
            hot_score=float(row["hot_score"] or 0.0),
            like_count=int(row["like_count"] or 0),
            favorite_count=int(row["favorite_count"] or 0),
            comment_count=int(row["comment_count"] or 0),
            view_count=int(row["view_count"] or 0),
            width=int(row["width"] or 0),
            height=int(row["height"] or 0),
            created_at=row["created_at"],
        )
        for row in rows
    ]


def load_embeddings(post_ids: Sequence[int]) -> Dict[int, np.ndarray]:
    unique_ids = sorted({int(post_id) for post_id in post_ids if post_id})
    if not unique_ids:
        return {}
    try:
        from pymilvus import Collection, connections

        connections.connect(host=MILVUS_HOST, port=MILVUS_PORT)
        collection = Collection(MILVUS_COLLECTION)
        collection.load()
    except Exception as exc:
        log.warning("Milvus unavailable, clustering will fall back to text features only: %s", exc)
        return {}

    emb_map: Dict[int, np.ndarray] = {}
    chunk_size = 200
    for start in range(0, len(unique_ids), chunk_size):
        chunk = unique_ids[start:start + chunk_size]
        try:
            rows = collection.query(
                expr=f"post_id in {chunk}",
                output_fields=["post_id", "embedding"],
            )
        except Exception as exc:
            log.warning("Milvus query failed for chunk starting at %s: %s", start, exc)
            continue
        for row in rows:
            emb_map[int(row["post_id"])] = l2_normalize(np.asarray(row["embedding"], dtype=np.float32))
    return emb_map


def split_csv(raw: str) -> List[str]:
    if not raw:
        return []
    return [part.strip().lower() for part in raw.split(",") if part and part.strip()]


def tokenize(*parts: str) -> List[str]:
    tokens: List[str] = []
    for part in parts:
        if not part:
            continue
        tokens.extend(TOKEN_RE.findall(part.lower()))
    return [token for token in tokens if len(token) >= 2 and token not in STOPWORDS]


def hashed_text_vector(tokens: Sequence[str], dim: int = HASHED_TEXT_DIM) -> np.ndarray:
    vector = np.zeros(dim, dtype=np.float32)
    for token in tokens:
        digest = hashlib.md5(token.encode("utf-8")).hexdigest()
        index = int(digest, 16) % dim
        vector[index] += 1.0
    total = float(vector.sum())
    if total > 0:
        vector /= total
    return vector


def l2_normalize(vector: np.ndarray) -> np.ndarray:
    norm = float(np.linalg.norm(vector))
    if norm <= 1e-8:
        return np.zeros_like(vector, dtype=np.float32)
    return (vector / norm).astype(np.float32)


def choose_cluster_count(size: int, minimum: int, maximum: int, divisor: int) -> int:
    if size <= 0:
        return minimum
    value = int(round(math.sqrt(size / max(divisor, 1)) * 5))
    return max(minimum, min(maximum, value))


def kmeans_lite(vectors: np.ndarray, cluster_count: int, seed: int) -> Tuple[np.ndarray, np.ndarray]:
    if len(vectors) == 0:
        return np.array([], dtype=np.int32), np.zeros((0, 0), dtype=np.float32)
    if len(vectors) <= cluster_count:
        assignments = np.arange(len(vectors), dtype=np.int32)
        return assignments, vectors.astype(np.float32)

    rng = np.random.default_rng(seed)
    chosen = rng.choice(len(vectors), size=cluster_count, replace=False)
    centroids = vectors[chosen].astype(np.float32)
    assignments = np.zeros(len(vectors), dtype=np.int32)

    for _ in range(KMEANS_ITERS):
        scores = vectors @ centroids.T
        next_assignments = np.argmax(scores, axis=1).astype(np.int32)
        if np.array_equal(assignments, next_assignments):
            break
        assignments = next_assignments
        next_centroids = []
        for cluster_id in range(cluster_count):
            members = vectors[assignments == cluster_id]
            if len(members) == 0:
                next_centroids.append(vectors[rng.integers(0, len(vectors))])
                continue
            next_centroids.append(l2_normalize(members.mean(axis=0)))
        centroids = np.vstack(next_centroids).astype(np.float32)

    return assignments, centroids


def primary_category(tokens: Sequence[str]) -> str:
    token_set = set(tokens)
    best_category = "image"
    best_score = 0
    for category, hints in PRIMARY_CATEGORY_RULES.items():
        score = len(token_set & hints)
        if score > best_score:
            best_score = score
            best_category = category
    return best_category


def orientation(row: PostRow) -> str:
    if row.width <= 0 or row.height <= 0:
        return "unknown"
    ratio = row.width / max(row.height, 1)
    if ratio >= 1.25:
        return "landscape"
    if ratio <= 0.8:
        return "portrait"
    return "square"


def style_tags(tokens: Sequence[str], row: PostRow) -> List[str]:
    token_set = set(tokens)
    styles = set()
    for label, hints in STYLE_HINT_RULES.items():
        if token_set & hints:
            styles.add(label)

    image_orientation = orientation(row)
    if image_orientation != "unknown":
        styles.add(image_orientation)
    if image_orientation == "portrait" and {"wallpaper", "background", "iphone", "android"} & token_set:
        styles.add("mobile-wallpaper")
    if image_orientation == "landscape" and {"wallpaper", "background", "desktop"} & token_set:
        styles.add("desktop-wallpaper")
    if {"anime", "manga", "cartoon"} & token_set:
        styles.add("illustration")
    return sorted(styles)


def score_quality(row: PostRow, token_count: int) -> float:
    engagement = math.log1p(
        row.like_count * 2.0
        + row.favorite_count * 3.0
        + row.comment_count * 2.5
        + row.view_count / 20.0
        + row.hot_score * 4.0
    ) / 8.0
    resolution = math.log1p(max(row.width, 1) * max(row.height, 1)) / 18.0
    metadata = min((token_count + len(split_csv(row.tags))) / 20.0, 1.0)
    return round(min(1.5, engagement * 0.48 + resolution * 0.34 + metadata * 0.18), 4)


def score_safety(tokens: Sequence[str]) -> float:
    token_set = set(tokens)
    if token_set & UNSAFE_HINTS:
        return 0.2
    return 1.0


def cluster_keywords(token_lists: Sequence[Sequence[str]], topn: int = 6) -> List[str]:
    counter: Counter[str] = Counter()
    for tokens in token_lists:
        counter.update(token for token in tokens if token not in STOPWORDS)
    return [token for token, _ in counter.most_common(topn)]


def readable_label(keywords: Sequence[str], fallback: str) -> str:
    cleaned = [word.replace("_", "-") for word in keywords if word]
    if not cleaned:
        return fallback
    return "-".join(cleaned[:2])


def build_combined_matrix(rows: Sequence[PostRow], emb_map: Dict[int, np.ndarray]) -> Tuple[np.ndarray, Dict[int, List[str]]]:
    token_map: Dict[int, List[str]] = {}
    vectors: List[np.ndarray] = []
    embedding_dim = len(next(iter(emb_map.values()))) if emb_map else 0
    for row in rows:
        tokens = tokenize(row.title, row.content, row.tags.replace(",", " "))
        token_map[row.post_id] = tokens
        text_vector = hashed_text_vector(tokens)
        image_vector = emb_map.get(row.post_id)
        if image_vector is None:
            if embedding_dim > 0:
                image_vector = np.zeros(embedding_dim, dtype=np.float32)
                combined = np.concatenate([image_vector, text_vector], axis=0)
            else:
                combined = text_vector
        else:
            combined = np.concatenate([image_vector * 0.82, text_vector * 0.38], axis=0)
        vectors.append(l2_normalize(combined.astype(np.float32)))
    return np.vstack(vectors).astype(np.float32), token_map


def describe_clusters(rows: Sequence[PostRow],
                      assignments: np.ndarray,
                      centroids: np.ndarray,
                      token_map: Dict[int, List[str]],
                      level_prefix: str,
                      taxonomy_version: str) -> Dict[int, dict]:
    cluster_rows: Dict[int, List[PostRow]] = defaultdict(list)
    for row, cluster_id in zip(rows, assignments.tolist()):
        cluster_rows[int(cluster_id)].append(row)

    metadata: Dict[int, dict] = {}
    for cluster_id, members in cluster_rows.items():
        member_tokens = [token_map[row.post_id] for row in members]
        keywords = cluster_keywords(member_tokens)
        category = primary_category([token for tokens in member_tokens for token in tokens])
        label = readable_label(keywords, fallback=f"{category}-{cluster_id}")
        cluster_key = f"{level_prefix}-{cluster_id}"
        sample_post_ids = [row.post_id for row in members[:12]]
        metadata[cluster_id] = {
            "cluster_key": cluster_key,
            "category": category,
            "keywords": keywords,
            "label": label,
            "post_count": len(members),
            "sample_post_ids": sample_post_ids,
            "centroid": centroids[cluster_id] if len(centroids) > cluster_id else None,
            "taxonomy_version": taxonomy_version,
        }
    return metadata


def cosine(left: np.ndarray, right: np.ndarray) -> float:
    if left is None or right is None or len(left) == 0 or len(right) == 0:
        return 0.0
    return float(np.dot(left, right))


def build_semantics(rows: Sequence[PostRow],
                    vectors: np.ndarray,
                    token_map: Dict[int, List[str]],
                    taxonomy_version: str,
                    embedding_version: str,
                    seed: int) -> Tuple[List[dict], List[dict]]:
    if not rows:
        return [], []

    top_k = choose_cluster_count(len(rows), TOP_CLUSTER_MIN, TOP_CLUSTER_MAX, 320)
    top_assignments, top_centroids = kmeans_lite(vectors, top_k, seed)
    top_meta = describe_clusters(rows, top_assignments, top_centroids, token_map, "lvl1", taxonomy_version)

    cluster_rows: Dict[int, List[PostRow]] = defaultdict(list)
    cluster_vectors: Dict[int, List[np.ndarray]] = defaultdict(list)
    for row, assignment, vector in zip(rows, top_assignments.tolist(), vectors):
        cluster_rows[int(assignment)].append(row)
        cluster_vectors[int(assignment)].append(vector)

    post_updates: List[dict] = []
    cluster_upserts: List[dict] = []

    for cluster_id, meta in top_meta.items():
        cluster_upserts.append({
            "cluster_key": meta["cluster_key"],
            "parent_cluster_key": None,
            "cluster_level": 1,
            "cluster_label": meta["label"],
            "keywords_json": json.dumps(meta["keywords"], ensure_ascii=False),
            "sample_post_ids_json": json.dumps(meta["sample_post_ids"], ensure_ascii=False),
            "post_count": meta["post_count"],
            "taxonomy_version": meta["taxonomy_version"],
        })

    for cluster_id, members in cluster_rows.items():
        member_vectors = np.vstack(cluster_vectors[cluster_id]).astype(np.float32)
        if len(members) < MIN_SUB_CLUSTER_SIZE:
            sub_assignments = np.zeros(len(members), dtype=np.int32)
            sub_centroids = np.asarray([l2_normalize(member_vectors.mean(axis=0))], dtype=np.float32)
        else:
            sub_k = choose_cluster_count(len(members), 2, SUB_CLUSTER_MAX, 95)
            sub_assignments, sub_centroids = kmeans_lite(member_vectors, sub_k, seed + cluster_id + 17)

        sub_meta = describe_clusters(
            members,
            sub_assignments,
            sub_centroids,
            token_map,
            f"{top_meta[cluster_id]['cluster_key']}-s",
            taxonomy_version,
        )

        for sub_cluster_id, meta in sub_meta.items():
            cluster_upserts.append({
                "cluster_key": meta["cluster_key"],
                "parent_cluster_key": top_meta[cluster_id]["cluster_key"],
                "cluster_level": 2,
                "cluster_label": meta["label"],
                "keywords_json": json.dumps(meta["keywords"], ensure_ascii=False),
                "sample_post_ids_json": json.dumps(meta["sample_post_ids"], ensure_ascii=False),
                "post_count": meta["post_count"],
                "taxonomy_version": meta["taxonomy_version"],
            })

        for member_index, row in enumerate(members):
            sub_cluster_id = int(sub_assignments[member_index])
            top_cluster = top_meta[cluster_id]
            sub_cluster = sub_meta[sub_cluster_id]
            tokens = token_map[row.post_id]
            styles = style_tags(tokens, row)
            primary = top_cluster["category"]
            cluster_keywords_combined = list(dict.fromkeys(
                top_cluster["keywords"][:4]
                + sub_cluster["keywords"][:4]
                + split_csv(row.tags)[:4]
            ))
            semantic_labels = list(dict.fromkeys([primary] + cluster_keywords_combined))[:12]
            topic_segments = [
                primary,
                top_cluster["label"],
                sub_cluster["label"],
            ]
            topic_path = "/".join(segment for segment in topic_segments if segment)

            quality_score = score_quality(row, len(tokens))
            safety_score = score_safety(tokens)
            centroid = sub_cluster.get("centroid")
            current_vector = member_vectors[member_index]
            centrality = (cosine(current_vector, centroid) + 1.0) / 2.0 if centroid is not None else 0.0
            aesthetic_score = round(min(1.5, quality_score * 0.40 + centrality * 0.45 + math.log1p(row.hot_score + 1.0) / 6.0), 4)

            taxonomy = {
                "primary_category": primary,
                "topic_keywords": semantic_labels[:8],
                "style_tags": styles,
                "orientation": orientation(row),
                "topic_cluster_key": top_cluster["cluster_key"],
                "subtopic_cluster_key": sub_cluster["cluster_key"],
                "source_tags": split_csv(row.tags),
            }
            post_updates.append({
                "post_id": row.post_id,
                "topic_path": topic_path,
                "semantic_tags": ",".join(semantic_labels),
                "style_tags": ",".join(styles),
                "taxonomy_json": json.dumps(taxonomy, ensure_ascii=False),
                "topic_cluster_key": top_cluster["cluster_key"],
                "subtopic_cluster_key": sub_cluster["cluster_key"],
                "quality_score": quality_score,
                "aesthetic_score": aesthetic_score,
                "safety_score": safety_score,
                "embedding_version": embedding_version,
                "taxonomy_version": taxonomy_version,
            })

    return post_updates, cluster_upserts


def upsert_topic_clusters(conn, rows: Sequence[dict]) -> None:
    if not rows:
        return
    sql = """
        INSERT INTO topic_clusters (
            cluster_key,
            parent_cluster_key,
            cluster_level,
            cluster_label,
            keywords_json,
            sample_post_ids_json,
            post_count,
            taxonomy_version
        )
        VALUES (
            %(cluster_key)s,
            %(parent_cluster_key)s,
            %(cluster_level)s,
            %(cluster_label)s,
            %(keywords_json)s,
            %(sample_post_ids_json)s,
            %(post_count)s,
            %(taxonomy_version)s
        )
        ON DUPLICATE KEY UPDATE
            parent_cluster_key = VALUES(parent_cluster_key),
            cluster_level = VALUES(cluster_level),
            cluster_label = VALUES(cluster_label),
            keywords_json = VALUES(keywords_json),
            sample_post_ids_json = VALUES(sample_post_ids_json),
            post_count = VALUES(post_count),
            taxonomy_version = VALUES(taxonomy_version)
    """
    with conn.cursor() as cur:
        cur.executemany(sql, list(rows))


def update_posts(conn, rows: Sequence[dict]) -> None:
    if not rows:
        return
    sql = """
        UPDATE posts
        SET topic_path = %(topic_path)s,
            semantic_tags = %(semantic_tags)s,
            style_tags = %(style_tags)s,
            taxonomy_json = %(taxonomy_json)s,
            topic_cluster_key = %(topic_cluster_key)s,
            subtopic_cluster_key = %(subtopic_cluster_key)s,
            quality_score = %(quality_score)s,
            aesthetic_score = %(aesthetic_score)s,
            safety_score = %(safety_score)s,
            embedding_version = %(embedding_version)s,
            taxonomy_version = %(taxonomy_version)s
        WHERE id = %(post_id)s
    """
    with conn.cursor() as cur:
        cur.executemany(sql, list(rows))


def main() -> None:
    args = parse_args()
    log.info("loading posts...")
    conn = mysql_conn()
    try:
        rows = load_posts(conn, args.limit)
        if not rows:
            log.warning("no posts found, stop")
            return
        emb_map = load_embeddings([row.post_id for row in rows])
        vectors, token_map = build_combined_matrix(rows, emb_map)
        log.info("posts=%s embeddings=%s matrix_shape=%s", len(rows), len(emb_map), tuple(vectors.shape))
        post_updates, cluster_upserts = build_semantics(
            rows=rows,
            vectors=vectors,
            token_map=token_map,
            taxonomy_version=args.taxonomy_version,
            embedding_version=args.embedding_version,
            seed=args.seed,
        )

        if args.dry_run:
            log.info("dry-run mode: previewing first 5 rows")
            for row in post_updates[:5]:
                log.info(json.dumps(row, ensure_ascii=False))
            return

        upsert_topic_clusters(conn, cluster_upserts)
        update_posts(conn, post_updates)
        conn.commit()
        log.info("semantic taxonomy updated: posts=%s clusters=%s", len(post_updates), len(cluster_upserts))
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


if __name__ == "__main__":
    main()
