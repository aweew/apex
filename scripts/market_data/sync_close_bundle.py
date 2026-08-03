#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""收盘一键同步包：指数 → 板块 → 涨停 → 热点 → 资讯。"""

from __future__ import annotations

import argparse
import subprocess
import sys
import time
from datetime import date, timedelta
from pathlib import Path


def run_one(script: Path, args: list[str]) -> int:
    cmd = [sys.executable, "-u", str(script), *args]
    print(f"[CLOSE_BUNDLE] run: {' '.join(cmd)}", flush=True)
    proc = subprocess.run(cmd, cwd=str(script.parent))
    print(f"[CLOSE_BUNDLE] exit={proc.returncode} script={script.name}", flush=True)
    return proc.returncode


def run_with_retries(script: Path, args: list[str], retries: int, pause: float) -> int:
    """失败退避重试；东财偶发断连/超时常见。"""
    attempts = max(retries, 1)
    code = 1
    for i in range(1, attempts + 1):
        code = run_one(script, args)
        if code == 0:
            return 0
        if i < attempts:
            wait = pause * i
            print(
                f"[CLOSE_BUNDLE] step retry {i}/{attempts} script={script.name} sleep={wait:.0f}s",
                flush=True,
            )
            time.sleep(wait)
    return code


def main() -> int:
    parser = argparse.ArgumentParser(description="Close market one-click sync bundle")
    parser.add_argument(
        "--start",
        default="",
        help="index start yyyyMMdd；空则约 60 个自然日前（日常增量）",
    )
    parser.add_argument("--types", default="INDUSTRY,CONCEPT,THEME")
    parser.add_argument("--date", default="", help="limit-up date yyyyMMdd optional")
    parser.add_argument("--hot-sources", default="eastmoney,baidu")
    parser.add_argument("--news-sources", default="eastmoney,cls,ths,sina")
    parser.add_argument("--hot-limit", type=int, default=50)
    parser.add_argument("--news-limit", type=int, default=80)
    parser.add_argument(
        "--step-retries",
        type=int,
        default=3,
        help="单步失败重试次数（含首次），默认 3",
    )
    parser.add_argument(
        "--continue-on-error",
        action="store_true",
        default=True,
        help="某步最终仍失败时继续后续步骤（默认开启），结束时若有失败返回非 0",
    )
    parser.add_argument(
        "--strict",
        action="store_true",
        help="任一步失败立即停止（关闭 continue-on-error）",
    )
    parser.add_argument(
        "--skip",
        default="",
        help="跳过步骤，逗号分隔：index,sector,limit_up,hot,news",
    )
    args = parser.parse_args()
    continue_on_error = bool(args.continue_on_error) and not bool(args.strict)

    start = (args.start or "").strip()
    if not start:
        start = (date.today() - timedelta(days=60)).strftime("%Y%m%d")

    skip = {x.strip().lower() for x in (args.skip or "").split(",") if x.strip()}
    base = Path(__file__).resolve().parent
    steps: list[tuple[str, Path, list[str]]] = [
        (
            "index",
            base / "sync_index.py",
            ["--start", start, "--sleep", "0.25"],
        ),
        (
            "sector",
            base / "sync_sector.py",
            ["--mode", "quote", "--types", args.types, "--sleep", "0.35"],
        ),
        (
            "limit_up",
            base / "sync_limit_up.py",
            (["--date", args.date] if args.date else [])
            + ["--with-prev", "--timeout", "45", "--retries", "4"],
        ),
        (
            "hot",
            base / "sync_hot.py",
            [
                "--sources",
                args.hot_sources,
                "--limit",
                str(max(args.hot_limit, 1)),
            ],
        ),
        (
            "news",
            base / "sync_news.py",
            [
                "--sources",
                args.news_sources,
                "--limit",
                str(max(args.news_limit, 1)),
            ],
        ),
    ]

    total = len([s for s in steps if s[0] not in skip])
    done = 0
    failed: list[str] = []
    for key, script, script_args in steps:
        if key in skip:
            print(f"[CLOSE_BUNDLE] skip {key}", flush=True)
            continue
        if not script.is_file():
            print(f"[CLOSE_BUNDLE] missing {script.name}", flush=True)
            return 2
        done += 1
        print(f"[CLOSE_BUNDLE] step {done}/{total}: {key}", flush=True)
        # 涨停池外网更脆，多给一次重试预算
        retries = args.step_retries + (1 if key == "limit_up" else 0)
        code = run_with_retries(script, script_args, retries=retries, pause=8.0)
        if code != 0:
            print(f"[CLOSE_BUNDLE] failed at {key}", flush=True)
            failed.append(key)
            if not continue_on_error:
                return code
    if failed:
        print(f"[CLOSE_BUNDLE] done with failures: {','.join(failed)}", flush=True)
        return 1
    print("[CLOSE_BUNDLE] all done", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
