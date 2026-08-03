#!/usr/bin/env python3
"""
东财涨停池 → Apex MySQL（连板天梯 / 涨停复盘）

示例：
  python sync_limit_up.py
  python sync_limit_up.py --date 20260802
  python sync_limit_up.py --date 20260802 --with-prev
"""

from __future__ import annotations

import argparse
import os
import re
import sys
import time
from datetime import date, datetime, timedelta
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any, Dict, List, Optional

import pymysql
from pymysql.cursors import DictCursor

try:
    from dotenv import load_dotenv
except ImportError:  # pragma: no cover
    load_dotenv = None

ROOT = Path(__file__).resolve().parent

EM_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    ),
    "Referer": "https://quote.eastmoney.com/ztb/detail",
    "Accept": "*/*",
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


def parse_number(val: Any) -> Optional[Decimal]:
    if val is None or isinstance(val, bool):
        return None
    if isinstance(val, (int, float, Decimal)):
        try:
            return Decimal(str(val))
        except (InvalidOperation, ValueError):
            return None
    text = str(val).strip().replace(",", "").replace("%", "")
    if not text or text in {"-", "--", "None", "nan"}:
        return None
    try:
        return Decimal(text)
    except (InvalidOperation, ValueError):
        return None


def normalize_code(raw: Any) -> Optional[str]:
    text = str(raw or "").strip().upper()
    if not text:
        return None
    m = re.match(r"^(SH|SZ|BJ)?(\d{6})$", text.replace(".", ""))
    if not m:
        return None
    return m.group(2)


def parse_day(text: Optional[str]) -> date:
    if not text:
        return date.today()
    s = text.strip().replace("-", "")
    if len(s) != 8:
        raise ValueError(f"日期格式错误: {text}")
    return date(int(s[:4]), int(s[4:6]), int(s[6:8]))


def fmt_seal_time(raw: Any) -> Optional[str]:
    """092500 / 92500 -> 09:25:00 存原始规范化 HHMMSS。"""
    if raw is None:
        return None
    text = str(raw).strip()
    if not text or text in {"-", "None"}:
        return None
    digits = re.sub(r"\D", "", text)
    if not digits:
        return None
    digits = digits.zfill(6)[-6:]
    return digits


# 东财涨停池主机：push2ex 常超时，备选同路径
ZT_POOL_HOSTS = (
    "https://push2ex.eastmoney.com/getTopicZTPool",
    "https://push2delay.eastmoney.com/getTopicZTPool",
    "https://82.push2.eastmoney.com/getTopicZTPool",
)


def _http_get_json(session, url: str, params: Dict[str, Any], timeout: float, retries: int) -> Dict[str, Any]:
    """带退避重试的 GET JSON；换主机由上层循环。"""
    import requests

    last_err: Optional[Exception] = None
    for attempt in range(1, max(retries, 1) + 1):
        try:
            r = session.get(url, params=params, timeout=timeout)
            r.raise_for_status()
            return r.json() or {}
        except (requests.RequestException, ValueError) as ex:
            last_err = ex
            wait = min(2 ** attempt, 12)
            print(f"  retry {attempt}/{retries} host={url.split('/')[2]} wait={wait}s err={ex}", flush=True)
            time.sleep(wait)
    assert last_err is not None
    raise last_err


def fetch_zt_pool(trade_date: date, timeout: float = 45, retries: int = 4) -> List[Dict[str, Any]]:
    import requests

    day = trade_date.strftime("%Y%m%d")
    rows: List[Dict[str, Any]] = []
    page = 0
    session = requests.Session()
    session.headers.update(EM_HEADERS)
    host_idx = 0
    while True:
        params = {
            "ut": "7eea3edcaed734bea9cbfc24409ed989",
            "dpt": "wz.ztzt",
            "Pageindex": page,
            "pagesize": 200,
            "sort": "fbt:asc",
            "date": day,
        }
        data: Dict[str, Any] = {}
        last_err: Optional[Exception] = None
        # 同一页可轮换主机，避免单点超时拖死整包
        for offset in range(len(ZT_POOL_HOSTS)):
            url = ZT_POOL_HOSTS[(host_idx + offset) % len(ZT_POOL_HOSTS)]
            try:
                payload = _http_get_json(session, url, params, timeout=timeout, retries=retries)
                data = payload.get("data") or {}
                host_idx = (host_idx + offset) % len(ZT_POOL_HOSTS)
                print(f"  page={page} ok host={url.split('/')[2]}", flush=True)
                last_err = None
                break
            except Exception as ex:  # noqa: BLE001
                last_err = ex
                continue
        if last_err is not None:
            raise last_err
        pool = data.get("pool") or []
        if not pool:
            break
        for item in pool:
            code = normalize_code(item.get("c"))
            if not code:
                continue
            try:
                lbc = max(int(item.get("lbc") or 1), 1)
            except (TypeError, ValueError):
                lbc = 1
            zttj = item.get("zttj") or {}
            zt_stats = None
            if isinstance(zttj, dict) and zttj.get("days") is not None:
                zt_stats = f"{zttj.get('days')}/{zttj.get('ct')}"
            industry = str(item.get("hybk") or "").strip() or None
            # 价格字段东财常 *1000
            price_raw = parse_number(item.get("p"))
            latest_price = (price_raw / Decimal(1000)) if price_raw is not None else None
            rows.append({
                "code": code,
                "name": str(item.get("n") or "").strip() or None,
                "pct_chg": parse_number(item.get("zdp")),
                "latest_price": latest_price,
                "amount": parse_number(item.get("amount")),
                "circ_mv": parse_number(item.get("ltsz")),
                "turnover_rate": parse_number(item.get("hs")),
                "lianban": lbc,
                "first_seal_time": fmt_seal_time(item.get("fbt")),
                "last_seal_time": fmt_seal_time(item.get("lbt")),
                "break_count": int(item.get("zbc") or 0),
                "seal_amount": parse_number(item.get("fund")),
                "industry": industry,
                "theme": industry,
                "zt_stats": zt_stats,
            })
        total = int(data.get("tc") or 0)
        if (page + 1) * 200 >= total or len(pool) < 200:
            break
        page += 1
        time.sleep(0.35)
    return rows


UPSERT_SQL = """
INSERT INTO limit_up_pool (
  trade_date, code, name, pct_chg, latest_price, amount, circ_mv, turnover_rate,
  lianban, first_seal_time, last_seal_time, break_count, seal_amount,
  industry, theme, zt_stats, source, synced_at, create_time, update_time, deleted
) VALUES (
  %s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,NOW(),NOW(),0
)
ON DUPLICATE KEY UPDATE
  name=VALUES(name), pct_chg=VALUES(pct_chg), latest_price=VALUES(latest_price),
  amount=VALUES(amount), circ_mv=VALUES(circ_mv), turnover_rate=VALUES(turnover_rate),
  lianban=VALUES(lianban), first_seal_time=VALUES(first_seal_time),
  last_seal_time=VALUES(last_seal_time), break_count=VALUES(break_count),
  seal_amount=VALUES(seal_amount), industry=VALUES(industry), theme=VALUES(theme),
  zt_stats=VALUES(zt_stats), source=VALUES(source), synced_at=VALUES(synced_at),
  update_time=NOW(), deleted=0
"""


def upsert_day(conn, trade_date: date, rows: List[Dict[str, Any]]) -> int:
    synced_at = datetime.now()
    with conn.cursor() as cur:
        # 当日先软删再写，避免已掉出涨停池的残留
        cur.execute(
            "UPDATE limit_up_pool SET deleted=1, update_time=NOW() "
            "WHERE trade_date=%s AND deleted=0",
            (trade_date,),
        )
        if not rows:
            return 0
        params = [
            (
                trade_date,
                r["code"],
                r.get("name"),
                r.get("pct_chg"),
                r.get("latest_price"),
                r.get("amount"),
                r.get("circ_mv"),
                r.get("turnover_rate"),
                r.get("lianban"),
                r.get("first_seal_time"),
                r.get("last_seal_time"),
                r.get("break_count"),
                r.get("seal_amount"),
                r.get("industry"),
                r.get("theme"),
                r.get("zt_stats"),
                "eastmoney-zt",
                synced_at,
            )
            for r in rows
        ]
        cur.executemany(UPSERT_SQL, params)
    return len(rows)


def sync_one(conn, trade_date: date, timeout: float = 45, retries: int = 4) -> int:
    print(f"拉取涨停池 {trade_date} ...", flush=True)
    rows = fetch_zt_pool(trade_date, timeout=timeout, retries=retries)
    n = upsert_day(conn, trade_date, rows)
    conn.commit()
    print(f"{trade_date} upsert={n}", flush=True)
    return n


def main() -> int:
    load_env()
    parser = argparse.ArgumentParser(description="同步涨停池（连板天梯）")
    parser.add_argument("--date", default="", help="交易日 YYYYMMDD / YYYY-MM-DD，默认今天")
    parser.add_argument("--with-prev", action="store_true", help="同时同步前一自然日（用于晋级率）")
    parser.add_argument("--timeout", type=float, default=45, help="单次请求读超时秒，默认 45")
    parser.add_argument("--retries", type=int, default=4, help="单主机重试次数，默认 4")
    args = parser.parse_args()

    trade_date = parse_day(args.date or None)
    conn = db_conn()
    try:
        total = sync_one(conn, trade_date, timeout=args.timeout, retries=args.retries)
        if args.with_prev:
            # 向前最多回看 10 个自然日找有数据的前一日；先落库昨天方便晋级率
            prev = trade_date - timedelta(days=1)
            for _ in range(10):
                try:
                    total += sync_one(conn, prev, timeout=args.timeout, retries=args.retries)
                    break
                except Exception as ex:  # noqa: BLE001
                    print(f"前一日 {prev} 失败: {ex}", file=sys.stderr, flush=True)
                    prev -= timedelta(days=1)
                    time.sleep(1.5)
        print(f"done total={total}", flush=True)
        return 0
    except Exception as ex:  # noqa: BLE001
        conn.rollback()
        print(f"FAIL {ex}", file=sys.stderr, flush=True)
        return 1
    finally:
        conn.close()


if __name__ == "__main__":
    raise SystemExit(main())
