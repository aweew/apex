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
from datetime import date, datetime
from pathlib import Path
from typing import Optional, Sequence

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


def fetch_missing(
    conn,
    batch: int,
    min_bars: int,
    expected_date: Optional[date] = None,
    excluded_codes: Optional[Sequence[str]] = None,
) -> list[str]:
    conditions = ["t2.bar_count IS NULL OR t2.bar_count < %s"]
    params = [min_bars]
    if expected_date is not None:
        conditions.append("t2.latest_trade_date < %s")
        params.append(expected_date)
    excluded = [code for code in (excluded_codes or []) if code]
    exclude_sql = ""
    if excluded:
        placeholders = ",".join(["%s"] * len(excluded))
        exclude_sql = f" AND t1.code NOT IN ({placeholders})"
        params.extend(excluded)
    params.append(batch)
    sql = f"""
    SELECT t1.code
    FROM stock_basic t1
    LEFT JOIN (
      SELECT code,
             COUNT(*) AS bar_count,
             MAX(trade_date) AS latest_trade_date
      FROM bar_daily
      WHERE deleted = 0
      GROUP BY code
    ) t2 ON t2.code = t1.code
    WHERE t1.deleted = 0
      AND ({' OR '.join(conditions)})
      {exclude_sql}
    ORDER BY t1.code
    LIMIT %s
    """
    with conn.cursor() as cur:
        cur.execute(sql, tuple(params))
        return [r["code"] for r in cur.fetchall()]


def main() -> int:
    load_env()
    p = argparse.ArgumentParser(description="分批补齐缺失日线")
    p.add_argument("--batch", type=int, default=80, help="每轮代码数")
    p.add_argument("--rounds", type=int, default=3, help="最多轮数；0=直到没有缺口")
    p.add_argument("--start", default="20240101")
    p.add_argument("--sleep", type=float, default=0.18)
    p.add_argument("--min-bars", type=int, default=30, help="少于此根视为缺口")
    p.add_argument("--expected-date", default="", help="期望最新交易日 yyyy-MM-dd")
    args = p.parse_args()

    expected_date = None
    if args.expected_date:
        try:
            expected_date = datetime.strptime(args.expected_date, "%Y-%m-%d").date()
        except ValueError:
            p.error("--expected-date 必须为 yyyy-MM-dd")

    script = ROOT / "sync_a_share.py"
    rounds = max(0, int(args.rounds))
    done_rounds = 0
    attempted_codes = set()
    failed_rounds = []
    while True:
        if rounds and done_rounds >= rounds:
            break
        conn = db_conn()
        try:
            codes = fetch_missing(
                conn,
                max(1, int(args.batch)),
                max(1, int(args.min_bars)),
                expected_date,
                sorted(attempted_codes),
            )
        finally:
            conn.close()
        if not codes:
            print("无未处理缺口，结束")
            return 1 if failed_rounds else 0
        done_rounds += 1
        attempted_codes.update(codes)
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
        if expected_date is not None:
            cmd.extend(["--end", expected_date.strftime("%Y%m%d")])
        # Windows 上偶发 exit=-1（进程被外部打断/管道异常）；对瞬时失败重试一轮
        rc = -1
        attempts = 2
        for attempt in range(1, attempts + 1):
            print(f"[round {done_rounds}] run attempt={attempt}/{attempts}")
            sys.stdout.flush()
            rc = subprocess.call(cmd, cwd=str(ROOT))
            if rc == 0:
                break
            print(f"round {done_rounds} attempt={attempt} 退出码 {rc}", file=sys.stderr)
            sys.stderr.flush()
            # 非瞬时错误（脚本业务失败）不再重试
            if rc > 0:
                break
        if rc != 0:
            print(f"round {done_rounds} 最终失败 exit={rc}", file=sys.stderr)
            failed_rounds.append(done_rounds)
    print(f"完成 rounds={done_rounds} failed_rounds={failed_rounds}")
    return 1 if failed_rounds else 0


if __name__ == "__main__":
    raise SystemExit(main())
