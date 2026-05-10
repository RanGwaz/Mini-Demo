#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Run queued content_rebuild_tasks.

This worker intentionally stays outside the Java web process. The operation
console creates tasks, and this script runs them when the operator is ready.

Run:
  python backend/scripts/run_rebuild_tasks.py --once
  python backend/scripts/run_rebuild_tasks.py --task-id 12
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import time
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

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent

TASK_COMMANDS = {
    "SEARCH_INDEX": [["rebuild_search_index.py"]],
    "THUMBNAIL": [["handle_data", "generate_post_thumbnails.py"]],
    "EMBEDDING": [["extract_image_embeddings.py"]],
    "SEMANTIC": [["build_post_semantics.py"]],
    "FEATURE": [["feature_engineering.py"]],
    "I2I": [["build_i2i_neighbors.py"]],
    "ALL": [
        ["rebuild_search_index.py"],
        ["handle_data", "generate_post_thumbnails.py"],
        ["extract_image_embeddings.py"],
        ["build_post_semantics.py"],
        ["feature_engineering.py"],
        ["build_i2i_neighbors.py"],
    ],
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run pending rebuild tasks")
    parser.add_argument("--once", action="store_true", help="Run one polling pass and exit")
    parser.add_argument("--task-id", type=int, default=0)
    parser.add_argument("--sleep", type=int, default=15)
    parser.add_argument("--limit", type=int, default=1)
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


def fetch_tasks(conn, args: argparse.Namespace) -> list[dict[str, Any]]:
    with conn.cursor() as cursor:
        if args.task_id:
            cursor.execute("SELECT * FROM content_rebuild_tasks WHERE id=%s", (args.task_id,))
        else:
            cursor.execute(
                """
                SELECT *
                FROM content_rebuild_tasks
                WHERE status='PENDING'
                ORDER BY created_at ASC
                LIMIT %s
                """,
                (args.limit,),
            )
        return list(cursor.fetchall())


def update_task(conn, task_id: int, status: str, *, success: int | None = None, failed: int | None = None, error: str = "") -> None:
    fields = ["status=%s", "updated_at=NOW()"]
    params: list[Any] = [status]
    if status == "RUNNING":
        fields.append("started_at=COALESCE(started_at, NOW())")
    if status in {"SUCCESS", "FAILED", "CANCELED"}:
        fields.append("finished_at=NOW()")
    if success is not None:
        fields.append("success_count=%s")
        params.append(success)
    if failed is not None:
        fields.append("failed_count=%s")
        params.append(failed)
    if error:
        fields.append("error_message=%s")
        params.append(error[:1024])
    params.append(task_id)
    with conn.cursor() as cursor:
        cursor.execute(f"UPDATE content_rebuild_tasks SET {', '.join(fields)} WHERE id=%s", params)
    conn.commit()


def task_env(task: dict[str, Any]) -> dict[str, str]:
    merged = os.environ.copy()
    if task.get("batch_id"):
        merged["REBUILD_BATCH_ID"] = str(task["batch_id"])
    if task.get("post_id"):
        merged["REBUILD_POST_ID"] = str(task["post_id"])
    if task.get("scope_type"):
        merged["REBUILD_SCOPE_TYPE"] = str(task["scope_type"])
    if task.get("scope_id"):
        merged["REBUILD_SCOPE_ID"] = str(task["scope_id"])
    if task.get("params_json"):
        try:
            params = json.loads(task["params_json"])
            for key, value in params.items():
                merged[f"REBUILD_PARAM_{str(key).upper()}"] = str(value)
        except Exception:
            pass
    return merged


def run_command(parts: list[str], task: dict[str, Any]) -> None:
    script = SCRIPT_DIR.joinpath(*parts)
    command = [sys.executable, str(script)]
    print(f"running task={task['id']} type={task['task_type']} command={' '.join(command)}")
    subprocess.run(command, cwd=str(REPO_ROOT), env=task_env(task), check=True)


def run_task(conn, task: dict[str, Any]) -> None:
    task_type = str(task["task_type"]).upper()
    commands = TASK_COMMANDS.get(task_type)
    if not commands:
        raise ValueError(f"unsupported task_type: {task_type}")

    update_task(conn, int(task["id"]), "RUNNING")
    success = 0
    try:
        for parts in commands:
            run_command(parts, task)
            success += 1
            update_task(conn, int(task["id"]), "RUNNING", success=success, failed=0)
        update_task(conn, int(task["id"]), "SUCCESS", success=success, failed=0)
    except Exception as exc:
        update_task(conn, int(task["id"]), "FAILED", success=success, failed=1, error=str(exc))
        raise


def main() -> int:
    args = parse_args()
    while True:
        conn = connect_mysql()
        try:
            tasks = fetch_tasks(conn, args)
            if not tasks:
                if args.once or args.task_id:
                    print("no rebuild tasks to run")
                    return 0
                time.sleep(args.sleep)
                continue
            for task in tasks:
                run_task(conn, task)
        finally:
            conn.close()
        if args.once or args.task_id:
            return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"error: {exc}", file=sys.stderr)
        raise SystemExit(1)
