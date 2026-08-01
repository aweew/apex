#!/usr/bin/env python3
"""
持续补齐尚无/过少日线的股票（从 MySQL 查询缺口，分批调用 sync_a_share）。

示例：
  python sync_missing_bars.py --batch 80 --rounds 5 --start 20240101 --sleep 0.18
"""

from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

import pymysql
from pymysql.cursors import DictCursor

try:
    from dotenv import load_dotenv
except ImportError:  # pragma: no cover
    load_dotenv = None

import os

ROOT = Path(__file__).resolve().parent


def load_env() -> None:
    if load_dotenv is not None:
        load_dotenv(ROOT / ".env")
        load_dotenv(ROOT / ".env.example", override=False)


def db_conn():
    return pymysql.connect(
        host=os.getenv("MYSQL_HOST", "127.0.0.1"),
        port=int(os.getenv("MYSQL_PORT", "3306")),
        user=os.getenv("MYSQL_USER", "root"),
        password=os.getenv("MYSQL_PASSWORD", "apex123"),
        database=os.getenv("MYSQL_DB", "apex"),
        charset="utf8mb4",
        cursorclass=DictCursor,
    )


def fetch_missing(conn, batch: int, min_bars: int) -> list[str]:
    sql = """
    SELECT s.code
    FROM stock_basic s
    LEFT JOIN (
      SELECT code, COUNT(*) c FROM bar_daily WHERE deleted = 0 GROUP BY code
    ) b ON b.code = s.code
    WHERE s.deleted = 0 AND (b.c IS NULL OR b.c < %s)
    ORDER BY s.code
    LIMIT %s
    """
    with conn.cursor() as cur:
        cur.execute(sql, (min_bars, batch))
        return [r["code"] for r in cur.fetchall()]


def main() -> int:
    load_env()
    p = argparse.ArgumentParser(description="分批补齐缺失日线")
    p.add_argument("--batch", type=int, default=80, help="每轮代码数")
    p.add_argument("--rounds", type=int, default=3, help="最多轮数；0=直到没有缺口")
    p.add_argument("--start", default="20240101")
    p.add_argument("--sleep", type=float, default=0.18)
    p.add_argument("--min-bars", type=int, default=30, help="少于此根视为缺口")
    args = p.parse_args()

    script = ROOT / "sync_a_share.py"
    rounds = max(0, int(args.rounds))
    done_rounds = 0
    while True:
        if rounds and done_rounds >= rounds:
            break
        conn = db_conn()
        try:
            codes = fetch_missing(conn, max(1, int(args.batch)), max(1, int(args.min_bars)))
        finally:
            conn.close()
        if not codes:
            print("无缺口，结束")
            return 0
        done_rounds += 1
        joined = ",".join(codes)
        print(f"==== round {done_rounds} codes={len(codes)} first={codes[0]} last={codes[-1]} ====")
        cmd = [
            sys.executable,
            "-u",
            str(script),
            "--mode",
            "bars",
            "--codes",
            joined,
            "--start",
            args.start,
            "--sleep",
            str(args.sleep),
            "--no-resume",
        ]
        rc = subprocess.call(cmd, cwd=str(ROOT))
        if rc != 0:
            print(f"round {done_rounds} 退出码 {rc}", file=sys.stderr)
            return rc
    print(f"完成 rounds={done_rounds}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
