#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Minimal production smoke check for staging/prod.

Run:
  python backend/scripts/production_smoke_check.py --base-url http://localhost:8080
"""

from __future__ import annotations

import argparse
import sys

import requests


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Smoke check HTTP endpoints")
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--timeout", type=float, default=5.0)
    return parser.parse_args()


def check(base_url: str, path: str, timeout: float) -> None:
    url = base_url.rstrip("/") + path
    response = requests.get(url, timeout=timeout)
    if response.status_code >= 400:
        raise RuntimeError(f"{url} returned {response.status_code}: {response.text[:200]}")
    print(f"ok {path}")


def main() -> int:
    args = parse_args()
    check(args.base_url, "/actuator/health", args.timeout)
    check(args.base_url, "/api/channels", args.timeout)
    check(args.base_url, "/api/topics/trending?limit=5", args.timeout)
    check(args.base_url, "/api/feed?page=1&pageSize=5", args.timeout)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"smoke check failed: {exc}", file=sys.stderr)
        raise SystemExit(1)
