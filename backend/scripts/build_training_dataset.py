#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Build a ranking training dataset from feed impressions and user events.

Output is JSONL with one row per impression. Positive labels come from clicks,
detail views, likes, favorites, comments, and shares within the attribution
window after an impression.

Run:
  python backend/scripts/build_training_dataset.py --output data/training/feed_rank.jsonl --register
"""

from __future__ import annotations

import argparse
import json
import os
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any

import pymysql


def env(name: str, default: str) -> str:
    return os.getenv(name, default)


MYSQL_HOST = env("MYSQL_HOST", "127.0.0.1")
MYSQL_PORT = int(env("MYSQL_PORT", "3306"))
MYSQL_USER = env("MYSQL_USER", "root")
MYSQL_PASSWORD = env("MYSQL_PASSWORD", "root123456")
MYSQL_DATABASE = env("MYSQL_DATABASE", "image_social")

POSITIVE_EVENTS = ("POST_CLICK", "POST_DETAIL_VIEW", "POST_LIKE", "POST_FAVORITE", "POST_COMMENT", "POST_SHARE")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build feed ranking training JSONL")
    parser.add_argument("--output", required=True)
    parser.add_argument("--days", type=int, default=30)
    parser.add_argument("--attribution-hours", type=int, default=24)
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--name", default="")
    parser.add_argument("--operator-id", type=int, default=1)
    parser.add_argument("--register", action="store_true")
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


def load_rows(conn, days: int, attribution_hours: int, limit: int) -> list[dict[str, Any]]:
    since = datetime.now() - timedelta(days=days)
    limit_sql = "LIMIT %s" if limit > 0 else ""
    event_placeholders = ", ".join(["%s"] * len(POSITIVE_EVENTS))
    params: list[Any] = [*POSITIVE_EVENTS, attribution_hours, since]
    if limit > 0:
        params.append(limit)
    sql = f"""
        SELECT
          i.request_id,
          i.user_id,
          i.post_id,
          i.rank_position,
          i.recall_source,
          i.rank_score,
          i.channel_code,
          i.topic_names,
          i.created_at AS impressed_at,
          CASE WHEN EXISTS (
            SELECT 1
            FROM user_events e
            WHERE e.user_id = i.user_id
              AND e.target_type = 'POST'
              AND e.target_id = i.post_id
              AND e.event_type IN ({event_placeholders})
              AND e.created_at >= i.created_at
              AND e.created_at < DATE_ADD(i.created_at, INTERVAL %s HOUR)
          ) THEN 1 ELSE 0 END AS label
        FROM feed_impression_logs i
        WHERE i.created_at >= %s
        ORDER BY i.created_at ASC
        {limit_sql}
    """
    with conn.cursor() as cursor:
        cursor.execute(sql, params)
        return list(cursor.fetchall())


def write_jsonl(path: str, rows: list[dict[str, Any]]) -> tuple[int, int]:
    output = Path(path)
    output.parent.mkdir(parents=True, exist_ok=True)
    positives = 0
    with output.open("w", encoding="utf-8") as handle:
        for row in rows:
            row = dict(row)
            row["label"] = int(row["label"])
            positives += row["label"]
            row["impressed_at"] = str(row["impressed_at"])
            handle.write(json.dumps(row, ensure_ascii=False) + "\n")
    return len(rows), positives


def register_dataset(conn, args: argparse.Namespace, total: int, positives: int) -> None:
    name = args.name or f"feed-ranking-{datetime.now().strftime('%Y%m%d%H%M%S')}"
    metrics = {"days": args.days, "attributionHours": args.attribution_hours}
    with conn.cursor() as cursor:
        cursor.execute(
            """
            INSERT INTO training_datasets
              (name, dataset_type, status, split_strategy, row_count, positive_count, negative_count, file_path, metrics_json, operator_id)
            VALUES (%s, 'RANKING', 'READY', 'time_8_1_1', %s, %s, %s, %s, %s, %s)
            """,
            (name, total, positives, max(total - positives, 0), args.output, json.dumps(metrics), args.operator_id),
        )


def main() -> int:
    args = parse_args()
    conn = connect_mysql()
    try:
        rows = load_rows(conn, args.days, args.attribution_hours, args.limit)
        total, positives = write_jsonl(args.output, rows)
        if args.register:
            register_dataset(conn, args, total, positives)
            conn.commit()
        else:
            conn.rollback()
        print(f"dataset_rows={total} positives={positives} output={args.output}")
        return 0
    finally:
        conn.close()


if __name__ == "__main__":
    raise SystemExit(main())
