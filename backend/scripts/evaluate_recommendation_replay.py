#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Offline replay evaluation for the recommendation candidate layer.

This evaluator uses time split:
  train window: validation_start - TRAIN_DAYS to validation_start
  validation window: validation_start to validation_start + VALIDATION_DAYS

It evaluates an i2i candidate policy with hot fallback and writes a JSON report.
"""

import json
import logging
import math
import os
import sys
from collections import defaultdict
from datetime import datetime, timedelta
from pathlib import Path

import pandas as pd
from sqlalchemy import bindparam, create_engine, text

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[logging.StreamHandler(sys.stdout)],
)
log = logging.getLogger(__name__)


def _env(name, default):
    return os.getenv(name, default)


def _env_int(name, default):
    value = os.getenv(name)
    return int(value) if value not in (None, "") else default


MYSQL_HOST = _env("MYSQL_HOST", "127.0.0.1")
MYSQL_PORT = _env_int("MYSQL_PORT", 3306)
MYSQL_USER = _env("MYSQL_USER", "root")
MYSQL_PASSWORD = _env("MYSQL_PASSWORD", "root123456")
MYSQL_DATABASE = _env("MYSQL_DATABASE", "image_social")

TRAIN_DAYS = _env_int("REPLAY_TRAIN_DAYS", 30)
VALIDATION_DAYS = _env_int("REPLAY_VALIDATION_DAYS", 2)
MAX_USERS = _env_int("REPLAY_MAX_USERS", 2000)
K_VALUES = [10, 20, 50]
OUTPUT_DIR = Path(_env("REPLAY_OUTPUT_DIR", "reports/recommendation_eval"))

POSITIVE_EVENT_TYPES = (
    "POST_CLICK",
    "POST_DETAIL_VIEW",
    "POST_LIKE",
    "POST_FAVORITE",
    "POST_COMMENT",
    "POST_SHARE",
)


def get_engine():
    url = (
        f"mysql+pymysql://{MYSQL_USER}:{MYSQL_PASSWORD}"
        f"@{MYSQL_HOST}:{MYSQL_PORT}/{MYSQL_DATABASE}?charset=utf8mb4"
    )
    return create_engine(url, pool_pre_ping=True)


def load_events(engine, start_at, end_at):
    sql = text(
        """
        SELECT user_id, target_id AS post_id, event_type, created_at
        FROM user_events
        WHERE target_type = 'POST'
          AND user_id IS NOT NULL
          AND target_id IS NOT NULL
          AND event_type IN :event_types
          AND created_at >= :start_at
          AND created_at < :end_at
        ORDER BY user_id ASC, created_at DESC
        """
    ).bindparams(bindparam("event_types", expanding=True))
    with engine.connect() as conn:
        result = conn.execute(
            sql,
            {
            "event_types": list(POSITIVE_EVENT_TYPES),
            "start_at": start_at,
            "end_at": end_at,
            },
        )
        return pd.DataFrame(result.mappings().all())


def load_hot_posts(engine, limit=500):
    sql = text(
        """
        SELECT id
        FROM posts
        WHERE visibility = 'PUBLIC' AND audit_status = 'APPROVED'
        ORDER BY hot_score DESC, created_at DESC
        LIMIT :limit
        """
    )
    with engine.connect() as conn:
        result = conn.execute(sql, {"limit": limit})
        return [int(row["id"]) for row in result.mappings().all()]


def load_i2i(engine, seed_ids, limit):
    if not seed_ids:
        return []
    sql = text(
        """
        SELECT neighbor_post_id, MAX(score) AS score
        FROM post_i2i_neighbors
        WHERE post_id IN :seed_ids
        GROUP BY neighbor_post_id
        ORDER BY score DESC
        LIMIT :limit
        """
    ).bindparams(bindparam("seed_ids", expanding=True))
    with engine.connect() as conn:
        result = conn.execute(sql, {"seed_ids": list(seed_ids), "limit": limit})
        return [int(row["neighbor_post_id"]) for row in result.mappings().all()]


def dcg_at_k(candidates, positives, k):
    score = 0.0
    for index, post_id in enumerate(candidates[:k]):
        if post_id in positives:
            score += 1.0 / math.log2(index + 2)
    return score


def ndcg_at_k(candidates, positives, k):
    ideal_hits = min(len(positives), k)
    if ideal_hits <= 0:
        return 0.0
    ideal = sum(1.0 / math.log2(index + 2) for index in range(ideal_hits))
    return dcg_at_k(candidates, positives, k) / ideal


def evaluate_user(engine, train_seed_ids, validation_positive_ids, hot_posts, candidate_limit=200):
    candidates = []
    seen = set(train_seed_ids)
    for post_id in load_i2i(engine, train_seed_ids[:12], candidate_limit):
        if post_id not in seen:
            seen.add(post_id)
            candidates.append(post_id)
    for post_id in hot_posts:
        if post_id not in seen:
            seen.add(post_id)
            candidates.append(post_id)
        if len(candidates) >= candidate_limit:
            break

    positives = set(validation_positive_ids)
    result = {
        "candidate_count": len(candidates),
        "coverage_posts": set(candidates),
    }
    for k in K_VALUES:
        top_k = candidates[:k]
        hits = len([post_id for post_id in top_k if post_id in positives])
        result[f"hit_rate@{k}"] = 1.0 if hits > 0 else 0.0
        result[f"recall@{k}"] = hits / max(1, len(positives))
        result[f"ndcg@{k}"] = ndcg_at_k(candidates, positives, k)
    return result


def main():
    engine = get_engine()
    validation_end = datetime.now()
    validation_start = validation_end - timedelta(days=VALIDATION_DAYS)
    train_start = validation_start - timedelta(days=TRAIN_DAYS)

    log.info("[replay] train=%s -> %s, validation=%s -> %s", train_start, validation_start, validation_start, validation_end)
    train_events = load_events(engine, train_start, validation_start)
    validation_events = load_events(engine, validation_start, validation_end)
    hot_posts = load_hot_posts(engine)

    train_by_user = defaultdict(list)
    for _, row in train_events.iterrows():
        post_id = int(row["post_id"])
        if post_id not in train_by_user[row["user_id"]]:
            train_by_user[row["user_id"]].append(post_id)

    validation_by_user = defaultdict(list)
    for _, row in validation_events.iterrows():
        post_id = int(row["post_id"])
        if post_id not in validation_by_user[row["user_id"]]:
            validation_by_user[row["user_id"]].append(post_id)

    users = [user_id for user_id in validation_by_user.keys() if train_by_user.get(user_id)]
    users = users[:MAX_USERS]
    if not users:
        log.warning("[replay] no users with both train and validation positives")
        return

    aggregate = defaultdict(float)
    coverage = set()
    evaluated = 0
    for user_id in users:
        result = evaluate_user(engine, train_by_user[user_id], validation_by_user[user_id], hot_posts)
        evaluated += 1
        coverage.update(result.pop("coverage_posts"))
        for key, value in result.items():
            aggregate[key] += float(value)

    summary = {
        "generated_at": datetime.now().isoformat(timespec="seconds"),
        "train_start": train_start.isoformat(timespec="seconds"),
        "validation_start": validation_start.isoformat(timespec="seconds"),
        "validation_end": validation_end.isoformat(timespec="seconds"),
        "evaluated_users": evaluated,
        "unique_candidate_coverage": len(coverage),
        "metrics": {key: round(value / evaluated, 6) for key, value in sorted(aggregate.items())},
    }

    output_dir = OUTPUT_DIR / datetime.now().strftime("%Y%m%d_%H%M%S")
    output_dir.mkdir(parents=True, exist_ok=True)
    output_file = output_dir / "summary.json"
    output_file.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    log.info("[replay] wrote %s", output_file)
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
