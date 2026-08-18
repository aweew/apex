#!/usr/bin/env python3
"""
回补 bar_daily.turnover_rate（换手率%）

优先 BaoStock（本机可直连）；失败再试东财直连 / AKShare。
仅更新换手率为空的交易日，不覆盖已有值。

示例：
  python backfill_turnover.py --codes 600519,300308 --sleep 0.2
  python backfill_turnover.py --limit 50 --sleep 0.2
  python backfill_turnover.py --all --sleep 0.15
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.request
from datetime import date, datetime
from pathlib import Path
from typing import List, Optional, Sequence, Tuple

import pymysql
from pymysql.cursors import DictCursor

try:
    from dotenv import load_dotenv
except ImportError:  # pragma: no cover
    load_dotenv = None

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
        autocommit=False,
    )


def _num(v):
    if v is None:
        return None
    try:
        text = str(v).strip()
        if text in ("", "nan", "None", "-", "null"):
            return None
        return float(text)
    except Exception:
        return None


def resolve_market(code: str) -> str:
    if code.startswith(("5", "6", "9")):
        return "SH"
    if code.startswith(("4", "8")):
        return "BJ"
    return "SZ"


def to_secid(code: str) -> str:
    return f"1.{code}" if resolve_market(code) == "SH" else f"0.{code}"


def to_baostock_code(code: str) -> str:
    market = resolve_market(code).lower()
    return f"{market}.{code}"


def list_codes(conn, codes: Optional[Sequence[str]], limit: int, only_missing: bool) -> List[str]:
    requested_codes = (
        list(dict.fromkeys(c.strip() for c in codes if c and c.strip()))
        if codes
        else []
    )
    sql = """
    SELECT DISTINCT t1.code
    FROM stock_basic t1
    INNER JOIN bar_daily t2
      ON t2.code = t1.code
     AND t2.deleted = 0
    WHERE t1.deleted = 0
    """
    params: List[object] = []
    if only_missing:
        sql += " AND t2.turnover_rate IS NULL"
    if requested_codes:
        placeholders = ",".join(["%s"] * len(requested_codes))
        sql += f" AND t1.code IN ({placeholders})"
        params.extend(requested_codes)
    sql += " ORDER BY t1.code"
    if limit and limit > 0:
        sql += " LIMIT %s"
        params.append(int(limit))
    with conn.cursor() as cur:
        cur.execute(sql, tuple(params))
        selected_codes = [r["code"] for r in cur.fetchall()]
    if not requested_codes:
        return selected_codes
    selected_code_set = set(selected_codes)
    return [code for code in requested_codes if code in selected_code_set]


def date_range(conn, code: str) -> Tuple[Optional[date], Optional[date]]:
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT MIN(trade_date) AS d0, MAX(trade_date) AS d1
            FROM bar_daily
            WHERE deleted = 0 AND code = %s AND turnover_rate IS NULL
            """,
            (code,),
        )
        row = cur.fetchone() or {}
        return row.get("d0"), row.get("d1")


def http_get(url: str, retries: int = 2) -> str:
    last_err: Exception | None = None
    for attempt in range(retries):
        try:
            req = urllib.request.Request(
                url,
                headers={
                    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                    "Referer": "https://quote.eastmoney.com/",
                    "Accept": "*/*",
                },
            )
            with urllib.request.urlopen(req, timeout=30) as resp:
                return resp.read().decode("utf-8", errors="replace")
        except Exception as ex:  # noqa: BLE001
            last_err = ex
            time.sleep(0.5 * (attempt + 1))
    raise RuntimeError(str(last_err) if last_err else "http failed")


def fetch_baostock(code: str, start: str, end: str) -> List[Tuple[date, float]]:
    import baostock as bs

    # start/end 为 yyyyMMdd → yyyy-MM-dd
    start_d = f"{start[:4]}-{start[4:6]}-{start[6:8]}"
    end_d = f"{end[:4]}-{end[4:6]}-{end[6:8]}"
    rs = bs.query_history_k_data_plus(
        to_baostock_code(code),
        "date,turn",
        start_date=start_d,
        end_date=end_d,
        frequency="d",
        adjustflag="2",
    )
    if rs.error_code != "0":
        raise RuntimeError(rs.error_msg or rs.error_code)
    out: List[Tuple[date, float]] = []
    while rs.error_code == "0" and rs.next():
        row = rs.get_row_data()
        if not row or len(row) < 2:
            continue
        rate = _num(row[1])
        if rate is None:
            continue
        out.append((datetime.strptime(row[0][:10], "%Y-%m-%d").date(), rate))
    if not out:
        raise RuntimeError("empty baostock turn")
    return out


def fetch_em_direct(code: str, start: str, end: str) -> List[Tuple[date, float]]:
    url = (
        "https://push2his.eastmoney.com/api/qt/stock/kline/get"
        f"?secid={to_secid(code)}"
        "&fields1=f1,f2,f3,f4,f5,f6"
        "&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61"
        "&klt=101&fqt=1"
        f"&beg={start}&end={end}"
    )
    body = http_get(url)
    root = json.loads(body)
    klines = (root.get("data") or {}).get("klines") or []
    out: List[Tuple[date, float]] = []
    for line in klines:
        parts = str(line).split(",")
        if len(parts) < 11:
            continue
        rate = _num(parts[10])
        if rate is None:
            continue
        out.append((datetime.strptime(parts[0][:10], "%Y-%m-%d").date(), rate))
    if not out:
        raise RuntimeError("empty klines")
    return out


def fetch_em_akshare(code: str, start: str, end: str) -> List[Tuple[date, float]]:
    import akshare as ak

    df = ak.stock_zh_a_hist(
        symbol=code,
        period="daily",
        start_date=start,
        end_date=end,
        adjust="qfq",
    )
    if df is None or df.empty:
        raise RuntimeError("empty hist")
    rename = {"日期": "date", "换手率": "turnover", "date": "date", "turnover": "turnover"}
    data = df.rename(columns=rename)
    if "date" not in data.columns:
        data = data.reset_index()
        data = data.rename(columns={"日期": "date", "index": "date"})
    out: List[Tuple[date, float]] = []
    for _, r in data.iterrows():
        trade_date = r.get("date")
        rate = _num(r.get("turnover"))
        if trade_date is None or rate is None:
            continue
        if hasattr(trade_date, "date"):
            trade_date = trade_date.date() if not isinstance(trade_date, date) else trade_date
        else:
            trade_date = datetime.strptime(str(trade_date)[:10], "%Y-%m-%d").date()
        out.append((trade_date, rate))
    if not out:
        raise RuntimeError("no turnover column")
    return out


def fetch_turnover_rows(code: str, start: str, end: str) -> List[Tuple[date, float]]:
    errors: List[str] = []
    try:
        return fetch_baostock(code, start, end)
    except Exception as ex:  # noqa: BLE001
        errors.append(f"baostock:{ex}")
    try:
        return fetch_em_direct(code, start, end)
    except Exception as ex:  # noqa: BLE001
        errors.append(f"em-direct:{ex}")
    try:
        return fetch_em_akshare(code, start, end)
    except Exception as ex:  # noqa: BLE001
        errors.append(f"akshare:{ex}")
    raise RuntimeError(" | ".join(errors))


def update_turnover(conn, code: str, rows: List[Tuple[date, float]]) -> int:
    if not rows:
        return 0
    sql = """
    UPDATE bar_daily
    SET turnover_rate = %s, update_time = NOW()
    WHERE deleted = 0 AND code = %s AND trade_date = %s
      AND turnover_rate IS NULL
    """
    n = 0
    with conn.cursor() as cur:
        for trade_date, rate in rows:
            cur.execute(sql, (rate, code, trade_date))
            n += cur.rowcount
    conn.commit()
    return n


def main() -> int:
    load_env()
    parser = argparse.ArgumentParser(description="回补日线换手率")
    parser.add_argument("--codes", default="", help="逗号分隔代码，默认扫库内缺换手的股票")
    parser.add_argument("--limit", type=int, default=0, help="最多处理多少只（0=不限）")
    parser.add_argument("--all", action="store_true", help="处理全部缺换手的代码")
    parser.add_argument("--sleep", type=float, default=0.15, help="每只间隔秒")
    parser.add_argument("--include-filled", action="store_true", help="不限定 turnover_rate IS NULL 的代码")
    args = parser.parse_args()

    code_list = [c.strip() for c in args.codes.split(",") if c.strip()] if args.codes else None
    if not code_list and not args.all and args.limit <= 0:
        print("请指定 --codes / --limit / --all", file=sys.stderr)
        return 2

    import baostock as bs

    lg = bs.login()
    if lg.error_code != "0":
        print(f"baostock 登录失败，异常={lg.error_msg}", file=sys.stderr)
        return 1

    conn = db_conn()
    try:
        codes = list_codes(
            conn, code_list, args.limit if not args.all else 0, only_missing=not args.include_filled
        )
        print(f"待处理 {len(codes)} 只")
        ok = 0
        fail = 0
        updated = 0
        for i, code in enumerate(codes, 1):
            d0, d1 = date_range(conn, code)
            if d0 is None or d1 is None:
                print(f"[{i}/{len(codes)}] {code} 跳过（没有缺失区间）")
                ok += 1
                continue
            start = d0.strftime("%Y%m%d")
            end = d1.strftime("%Y%m%d")
            try:
                rows = fetch_turnover_rows(code, start, end)
                n = update_turnover(conn, code, rows)
                updated += n
                ok += 1
                print(f"[{i}/{len(codes)}] {code} {start}-{end} 拉取数={len(rows)}，更新数={n}")
            except Exception as ex:  # noqa: BLE001
                fail += 1
                print(f"[{i}/{len(codes)}] {code} 失败，异常={ex}")
            time.sleep(max(args.sleep, 0))
        print(f"完成，成功数={ok}，失败数={fail}，更新数={updated}")
        return 0 if fail == 0 else 1
    finally:
        conn.close()
        bs.logout()


if __name__ == "__main__":
    raise SystemExit(main())
