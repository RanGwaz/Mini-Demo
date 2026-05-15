#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
图片 Embedding 提取脚本：从 MinIO 下载帖子封面图，用 CLIP 提取特征向量，存入 Milvus。

特性：
  1) 支持断点续跑（本地 checkpoint）
  2) 每批写入后立即 flush，避免中断后进度丢失
  3) 启动时同时读取 Milvus 已有 ID + checkpoint，自动跳过
"""

import io
import json
import logging
import os
import sys
import time
from typing import List, Set, Tuple

import pymysql
import requests
import torch
from PIL import Image
from pymilvus import Collection, CollectionSchema, DataType, FieldSchema, connections, utility
from transformers import CLIPModel, CLIPProcessor

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[logging.StreamHandler(sys.stdout)],
)
log = logging.getLogger(__name__)

# ===== 配置（默认值可被环境变量覆盖） =====
MYSQL_HOST = os.getenv("MYSQL_HOST", "127.0.0.1")
MYSQL_PORT = int(os.getenv("MYSQL_PORT", "3306"))
MYSQL_USER = os.getenv("MYSQL_USER", "root")
MYSQL_PASSWORD = os.getenv("MYSQL_PASSWORD", "root123456")
MYSQL_DATABASE = os.getenv("MYSQL_DATABASE", "image_social")

MILVUS_HOST = os.getenv("MILVUS_HOST", "127.0.0.1")
MILVUS_PORT = int(os.getenv("MILVUS_PORT", "19530"))
COLLECTION_NAME = os.getenv("MILVUS_COLLECTION_NAME", "post_embeddings")
EMBEDDING_DIM = int(os.getenv("EMBEDDING_DIM", "512"))
MINIO_INTERNAL_ENDPOINT = os.getenv("MINIO_INTERNAL_ENDPOINT", "").strip().rstrip("/")
MINIO_PUBLIC_ENDPOINTS = [
    item.strip().rstrip("/")
    for item in os.getenv("MINIO_PUBLIC_ENDPOINTS", "http://localhost:9000,http://127.0.0.1:9000").split(",")
    if item.strip()
]

BATCH_SIZE = int(os.getenv("BATCH_SIZE", "16"))
DOWNLOAD_TIMEOUT = int(os.getenv("DOWNLOAD_TIMEOUT", "15"))
MAX_RETRIES = int(os.getenv("MAX_RETRIES", "2"))
MILVUS_QUERY_WINDOW = int(os.getenv("MILVUS_QUERY_WINDOW", "10000"))  # offset+limit <= 16384

CLIP_MODEL_NAME = os.getenv("CLIP_MODEL_NAME", "openai/clip-vit-base-patch32")
CLIP_MODEL_PATH = os.getenv("CLIP_MODEL_PATH", "").strip()
CHECKPOINT_FILE = os.getenv("CHECKPOINT_FILE", "extract_embeddings_checkpoint.json")
# ========================================


def load_checkpoint() -> Set[int]:
    if not os.path.exists(CHECKPOINT_FILE):
        return set()
    try:
        with open(CHECKPOINT_FILE, "r", encoding="utf-8") as f:
            data = json.load(f)
        ids = set(int(x) for x in data.get("processed_ids", []))
        log.info("读取本地 checkpoint：%d 条", len(ids))
        return ids
    except Exception as e:
        log.warning("读取 checkpoint 失败，忽略：%s", e)
        return set()


def save_checkpoint(ids: Set[int]):
    try:
        with open(CHECKPOINT_FILE, "w", encoding="utf-8") as f:
            json.dump({"processed_ids": sorted(ids)}, f, ensure_ascii=False)
    except Exception as e:
        log.warning("写入 checkpoint 失败：%s", e)


def connect_mysql():
    return pymysql.connect(
        host=MYSQL_HOST,
        port=MYSQL_PORT,
        user=MYSQL_USER,
        password=MYSQL_PASSWORD,
        database=MYSQL_DATABASE,
        charset="utf8mb4",
        autocommit=True,
    )


def init_milvus() -> Collection:
    connections.connect(host=MILVUS_HOST, port=MILVUS_PORT)
    if utility.has_collection(COLLECTION_NAME):
        log.info("Collection '%s' 已存在，直接使用", COLLECTION_NAME)
        col = Collection(COLLECTION_NAME)
        col.load()
        return col

    fields = [
        FieldSchema(name="post_id", dtype=DataType.INT64, is_primary=True),
        FieldSchema(name="embedding", dtype=DataType.FLOAT_VECTOR, dim=EMBEDDING_DIM),
    ]
    schema = CollectionSchema(fields, description="Post image embeddings via CLIP")
    col = Collection(COLLECTION_NAME, schema)
    col.create_index(
        field_name="embedding",
        index_params={"metric_type": "IP", "index_type": "IVF_FLAT", "params": {"nlist": 128}},
    )
    col.load()
    log.info("Collection '%s' 创建完成", COLLECTION_NAME)
    return col


def fetch_posts_from_db() -> List[Tuple[int, str]]:
    conn = connect_mysql()
    with conn.cursor() as cur:
        cur.execute(
            "SELECT id, cover_url FROM posts "
            "WHERE deleted=0 AND cover_url IS NOT NULL AND cover_url!=''"
        )
        rows = cur.fetchall()
    conn.close()
    log.info("共读取到 %d 条帖子", len(rows))
    return rows


def already_indexed(collection: Collection) -> Set[int]:
    try:
        ids = set()  # type: Set[int]
        offset = 0
        while True:
            results = collection.query(
                expr="post_id >= 0",
                output_fields=["post_id"],
                offset=offset,
                limit=MILVUS_QUERY_WINDOW,
            )
            if not results:
                break
            ids.update(int(r["post_id"]) for r in results)
            fetched = len(results)
            offset += fetched
            if fetched < MILVUS_QUERY_WINDOW:
                break
        log.info("Milvus 中已有 %d 条 embedding", len(ids))
        return ids
    except Exception as e:
        log.warning("查询已索引 ID 失败（首次运行可忽略）: %s", e)
        return set()


def download_image(url: str):
    url = normalize_image_url(url)
    for attempt in range(1, MAX_RETRIES + 1):
        try:
            resp = requests.get(url, timeout=DOWNLOAD_TIMEOUT)
            resp.raise_for_status()
            return Image.open(io.BytesIO(resp.content)).convert("RGB")
        except Exception as e:
            log.warning("下载失败(%d/%d): %s -> %s", attempt, MAX_RETRIES, url, e)
            if attempt < MAX_RETRIES:
                time.sleep(2)
    return None


def normalize_image_url(url: str) -> str:
    raw = (url or "").strip()
    if not raw or not MINIO_INTERNAL_ENDPOINT:
        return raw
    for public_endpoint in MINIO_PUBLIC_ENDPOINTS:
        if raw.startswith(public_endpoint + "/"):
            return MINIO_INTERNAL_ENDPOINT + raw[len(public_endpoint):]
    return raw


def load_clip():
    if CLIP_MODEL_PATH:
        log.info("从本地离线路径加载 CLIP: %s", CLIP_MODEL_PATH)
        model = CLIPModel.from_pretrained(CLIP_MODEL_PATH, local_files_only=True)
        processor = CLIPProcessor.from_pretrained(CLIP_MODEL_PATH, local_files_only=True)
        return model, processor

    try:
        log.info("在线加载 CLIP: %s", CLIP_MODEL_NAME)
        model = CLIPModel.from_pretrained(CLIP_MODEL_NAME)
        processor = CLIPProcessor.from_pretrained(CLIP_MODEL_NAME)
        return model, processor
    except Exception as online_err:
        log.warning("在线加载失败，尝试本地缓存: %s", online_err)
        model = CLIPModel.from_pretrained(CLIP_MODEL_NAME, local_files_only=True)
        processor = CLIPProcessor.from_pretrained(CLIP_MODEL_NAME, local_files_only=True)
        return model, processor


def extract_and_insert(posts: List[Tuple[int, str]], collection: Collection, checkpoint_ids: Set[int]):
    log.info("加载 CLIP 模型（首次运行会下载约 600MB）...")
    model, processor = load_clip()

    device = "cuda" if torch.cuda.is_available() else "cpu"
    model.to(device)
    model.eval()
    log.info("CLIP 运行设备: %s", device)

    total_inserted = 0
    for batch_start in range(0, len(posts), BATCH_SIZE):
        batch = posts[batch_start: batch_start + BATCH_SIZE]
        images = []
        valid_ids = []

        for post_id, url in batch:
            img = download_image(url)
            if img is not None:
                images.append(img)
                valid_ids.append(int(post_id))

        if not images:
            continue

        inputs = processor(images=images, return_tensors="pt", padding=True)
        inputs = {k: v.to(device) for k, v in inputs.items()}

        with torch.no_grad():
            feats = model.get_image_features(**inputs)

        feats = feats / feats.norm(dim=-1, keepdim=True)
        emb_list = feats.cpu().numpy().tolist()

        collection.insert([valid_ids, emb_list])
        collection.flush()

        checkpoint_ids.update(valid_ids)
        save_checkpoint(checkpoint_ids)

        total_inserted += len(valid_ids)
        log.info(
            "批次 %d: 插入 %d/%d 条，累计 %d",
            batch_start // BATCH_SIZE + 1,
            len(valid_ids),
            len(batch),
            total_inserted,
        )

    log.info("Embedding 提取完成，共插入 %d 条", total_inserted)


def main():
    log.info("===== 图片 Embedding 提取启动 =====")

    log.info("连接 Milvus...")
    collection = init_milvus()

    log.info("读取帖子列表...")
    all_posts = fetch_posts_from_db()
    if not all_posts:
        log.info("没有需要处理的帖子，退出")
        return

    indexed_ids = already_indexed(collection)
    checkpoint_ids = load_checkpoint()
    done_ids = indexed_ids.union(checkpoint_ids)

    pending = [(pid, url) for pid, url in all_posts if int(pid) not in done_ids]
    log.info("需要处理: %d 条（跳过已处理: %d 条）", len(pending), len(done_ids))

    if pending:
        extract_and_insert(pending, collection, checkpoint_ids)
    else:
        log.info("所有帖子均已索引，无需重复处理")

    log.info("===== 图片 Embedding 提取完成 =====")


if __name__ == "__main__":
    main()
