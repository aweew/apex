#!/usr/bin/env python3
"""
持续补齐尚无/过少日线的股票（从 MySQL 查询缺口，分批调用 sync_a_share）。

示例：
  python sync_missing_bars.py --batch 80 --rounds 0 --max-minutes 150 --start 20240101 --sleep 0.18
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import time
from datetime import date, datetime
from pathlib import Path
from typing import Optional

import pymysql
from pymysql.cursors import DictCursor

try:
    from dotenv import load_dotenv
except ImportError:  # pragma: no cover
    load_dotenv = None

ROOT = Path(__file__).resolve().parent
PROGRESS_DIR = Path(os.getenv("APEX_SYNC_PROGRESS_DIR", str(ROOT / ".progress")))
PROGRESS_PATH = PROGRESS_DIR / "missing_bars.json"


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


def load_cursor() -> str:
    if not PROGRESS_PATH.is_file():
        return ""
    try:
        progress = json.loads(PROGRESS_PATH.read_text(encoding="utf-8"))
        return str(progress.get("cursor") or "").strip()
    except (OSError, ValueError, TypeError):
        return ""


def save_cursor(code: str) -> None:
    PROGRESS_PATH.parent.mkdir(parents=True, exist_ok=True)
    temporary_path = PROGRESS_PATH.with_suffix(".tmp")
    progress = {
        "cursor": code,
        "updated_at": datetime.now().isoformat(timespec="seconds"),
    }
    temporary_path.write_text(
        json.dumps(progress, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    temporary_path.replace(PROGRESS_PATH)


def fetch_missing(
    conn,
    batch: int,
    min_bars: int,
    expected_date: Optional[date] = None,
    after_code: Optional[str] = None,
) -> list[str]:
    conditions = ["t2.bar_count IS NULL OR t2.bar_count < %s"]
    params = [min_bars]
    if expected_date is not None:
        conditions.append("t2.latest_trade_date < %s")
        params.append(expected_date)
    cursor_sql = ""
    if after_code:
        cursor_sql = " AND t1.code > %s"
        params.append(after_code)
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
      {cursor_sql}
    ORDER BY t1.code
    LIMIT %s
    """
    with conn.cursor() as cur:
        cur.execute(sql, tuple(params))
        return [r["code"] for r in cur.fetchall()]


def count_missing(
    conn,
    min_bars: int,
    expected_date: Optional[date] = None,
) -> int:
    conditions = ["t2.bar_count IS NULL OR t2.bar_count < %s"]
    params = [min_bars]
    if expected_date is not None:
        conditions.append("t2.latest_trade_date < %s")
        params.append(expected_date)
    sql = f"""
    SELECT COUNT(*) AS missing_count
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
    """
    with conn.cursor() as cur:
        cur.execute(sql, tuple(params))
        row = cur.fetchone()
        return int(row["missing_count"]) if row else 0


def main() -> int:
    load_env()
    p = argparse.ArgumentParser(description="分批补齐缺失日线")
    p.add_argument("--batch", type=int, default=80, help="每轮代码数")
    p.add_argument("--rounds", type=int, default=3, help="最多轮数；0=直到没有缺口")
    p.add_argument("--max-minutes", type=float, default=0, help="最长运行分钟数；0=不限制")
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
    max_minutes = max(0, float(args.max_minutes))
    started_at = time.monotonic()
    deadline = started_at + max_minutes * 60 if max_minutes else None
    done_rounds = 0
    failed_rounds = []
    cursor = load_cursor()
    stop_reason = ""
    while True:
        if rounds and done_rounds >= rounds:
            stop_reason = "已达轮数上限"
            break
        if deadline is not None and time.monotonic() >= deadline:
            stop_reason = "已达时间预算"
            break
        conn = db_conn()
        try:
            codes = fetch_missing(
                conn,
                max(1, int(args.batch)),
                max(1, int(args.min_bars)),
                expected_date,
                after_code=cursor or None,
            )
            if not codes and cursor:
                codes = fetch_missing(
                    conn,
                    max(1, int(args.batch)),
                    max(1, int(args.min_bars)),
                    expected_date,
                    after_code=None,
                )
        finally:
            conn.close()
        if not codes:
            stop_reason = "已无数据缺口"
            break
        done_rounds += 1
        joined = ",".join(codes)
        print(f"==== 轮次 {done_rounds}，证券数={len(codes)}，首个代码={codes[0]}，末个代码={codes[-1]} ====")
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
            timeout_seconds = None
            if deadline is not None:
                remaining_seconds = deadline - time.monotonic()
                if remaining_seconds <= 0:
                    rc = 124
                    stop_reason = "已达时间预算"
                    break
                timeout_seconds = max(1, int(remaining_seconds))
            print(f"[轮次 {done_rounds}] 执行尝试={attempt}/{attempts}")
            sys.stdout.flush()
            try:
                rc = subprocess.call(cmd, cwd=str(ROOT), timeout=timeout_seconds)
            except subprocess.TimeoutExpired:
                rc = 124
                stop_reason = "已达时间预算"
            if rc == 0:
                break
            print(f"轮次 {done_rounds}，尝试次数={attempt}，退出码={rc}", file=sys.stderr)
            sys.stderr.flush()
            # 非瞬时错误（脚本业务失败）不再重试
            if rc > 0:
                break
        cursor = codes[-1]
        save_cursor(cursor)
        if rc != 0:
            print(f"轮次 {done_rounds} 最终失败，退出码={rc}", file=sys.stderr)
            failed_rounds.append(done_rounds)
        if stop_reason == "已达时间预算":
            break

    conn = db_conn()
    try:
        remaining_count = count_missing(conn, max(1, int(args.min_bars)), expected_date)
    finally:
        conn.close()
    print(
        f"完成，原因={stop_reason}，执行轮数={done_rounds}，"
        f"失败轮次={failed_rounds}，剩余缺口={remaining_count}"
    )
    success_rounds = done_rounds - len(failed_rounds)
    if remaining_count > 0:
        print(
            f"未完成，成功数={success_rounds}，失败数={len(failed_rounds)}，"
            f"剩余缺口={remaining_count}"
        )
        return 1
    return 1 if failed_rounds else 0


if __name__ == "__main__":
    raise SystemExit(main())
