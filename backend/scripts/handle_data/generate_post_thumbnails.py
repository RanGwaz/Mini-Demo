#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
历史图片缩略图补齐脚本（posts + post_assets）。
"""

import io
import logging
import os
import sys
from dataclasses import dataclass
from typing import Iterable, Optional
from urllib.parse import unquote, urlparse

import pymysql
import requests
from minio import Minio
from minio.error import S3Error
from PIL import Image, ImageOps

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

MINIO_ENDPOINT = os.getenv("MINIO_ENDPOINT", "localhost:9000")
MINIO_ACCESS_KEY = os.getenv("MINIO_ACCESS_KEY", "minioadmin")
MINIO_SECRET_KEY = os.getenv("MINIO_SECRET_KEY", "minioadmin")
MINIO_BUCKET = os.getenv("MINIO_BUCKET", "image-social-dev")
MINIO_PUBLIC_ENDPOINT = os.getenv("MINIO_PUBLIC_ENDPOINT", "http://localhost:9000")
MINIO_SECURE = os.getenv("MINIO_SECURE", "0") == "1"

THUMB_MAX_EDGE = int(os.getenv("THUMB_MAX_EDGE", "720"))
THUMB_QUALITY = int(os.getenv("THUMB_QUALITY", "82"))
BATCH_SIZE = int(os.getenv("BATCH_SIZE", "200"))
PROGRESS_EVERY = int(os.getenv("PROGRESS_EVERY", "1"))
COMMIT_EVERY = int(os.getenv("COMMIT_EVERY", "20"))
DOWNLOAD_TIMEOUT = int(os.getenv("DOWNLOAD_TIMEOUT", "20"))
TARGET = os.getenv("TARGET", "all").lower()
DRY_RUN = os.getenv("DRY_RUN", "0") == "1"


@dataclass
class AssetRow:
    asset_id: int
    post_id: int
    object_key: Optional[str]
    file_url: Optional[str]
    file_type: Optional[str]
    sort_order: int
    thumb_url: Optional[str]
    width: Optional[int]
    height: Optional[int]


def connect_db():
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


def connect_minio() -> Minio:
    return Minio(
        MINIO_ENDPOINT,
        access_key=MINIO_ACCESS_KEY,
        secret_key=MINIO_SECRET_KEY,
        secure=MINIO_SECURE,
    )


def fetch_assets(conn) -> list[AssetRow]:
    sql = """
        SELECT id, post_id, object_key, file_url, file_type, sort_order, thumb_url, width, height
        FROM post_assets
        WHERE (thumb_url IS NULL OR thumb_url = '' OR width IS NULL OR height IS NULL)
        ORDER BY post_id ASC, sort_order ASC, id ASC
    """
    with conn.cursor() as cur:
        cur.execute(sql)
        rows = cur.fetchall()
    return [AssetRow(
        asset_id=int(row["id"]),
        post_id=int(row["post_id"]),
        object_key=row.get("object_key"),
        file_url=row.get("file_url"),
        file_type=row.get("file_type"),
        sort_order=int(row.get("sort_order") or 0),
        thumb_url=row.get("thumb_url"),
        width=row.get("width"),
        height=row.get("height"),
    ) for row in rows]


def fetch_posts_missing_thumb(conn):
    sql = """
        SELECT p.id, p.cover_url
        FROM posts p
        WHERE p.deleted = 0
          AND (p.thumb_url IS NULL OR p.thumb_url = '')
          AND p.cover_url IS NOT NULL AND p.cover_url != ''
        ORDER BY p.id ASC
    """
    with conn.cursor() as cur:
        cur.execute(sql)
        return cur.fetchall()


def normalize_object_key(value: Optional[str]) -> Optional[str]:
    if not value:
        return None
    value = value.strip()
    if not value:
        return None
    if value.startswith(("http://", "https://")):
        parsed = urlparse(value)
        path = unquote(parsed.path.lstrip("/"))
        bucket_prefix = f"{MINIO_BUCKET}/"
        if path.startswith(bucket_prefix):
            return path[len(bucket_prefix):]
        return path
    return value.lstrip("/")


def fetch_bytes_from_minio(client: Minio, object_key: str) -> Optional[bytes]:
    try:
        response = client.get_object(MINIO_BUCKET, object_key)
        try:
            return response.read()
        finally:
            response.close()
            response.release_conn()
    except S3Error:
        return None


def download_image(url: str) -> Optional[bytes]:
    try:
        resp = requests.get(url, timeout=DOWNLOAD_TIMEOUT)
        resp.raise_for_status()
        return resp.content
    except Exception as exc:
        log.warning("下载失败 %s -> %s", url, exc)
        return None


def load_original_bytes(minio_client: Minio, object_key: Optional[str], file_url: Optional[str]) -> Optional[bytes]:
    normalized_key = normalize_object_key(object_key) or normalize_object_key(file_url)
    if normalized_key:
        raw = fetch_bytes_from_minio(minio_client, normalized_key)
        if raw:
            return raw
    if file_url and file_url.startswith(("http://", "https://")):
        return download_image(file_url)
    return None


def build_thumbnail(raw_bytes: bytes) -> tuple[bytes, int, int]:
    with Image.open(io.BytesIO(raw_bytes)) as img:
        img = ImageOps.exif_transpose(img).convert("RGB")
        width, height = img.size
        long_edge = max(width, height)
        if long_edge > THUMB_MAX_EDGE:
            scale = THUMB_MAX_EDGE / float(long_edge)
            img = img.resize((max(1, int(width * scale)), max(1, int(height * scale))), Image.Resampling.LANCZOS)
        out = io.BytesIO()
        img.save(out, format="JPEG", quality=THUMB_QUALITY, optimize=True)
        return out.getvalue(), width, height


def build_thumb_object_key(asset: AssetRow) -> str:
    source_key = normalize_object_key(asset.object_key) or f"images/post-{asset.post_id}-asset-{asset.asset_id}.jpg"
    source_key = source_key.replace("\\", "/")
    base = source_key.rsplit(".", 1)[0]
    return f"thumbs/{base}.jpg"


def public_url_for(key: str) -> str:
    return f"{MINIO_PUBLIC_ENDPOINT.rstrip('/')}/{MINIO_BUCKET}/{key}"


def put_thumb(minio_client: Minio, object_key: str, thumb_bytes: bytes):
    minio_client.put_object(
        MINIO_BUCKET,
        object_key,
        io.BytesIO(thumb_bytes),
        len(thumb_bytes),
        content_type="image/jpeg",
    )


def update_asset(conn, asset_id: int, thumb_url: str, width: int, height: int):
    with conn.cursor() as cur:
        cur.execute(
            "UPDATE post_assets SET thumb_url=%s, width=%s, height=%s WHERE id=%s",
            (thumb_url, width, height, asset_id),
        )


def sync_post_thumb(conn, post_id: int, thumb_url: str):
    with conn.cursor() as cur:
        cur.execute(
            "UPDATE posts SET thumb_url=%s WHERE id=%s AND (thumb_url IS NULL OR thumb_url = '')",
            (thumb_url, post_id),
        )


def fetch_first_asset_thumb(conn, post_id: int) -> Optional[str]:
    with conn.cursor() as cur:
        cur.execute(
            "SELECT thumb_url FROM post_assets WHERE post_id=%s AND thumb_url IS NOT NULL AND thumb_url != '' ORDER BY sort_order ASC, id ASC LIMIT 1",
            (post_id,),
        )
        row = cur.fetchone()
    return row["thumb_url"] if row else None


def process_assets(conn, minio_client: Minio, rows: Iterable[AssetRow]):
    success = 0
    failed = 0
    touched_posts = set()
    for index, asset in enumerate(rows, start=1):
        raw = load_original_bytes(minio_client, asset.object_key, asset.file_url)
        if not raw:
            failed += 1
            log.warning("读取原图失败 asset_id=%s post_id=%s", asset.asset_id, asset.post_id)
            continue
        try:
            thumb_bytes, width, height = build_thumbnail(raw)
            thumb_key = build_thumb_object_key(asset)
            thumb_url = public_url_for(thumb_key)
            if not DRY_RUN:
                put_thumb(minio_client, thumb_key, thumb_bytes)
                update_asset(conn, asset.asset_id, thumb_url, width, height)
                if index % COMMIT_EVERY == 0:
                    conn.commit()
            touched_posts.add(asset.post_id)
            success += 1
            if index % PROGRESS_EVERY == 0:
                log.info("asset 已处理 id=%s post_id=%s thumb=%s 成功=%s 失败=%s", asset.asset_id, asset.post_id, thumb_url, success, failed)
        except Exception as exc:
            failed += 1
            log.warning("处理失败 asset_id=%s: %s", asset.asset_id, exc)
        if index % BATCH_SIZE == 0 and not DRY_RUN:
            log.info("asset 批次进度 %s，成功=%s，失败=%s", index, success, failed)
    if not DRY_RUN:
        for post_id in touched_posts:
            thumb_url = fetch_first_asset_thumb(conn, post_id)
            if thumb_url:
                sync_post_thumb(conn, post_id, thumb_url)
        conn.commit()
    return success, failed


def process_posts(conn, minio_client: Minio, rows):
    success = 0
    failed = 0
    for index, row in enumerate(rows, start=1):
        post_id = int(row["id"])
        cover_url = row["cover_url"]
        raw = load_original_bytes(minio_client, None, cover_url)
        if not raw:
            failed += 1
            continue
        try:
            thumb_bytes, _, _ = build_thumbnail(raw)
            thumb_key = f"thumbs/posts/{post_id}.jpg"
            thumb_url = public_url_for(thumb_key)
            if not DRY_RUN:
                put_thumb(minio_client, thumb_key, thumb_bytes)
                sync_post_thumb(conn, post_id, thumb_url)
                if index % COMMIT_EVERY == 0:
                    conn.commit()
            success += 1
            if index % PROGRESS_EVERY == 0:
                log.info("post 已处理 post_id=%s thumb=%s 成功=%s 失败=%s", post_id, thumb_url, success, failed)
        except Exception as exc:
            failed += 1
            log.warning("post 处理失败 post_id=%s: %s", post_id, exc)
        if index % BATCH_SIZE == 0 and not DRY_RUN:
            log.info("post 批次进度 %s，成功=%s，失败=%s", index, success, failed)
    if not DRY_RUN:
        conn.commit()
    return success, failed


def main():
    log.info("===== 历史缩略图补齐启动 =====")
    log.info("TARGET=%s DRY_RUN=%s THUMB_MAX_EDGE=%s PROGRESS_EVERY=%s COMMIT_EVERY=%s", TARGET, DRY_RUN, THUMB_MAX_EDGE, PROGRESS_EVERY, COMMIT_EVERY)
    conn = connect_db()
    minio_client = connect_minio()
    try:
        if TARGET in ("all", "assets"):
            assets = fetch_assets(conn)
            log.info("待处理 post_assets: %s", len(assets))
            a_success, a_failed = process_assets(conn, minio_client, assets)
            log.info("post_assets 完成：成功=%s 失败=%s", a_success, a_failed)
        if TARGET in ("all", "posts"):
            posts = fetch_posts_missing_thumb(conn)
            log.info("待处理 posts: %s", len(posts))
            p_success, p_failed = process_posts(conn, minio_client, posts)
            log.info("posts 完成：成功=%s 失败=%s", p_success, p_failed)
    finally:
        if not DRY_RUN:
            conn.commit()
        conn.close()
    log.info("===== 历史缩略图补齐结束 =====")


if __name__ == "__main__":
    main()
