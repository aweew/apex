#!/usr/bin/env python3
"""凌晨数据补缺：日线 K 线、公司资料、财务基本面。"""

from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path
from typing import List, Tuple

ROOT = Path(__file__).resolve().parent
RepairStep = Tuple[str, Path, List[str]]


def build_steps(
    expected_date: str,
    start: str,
    bars_batch: int,
    bars_rounds: int,
    profile_limit: int,
    fundamental_limit: int,
    stale_days: int,
) -> List[RepairStep]:
    return [
        (
            "daily_bars",
            ROOT / "sync_missing_bars.py",
            [
                "--batch", str(max(1, bars_batch)),
                "--rounds", str(max(1, bars_rounds)),
                "--start", start,
                "--expected-date", expected_date,
                "--sleep", "0.18",
            ],
        ),
        (
            "company_profile",
            ROOT / "sync_company_profile.py",
            [
                "--missing",
                "--limit", str(max(1, profile_limit)),
                "--stale-days", str(max(1, stale_days)),
                "--sleep", "0.2",
            ],
        ),
        (
            "fundamentals",
            ROOT / "sync_fundamentals.py",
            [
                "--mode", "all",
                "--missing",
                "--limit", str(max(1, fundamental_limit)),
                "--sleep", "0.6",
                "--no-resume",
            ],
        ),
    ]


def run_one(script: Path, script_args: List[str]) -> int:
    if not script.is_file():
        print(f"[NIGHTLY_REPAIR] missing script={script.name}", flush=True)
        return 2
    command = [sys.executable, "-u", str(script), *script_args]
    print(f"[NIGHTLY_REPAIR] run: {' '.join(command)}", flush=True)
    process = subprocess.run(command, cwd=str(ROOT))
    print(f"[NIGHTLY_REPAIR] exit={process.returncode} script={script.name}", flush=True)
    return process.returncode


def run_steps(steps: List[RepairStep]) -> int:
    failed_steps = []
    total = len(steps)
    for index, (step_name, script, script_args) in enumerate(steps, 1):
        print(f"[NIGHTLY_REPAIR] step {index}/{total}: {step_name}", flush=True)
        exit_code = run_one(script, script_args)
        if exit_code != 0:
            failed_steps.append(step_name)
            print(f"[NIGHTLY_REPAIR] failed step={step_name} exit={exit_code}", flush=True)
    if failed_steps:
        print(f"[NIGHTLY_REPAIR] done with failures: {','.join(failed_steps)}", flush=True)
        return 1
    print("[NIGHTLY_REPAIR] all done", flush=True)
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Repair missing A-share market data at night")
    parser.add_argument("--expected-date", required=True, help="期望最新交易日 yyyy-MM-dd")
    parser.add_argument("--start", default="20240101", help="日线补齐开始日期 yyyyMMdd")
    parser.add_argument("--bars-batch", type=int, default=80, help="每轮日线代码数")
    parser.add_argument("--bars-rounds", type=int, default=10, help="每晚日线补齐轮数")
    parser.add_argument("--profile-limit", type=int, default=300, help="每晚公司资料上限")
    parser.add_argument("--fundamental-limit", type=int, default=60, help="每晚财务股票上限")
    parser.add_argument("--stale-days", type=int, default=90, help="公司资料过期天数")
    args = parser.parse_args()

    steps = build_steps(
        expected_date=args.expected_date,
        start=args.start,
        bars_batch=args.bars_batch,
        bars_rounds=args.bars_rounds,
        profile_limit=args.profile_limit,
        fundamental_limit=args.fundamental_limit,
        stale_days=args.stale_days,
    )
    return run_steps(steps)


if __name__ == "__main__":
    raise SystemExit(main())
