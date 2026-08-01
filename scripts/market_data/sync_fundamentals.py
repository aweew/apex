#!/usr/bin/env python3
"""
A 股公司基本面导入 Apex MySQL

数据源（AKShare）：
  - stock_financial_analysis_indicator  → stock_fin_indicator（关键列 + 全量 JSON）
  - stock_financial_abstract_ths        → stock_fin_abstract（关键列 + 全量 JSON）
  - stock_financial_report_sina         → stock_fin_report_item（利润/资产负债/现金，EAV）

示例：
  python sync_fundamentals.py --mode all --codes 000001,600519 --sleep 0.8
  python sync_fundamentals.py --mode all --limit 5 --sleep 0.8
  python sync_fundamentals.py --mode indicator --sleep 0.5
"""

from __future__ import annotations

import argparse
import json
import math
import os
import re
import sys
import time
from datetime import date, datetime
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional, Sequence, Tuple

import pymysql
from pymysql.cursors import DictCursor

try:
    from dotenv import load_dotenv
except ImportError:  # pragma: no cover
    load_dotenv = None

ROOT = Path(__file__).resolve().parent
PROGRESS_PATH = ROOT / ".progress" / "fund_progress.json"

STATEMENT_MAP = {
    "profit": "利润表",
    "balance": "资产负债表",
    "cashflow": "现金流量表",
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


def normalize_code(raw: Any) -> Optional[str]:
    text = re.sub(r"\D", "", str(raw or ""))
    if len(text) >= 6:
        return text[-6:]
    return None


def list_codes(conn, limit: Optional[int] = None, codes: Optional[Sequence[str]] = None) -> List[str]:
    if codes:
        out = []
        for c in codes:
            n = normalize_code(c)
            if n:
                out.append(n)
        return out
    sql = "SELECT code FROM stock_basic WHERE deleted = 0 ORDER BY code"
    if limit:
        sql += f" LIMIT {int(limit)}"
    with conn.cursor() as cur:
        cur.execute(sql)
        return [r["code"] for r in cur.fetchall()]


def parse_date(val: Any) -> Optional[date]:
    if val is None or (isinstance(val, float) and math.isnan(val)):
        return None
    if isinstance(val, datetime):
        return val.date()
    if isinstance(val, date):
        return val
    text = str(val).strip()
    if not text or text.lower() in {"nan", "none", "null", "false", "--", "-"}:
        return None
    text = text.replace("/", "-")
    for fmt in ("%Y-%m-%d", "%Y%m%d", "%Y.%m.%d"):
        try:
            return datetime.strptime(text[:10] if fmt == "%Y-%m-%d" else text[:8] if fmt == "%Y%m%d" else text, fmt).date()
        except ValueError:
            continue
    # 2026-03-31 00:00:00
    try:
        return datetime.fromisoformat(text.replace("Z", "+00:00")[:19]).date()
    except ValueError:
        return None


def is_empty(val: Any) -> bool:
    if val is None:
        return True
    if isinstance(val, bool):
        return True  # THS abstract uses False as missing
    if isinstance(val, float) and (math.isnan(val) or math.isinf(val)):
        return True
    text = str(val).strip()
    return text == "" or text.lower() in {"nan", "none", "null", "false", "--", "-", "false"}


def parse_number(val: Any) -> Optional[Decimal]:
    """解析数字 / 百分比 / 中文单位（万、亿）。"""
    if is_empty(val):
        return None
    if isinstance(val, (int, float, Decimal)) and not isinstance(val, bool):
        try:
            if isinstance(val, float) and (math.isnan(val) or math.isinf(val)):
                return None
            return Decimal(str(val))
        except (InvalidOperation, ValueError):
            return None

    text = str(val).strip().replace(",", "").replace(" ", "")
    if not text or text.lower() in {"false", "nan", "none", "null", "--", "-"}:
        return None

    mult = Decimal("1")
    if text.endswith("%"):
        text = text[:-1]
    if text.endswith("万亿"):
        mult = Decimal("1000000000000")
        text = text[:-2]
    elif text.endswith("亿"):
        mult = Decimal("100000000")
        text = text[:-1]
    elif text.endswith("万"):
        mult = Decimal("10000")
        text = text[:-1]

    # 去掉可能残留中文
    text = re.sub(r"[^\d.\-eE+]", "", text)
    if not text or text in {".", "-", "+"}:
        return None
    try:
        return Decimal(text) * mult
    except (InvalidOperation, ValueError):
        return None


def cell_text(val: Any) -> Optional[str]:
    if is_empty(val):
        return None
    text = str(val).strip()
    return text[:64] if text else None


def row_payload(row: Dict[str, Any]) -> str:
    out: Dict[str, Any] = {}
    for k, v in row.items():
        if is_empty(v):
            continue
        if isinstance(v, (date, datetime)):
            out[str(k)] = v.isoformat()
        elif isinstance(v, Decimal):
            out[str(k)] = str(v)
        elif isinstance(v, (int, float)):
            if isinstance(v, float) and (math.isnan(v) or math.isinf(v)):
                continue
            out[str(k)] = v
        else:
            out[str(k)] = str(v)
    return json.dumps(out, ensure_ascii=False)


def get_col(row: Dict[str, Any], *names: str) -> Any:
    for name in names:
        if name in row:
            return row[name]
    # fuzzy contains
    keys = list(row.keys())
    for name in names:
        for k in keys:
            if name in str(k):
                return row[k]
    return None


def upsert_indicators(conn, code: str, rows: List[Dict[str, Any]]) -> int:
    if not rows:
        return 0
    sql = """
    INSERT INTO stock_fin_indicator (
      code, report_date, eps, eps_weighted, eps_adjusted, eps_excl, bps, ocfps,
      capital_reserve_ps, undistributed_ps, roe, roa, gross_margin, net_margin,
      operate_margin, debt_ratio, current_ratio, quick_ratio, payload, source,
      create_time, update_time, deleted
    ) VALUES (
      %s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,NOW(),NOW(),0
    )
    ON DUPLICATE KEY UPDATE
      eps=VALUES(eps), eps_weighted=VALUES(eps_weighted), eps_adjusted=VALUES(eps_adjusted),
      eps_excl=VALUES(eps_excl), bps=VALUES(bps), ocfps=VALUES(ocfps),
      capital_reserve_ps=VALUES(capital_reserve_ps), undistributed_ps=VALUES(undistributed_ps),
      roe=VALUES(roe), roa=VALUES(roa), gross_margin=VALUES(gross_margin),
      net_margin=VALUES(net_margin), operate_margin=VALUES(operate_margin),
      debt_ratio=VALUES(debt_ratio), current_ratio=VALUES(current_ratio),
      quick_ratio=VALUES(quick_ratio), payload=VALUES(payload), source=VALUES(source),
      update_time=NOW(), deleted=0
    """
    params = []
    for row in rows:
        report_date = parse_date(get_col(row, "日期", "报告期", "report_date"))
        if not report_date:
            continue
        params.append(
            (
                code,
                report_date,
                parse_number(get_col(row, "摊薄每股收益(元)")),
                parse_number(get_col(row, "加权每股收益(元)")),
                parse_number(get_col(row, "每股收益_调整后(元)")),
                parse_number(get_col(row, "扣除非经常性损益后的每股收益(元)")),
                parse_number(get_col(row, "每股净资产_调整后(元)", "每股净资产_调整前(元)")),
                parse_number(get_col(row, "每股经营性现金流(元)")),
                parse_number(get_col(row, "每股资本公积金(元)")),
                parse_number(get_col(row, "每股未分配利润(元)")),
                parse_number(get_col(row, "净资产收益率(%)", "加权净资产收益率(%)")),
                parse_number(get_col(row, "总资产净利润率(%)", "总资产利润率(%)")),
                parse_number(get_col(row, "销售毛利率(%)")),
                parse_number(get_col(row, "销售净利率(%)")),
                parse_number(get_col(row, "营业利润率(%)")),
                parse_number(get_col(row, "资产负债率(%)")),
                parse_number(get_col(row, "流动比率")),
                parse_number(get_col(row, "速动比率")),
                row_payload(row),
                "akshare",
            )
        )
    if not params:
        return 0
    with conn.cursor() as cur:
        cur.executemany(sql, params)
    conn.commit()
    return len(params)


def upsert_abstracts(conn, code: str, rows: List[Dict[str, Any]]) -> int:
    if not rows:
        return 0
    sql = """
    INSERT INTO stock_fin_abstract (
      code, report_date, net_profit, net_profit_yoy, net_profit_excl, net_profit_excl_yoy,
      revenue, revenue_yoy, eps_basic, bps, ocfps, net_margin, roe, debt_ratio,
      current_ratio, quick_ratio, payload, source, create_time, update_time, deleted
    ) VALUES (
      %s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,NOW(),NOW(),0
    )
    ON DUPLICATE KEY UPDATE
      net_profit=VALUES(net_profit), net_profit_yoy=VALUES(net_profit_yoy),
      net_profit_excl=VALUES(net_profit_excl), net_profit_excl_yoy=VALUES(net_profit_excl_yoy),
      revenue=VALUES(revenue), revenue_yoy=VALUES(revenue_yoy),
      eps_basic=VALUES(eps_basic), bps=VALUES(bps), ocfps=VALUES(ocfps),
      net_margin=VALUES(net_margin), roe=VALUES(roe), debt_ratio=VALUES(debt_ratio),
      current_ratio=VALUES(current_ratio), quick_ratio=VALUES(quick_ratio),
      payload=VALUES(payload), source=VALUES(source), update_time=NOW(), deleted=0
    """
    params = []
    for row in rows:
        report_date = parse_date(get_col(row, "报告期", "日期"))
        if not report_date:
            continue
        params.append(
            (
                code,
                report_date,
                parse_number(get_col(row, "净利润")),
                parse_number(get_col(row, "净利润同比增长率")),
                parse_number(get_col(row, "扣非净利润")),
                parse_number(get_col(row, "扣非净利润同比增长率")),
                parse_number(get_col(row, "营业总收入")),
                parse_number(get_col(row, "营业总收入同比增长率")),
                parse_number(get_col(row, "基本每股收益")),
                parse_number(get_col(row, "每股净资产")),
                parse_number(get_col(row, "每股经营现金流")),
                parse_number(get_col(row, "销售净利率")),
                parse_number(get_col(row, "净资产收益率", "净资产收益率-摊薄")),
                parse_number(get_col(row, "资产负债率")),
                parse_number(get_col(row, "流动比率")),
                parse_number(get_col(row, "速动比率")),
                row_payload(row),
                "akshare-ths",
            )
        )
    if not params:
        return 0
    with conn.cursor() as cur:
        cur.executemany(sql, params)
    conn.commit()
    return len(params)


def upsert_report_items(conn, code: str, statement_type: str, rows: List[Dict[str, Any]]) -> int:
    if not rows:
        return 0
    sql = """
    INSERT INTO stock_fin_report_item (
      code, statement_type, report_date, item_name, item_value, item_value_text,
      source, create_time, update_time, deleted
    ) VALUES (%s,%s,%s,%s,%s,%s,%s,NOW(),NOW(),0)
    ON DUPLICATE KEY UPDATE
      item_value=VALUES(item_value), item_value_text=VALUES(item_value_text),
      source=VALUES(source), update_time=NOW(), deleted=0
    """
    params = []
    date_keys = {"报告日", "报告期", "日期", "report_date"}
    for row in rows:
        report_date = None
        for dk in date_keys:
            if dk in row:
                report_date = parse_date(row[dk])
                if report_date:
                    break
        if not report_date:
            continue
        for key, val in row.items():
            if key in date_keys:
                continue
            item_name = str(key).strip()
            if not item_name:
                continue
            if is_empty(val):
                continue
            params.append(
                (
                    code,
                    statement_type,
                    report_date,
                    item_name[:128],
                    parse_number(val),
                    cell_text(val),
                    "akshare-sina",
                )
            )
    if not params:
        return 0
    # batch insert
    with conn.cursor() as cur:
        batch = 500
        for i in range(0, len(params), batch):
            cur.executemany(sql, params[i : i + batch])
    conn.commit()
    return len(params)


def df_to_rows(df) -> List[Dict[str, Any]]:
    if df is None or getattr(df, "empty", True):
        return []
    return df.to_dict(orient="records")


def sync_indicator(conn, code: str) -> int:
    import akshare as ak

    df = ak.stock_financial_analysis_indicator(symbol=code)
    return upsert_indicators(conn, code, df_to_rows(df))


def sync_abstract(conn, code: str) -> int:
    import akshare as ak

    df = ak.stock_financial_abstract_ths(symbol=code)
    return upsert_abstracts(conn, code, df_to_rows(df))


def sync_reports(conn, code: str) -> int:
    import akshare as ak

    total = 0
    for stype, symbol in STATEMENT_MAP.items():
        df = ak.stock_financial_report_sina(stock=code, symbol=symbol)
        total += upsert_report_items(conn, code, stype, df_to_rows(df))
        time.sleep(0.15)
    return total


def mark_done(progress: Dict[str, Any], mode: str, code: str, ok: bool, detail: str) -> None:
    bucket = progress.setdefault(mode, {})
    bucket[code] = {
        "ok": ok,
        "detail": detail,
        "ts": datetime.now().isoformat(timespec="seconds"),
    }
    save_progress(progress)


def run_mode(
    conn,
    mode: str,
    codes: List[str],
    sleep_s: float,
    resume: bool,
    progress: Dict[str, Any],
) -> None:
    done = progress.get(mode, {}) if resume else {}
    total = len(codes)
    ok_n = fail_n = skip_n = 0
    for idx, code in enumerate(codes, 1):
        if resume and done.get(code, {}).get("ok"):
            skip_n += 1
            print(f"[{idx}/{total}] {code} skip (done)")
            continue
        try:
            if mode == "indicator":
                n = sync_indicator(conn, code)
            elif mode == "abstract":
                n = sync_abstract(conn, code)
            elif mode == "reports":
                n = sync_reports(conn, code)
            else:
                raise ValueError(mode)
            mark_done(progress, mode, code, True, f"rows={n}")
            ok_n += 1
            print(f"[{idx}/{total}] {code} {mode} ok rows={n}")
        except Exception as ex:
            conn.rollback()
            mark_done(progress, mode, code, False, str(ex)[:300])
            fail_n += 1
            print(f"[{idx}/{total}] {code} {mode} FAIL {ex}", file=sys.stderr)
        if sleep_s > 0:
            time.sleep(sleep_s)
    print(f"{mode} 完成：ok={ok_n} fail={fail_n} skip={skip_n}")


def main() -> int:
    load_env()
    parser = argparse.ArgumentParser(description="同步 A 股公司基本面到 Apex MySQL")
    parser.add_argument(
        "--mode",
        choices=["indicator", "abstract", "reports", "all"],
        default="all",
        help="同步类型",
    )
    parser.add_argument("--codes", default="", help="逗号分隔代码，优先于库内列表")
    parser.add_argument("--limit", type=int, default=0, help="从 stock_basic 取前 N 只")
    parser.add_argument("--sleep", type=float, default=0.8, help="每只股票间隔秒")
    parser.add_argument("--no-resume", action="store_true", help="忽略进度强制重跑")
    args = parser.parse_args()

    code_list = [c.strip() for c in args.codes.split(",") if c.strip()] or None
    limit = args.limit if args.limit and args.limit > 0 else None
    resume = not args.no_resume
    progress = load_progress()

    conn = db_conn()
    try:
        codes = list_codes(conn, limit=limit, codes=code_list)
        if not codes:
            print("没有可同步的股票代码（请先同步 stock_basic 列表）", file=sys.stderr)
            return 1
        print(f"待同步 {len(codes)} 只，mode={args.mode}, resume={resume}, sleep={args.sleep}")

        modes: List[str]
        if args.mode == "all":
            modes = ["indicator", "abstract", "reports"]
        else:
            modes = [args.mode]

        for mode in modes:
            run_mode(conn, mode, codes, args.sleep, resume, progress)
        return 0
    finally:
        conn.close()


if __name__ == "__main__":
    raise SystemExit(main())
