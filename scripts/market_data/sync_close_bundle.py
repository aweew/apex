#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""收盘同步包：INDEX → SECTOR_QUOTE → LIMIT_UP。"""

from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path


def run_one(script: Path, args: list[str]) -> int:
    cmd = [sys.executable, "-u", str(script), *args]
    print(f"[CLOSE_BUNDLE] run: {' '.join(cmd)}", flush=True)
    proc = subprocess.run(cmd, cwd=str(script.parent))
    print(f"[CLOSE_BUNDLE] exit={proc.returncode} script={script.name}", flush=True)
    return proc.returncode


def main() -> int:
    parser = argparse.ArgumentParser(description="Close market sync bundle")
    parser.add_argument("--start", default="20180101", help="index start yyyyMMdd")
    parser.add_argument("--types", default="INDUSTRY,CONCEPT,THEME")
    parser.add_argument("--date", default="", help="limit-up date yyyyMMdd optional")
    args = parser.parse_args()

    base = Path(__file__).resolve().parent
    steps = [
        (base / "sync_index.py", ["--start", args.start, "--sleep", "0.25"]),
        (base / "sync_sector.py", ["--mode", "quote", "--types", args.types, "--sleep", "0.35"]),
        (
            base / "sync_limit_up.py",
            (["--date", args.date] if args.date else []) + ["--with-prev"],
        ),
    ]
    for script, script_args in steps:
        if not script.is_file():
            print(f"[CLOSE_BUNDLE] missing {script.name}", flush=True)
            return 2
        code = run_one(script, script_args)
        if code != 0:
            return code
    print("[CLOSE_BUNDLE] all done", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
