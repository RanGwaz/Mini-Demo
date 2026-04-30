from __future__ import annotations

import json
import os
import re
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path
from typing import Iterable

import pymysql

MYSQL_HOST = os.getenv("MYSQL_HOST", "127.0.0.1")
MYSQL_PORT = int(os.getenv("MYSQL_PORT", "3306"))
MYSQL_USER = os.getenv("MYSQL_USER", "root")
MYSQL_PASSWORD = os.getenv("MYSQL_PASSWORD", "root123456")
MYSQL_DATABASE = os.getenv("MYSQL_DATABASE", "image_social")

VOCAB_SIZE = int(os.getenv("TAG_VOCAB_SIZE", "64"))
MIN_POST_COUNT = int(os.getenv("TAG_VOCAB_MIN_POST_COUNT", "1"))
OUTPUT_PATH = Path(
    os.getenv(
        "TAG_VOCAB_OUT",
        str(Path(__file__).resolve().parents[2] / "infra" / "docker" / "models" / "tag_vocabulary.json"),
    )
)

TOKEN_SPLITTER = re.compile(r"[,|/\\>\s_\-:;，。！？、]+")


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


def split_terms(*values: object, limit_per_field: int = 24) -> list[str]:
    terms: list[str] = []
    for value in values:
        if value is None:
            continue
        count = 0
        for token in TOKEN_SPLITTER.split(str(value).strip().lower()):
            cleaned = token.strip()
            if len(cleaned) < 2 or cleaned.isdigit():
                continue
            terms.append(cleaned)
            count += 1
            if count >= limit_per_field:
                break
    return terms


def load_post_rows(conn) -> Iterable[dict]:
    with conn.cursor() as cursor:
        cursor.execute(
            """
            SELECT id,
                   title,
                   content,
                   tags,
                   topic_path,
                   topic_cluster_key,
                   subtopic_cluster_key,
                   semantic_tags,
                   style_tags
            FROM posts
            WHERE visibility = 'PUBLIC'
              AND audit_status = 'APPROVED'
            """
        )
        return cursor.fetchall()


def build_vocab(rows: Iterable[dict]) -> list[tuple[str, int, int]]:
    source_counter: Counter[str] = Counter()
    post_counter: defaultdict[str, set[int]] = defaultdict(set)
    for row in rows:
        post_id = int(row.get("id") or 0)
        terms = split_terms(
            row.get("tags"),
            row.get("semantic_tags"),
            row.get("style_tags"),
            row.get("topic_path"),
            row.get("topic_cluster_key"),
            row.get("subtopic_cluster_key"),
            row.get("title"),
            row.get("content"),
        )
        source_counter.update(terms)
        for term in set(terms):
            post_counter[term].add(post_id)

    ranked = []
    for term, source_count in source_counter.items():
        post_count = len(post_counter.get(term, set()))
        if post_count < MIN_POST_COUNT:
            continue
        ranked.append((term, int(source_count), int(post_count)))
    ranked.sort(key=lambda item: (item[2], item[1], item[0]), reverse=True)
    return ranked[: max(1, VOCAB_SIZE)]


def persist_vocab(conn, vocab: list[tuple[str, int, int]]) -> None:
    with conn.cursor() as cursor:
        cursor.execute(
            """
            CREATE TABLE IF NOT EXISTS recommendation_tag_dictionary (
                tag_id INT NOT NULL,
                term VARCHAR(128) NOT NULL,
                source_count INT NOT NULL DEFAULT 0,
                post_count INT NOT NULL DEFAULT 0,
                updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                PRIMARY KEY (tag_id),
                UNIQUE KEY uk_recommendation_tag_dictionary_term (term),
                KEY idx_recommendation_tag_dictionary_post_count (post_count DESC)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """
        )
        cursor.execute("TRUNCATE TABLE recommendation_tag_dictionary")
        cursor.executemany(
            """
            INSERT INTO recommendation_tag_dictionary(tag_id, term, source_count, post_count, updated_at)
            VALUES (%s, %s, %s, %s, NOW())
            """,
            [(index, term, source_count, post_count) for index, (term, source_count, post_count) in enumerate(vocab)],
        )
    conn.commit()


def write_json(vocab: list[tuple[str, int, int]]) -> None:
    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    tag_to_id = {term: index for index, (term, _, _) in enumerate(vocab)}
    payload = {
        "generated_at": datetime.now().isoformat(),
        "dimension": len(tag_to_id),
        "tag_to_id": tag_to_id,
        "items": [
            {
                "tag_id": index,
                "term": term,
                "source_count": source_count,
                "post_count": post_count,
            }
            for index, (term, source_count, post_count) in enumerate(vocab)
        ],
    }
    OUTPUT_PATH.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")


def main() -> None:
    conn = mysql_conn()
    try:
        rows = load_post_rows(conn)
        vocab = build_vocab(rows)
        persist_vocab(conn, vocab)
        write_json(vocab)
    finally:
        conn.close()
    print(f"[tag-vocab] exported {len(vocab)} terms -> {OUTPUT_PATH}")


if __name__ == "__main__":
    main()
