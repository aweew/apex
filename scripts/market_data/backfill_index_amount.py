# -*- coding: utf-8 -*-
"""回填 index_bar.amount（沪/深/北近 N 日），供看板放缩量 MA。"""
from __future__ import annotations

import os
import subprocess
import json
import re
from datetime import date
from decimal import Decimal
from pathlib import Path

import pymysql
from pymysql.cursors import DictCursor

try:
    from dotenv import load_dotenv
except ImportError:
    load_dotenv = None

ROOT = Path(__file__).resolve().parent
if load_dotenv:
    load_dotenv(ROOT / ".env")
    load_dotenv(ROOT / ".env.example", override=False)

CODE_MAP = {
    "sh000001": "CN_SH",
    "sz399001": "CN_SZ",
    "bj899050": "CN_BJ50",
}


def db():
    return pymysql.connect(
        host=os.getenv("MYSQL_HOST", "127.0.0.1"),
        port=int(os.getenv("MYSQL_PORT", "3306")),
        user=os.getenv("MYSQL_USER", "root"),
        password=os.getenv("MYSQL_PASSWORD", "apex123"),
        database=os.getenv("MYSQL_DB", "apex"),
        charset="utf8mb4",
        autocommit=False,
        cursorclass=DictCursor,
    )


def fetch_via_westock(limit: int = 12) -> list[dict]:
    syms = ",".join(CODE_MAP.keys())
    cmd = [
        "npx", "-y", "westock-data-skillhub@1.0.5",
        "kline", syms, "--period", "day", "--limit", str(limit),
    ]
    out = subprocess.check_output(cmd, cwd=str(ROOT.parent), text=True, encoding="utf-8", errors="replace")
    rows = []
    # parse markdown table lines
    for line in out.splitlines():
        if not line.startswith("|"):
            continue
        parts = [p.strip() for p in line.strip("|").split("|")]
        if len(parts) < 8 or parts[0] in ("symbol", "---"):
            continue
        if parts[0] not in CODE_MAP:
            continue
        try:
            trade_date = date.fromisoformat(parts[1])
            amount = Decimal(parts[7].replace(",", ""))
        except Exception:
            continue
        if amount <= 0:
            continue
        rows.append({
            "code": CODE_MAP[parts[0]],
            "trade_date": trade_date,
            "amount": amount,
        })
    return rows


def main():
    rows = fetch_via_westock(12)
    if not rows:
        raise SystemExit("no rows from westock")
    conn = db()
    updated = 0
    try:
        with conn.cursor() as cur:
            for r in rows:
                cur.execute(
                    """
                    UPDATE index_bar
                    SET amount = %s, update_time = NOW()
                    WHERE code = %s AND trade_date = %s AND deleted = 0
                      AND (amount IS NULL OR amount = 0 OR amount <> %s)
                    """,
                    (r["amount"], r["code"], r["trade_date"], r["amount"]),
                )
                updated += cur.rowcount
        conn.commit()
    finally:
        conn.close()
    print(f"指数成交额回填完成，数据源行数={len(rows)}，更新数={updated}")


if __name__ == "__main__":
    main()
