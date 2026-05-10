#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Plan or execute a safe cleanup of simulated/Unsplash-like content.

Default mode is dry-run and only writes a JSON plan. Execute mode soft-hides
matching posts by setting visibility=PRIVATE and audit_status=REJECTED. It does
not delete MinIO objects, Milvus vectors, comments, likes, or recommendation
code paths.

Run:
  python backend/scripts/cleanup_simulated_content.py --plan-file cleanup-plan.json
  python backend/scripts/cleanup_simulated_content.py --execute --confirm SOFT_HIDE_SIMULATED_CONTENT
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from datetime import datetime
from typing import Any

import pymysql


def env(name: str, default: str) -> str:
    return os.getenv(name, default)


MYSQL_HOST = env("MYSQL_HOST", "127.0.0.1")
MYSQL_PORT = int(env("MYSQL_PORT", "3306"))
MYSQL_USER = env("MYSQL_USER", "root")
MYSQL_PASSWORD = env("MYSQL_PASSWORD", "root123456")
MYSQL_DATABASE = env("MYSQL_DATABASE", "image_social")

SIMULATED_PATTERN = (
    "unsplash|randomuser|picsum|placeholder|lorem ipsum|mock|sample|demo|"
    "模拟|测试数据|占位|示例"
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Plan or soft-hide simulated content")
    parser.add_argument("--limit", type=int, default=0, help="Limit matched posts, 0 means all")
    parser.add_argument("--plan-file", default="simulated-content-cleanup-plan.json")
    parser.add_argument("--execute", action="store_true")
    parser.add_argument("--confirm", default="")
    parser.add_argument("--clear-derived", action="store_true", help="Also truncate post_features and post_i2i_neighbors")
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


def find_simulated_posts(conn, limit: int) -> list[dict[str, Any]]:
    limit_sql = "LIMIT %s" if limit and limit > 0 else ""
    params: tuple[Any, ...] = (SIMULATED_PATTERN, limit) if limit and limit > 0 else (SIMULATED_PATTERN,)
    sql = f"""
        SELECT id, author_id, channel_code, title, cover_url, thumb_url, tags, created_at
        FROM posts
        WHERE COALESCE(deleted, 0) = 0
          AND (
            LOWER(CONCAT_WS(' ', title, content, tags, semantic_tags, style_tags, cover_url, thumb_url, extra))
            REGEXP %s
          )
        ORDER BY id ASC
        {limit_sql}
    """
    with conn.cursor() as cursor:
        cursor.execute(sql, params)
        return list(cursor.fetchall())


def find_candidate_objects(conn, post_ids: list[int]) -> list[str]:
    if not post_ids:
        return []
    placeholders = ",".join(["%s"] * len(post_ids))
    sql = f"""
        SELECT object_key
        FROM post_assets
        WHERE post_id IN ({placeholders})
          AND object_key IS NOT NULL
          AND object_key != ''
          AND LOWER(object_key) REGEXP 'unsplash|migrated|placeholder|mock|demo'
    """
    with conn.cursor() as cursor:
        cursor.execute(sql, post_ids)
        return [row["object_key"] for row in cursor.fetchall()]


def write_plan(path: str, posts: list[dict[str, Any]], objects: list[str]) -> None:
    payload = {
        "generatedAt": datetime.now().isoformat(timespec="seconds"),
        "mode": "soft-hide-plan",
        "postCount": len(posts),
        "posts": [
            {
                "id": row["id"],
                "authorId": row["author_id"],
                "channelCode": row["channel_code"],
                "title": row["title"],
                "coverUrl": row["cover_url"],
                "tags": row["tags"],
                "createdAt": str(row["created_at"]),
            }
            for row in posts
        ],
        "candidateMinioObjects": objects,
        "nextSteps": [
            "review this plan",
            "run with --execute --confirm SOFT_HIDE_SIMULATED_CONTENT to soft-hide posts",
            "run rebuild tasks for SEARCH_INDEX, FEATURE, I2I, EMBEDDING after cleanup",
        ],
    }
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=2)


def soft_hide_posts(conn, post_ids: list[int]) -> None:
    if not post_ids:
        return
    placeholders = ",".join(["%s"] * len(post_ids))
    sql = f"""
        UPDATE posts
        SET visibility = 'PRIVATE',
            audit_status = 'REJECTED',
            taxonomy_version = 'simulated-content-cleanup-v1',
            updated_at = NOW()
        WHERE id IN ({placeholders})
    """
    with conn.cursor() as cursor:
        cursor.execute(sql, post_ids)


def clear_derived_tables(conn) -> None:
    with conn.cursor() as cursor:
        cursor.execute("TRUNCATE TABLE post_i2i_neighbors")
        cursor.execute("TRUNCATE TABLE post_features")


def main() -> int:
    args = parse_args()
    conn = connect_mysql()
    try:
        posts = find_simulated_posts(conn, args.limit)
        post_ids = [int(row["id"]) for row in posts]
        objects = find_candidate_objects(conn, post_ids)
        write_plan(args.plan_file, posts, objects)
        print(f"plan written: {args.plan_file}")
        print(f"matched posts: {len(post_ids)}, candidate minio objects: {len(objects)}")

        if not args.execute:
            conn.rollback()
            print("dry-run only, no database rows changed")
            return 0

        if args.confirm != "SOFT_HIDE_SIMULATED_CONTENT":
            raise ValueError("execute mode requires --confirm SOFT_HIDE_SIMULATED_CONTENT")

        soft_hide_posts(conn, post_ids)
        if args.clear_derived:
            clear_derived_tables(conn)
        conn.commit()
        print("cleanup committed")
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
