#!/usr/bin/env python3
"""
全 A 股票列表 + 日线历史导入 Apex MySQL（stock_basic / bar_daily）

依赖 AKShare（免费）。支持断点续传、限速、小批量试跑。

示例：
  python sync_a_share.py --mode list
  python sync_a_share.py --mode bars --start 20180101 --sleep 0.4
  python sync_a_share.py --mode all --start 20180101 --limit 5
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
from datetime import date, datetime
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional, Sequence, Tuple

import pymysql
from pymysql.cursors import DictCursor

try:
    from dotenv import load_dotenv
except ImportError:  # pragma: no cover
    load_dotenv = None

ROOT = Path(__file__).resolve().parent
PROGRESS_PATH = ROOT / ".progress" / "bars_progress.json"
SOURCE = "akshare"


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


def resolve_market(code: str) -> str:
    if code.startswith(("5", "6", "9")):
        return "SH"
    if code.startswith(("4", "8")):
        return "BJ"
    return "SZ"


def is_st(name: str) -> int:
    upper = (name or "").upper()
    return 1 if "ST" in upper else 0


def normalize_code(raw: Any) -> Optional[str]:
    text = re.sub(r"\D", "", str(raw or ""))
    if len(text) >= 6:
        return text[-6:]
    return None


def load_progress() -> Dict[str, Any]:
    if not PROGRESS_PATH.exists():
        return {}
    try:
        return json.loads(PROGRESS_PATH.read_text(encoding="utf-8"))
    except Exception:
        return {}


def save_progress(progress: Dict[str, Any]) -> None:
    PROGRESS_PATH.parent.mkdir(parents=True, exist_ok=True)
    tmp = PROGRESS_PATH.with_suffix(".tmp")
    tmp.write_text(json.dumps(progress, ensure_ascii=False, indent=2), encoding="utf-8")
    tmp.replace(PROGRESS_PATH)


def upsert_stock_basic(conn, rows: List[Tuple[str, str, str, int]]) -> int:
    if not rows:
        return 0
    sql = """
    INSERT INTO stock_basic (code, name, market, st_flag, source, create_time, update_time, deleted)
    VALUES (%s, %s, %s, %s, %s, NOW(), NOW(), 0)
    ON DUPLICATE KEY UPDATE
      name = VALUES(name),
      market = VALUES(market),
      st_flag = VALUES(st_flag),
      source = VALUES(source),
      update_time = NOW(),
      deleted = 0
    """
    # source column may not exist on old DB — fallback without source
    with conn.cursor() as cur:
        try:
            cur.executemany(sql, [(c, n, m, st, SOURCE) for c, n, m, st in rows])
        except pymysql.err.OperationalError as ex:
            if "Unknown column 'source'" not in str(ex):
                raise
            sql2 = """
            INSERT INTO stock_basic (code, name, market, st_flag, create_time, update_time, deleted)
            VALUES (%s, %s, %s, %s, NOW(), NOW(), 0)
            ON DUPLICATE KEY UPDATE
              name = VALUES(name),
              market = VALUES(market),
              st_flag = VALUES(st_flag),
              update_time = NOW(),
              deleted = 0
            """
            cur.executemany(sql2, rows)
    conn.commit()
    return len(rows)


def sync_stock_list(conn, limit: Optional[int] = None) -> int:
    import akshare as ak

    print("拉取全 A 代码列表…")
    df = ak.stock_info_a_code_name()
    if df is None or df.empty:
        raise RuntimeError("AKShare 返回空股票列表")

    # columns: code, name
    cols = {c.lower(): c for c in df.columns}
    code_col = cols.get("code") or list(df.columns)[0]
    name_col = cols.get("name") or list(df.columns)[1]

    rows: List[Tuple[str, str, str, int]] = []
    for _, item in df.iterrows():
        code = normalize_code(item[code_col])
        if not code:
            continue
        name = str(item[name_col] or "").strip()
        rows.append((code, name, resolve_market(code), is_st(name)))
        if limit and len(rows) >= limit:
            break

    count = upsert_stock_basic(conn, rows)
    print(f"股票列表已写入/更新 {count} 只")
    return count


def list_codes(
    conn,
    limit: Optional[int] = None,
    codes: Optional[Sequence[str]] = None,
) -> List[str]:
    if codes:
        cleaned: List[str] = []
        seen = set()
        for raw in codes:
            code = normalize_code(raw)
            if code and code not in seen:
                seen.add(code)
                cleaned.append(code)
        if limit:
            return cleaned[: int(limit)]
        return cleaned
    sql = "SELECT code FROM stock_basic WHERE deleted = 0 ORDER BY code"
    if limit:
        sql += f" LIMIT {int(limit)}"
    with conn.cursor() as cur:
        cur.execute(sql)
        return [r["code"] for r in cur.fetchall()]


def max_bar_date(conn, code: str) -> Optional[date]:
    with conn.cursor() as cur:
        cur.execute(
            "SELECT MAX(trade_date) AS d FROM bar_daily WHERE code = %s AND deleted = 0",
            (code,),
        )
        row = cur.fetchone()
        return row["d"] if row and row["d"] else None


def upsert_bars(conn, code: str, bars: List[Tuple]) -> int:
    if not bars:
        return 0
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
      amount = COALESCE(VALUES(amount), amount),
      pct_chg = COALESCE(VALUES(pct_chg), pct_chg),
      turnover_rate = COALESCE(VALUES(turnover_rate), turnover_rate),
      source = VALUES(source),
      update_time = NOW(),
      deleted = 0
    """
    with conn.cursor() as cur:
        cur.executemany(sql, bars)
    conn.commit()
    return len(bars)


def fetch_hist_bars(code: str, start: str, end: str):
    """东财优先（含换手率）；失败再试新浪。"""
    import akshare as ak

    market = resolve_market(code)
    prefix = {"SH": "sh", "SZ": "sz", "BJ": "bj"}.get(market, "sz")
    sina_symbol = f"{prefix}{code}"
    errors: List[str] = []

    try:
        df = ak.stock_zh_a_hist(
            symbol=code,
            period="daily",
            start_date=start,
            end_date=end,
            adjust="qfq",
        )
        if df is not None and not df.empty:
            return df, "akshare-em"
    except Exception as ex:
        errors.append(f"em:{ex}")

    try:
        df = ak.stock_zh_a_daily(
            symbol=sina_symbol,
            start_date=start,
            end_date=end,
            adjust="qfq",
        )
        if df is not None and not df.empty:
            return df, "akshare-sina"
    except Exception as ex:
        errors.append(f"sina:{ex}")

    raise RuntimeError(" | ".join(errors) if errors else "empty history")


def to_bar_rows(code: str, df, source: str = SOURCE) -> List[Tuple]:
    if df is None or df.empty:
        return []
    # 兼容中英文列名（东财 hist / 新浪 daily）
    rename = {
        "日期": "date",
        "date": "date",
        "开盘": "open",
        "open": "open",
        "收盘": "close",
        "close": "close",
        "最高": "high",
        "high": "high",
        "最低": "low",
        "low": "low",
        "成交量": "volume",
        "volume": "volume",
        "成交额": "amount",
        "amount": "amount",
        "涨跌幅": "pct",
        "换手率": "turnover",
        "turnover": "turnover",
        "turnover_rate": "turnover",
    }
    data = df.rename(columns=rename)
    if "date" not in data.columns and data.index.name in ("date", "日期"):
        data = data.reset_index()
        data = data.rename(columns={"日期": "date", "index": "date"})
    if "date" not in data.columns:
        data = data.reset_index()
        for cand in ("date", "日期", "index"):
            if cand in data.columns:
                data = data.rename(columns={cand: "date"})
                break

    rows: List[Tuple] = []
    for _, r in data.iterrows():
        trade_date = r.get("date")
        if trade_date is None:
            continue
        if hasattr(trade_date, "date"):
            trade_date = trade_date.date() if not isinstance(trade_date, date) else trade_date
        else:
            trade_date = datetime.strptime(str(trade_date)[:10], "%Y-%m-%d").date()
        close_v = _num(r.get("close"))
        open_v = _num(r.get("open"))
        pct = _num(r.get("pct"))
        rows.append(
            (
                code,
                trade_date,
                open_v,
                _num(r.get("high")),
                _num(r.get("low")),
                close_v,
                _num(r.get("volume")),
                _num(r.get("amount")),
                pct,
                _num(r.get("turnover")),
                source,
            )
        )
    return rows


def _num(v):
    if v is None:
        return None
    try:
        if str(v) in ("", "nan", "None"):
            return None
        return float(v)
    except Exception:
        return None


def sync_bars(
    conn,
    start: str,
    end: str,
    sleep_sec: float,
    limit: Optional[int],
    resume: bool,
    only_missing: bool,
    codes: Optional[Sequence[str]] = None,
) -> None:
    codes = list_codes(conn, limit=limit, codes=codes)
    if not codes:
        print("无待同步代码（stock_basic 为空或 --codes 无效），请先执行 --mode list")
        return

    progress = load_progress() if resume else {}
    total = len(codes)
    ok = fail = skip = 0
    print(f"开始同步日线：{total} 只，区间 {start} ~ {end}，sleep={sleep_sec}s")

    for idx, code in enumerate(codes, 1):
        state = progress.get(code) or {}
        if resume and state.get("status") == "done" and state.get("end") == end:
            skip += 1
            if idx % 200 == 0:
                print(f"[{idx}/{total}] skip done…")
            continue

        local_max = max_bar_date(conn, code)
        fetch_start = start
        if only_missing and local_max is not None:
            # 已有数据则从最后交易日前 5 天续拉；若已贴近 end（含周末空隙）则跳过
            from datetime import timedelta

            end_dt = datetime.strptime(end, "%Y%m%d").date()
            if (end_dt - local_max).days <= 10:
                progress[code] = {
                    "status": "done",
                    "end": end,
                    "max_date": str(local_max),
                    "updated_at": datetime.now().isoformat(timespec="seconds"),
                }
                skip += 1
                if idx % 200 == 0:
                    print(f"[{idx}/{total}] skip fresh…")
                continue
            fetch_start = max(start, (local_max - timedelta(days=5)).strftime("%Y%m%d"))
            if fetch_start.replace("-", "") >= end:
                progress[code] = {
                    "status": "done",
                    "end": end,
                    "max_date": str(local_max),
                    "updated_at": datetime.now().isoformat(timespec="seconds"),
                }
                skip += 1
                continue

        try:
            df, src = fetch_hist_bars(code, fetch_start, end)
            rows = to_bar_rows(code, df, source=src)
            n = upsert_bars(conn, code, rows)
            max_d = str(rows[-1][1]) if rows else (str(local_max) if local_max else None)
            progress[code] = {
                "status": "done",
                "end": end,
                "bars": n,
                "source": src,
                "max_date": max_d,
                "updated_at": datetime.now().isoformat(timespec="seconds"),
            }
            ok += 1
            print(f"[{idx}/{total}] {code} OK bars={n} src={src}")
        except Exception as ex:
            fail += 1
            progress[code] = {
                "status": "fail",
                "end": end,
                "error": str(ex)[:300],
                "updated_at": datetime.now().isoformat(timespec="seconds"),
            }
            print(f"[{idx}/{total}] {code} FAIL {ex}")
            try:
                conn.rollback()
            except Exception:
                pass

        if idx % 20 == 0:
            save_progress(progress)
        time.sleep(max(0.05, sleep_sec))

    save_progress(progress)
    print(f"完成：ok={ok}, fail={fail}, skip={skip}, total={total}")
    print(f"进度文件：{PROGRESS_PATH}")


def parse_args():
    today = date.today().strftime("%Y%m%d")
    p = argparse.ArgumentParser(description="Apex 全 A 行情导入（AKShare → MySQL）")
    p.add_argument("--mode", choices=["list", "bars", "all"], default="all")
    p.add_argument("--start", default="20180101", help="日线开始 yyyymmdd")
    p.add_argument("--end", default=today, help="日线结束 yyyymmdd")
    p.add_argument("--sleep", type=float, default=0.35, help="每只股票间隔秒")
    p.add_argument("--limit", type=int, default=None, help="仅处理前 N 只（试跑）")
    p.add_argument("--codes", default=None, help="仅同步指定代码，逗号分隔，如 300308,600519")
    p.add_argument("--no-resume", action="store_true", help="忽略进度文件")
    p.add_argument("--full-refresh", action="store_true", help="不走增量，强制按 start 全量重拉")
    return p.parse_args()


def main() -> int:
    load_env()
    args = parse_args()
    print(
        f"DB={os.getenv('MYSQL_USER', 'root')}@"
        f"{os.getenv('MYSQL_HOST', '127.0.0.1')}:{os.getenv('MYSQL_PORT', '3306')}/"
        f"{os.getenv('MYSQL_DB', 'apex')}"
    )
    code_list = None
    if args.codes:
        code_list = [x.strip() for x in str(args.codes).split(",") if x.strip()]
    conn = db_conn()
    try:
        if args.mode in ("list", "all") and not code_list:
            sync_stock_list(conn, limit=args.limit if args.mode == "list" else None)
        if args.mode in ("bars", "all"):
            # all 模式下 list 已全量写入；bars 的 limit 才限制同步数量
            bar_limit = args.limit if args.mode == "bars" else args.limit
            sync_bars(
                conn,
                start=args.start,
                end=args.end,
                sleep_sec=args.sleep,
                limit=bar_limit,
                resume=not args.no_resume,
                only_missing=not args.full_refresh,
                codes=code_list,
            )
        return 0
    finally:
        conn.close()


if __name__ == "__main__":
    sys.exit(main())
