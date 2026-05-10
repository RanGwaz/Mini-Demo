#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Rebuild searchable post scope.

The current project search implementation reads MySQL directly and the Java
SearchIndexGateway can be a no-op. This script still gives the P7 pipeline a
stable SEARCH_INDEX step: it validates the effective searchable set and reports
counts. If an Elasticsearch gateway is added later, this file is the place to
push documents in batches.
"""

from __future__ import annotations

import os
import sys

import pymysql


def env(name: str, default: str) -> str:
    return os.getenv(name, default)


MYSQL_HOST = env("MYSQL_HOST", "127.0.0.1")
MYSQL_PORT = int(env("MYSQL_PORT", "3306"))
MYSQL_USER = env("MYSQL_USER", "root")
MYSQL_PASSWORD = env("MYSQL_PASSWORD", "root123456")
MYSQL_DATABASE = env("MYSQL_DATABASE", "image_social")


def connect_mysql():
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


def main() -> int:
    conn = connect_mysql()
    try:
        with conn.cursor() as cursor:
            cursor.execute(
                """
                SELECT COUNT(*) AS total
                FROM posts
                WHERE visibility = 'PUBLIC'
                  AND audit_status = 'APPROVED'
                  AND COALESCE(deleted, 0) = 0
                """
            )
            total = int(cursor.fetchone()["total"])
        print(f"searchable_posts={total}")
        print("mysql-backed search is ready; external index push is not configured")
        return 0
    finally:
        conn.close()


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"error: {exc}", file=sys.stderr)
        raise SystemExit(1)
