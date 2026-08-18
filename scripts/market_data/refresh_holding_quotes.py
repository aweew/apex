#!/usr/bin/env python3
"""临时：把 my_holding 缺行情的代码刷进 stock_basic.latest_price"""

from __future__ import annotations

import os
import time
import urllib.request
from decimal import Decimal
from pathlib import Path

import pymysql
from pymysql.cursors import DictCursor

try:
    from dotenv import load_dotenv
except ImportError:
    load_dotenv = None

ROOT = Path(__file__).resolve().parent


def db():
    if load_dotenv:
        load_dotenv(ROOT / ".env")
    return pymysql.connect(
        host=os.getenv("MYSQL_HOST", "127.0.0.1"),
        port=int(os.getenv("MYSQL_PORT", "3306")),
        user=os.getenv("MYSQL_USER", "root"),
        password=os.getenv("MYSQL_PASSWORD", "apex123"),
        database=os.getenv("MYSQL_DB", "apex"),
        charset="utf8mb4",
        cursorclass=DictCursor,
        autocommit=True,
    )


def sina_symbol(code: str) -> str:
    if len(code) <= 5:
        return "hk" + code.zfill(5)
    if code.startswith(("6", "9")):
        return "sh" + code
    if code.startswith(("4", "8")):
        return "bj" + code
    return "sz" + code


def fetch_quote(code: str):
    symbol = sina_symbol(code)
    req = urllib.request.Request(
        "https://hq.sinajs.cn/list=" + symbol,
        headers={"Referer": "https://finance.sina.com.cn", "User-Agent": "Mozilla/5.0"},
    )
    with urllib.request.urlopen(req, timeout=12) as resp:
        body = resp.read().decode("gbk", "ignore")
    start = body.find('"')
    end = body.rfind('"')
    if start < 0 or end <= start:
        raise RuntimeError("bad body")
    parts = body[start + 1 : end].split(",")
    if len(code) <= 5:
        if len(parts) < 9 or not parts[0]:
            raise RuntimeError("empty hk")
        name = parts[1] or parts[0]
        price = Decimal(parts[6])
        pct = Decimal(parts[8]) if parts[8] else None
        market = "HK"
    else:
        if len(parts) < 4 or not parts[0]:
            raise RuntimeError("empty")
        name = parts[0]
        price = Decimal(parts[3])
        prev = Decimal(parts[2]) if parts[2] else None
        pct = None
        if prev and prev != 0:
            pct = ((price - prev) * 100 / prev).quantize(Decimal("0.0001"))
        market = "SH" if code.startswith(("6", "9")) else ("BJ" if code.startswith(("4", "8")) else "SZ")
    return name, price, pct, market


def main():
    conn = db()
    with conn.cursor() as cur:
        cur.execute("SELECT code, name FROM my_holding WHERE deleted = 0 ORDER BY code")
        rows = cur.fetchall()
    ok = fail = 0
    for row in rows:
        code = row["code"]
        try:
            name, price, pct, market = fetch_quote(code)
            with conn.cursor() as cur:
                cur.execute(
                    """
                    INSERT INTO stock_basic
                      (code, name, market, latest_price, pct_chg, source, quote_time, create_time, update_time, deleted)
                    VALUES (%s,%s,%s,%s,%s,'sina',NOW(),NOW(),NOW(),0)
                    ON DUPLICATE KEY UPDATE
                      name=VALUES(name),
                      market=VALUES(market),
                      latest_price=VALUES(latest_price),
                      pct_chg=VALUES(pct_chg),
                      source=VALUES(source),
                      quote_time=NOW(),
                      update_time=NOW(),
                      deleted=0
                    """,
                    (code, name or row["name"], market, str(price), str(pct) if pct is not None else None),
                )
            print(f"成功，证券代码={code}，名称={name}，价格={price}")
            ok += 1
        except Exception as ex:
            print(f"失败，证券代码={code}，异常={ex}")
            fail += 1
        time.sleep(0.15)
    print(f"完成，成功数={ok}，失败数={fail}")
    conn.close()


if __name__ == "__main__":
    main()
