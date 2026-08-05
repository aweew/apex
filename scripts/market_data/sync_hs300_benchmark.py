#!/usr/bin/env python3
"""把沪深300写入 bar_daily.code=000300，供相对强弱等基准计算。"""

from __future__ import annotations

import os
import sys
from datetime import datetime
from decimal import Decimal
from pathlib import Path

import pymysql
from pymysql.cursors import DictCursor

try:
    from dotenv import load_dotenv
except ImportError:
    load_dotenv = None

ROOT = Path(__file__).resolve().parent


def main() -> int:
    if load_dotenv is not None:
        load_dotenv(ROOT / ".env")
        load_dotenv(ROOT / ".env.example", override=False)

    import akshare as ak

    df = ak.stock_zh_index_daily(symbol="sh000300")
    if df is None or df.empty:
        print("沪深300 拉取为空", file=sys.stderr)
        return 1
    df = df.tail(900).copy()

    conn = pymysql.connect(
        host=os.getenv("MYSQL_HOST", "127.0.0.1"),
        port=int(os.getenv("MYSQL_PORT", "3306")),
        user=os.getenv("MYSQL_USER", "root"),
        password=os.getenv("MYSQL_PASSWORD", "apex123"),
        database=os.getenv("MYSQL_DB", "apex"),
        charset="utf8mb4",
        autocommit=False,
        cursorclass=DictCursor,
    )
    sql = """
    INSERT INTO bar_daily
      (code, trade_date, open_price, high_price, low_price, close_price,
       volume, amount, pct_chg, turnover_rate, source, create_time, update_time, deleted)
    VALUES
      (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, NOW(), NOW(), 0)
    ON DUPLICATE KEY UPDATE
      open_price = VALUES(open_price),
      high_price = VALUES(high_price),
      low_price = VALUES(low_price),
      close_price = VALUES(close_price),
      volume = VALUES(volume),
      source = VALUES(source),
      update_time = NOW(),
      deleted = 0
    """
    rows = []
    prev_close = None
    for _, r in df.iterrows():
        d = r["date"]
        if hasattr(d, "date"):
            d = d.date()
        elif isinstance(d, str):
            d = datetime.strptime(d[:10], "%Y-%m-%d").date()
        close = Decimal(str(r["close"]))
        open_ = Decimal(str(r["open"]))
        high = Decimal(str(r["high"]))
        low = Decimal(str(r["low"]))
        vol = Decimal(str(r["volume"])) if r.get("volume") is not None else None
        pct = None
        if prev_close and prev_close != 0:
            pct = ((close - prev_close) / prev_close * Decimal("100")).quantize(Decimal("0.0001"))
        prev_close = close
        rows.append(("000300", d, open_, high, low, close, vol, None, pct, None, "akshare-hs300"))

    with conn.cursor() as cur:
        cur.executemany(sql, rows)
    conn.commit()
    conn.close()
    print(f"沪深300 已写入 bar_daily.000300 共 {len(rows)} 条")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
