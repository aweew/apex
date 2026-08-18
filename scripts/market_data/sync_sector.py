#!/usr/bin/env python3
"""
东财板块行情 + 资金流 + 成分股 → Apex MySQL

board_type:
  INDUSTRY 东财行业板块（二级行业口径）
  CONCEPT  概念板块
  THEME    题材（与概念同源，同步时双写）

示例：
  python sync_sector.py --mode quote --types INDUSTRY,CONCEPT,THEME
  python sync_sector.py --mode cons --types CONCEPT --codes BK0655 --sleep 0.3
  python sync_sector.py --mode all --types INDUSTRY
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
from typing import Any, Dict, List, Optional, Sequence, Set, Tuple

import pymysql
from pymysql.cursors import DictCursor

try:
    from dotenv import load_dotenv
except ImportError:  # pragma: no cover
    load_dotenv = None

ROOT = Path(__file__).resolve().parent

ALL_TYPES = ("INDUSTRY", "CONCEPT", "THEME")
# 东财 clist fs: m:90 t:2 行业 / t:3 概念
EM_TYPE_FS = {
    "INDUSTRY": "m:90 t:2 f:!50",
    "CONCEPT": "m:90 t:3 f:!50",
    "THEME": "m:90 t:3 f:!50",
}
FUND_SECTOR_TYPE = {
    "INDUSTRY": "行业资金流",
    "CONCEPT": "概念资金流",
    "THEME": "概念资金流",
}

# 常规 push2 常被掐；delay 节点更稳
EM_CLIST_HOSTS = (
    "https://push2delay.eastmoney.com/api/qt/clist/get",
    "https://push2.eastmoney.com/api/qt/clist/get",
    "https://82.push2.eastmoney.com/api/qt/clist/get",
    "https://88.push2.eastmoney.com/api/qt/clist/get",
    "https://17.push2.eastmoney.com/api/qt/clist/get",
    "https://39.push2.eastmoney.com/api/qt/clist/get",
)
EM_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    ),
    "Referer": "https://quote.eastmoney.com/center/boardlist.html",
    "Accept": "*/*",
    "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
    "Connection": "keep-alive",
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


def _daterange(begin: date, end: date) -> Set[date]:
    out: Set[date] = set()
    cur = begin
    while cur <= end:
        out.add(cur)
        cur += timedelta(days=1)
    return out


# 与后端 TradingCalendar 对齐的节假日（周末另判）
_A_SHARE_HOLIDAYS: Set[date] = set()
_A_SHARE_HOLIDAYS |= _daterange(date(2025, 1, 1), date(2025, 1, 1))
_A_SHARE_HOLIDAYS |= _daterange(date(2025, 1, 28), date(2025, 2, 4))
_A_SHARE_HOLIDAYS |= _daterange(date(2025, 4, 4), date(2025, 4, 6))
_A_SHARE_HOLIDAYS |= _daterange(date(2025, 5, 1), date(2025, 5, 5))
_A_SHARE_HOLIDAYS |= _daterange(date(2025, 5, 31), date(2025, 6, 2))
_A_SHARE_HOLIDAYS |= _daterange(date(2025, 10, 1), date(2025, 10, 8))
_A_SHARE_HOLIDAYS |= _daterange(date(2026, 1, 1), date(2026, 1, 3))
_A_SHARE_HOLIDAYS |= _daterange(date(2026, 2, 15), date(2026, 2, 23))
_A_SHARE_HOLIDAYS |= _daterange(date(2026, 4, 4), date(2026, 4, 6))
_A_SHARE_HOLIDAYS |= _daterange(date(2026, 5, 1), date(2026, 5, 5))
_A_SHARE_HOLIDAYS |= _daterange(date(2026, 6, 19), date(2026, 6, 21))
_A_SHARE_HOLIDAYS |= _daterange(date(2026, 9, 25), date(2026, 9, 27))
_A_SHARE_HOLIDAYS |= _daterange(date(2026, 10, 1), date(2026, 10, 7))


def is_trading_day(d: date) -> bool:
    if d.weekday() >= 5:
        return False
    return d not in _A_SHARE_HOLIDAYS


def resolve_trade_date(as_of: Optional[date] = None) -> date:
    """同步落库用交易日：非交易日回退到最近上一交易日，避免周末写成伪行情日。"""
    cur = as_of or date.today()
    for _ in range(20):
        if is_trading_day(cur):
            return cur
        cur -= timedelta(days=1)
    return as_of or date.today()


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
    # 东财偶发「1.23亿」
    multi = Decimal(1)
    if text.endswith("亿"):
        multi = Decimal("100000000")
        text = text[:-1]
    elif text.endswith("万"):
        multi = Decimal("10000")
        text = text[:-1]
    try:
        return Decimal(text) * multi
    except (InvalidOperation, ValueError):
        return None


def normalize_code(raw: Any) -> Optional[str]:
    text = str(raw or "").strip().upper()
    if not text:
        return None
    m = re.match(r"^(SH|SZ|BJ)?(\d{6})$", text.replace(".", ""))
    if m:
        return m.group(2)
    digits = re.sub(r"\D", "", text)
    if len(digits) >= 6:
        return digits[-6:]
    return None


def normalize_board_code(raw: Any) -> Optional[str]:
    text = str(raw or "").strip().upper()
    if not text:
        return None
    if re.match(r"^BK\d+$", text):
        return text
    return text


def load_name_map(conn) -> Dict[str, str]:
    mapping: Dict[str, str] = {}
    with conn.cursor() as cur:
        cur.execute(
            "SELECT code, name FROM stock_basic WHERE deleted = 0 AND name IS NOT NULL AND name <> ''"
        )
        for row in cur.fetchall():
            name = str(row["name"]).strip()
            code = str(row["code"]).strip()
            if name and code and name not in mapping:
                mapping[name] = code
    return mapping


def _row_from_em_item(item: Dict[str, Any]) -> Optional[Dict[str, Any]]:
    code = normalize_board_code(item.get("f12"))
    name = str(item.get("f14") or "").strip()
    if not code or not name:
        return None
    up_raw = parse_number(item.get("f104"))
    down_raw = parse_number(item.get("f105"))
    lead_name = str(item.get("f128") or "").strip() or None
    lead_code = normalize_code(item.get("f140"))
    return {
        "code": code,
        "name": name,
        "pct_chg": parse_number(item.get("f3")),
        "amount": parse_number(item.get("f6")),
        "net_inflow": parse_number(item.get("f62")),
        "up_count": int(up_raw) if up_raw is not None else None,
        "down_count": int(down_raw) if down_raw is not None else None,
        "lead_stock_name": lead_name,
        "lead_stock_code": lead_code,
        "lead_stock_pct": parse_number(item.get("f136")),
    }


def _fetch_em_clist(fs: str) -> List[Dict[str, Any]]:
    """直连东财 clist 分页；优先 push2delay，绕过易被掐的 push2。"""
    import math

    import requests

    fields = (
        "f12,f14,f2,f3,f4,f5,f6,f8,f20,f62,f104,f105,f128,f136,f140,f141"
    )
    base_params = {
        "pn": "1",
        "pz": "100",
        "po": "1",
        "np": "1",
        "ut": "bd1d9ddb04089700cf9c27f6f7426281",
        "fltt": "2",
        "invt": "2",
        "fid": "f3",
        "fs": fs,
        "fields": fields,
        "_": str(int(time.time() * 1000)),
    }
    last_err = None
    session = requests.Session()
    session.headers.update(EM_HEADERS)

    for host in EM_CLIST_HOSTS:
        for attempt in range(3):
            try:
                params = dict(base_params)
                params["pn"] = "1"
                params["_"] = str(int(time.time() * 1000))
                r = session.get(host, params=params, timeout=20)
                r.raise_for_status()
                data_json = r.json()
                data = (data_json or {}).get("data") or {}
                diff = data.get("diff") or []
                if not diff:
                    raise RuntimeError(f"empty diff from {host}")
                total = int(data.get("total") or len(diff))
                per_page = max(len(diff), 1)
                pages = max(1, math.ceil(total / per_page))
                items: List[Dict[str, Any]] = list(diff)
                for page in range(2, pages + 1):
                    time.sleep(0.35 + 0.15 * attempt)
                    params = dict(base_params)
                    params["pn"] = str(page)
                    params["_"] = str(int(time.time() * 1000))
                    rp = session.get(host, params=params, timeout=20)
                    rp.raise_for_status()
                    page_diff = ((rp.json() or {}).get("data") or {}).get("diff") or []
                    items.extend(page_diff)
                rows: List[Dict[str, Any]] = []
                seen: Set[str] = set()
                for item in items:
                    row = _row_from_em_item(item)
                    if not row or row["code"] in seen:
                        continue
                    seen.add(row["code"])
                    rows.append(row)
                if rows:
                    print(f"板块列表拉取成功，服务地址={host.split('//')[1].split('/')[0]}，行数={len(rows)}")
                    return rows
            except Exception as ex:
                last_err = ex
                print(f"板块列表拉取失败，服务地址={host}，尝试次数={attempt + 1}，异常={ex}", file=sys.stderr)
                time.sleep(1.5 * (attempt + 1))
    raise RuntimeError(f"东财 clist 全部失败: {last_err}")


def fetch_board_spot(board_type: str) -> List[Dict[str, Any]]:
    """拉取板块行情：优先 delay 直连，再降级 AKShare。"""
    fs = EM_TYPE_FS[board_type]
    last_err = None
    try:
        return _fetch_em_clist(fs)
    except Exception as ex:
        last_err = ex
        print(f"{board_type} 直连失败，尝试 AKShare: {ex}", file=sys.stderr)

    # 降级 AKShare（其内部仍可能打 push2，成功率较低）
    try:
        import akshare as ak

        if board_type == "INDUSTRY":
            df = ak.stock_board_industry_name_em()
        elif board_type in ("CONCEPT", "THEME"):
            df = ak.stock_board_concept_name_em()
        else:
            df = None
        rows: List[Dict[str, Any]] = []
        if df is not None and not getattr(df, "empty", True):
            for _, series in df.iterrows():
                code = normalize_board_code(series.get("板块代码"))
                name = str(series.get("板块名称") or "").strip()
                if not code or not name:
                    continue
                up_raw = parse_number(series.get("上涨家数"))
                down_raw = parse_number(series.get("下跌家数"))
                rows.append(
                    {
                        "code": code,
                        "name": name,
                        "pct_chg": parse_number(series.get("涨跌幅")),
                        "amount": parse_number(series.get("总市值")),
                        "net_inflow": None,
                        "up_count": int(up_raw) if up_raw is not None else None,
                        "down_count": int(down_raw) if down_raw is not None else None,
                        "lead_stock_name": str(series.get("领涨股票") or "").strip() or None,
                        "lead_stock_code": None,
                        "lead_stock_pct": parse_number(series.get("领涨股票-涨跌幅")),
                    }
                )
        if rows:
            print(f"{board_type} 使用 AKShare 降级成功，行数={len(rows)}")
            return rows
    except Exception as ex:
        last_err = ex
    raise RuntimeError(f"{board_type} 板块行情拉取失败: {last_err}")


def fetch_fund_flow_by_name(board_type: str) -> Dict[str, Decimal]:
    """名称 -> 今日主力净流入（元）。失败不阻断行情入库。"""
    import akshare as ak

    sector_type = FUND_SECTOR_TYPE[board_type]
    last_err = None
    df = None
    for attempt in range(4):
        try:
            df = ak.stock_sector_fund_flow_rank(indicator="今日", sector_type=sector_type)
            last_err = None
            break
        except Exception as ex:
            last_err = ex
            time.sleep(1.8 * (attempt + 1))
    if df is None or getattr(df, "empty", True):
        print(f"{board_type} 资金流跳过: {last_err}", file=sys.stderr)
        return {}
    mapping: Dict[str, Decimal] = {}
    name_col = "名称" if "名称" in df.columns else df.columns[0]
    inflow_col = None
    for c in df.columns:
        if "主力净流入-净额" in str(c) or str(c) == "今日主力净流入-净额":
            inflow_col = c
            break
    if inflow_col is None:
        for c in df.columns:
            if "净流入" in str(c) and "净额" in str(c):
                inflow_col = c
                break
    if inflow_col is None:
        print(f"{board_type} 资金流无净流入列: {list(df.columns)}", file=sys.stderr)
        return {}
    for _, row in df.iterrows():
        name = str(row.get(name_col, "") or "").strip()
        inflow = parse_number(row.get(inflow_col))
        if name and inflow is not None:
            mapping[name] = inflow
    return mapping


def upsert_basic(conn, board_type: str, items: Sequence[Dict[str, Any]]) -> int:
    if not items:
        return 0
    sql = """
    INSERT INTO sector_basic (code, name, board_type, source, create_time, update_time, deleted)
    VALUES (%s, %s, %s, 'eastmoney', NOW(), NOW(), 0)
    ON DUPLICATE KEY UPDATE
      name = VALUES(name),
      source = VALUES(source),
      update_time = NOW(),
      deleted = 0
    """
    params = [(it["code"], it["name"], board_type) for it in items]
    with conn.cursor() as cur:
        cur.executemany(sql, params)
    return len(params)


def compound_pct(pct_list: Sequence[Optional[Decimal]]) -> Optional[Decimal]:
    acc = Decimal(1)
    used = 0
    for p in pct_list:
        if p is None:
            continue
        acc *= Decimal(1) + (Decimal(p) / Decimal(100))
        used += 1
    if used == 0:
        return None
    return (acc - Decimal(1)) * Decimal(100)


def build_move_reason(it: Dict[str, Any]) -> str:
    pct = it.get("pct_chg")
    net = it.get("net_inflow")
    up_n = it.get("up_count")
    down_n = it.get("down_count")
    lead = it.get("lead_stock_name")
    lead_pct = it.get("lead_stock_pct")
    p3 = it.get("pct_chg_3d")
    p5 = it.get("pct_chg_5d")
    lu = it.get("limit_up_count")
    lb = it.get("max_lianban")
    parts: List[str] = []
    if pct is not None:
        parts.append(("上涨" if pct >= 0 else "下跌") + f"{pct:.2f}%")
    if lead:
        lp = f"{lead_pct:+.2f}%" if lead_pct is not None else ""
        parts.append(f"领涨{lead}{lp}")
    if lu:
        parts.append(f"涨停{lu}家")
        if lb:
            parts.append(f"连板高度{lb}")
    if net is not None:
        yi = Decimal(net) / Decimal("100000000")
        parts.append(("净流入" if yi >= 0 else "净流出") + f"{abs(yi):.2f}亿")
    if up_n is not None or down_n is not None:
        parts.append(f"涨跌家数{up_n or 0}/{down_n or 0}")
    if p3 is not None and pct is not None and ((p3 > 0 and pct > 0) or (p3 < 0 and pct < 0)):
        parts.append(f"近3日{p3:+.2f}%")
    if p5 is not None and abs(p5) >= 5:
        parts.append(f"近5日{p5:+.2f}%")
    return "；".join(parts)[:500] if parts else ""


def market_prefix(code: str) -> str:
    if code.startswith(("5", "6", "9")):
        return "SH"
    if code.startswith(("4", "8")):
        return "BJ"
    return "SZ"


def fetch_zt_pool(trade_date: date) -> List[Dict[str, Any]]:
    """东财涨停池：代码 / 连板数 / 所属行业。"""
    import requests

    day = trade_date.strftime("%Y%m%d")
    url = "https://push2ex.eastmoney.com/getTopicZTPool"
    rows: List[Dict[str, Any]] = []
    page = 0
    session = requests.Session()
    session.headers.update({
        "User-Agent": EM_HEADERS["User-Agent"],
        "Referer": "https://quote.eastmoney.com/ztb/detail",
        "Accept": "*/*",
    })
    while True:
        params = {
            "ut": "7eea3edcaed734bea9cbfc24409ed989",
            "dpt": "wz.ztzt",
            "Pageindex": page,
            "pagesize": 200,
            "sort": "fbt:asc",
            "date": day,
        }
        r = session.get(url, params=params, timeout=20)
        r.raise_for_status()
        data = (r.json() or {}).get("data") or {}
        pool = data.get("pool") or []
        if not pool:
            break
        for item in pool:
            code = normalize_code(item.get("c"))
            if not code:
                continue
            lbc_raw = item.get("lbc")
            try:
                lbc = int(lbc_raw or 1)
            except (TypeError, ValueError):
                lbc = 1
            rows.append({
                "code": code,
                "name": str(item.get("n") or "").strip() or None,
                "lbc": max(lbc, 1),
                "hybk": str(item.get("hybk") or "").strip() or None,
            })
        total = int(data.get("tc") or 0)
        if (page + 1) * 200 >= total or len(pool) < 200:
            break
        page += 1
        time.sleep(0.2)
    return rows


def fetch_ssbk_names(code: str) -> List[str]:
    """个股所属板块名称（行业+概念），用于涨停归属。"""
    import requests

    url = (
        "https://emweb.securities.eastmoney.com/PC_HSF10/CoreConception/PageAjax"
        f"?code={market_prefix(code)}{code}"
    )
    r = requests.get(url, timeout=12, headers={
        "User-Agent": EM_HEADERS["User-Agent"],
        "Referer": "https://emweb.securities.eastmoney.com/",
    })
    r.raise_for_status()
    names: List[str] = []
    seen: Set[str] = set()
    for item in (r.json() or {}).get("ssbk") or []:
        name = str(item.get("BOARD_NAME") or "").strip()
        if name and name not in seen:
            seen.add(name)
            names.append(name)
    return names


def load_zt_context(trade_date: date) -> Dict[str, Any]:
    """一次拉取涨停池，并补齐涨停股所属板块名称。"""
    from concurrent.futures import ThreadPoolExecutor, as_completed

    zt_rows = fetch_zt_pool(trade_date)
    print(f"涨停池 {trade_date} 共 {len(zt_rows)} 只")
    ssbk_map: Dict[str, List[str]] = {}
    if not zt_rows:
        return {"rows": zt_rows, "ssbk_map": ssbk_map}

    codes = [r["code"] for r in zt_rows]
    ok = fail = 0
    with ThreadPoolExecutor(max_workers=8) as pool:
        futures = {pool.submit(fetch_ssbk_names, code): code for code in codes}
        for fut in as_completed(futures):
            code = futures[fut]
            try:
                ssbk_map[code] = fut.result()
                ok += 1
            except Exception:
                ssbk_map[code] = []
                fail += 1
    print(f"涨停股板块归属完成，成功数={ok}，失败数={fail}")
    return {"rows": zt_rows, "ssbk_map": ssbk_map}


def load_recent_pcts(conn, board_type: str, code: str, trade_date: date, n: int) -> List[Optional[Decimal]]:
    with conn.cursor() as cur:
        cur.execute(
            "SELECT pct_chg FROM sector_quote "
            "WHERE board_type=%s AND code=%s AND trade_date<=%s AND deleted=0 "
            "ORDER BY trade_date DESC LIMIT %s",
            (board_type, code, trade_date, n),
        )
        rows = cur.fetchall()
    return [parse_number(r.get("pct_chg")) for r in rows]


def _add_zt_to_bucket(
    buckets: Dict[str, Dict[str, Any]],
    board_name: Optional[str],
    stock_code: str,
    lbc: int,
) -> None:
    if not board_name:
        return
    bucket = buckets.setdefault(board_name, {"codes": set(), "max_lbc": 0})
    bucket["codes"].add(stock_code)
    bucket["max_lbc"] = max(int(bucket["max_lbc"] or 0), int(lbc or 1))


def enrich_limit_up_stats(
    conn,
    board_type: str,
    trade_date: date,
    items: List[Dict[str, Any]],
    zt_ctx: Optional[Dict[str, Any]],
) -> None:
    """按涨停池归属统计板块涨停家数与连板高度。"""
    if not items:
        return
    ctx = zt_ctx or {}
    zt_rows: List[Dict[str, Any]] = list(ctx.get("rows") or [])
    ssbk_map: Dict[str, List[str]] = dict(ctx.get("ssbk_map") or {})
    buckets: Dict[str, Dict[str, Any]] = {}
    zt_codes = {r["code"] for r in zt_rows}
    lbc_map = {r["code"]: int(r.get("lbc") or 1) for r in zt_rows}

    for zt in zt_rows:
        code = zt["code"]
        lbc = int(zt.get("lbc") or 1)
        if board_type == "INDUSTRY":
            _add_zt_to_bucket(buckets, zt.get("hybk"), code, lbc)
        for bname in ssbk_map.get(code, []):
            _add_zt_to_bucket(buckets, bname, code, lbc)

    # 已有成分股时，用成分 ∩ 涨停池兜底/补全
    sector_codes = [it["code"] for it in items]
    if sector_codes and zt_codes:
        placeholders = ",".join(["%s"] * len(sector_codes))
        sql = (
            f"SELECT sector_code, stock_code FROM sector_constituent "
            f"WHERE deleted=0 AND board_type=%s AND trade_date=%s "
            f"AND sector_code IN ({placeholders})"
        )
        with conn.cursor() as cur:
            cur.execute(sql, [board_type, trade_date, *sector_codes])
            cons_rows = cur.fetchall()
        code_to_name = {it["code"]: it["name"] for it in items}
        for row in cons_rows:
            stock_code = str(row["stock_code"])
            if stock_code not in zt_codes:
                continue
            sector_name = code_to_name.get(str(row["sector_code"]))
            _add_zt_to_bucket(buckets, sector_name, stock_code, lbc_map.get(stock_code, 1))

    for it in items:
        bucket = buckets.get(it["name"]) or {}
        codes = bucket.get("codes") or set()
        it["limit_up_count"] = len(codes)
        it["max_lianban"] = int(bucket.get("max_lbc") or 0) if codes else 0


def enrich_multi_day_and_reason(
    conn,
    board_type: str,
    trade_date: date,
    items: List[Dict[str, Any]],
    zt_ctx: Optional[Dict[str, Any]] = None,
) -> None:
    for it in items:
        code = it["code"]
        recent5 = load_recent_pcts(conn, board_type, code, trade_date, 5)
        # recent 已含当日（刚 upsert）
        it["pct_chg_3d"] = compound_pct(recent5[:3])
        it["pct_chg_5d"] = compound_pct(recent5[:5])
    enrich_limit_up_stats(conn, board_type, trade_date, items, zt_ctx)
    for it in items:
        it["move_reason"] = build_move_reason(it)
    sql = """
    UPDATE sector_quote
    SET pct_chg_3d=%s, pct_chg_5d=%s, limit_up_count=%s, max_lianban=%s,
        move_reason=%s, update_time=NOW()
    WHERE code=%s AND board_type=%s AND trade_date=%s AND deleted=0
    """
    params = [
        (it.get("pct_chg_3d"), it.get("pct_chg_5d"),
         it.get("limit_up_count"), it.get("max_lianban"),
         it.get("move_reason"),
         it["code"], board_type, trade_date)
        for it in items
    ]
    with conn.cursor() as cur:
        cur.executemany(sql, params)


def upsert_quotes(
    conn,
    board_type: str,
    trade_date: date,
    synced_at: datetime,
    items: Sequence[Dict[str, Any]],
    zt_ctx: Optional[Dict[str, Any]] = None,
) -> int:
    if not items:
        print(f"{board_type} 行情为空，已跳过", file=sys.stderr)
        return 0
    sql = """
    INSERT INTO sector_quote (
      code, name, board_type, trade_date, pct_chg, net_inflow, main_net_inflow, amount,
      up_count, down_count, lead_stock_code, lead_stock_name, lead_stock_pct,
      synced_at, create_time, update_time, deleted
    ) VALUES (
      %s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,NOW(),NOW(),0
    )
    ON DUPLICATE KEY UPDATE
      name = VALUES(name),
      pct_chg = VALUES(pct_chg),
      net_inflow = VALUES(net_inflow),
      main_net_inflow = VALUES(main_net_inflow),
      amount = VALUES(amount),
      up_count = VALUES(up_count),
      down_count = VALUES(down_count),
      lead_stock_code = VALUES(lead_stock_code),
      lead_stock_name = VALUES(lead_stock_name),
      lead_stock_pct = VALUES(lead_stock_pct),
      synced_at = VALUES(synced_at),
      update_time = NOW(),
      deleted = 0
    """
    params = []
    for it in items:
        net = it.get("net_inflow")
        params.append(
            (
                it["code"],
                it["name"],
                board_type,
                trade_date,
                it.get("pct_chg"),
                net,
                net,
                it.get("amount"),
                it.get("up_count"),
                it.get("down_count"),
                it.get("lead_stock_code"),
                it.get("lead_stock_name"),
                it.get("lead_stock_pct"),
                synced_at,
            )
        )
    with conn.cursor() as cur:
        cur.executemany(sql, params)
    enrich_multi_day_and_reason(conn, board_type, trade_date, list(items), zt_ctx)
    return len(params)


def replace_constituents(
    conn,
    sector_code: str,
    board_type: str,
    trade_date: date,
    synced_at: datetime,
    rows: Sequence[Dict[str, Any]],
) -> int:
    with conn.cursor() as cur:
        cur.execute(
            "UPDATE sector_constituent SET deleted = 1, update_time = NOW() "
            "WHERE sector_code = %s AND board_type = %s AND trade_date = %s AND deleted = 0",
            (sector_code, board_type, trade_date),
        )
        if not rows:
            return 0
        sql = """
        INSERT INTO sector_constituent (
          sector_code, board_type, stock_code, stock_name, pct_chg, latest_price,
          trade_date, synced_at, create_time, update_time, deleted
        ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,NOW(),NOW(),0)
        ON DUPLICATE KEY UPDATE
          stock_name = VALUES(stock_name),
          pct_chg = VALUES(pct_chg),
          latest_price = VALUES(latest_price),
          synced_at = VALUES(synced_at),
          update_time = NOW(),
          deleted = 0
        """
        params = [
            (
                sector_code,
                board_type,
                r["stock_code"],
                r.get("stock_name"),
                r.get("pct_chg"),
                r.get("latest_price"),
                trade_date,
                synced_at,
            )
            for r in rows
        ]
        cur.executemany(sql, params)
        return len(params)


def _constituent_row_from_em_item(item: Dict[str, Any]) -> Optional[Dict[str, Any]]:
    raw_code = str(item.get("f12") or "").strip()
    if not re.fullmatch(r"\d{6}", raw_code):
        return None
    code = normalize_code(raw_code)
    name = str(item.get("f14") or "").strip()
    if not code or not name:
        return None
    return {
        "stock_code": code,
        "stock_name": name,
        "pct_chg": parse_number(item.get("f3")),
        "latest_price": parse_number(item.get("f2")),
    }


def _fetch_em_constituents(sector_code: str) -> List[Dict[str, Any]]:
    """直连东财成分接口；delay 节点通常比 AKShare 默认节点稳定。"""
    import math

    import requests

    page_size = 200
    base_params = {
        "pn": "1",
        "pz": str(page_size),
        "po": "1",
        "np": "1",
        "ut": "bd1d9ddb04089700cf9c27f6f7426281",
        "fltt": "2",
        "invt": "2",
        "fid": "f3",
        "fs": f"b:{sector_code}+f:!50",
        "fields": "f2,f3,f12,f14",
        "_": str(int(time.time() * 1000)),
    }
    session = requests.Session()
    session.headers.update(EM_HEADERS)
    last_err = None

    for host in EM_CLIST_HOSTS[:2]:
        try:
            params = dict(base_params)
            response = session.get(host, params=params, timeout=6)
            response.raise_for_status()
            payload = response.json() or {}
            data = payload.get("data") or {}
            diff = data.get("diff") or []
            total = int(data.get("total") or len(diff))
            if total == 0:
                return []
            if total > 5000:
                raise RuntimeError(f"unexpected constituent total={total} from {host}")
            if not diff:
                raise RuntimeError(f"empty diff from {host}")

            items: List[Dict[str, Any]] = list(diff)
            pages = max(1, math.ceil(total / page_size))
            for page in range(2, pages + 1):
                page_params = dict(base_params)
                page_params["pn"] = str(page)
                page_params["_"] = str(int(time.time() * 1000))
                page_response = session.get(host, params=page_params, timeout=6)
                page_response.raise_for_status()
                page_data = (page_response.json() or {}).get("data") or {}
                items.extend(page_data.get("diff") or [])

            rows: List[Dict[str, Any]] = []
            seen: Set[str] = set()
            for item in items:
                row = _constituent_row_from_em_item(item)
                if not row or row["stock_code"] in seen:
                    continue
                seen.add(row["stock_code"])
                rows.append(row)
            if rows:
                print(f"板块成分直连拉取成功，板块代码={sector_code}，行数={len(rows)}")
                return rows
            raise RuntimeError(f"no valid constituents from {host}")
        except Exception as ex:
            last_err = ex
            print(f"板块成分直连拉取失败，服务地址={host}，板块代码={sector_code}，异常={ex}", file=sys.stderr)
    raise RuntimeError(f"东财成分直连失败 {sector_code}: {last_err}")


def fetch_constituents(board_type: str, sector_code: str, sector_name: str) -> List[Dict[str, Any]]:
    symbol = sector_code or sector_name
    last_err = None
    try:
        rows = _fetch_em_constituents(sector_code)
        if rows:
            return rows
    except Exception as ex:
        last_err = ex
        print(f"{board_type}/{symbol} 直连失败，尝试 AKShare: {ex}", file=sys.stderr)

    import akshare as ak

    df = None
    try:
        if board_type in ("CONCEPT", "THEME"):
            df = ak.stock_board_concept_cons_em(symbol=symbol)
        else:
            # 行业/地域均可用 BK 代码走 industry cons 接口
            df = ak.stock_board_industry_cons_em(symbol=symbol)
        last_err = None
    except Exception as ex:
        last_err = ex
    if df is None or getattr(df, "empty", True):
        raise RuntimeError(f"成分股拉取失败 {board_type}/{symbol}: {last_err}")

    code_col = "代码" if "代码" in df.columns else None
    name_col = "名称" if "名称" in df.columns else None
    pct_col = "涨跌幅" if "涨跌幅" in df.columns else None
    price_col = "最新价" if "最新价" in df.columns else None
    rows: List[Dict[str, Any]] = []
    for _, row in df.iterrows():
        code = normalize_code(row.get(code_col) if code_col else None)
        if not code:
            continue
        rows.append(
            {
                "stock_code": code,
                "stock_name": str(row.get(name_col) or "").strip() if name_col else None,
                "pct_chg": parse_number(row.get(pct_col) if pct_col else None),
                "latest_price": parse_number(row.get(price_col) if price_col else None),
            }
        )
    return rows


def sync_quote_types(
    conn,
    types: Sequence[str],
    name_map: Dict[str, str],
    sleep_s: float,
) -> Dict[str, int]:
    trade_date = resolve_trade_date()
    synced_at = datetime.now()
    result: Dict[str, int] = {}
    # CONCEPT/THEME 同源，只拉一次
    pulled: Dict[str, List[Dict[str, Any]]] = {}
    errors: List[str] = []
    print(f"板块行情落库交易日={trade_date}")
    try:
        zt_ctx = load_zt_context(trade_date)
    except Exception as ex:
        print(f"涨停池拉取失败，涨停家数/连板高度将为空: {ex}", file=sys.stderr)
        zt_ctx = {"rows": [], "ssbk_map": {}}
    for board_type in types:
        source_key = "CONCEPT" if board_type == "THEME" else board_type
        if source_key not in pulled:
            print(f"拉取 {source_key} 行情...")
            try:
                spots = fetch_board_spot(source_key)
                fund_map = fetch_fund_flow_by_name(source_key)
                for it in spots:
                    if it["name"] in fund_map:
                        it["net_inflow"] = fund_map[it["name"]]
                    if not it.get("lead_stock_code"):
                        lead_name = it.get("lead_stock_name")
                        if lead_name and lead_name in name_map:
                            it["lead_stock_code"] = name_map[lead_name]
                pulled[source_key] = spots
            except Exception as ex:
                conn.rollback()
                msg = f"{source_key} 拉取失败: {ex}"
                errors.append(msg)
                print("板块行情拉取异常：" + msg, file=sys.stderr)
                pulled[source_key] = []
            if sleep_s > 0:
                time.sleep(sleep_s)
        items = pulled.get(source_key) or []
        if not items:
            result[board_type] = 0
            print(f"{board_type} 行情为空，已跳过")
            continue
        upsert_basic(conn, board_type, items)
        n = upsert_quotes(conn, board_type, trade_date, synced_at, items, zt_ctx)
        conn.commit()
        result[board_type] = n
        print(f"{board_type} 行情写入完成，写入数={n}")
    if errors and not any(result.values()):
        raise RuntimeError(" ; ".join(errors))
    if errors:
        print("部分类型失败但仍有成功写入: " + " | ".join(errors), file=sys.stderr)
    return result


def resolve_sector_meta(conn, board_type: str, codes: Sequence[str]) -> List[Tuple[str, str, str]]:
    """返回 (code, name, board_type)。"""
    if codes:
        placeholders = ",".join(["%s"] * len(codes))
        sql = (
            f"SELECT code, name, board_type FROM sector_basic "
            f"WHERE deleted = 0 AND board_type = %s AND code IN ({placeholders})"
        )
        params: List[Any] = [board_type, *codes]
        with conn.cursor() as cur:
            cur.execute(sql, params)
            rows = cur.fetchall()
        if rows:
            return [(r["code"], r["name"], r["board_type"]) for r in rows]
        # 库中无记录时仍允许按代码拉成分
        return [(c, c, board_type) for c in codes]

    with conn.cursor() as cur:
        cur.execute(
            "SELECT code, name, board_type FROM sector_basic WHERE deleted = 0 AND board_type = %s",
            (board_type,),
        )
        rows = cur.fetchall()
    return [(r["code"], r["name"], r["board_type"]) for r in rows]


def sync_cons_types(
    conn,
    types: Sequence[str],
    codes: Sequence[str],
    sleep_s: float,
    limit: Optional[int],
) -> Dict[str, int]:
    trade_date = resolve_trade_date()
    synced_at = datetime.now()
    result: Dict[str, int] = {}
    print(f"板块成分落库交易日={trade_date}")
    for board_type in types:
        metas = resolve_sector_meta(conn, board_type, codes)
        if limit and limit > 0:
            metas = metas[:limit]
        total = 0
        for idx, (code, name, bt) in enumerate(metas, 1):
            try:
                cons = fetch_constituents(bt, code, name)
                n = replace_constituents(conn, code, bt, trade_date, synced_at, cons)
                conn.commit()
                total += n
                print(f"[{idx}/{len(metas)}] {bt} {code} {name} 成分股数={n}")
            except Exception as ex:
                conn.rollback()
                print(f"[{idx}/{len(metas)}] {bt} {code} 同步失败，异常={ex}", file=sys.stderr)
            if sleep_s > 0:
                time.sleep(sleep_s)
        result[board_type] = total
    return result


def parse_types(raw: str) -> List[str]:
    parts = [p.strip().upper() for p in (raw or "").split(",") if p.strip()]
    if not parts:
        return list(ALL_TYPES)
    bad = [p for p in parts if p not in ALL_TYPES]
    if bad:
        raise SystemExit(f"未知板块类型：{bad}，可选 {ALL_TYPES}")
    # 去重保序
    seen: Set[str] = set()
    out: List[str] = []
    for p in parts:
        if p not in seen:
            seen.add(p)
            out.append(p)
    return out


def main() -> None:
    parser = argparse.ArgumentParser(description="同步东财板块行情/资金流/成分股")
    parser.add_argument("--mode", choices=("list", "quote", "cons", "all"), default="quote")
    parser.add_argument("--types", default="INDUSTRY,CONCEPT,THEME")
    parser.add_argument("--codes", default="", help="成分股模式限定板块代码，逗号分隔")
    parser.add_argument("--limit", type=int, default=0, help="成分股模式最多同步 N 个板块")
    parser.add_argument("--sleep", type=float, default=0.35)
    args = parser.parse_args()

    load_env()
    types = parse_types(args.types)
    codes = [normalize_board_code(c) for c in args.codes.split(",") if c.strip()]
    codes = [c for c in codes if c]

    conn = db_conn()
    try:
        name_map = load_name_map(conn)
        if args.mode in ("list", "quote", "all"):
            sync_quote_types(conn, types, name_map, args.sleep)
        if args.mode in ("cons", "all"):
            # all 模式默认不拉全市场成分（太慢），除非显式 --codes 或 --limit
            if args.mode == "all" and not codes and not args.limit:
                print("全量模式跳过全部成分股；请用 --mode cons --codes BKxxxx 或 --limit N")
            else:
                lim = args.limit if args.limit > 0 else None
                sync_cons_types(conn, types, codes, args.sleep, lim)
        print("全部完成")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
