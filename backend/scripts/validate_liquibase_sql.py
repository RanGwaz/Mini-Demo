#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""Validate formatted SQL changesets included by the master changelog."""

from __future__ import annotations

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CHANGELOG_ROOT = ROOT / "src" / "main" / "resources" / "db" / "changelog"
SQL_DIR = CHANGELOG_ROOT / "sql"
MASTER = CHANGELOG_ROOT / "db.changelog-master.yaml"

CHANGESET_RE = re.compile(r"^--changeset\s+([A-Za-z0-9_.-]+):([A-Za-z0-9_.-]+)\b")


def fail(message: str) -> int:
    print(f"liquibase_sql_validation_failed: {message}", file=sys.stderr)
    return 1


def main() -> int:
    if not MASTER.exists():
        return fail(f"missing master changelog: {MASTER}")
    if not SQL_DIR.exists():
        return fail(f"missing sql changelog dir: {SQL_DIR}")

    master_text = MASTER.read_text(encoding="utf-8")
    if "path: db/changelog/sql" not in master_text:
        return fail("master changelog does not include db/changelog/sql")

    seen: dict[tuple[str, str], Path] = {}
    sql_files = sorted(SQL_DIR.glob("*.sql"))
    if not sql_files:
        return fail("no SQL changelog files found")

    for path in sql_files:
        text = path.read_text(encoding="utf-8")
        lines = text.splitlines()
        if not lines or lines[0].strip() != "--liquibase formatted sql":
            return fail(f"{path.name} must start with '--liquibase formatted sql'")

        changesets = []
        for line in lines:
            match = CHANGESET_RE.match(line.strip())
            if match:
                key = (match.group(1), match.group(2))
                if key in seen:
                    return fail(f"duplicate changeset {key[0]}:{key[1]} in {path.name} and {seen[key].name}")
                seen[key] = path
                changesets.append(key)
        if not changesets:
            return fail(f"{path.name} has no --changeset entries")

    print(f"validated_sql_changelogs={len(sql_files)} changesets={len(seen)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
