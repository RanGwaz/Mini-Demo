#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Import editorial Chinese cold-start content into content_import_batches/items.

The script does not publish posts directly. It prepares a batch for the P5
operation console, where an admin can review, publish, and rollback.

Run:
  python backend/scripts/import_chinese_seed_content.py --file data.jsonl --batch-name "中文冷启动第一批"
  python backend/scripts/import_chinese_seed_content.py --file data.jsonl --dry-run
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from dataclasses import dataclass
from typing import Any, Iterable

import pymysql


def env(name: str, default: str) -> str:
    return os.getenv(name, default)


MYSQL_HOST = env("MYSQL_HOST", "127.0.0.1")
MYSQL_PORT = int(env("MYSQL_PORT", "3306"))
MYSQL_USER = env("MYSQL_USER", "root")
MYSQL_PASSWORD = env("MYSQL_PASSWORD", "root123456")
MYSQL_DATABASE = env("MYSQL_DATABASE", "image_social")

FORBIDDEN_MARKERS = ("unsplash", "randomuser", "picsum", "placeholder", "lorem ipsum")


@dataclass
class SeedItem:
    title: str
    content: str
    channel_code: str
    topics: list[str]
    image_urls: list[str]
    raw: dict[str, Any]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Import Chinese seed content into an admin import batch")
    parser.add_argument("--file", required=True, help="UTF-8 JSONL file")
    parser.add_argument("--batch-name", default="中文冷启动内容批次")
    parser.add_argument("--description", default="由 import_chinese_seed_content.py 创建，等待运营后台审核发布")
    parser.add_argument("--source-type", default="EDITORIAL")
    parser.add_argument("--operator-id", type=int, default=1)
    parser.add_argument("--dry-run", action="store_true")
    return parser.parse_args()


def connect_mysql():
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


def as_list(value: Any) -> list[str]:
    if value is None:
        return []
    if isinstance(value, list):
        return [str(item).strip() for item in value if str(item).strip()]
    return [item.strip() for item in str(value).replace("，", ",").split(",") if item.strip()]


def reject_forbidden_markers(item: SeedItem, line_no: int) -> None:
    blob = " ".join([item.title, item.content, item.channel_code, *item.topics, *item.image_urls]).lower()
    marker = next((value for value in FORBIDDEN_MARKERS if value in blob), None)
    if marker:
        raise ValueError(f"line {line_no}: forbidden simulated-data marker found: {marker}")


def load_items(path: str) -> list[SeedItem]:
    items: list[SeedItem] = []
    with open(path, "r", encoding="utf-8") as handle:
        for line_no, line in enumerate(handle, 1):
            text = line.strip()
            if not text or text.startswith("#"):
                continue
            raw = json.loads(text)
            item = SeedItem(
                title=str(raw.get("title") or "").strip(),
                content=str(raw.get("content") or "").strip(),
                channel_code=str(raw.get("channel_code") or raw.get("channelCode") or "campus").strip(),
                topics=as_list(raw.get("topics")),
                image_urls=as_list(raw.get("image_urls") or raw.get("imageUrls")),
                raw=raw,
            )
            if not item.title and not item.content and not item.image_urls:
                raise ValueError(f"line {line_no}: title/content/image_urls cannot all be empty")
            if not item.channel_code:
                raise ValueError(f"line {line_no}: channel_code is required")
            reject_forbidden_markers(item, line_no)
            items.append(item)
    return items


def ensure_channels(conn, channel_codes: Iterable[str]) -> None:
    codes = sorted(set(channel_codes))
    if not codes:
        return
    placeholders = ",".join(["%s"] * len(codes))
    with conn.cursor() as cursor:
        cursor.execute(f"SELECT code FROM channels WHERE code IN ({placeholders})", codes)
        existing = {row["code"] for row in cursor.fetchall()}
    missing = [code for code in codes if code not in existing]
    if missing:
        raise ValueError(f"channels not found: {', '.join(missing)}")


def insert_batch(conn, args: argparse.Namespace, items: list[SeedItem]) -> int:
    with conn.cursor() as cursor:
        cursor.execute(
            """
            INSERT INTO content_import_batches
              (name, description, source_type, status, total_count, success_count, failed_count, operator_id)
            VALUES (%s, %s, %s, 'DRAFT', %s, 0, 0, %s)
            """,
            (args.batch_name, args.description, args.source_type, len(items), args.operator_id),
        )
        batch_id = int(cursor.lastrowid)
        for item in items:
            cursor.execute(
                """
                INSERT INTO content_import_items
                  (batch_id, title, content, channel_code, topic_names, image_urls, status, raw_payload)
                VALUES (%s, %s, %s, %s, %s, %s, 'DRAFT', %s)
                """,
                (
                    batch_id,
                    item.title,
                    item.content,
                    item.channel_code,
                    ",".join(item.topics),
                    ",".join(item.image_urls),
                    json.dumps(item.raw, ensure_ascii=False),
                ),
            )
    return batch_id


def main() -> int:
    args = parse_args()
    items = load_items(args.file)
    print(f"loaded {len(items)} seed items")
    if not items:
        return 0

    channels = sorted({item.channel_code for item in items})
    topics = sorted({topic for item in items for topic in item.topics})
    print(f"channels: {', '.join(channels)}")
    print(f"topics: {len(topics)}")

    conn = connect_mysql()
    try:
        ensure_channels(conn, channels)
        if args.dry_run:
            conn.rollback()
            print("dry-run passed, no rows inserted")
            return 0
        batch_id = insert_batch(conn, args, items)
        conn.commit()
        print(f"created import batch id={batch_id}, items={len(items)}")
        return 0
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"error: {exc}", file=sys.stderr)
        raise SystemExit(1)
