#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Register an offline evaluation report in offline_eval_reports.

Run:
  python backend/scripts/register_eval_report.py --model-version-id 1 --dataset-id 1 --auc 0.71 --ndcg 0.42
"""

from __future__ import annotations

import argparse
import json
import os

import pymysql


def env(name: str, default: str) -> str:
    return os.getenv(name, default)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Register offline eval report")
    parser.add_argument("--model-version-id", type=int, required=True)
    parser.add_argument("--dataset-id", type=int, required=True)
    parser.add_argument("--auc", type=float, default=0)
    parser.add_argument("--ndcg", type=float, default=0)
    parser.add_argument("--recall", type=float, default=0)
    parser.add_argument("--precision", type=float, default=0)
    parser.add_argument("--report-path", default="")
    parser.add_argument("--metrics-json", default="{}")
    return parser.parse_args()


def connect_mysql():
    return pymysql.connect(
        host=env("MYSQL_HOST", "127.0.0.1"),
        port=int(env("MYSQL_PORT", "3306")),
        user=env("MYSQL_USER", "root"),
        password=env("MYSQL_PASSWORD", "root123456"),
        database=env("MYSQL_DATABASE", "image_social"),
        charset="utf8mb4",
        autocommit=True,
    )


def main() -> int:
    args = parse_args()
    metrics = json.loads(args.metrics_json)
    metrics.update({"auc": args.auc, "ndcg": args.ndcg, "recall": args.recall, "precision": args.precision})
    conn = connect_mysql()
    try:
        with conn.cursor() as cursor:
            cursor.execute(
                """
                INSERT INTO offline_eval_reports
                  (model_version_id, dataset_id, auc, ndcg, recall_score, precision_score, metrics_json, report_path, status)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, 'READY')
                """,
                (args.model_version_id, args.dataset_id, args.auc, args.ndcg, args.recall, args.precision, json.dumps(metrics), args.report_path),
            )
        print("eval report registered")
        return 0
    finally:
        conn.close()


if __name__ == "__main__":
    raise SystemExit(main())
