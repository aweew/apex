#!/usr/bin/env python3
"""
主流市场指数日线同步 → index_bar（含历史回补）

A股：上证/深成/创业板/北证50/科创50（新浪）
港股：恒生/恒生科技（新浪港股指数）
日韩：日经225/首尔综合（新浪环球）
美国：道琼斯/纳斯达克/标普500（新浪美股指数）

示例：
  python sync_index.py --start 20180101
  python sync_index.py --codes CN_SH,US_DJI --start 20200101
  python sync_index.py --regions CN,US --start 20180101
"""

from __future__ import annotations

import argparse
import os
import sys
import time
from datetime import date, datetime
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any, Callable, Dict, List, Optional, Sequence, Tuple

import pymysql
from pymysql.cursors import DictCursor

try:
    from dotenv import load_dotenv
except ImportError:  # pragma: no cover
    load_dotenv = None

ROOT = Path(__file__).resolve().parent

# code -> meta
INDEX_META: Dict[str, Dict[str, Any]] = {
    "CN_SH": {"name": "上证指数", "region": "CN", "fetcher": "zh", "symbol": "sh000001"},
    "CN_SZ": {"name": "深证成指", "region": "CN", "fetcher": "zh", "symbol": "sz399001"},
    "CN_CYB": {"name": "创业板指", "region": "CN", "fetcher": "zh", "symbol": "sz399006"},
    "CN_BJ50": {"name": "北证50", "region": "CN", "fetcher": "zh", "symbol": "bj899050"},
    "CN_KC50": {"name": "科创50", "region": "CN", "fetcher": "zh", "symbol": "sh000688"},
    "CN_HS300": {"name": "沪深300", "region": "CN", "fetcher": "zh", "symbol": "sh000300"},
    "HK_HSI": {"name": "恒生指数", "region": "HK", "fetcher": "hk", "symbol": "HSI"},
    "HK_HSTECH": {"name": "恒生科技", "region": "HK", "fetcher": "hk", "symbol": "HSTECH"},
    "JP_N225": {"name": "日经225", "region": "JP", "fetcher": "global", "symbol": "日经225指数"},
    "KR_KOSPI": {"name": "韩国综指", "region": "KR", "fetcher": "global", "symbol": "首尔综合指数"},
    "US_DJI": {"name": "道琼斯", "region": "US", "fetcher": "us", "symbol": ".DJI"},
    "US_IXIC": {"name": "纳斯达克", "region": "US", "fetcher": "us", "symbol": ".IXIC"},
    "US_SPX": {"name": "标普500", "region": "US", "fetcher": "us", "symbol": ".INX"},
}


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
        autocommit=False,
        cursorclass=DictCursor,
    )


def _num(v) -> Optional[Decimal]:
    if v is None:
        return None
    try:
        text = str(v).strip().replace(",", "")
        if text in ("", "nan", "None", "-", "--"):
            return None
        return Decimal(text)
    except (InvalidOperation, ValueError):
        return None


def _date(v) -> Optional[date]:
    if v is None:
        return None
    if isinstance(v, datetime):
        return v.date()
    if isinstance(v, date):
        return v
    text = str(v).strip()[:10].replace("/", "-")
    try:
        return datetime.strptime(text, "%Y-%m-%d").date()
    except ValueError:
        return None


def fetch_zh(symbol: str, start: date):
    import akshare as ak

    # 优先东财日线（通常含成交额），供看板三市量能 MA；失败再回退新浪
    df = None
    try:
        df = ak.stock_zh_index_daily_em(symbol=symbol)
    except Exception as ex:
        print(f"eastmoney index daily miss {symbol}: {ex}", file=sys.stderr)
    if df is None or getattr(df, "empty", True):
        df = ak.stock_zh_index_daily(symbol=symbol)
    return normalize_df(df, start)


def fetch_hk(symbol: str, start: date):
    import akshare as ak

    df = ak.stock_hk_index_daily_sina(symbol=symbol)
    return normalize_df(df, start)


def fetch_global(symbol: str, start: date):
    import akshare as ak

    df = ak.index_global_hist_sina(symbol=symbol)
    return normalize_df(df, start)


def fetch_us(symbol: str, start: date):
    import akshare as ak

    df = ak.index_us_stock_sina(symbol=symbol)
    return normalize_df(df, start)


FETCHERS: Dict[str, Callable[[str, date], List[Dict[str, Any]]]] = {
    "zh": fetch_zh,
    "hk": fetch_hk,
    "global": fetch_global,
    "us": fetch_us,
}


def normalize_df(df, start: date) -> List[Dict[str, Any]]:
    if df is None or df.empty:
        return []
    rename = {
        "日期": "date",
        "date": "date",
        "开盘": "open",
        "open": "open",
        "最高": "high",
        "high": "high",
        "最低": "low",
        "low": "low",
        "收盘": "close",
        "close": "close",
        "成交量": "volume",
        "volume": "volume",
        "成交额": "amount",
        "amount": "amount",
    }
    data = df.rename(columns=rename)
    rows: List[Dict[str, Any]] = []
    for _, r in data.iterrows():
        trade_date = _date(r.get("date"))
        if trade_date is None or trade_date < start:
            continue
        close_v = _num(r.get("close"))
        if close_v is None:
            continue
        rows.append(
            {
                "trade_date": trade_date,
                "open": _num(r.get("open")),
                "high": _num(r.get("high")),
                "low": _num(r.get("low")),
                "close": close_v,
                "volume": _num(r.get("volume")),
                "amount": _num(r.get("amount")),
            }
        )
    rows.sort(key=lambda x: x["trade_date"])
    # 计算涨跌幅
    prev = None
    for row in rows:
        if prev is not None and prev != 0:
            row["pct_chg"] = ((row["close"] - prev) / prev * Decimal("100")).quantize(Decimal("0.0001"))
        else:
            row["pct_chg"] = None
        prev = row["close"]
    return rows


UPSERT_SQL = """
INSERT INTO index_bar (
  code, name, region, trade_date, open_price, high_price, low_price, close_price,
  volume, amount, pct_chg, source, create_time, update_time, deleted
) VALUES (
  %(code)s, %(name)s, %(region)s, %(trade_date)s, %(open)s, %(high)s, %(low)s, %(close)s,
  %(volume)s, %(amount)s, %(pct_chg)s, %(source)s, NOW(), NOW(), 0
)
ON DUPLICATE KEY UPDATE
  name=VALUES(name),
  region=VALUES(region),
  open_price=VALUES(open_price),
  high_price=VALUES(high_price),
  low_price=VALUES(low_price),
  close_price=VALUES(close_price),
  volume=COALESCE(VALUES(volume), volume),
  amount=COALESCE(VALUES(amount), amount),
  pct_chg=COALESCE(VALUES(pct_chg), pct_chg),
  source=VALUES(source),
  update_time=NOW(),
  deleted=0
"""


def upsert(conn, code: str, meta: Dict[str, Any], bars: List[Dict[str, Any]], source: str) -> int:
    if not bars:
        return 0
    payload = []
    for bar in bars:
        payload.append(
            {
                "code": code,
                "name": meta["name"],
                "region": meta["region"],
                "trade_date": bar["trade_date"],
                "open": bar.get("open"),
                "high": bar.get("high"),
                "low": bar.get("low"),
                "close": bar.get("close"),
                "volume": bar.get("volume"),
                "amount": bar.get("amount"),
                "pct_chg": bar.get("pct_chg"),
                "source": source,
            }
        )
    with conn.cursor() as cur:
        cur.executemany(UPSERT_SQL, payload)
    conn.commit()
    return len(payload)


def main() -> int:
    load_env()
    parser = argparse.ArgumentParser(description="同步主流市场指数日线")
    parser.add_argument("--codes", default="", help="逗号分隔内部代码，如 CN_SH,US_DJI")
    parser.add_argument("--regions", default="", help="逗号分隔市场：CN,HK,JP,KR,US")
    parser.add_argument("--start", default="20180101", help="起始日期 yyyyMMdd")
    parser.add_argument("--sleep", type=float, default=0.25)
    args = parser.parse_args()

    start = datetime.strptime(args.start, "%Y%m%d").date()
    codes = [c.strip().upper() for c in args.codes.split(",") if c.strip()]
    regions = {r.strip().upper() for r in args.regions.split(",") if r.strip()}
    selected = []
    for code, meta in INDEX_META.items():
        if codes and code not in codes:
            continue
        if regions and meta["region"] not in regions:
            continue
        selected.append((code, meta))
    if not selected:
        print("无匹配指数", file=sys.stderr)
        return 2

    conn = db_conn()
    ok = fail = total = 0
    try:
        print(f"待同步 {len(selected)} 个指数，start={start}")
        for i, (code, meta) in enumerate(selected, 1):
            fetcher = FETCHERS[meta["fetcher"]]
            try:
                bars = fetcher(meta["symbol"], start)
                n = upsert(conn, code, meta, bars, f"akshare-{meta['fetcher']}")
                total += n
                ok += 1
                last = bars[-1]["trade_date"] if bars else None
                print(f"[{i}/{len(selected)}] {code} {meta['name']} bars={n} last={last}")
            except Exception as ex:  # noqa: BLE001
                fail += 1
                print(f"[{i}/{len(selected)}] {code} FAIL {ex}", file=sys.stderr)
            time.sleep(max(args.sleep, 0))
        print(f"done ok={ok} fail={fail} upsert={total}")
        return 0 if fail == 0 else 1
    finally:
        conn.close()


if __name__ == "__main__":
    raise SystemExit(main())
