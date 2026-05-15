#!/usr/bin/env python3
# -*- coding: utf-8 -*-

from __future__ import annotations

import logging
import os
import subprocess
import sys
import time
from dataclasses import dataclass
from pathlib import Path


SCRIPT_DIR = Path(__file__).resolve().parent


def env_int(name: str, default: int) -> int:
    value = os.getenv(name)
    if value in (None, ""):
        return default
    return int(value)


FEATURE_INTERVAL_SECONDS = env_int("RECO_FEATURE_INTERVAL_SECONDS", 300)
I2I_INTERVAL_SECONDS = env_int("RECO_I2I_INTERVAL_SECONDS", 3600)
SLEEP_SECONDS = env_int("RECO_SCHEDULER_TICK_SECONDS", 10)


logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[logging.StreamHandler(sys.stdout)],
)
log = logging.getLogger("reco-maintenance")


@dataclass
class Job:
    name: str
    script: str
    interval_seconds: int
    next_run_at: float = 0.0


def run_job(job: Job) -> bool:
    started_at = time.monotonic()
    command = [sys.executable, str(SCRIPT_DIR / job.script)]
    log.info("[job:%s] started command=%s", job.name, " ".join(command))
    try:
        subprocess.run(command, cwd=str(SCRIPT_DIR), check=True)
    except subprocess.CalledProcessError as exc:
        elapsed = time.monotonic() - started_at
        log.exception("[job:%s] failed exit_code=%s elapsed=%.1fs", job.name, exc.returncode, elapsed)
        return False
    elapsed = time.monotonic() - started_at
    log.info("[job:%s] finished elapsed=%.1fs", job.name, elapsed)
    return True


def main() -> int:
    jobs = [
        Job("feature", "feature_engineering.py", FEATURE_INTERVAL_SECONDS),
        Job("i2i", "build_i2i_neighbors.py", I2I_INTERVAL_SECONDS),
    ]
    for job in jobs:
        job.next_run_at = 0.0

    log.info(
        "recommendation maintenance scheduler started feature_interval=%ss i2i_interval=%ss",
        FEATURE_INTERVAL_SECONDS,
        I2I_INTERVAL_SECONDS,
    )
    while True:
        now = time.monotonic()
        for job in jobs:
            if now < job.next_run_at:
                continue
            run_job(job)
            job.next_run_at = time.monotonic() + max(30, job.interval_seconds)
        time.sleep(max(1, SLEEP_SECONDS))


if __name__ == "__main__":
    raise SystemExit(main())
