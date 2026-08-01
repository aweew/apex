#!/usr/bin/env python3
"""
多平台热点股票同步到 Apex MySQL（market_hot）

数据源：
  - eastmoney   : ak.stock_hot_rank_em（东财人气榜）
  - xueqiu      : ak.stock_hot_follow_xq（雪球关注热度 TopN）
  - baidu       : ak.stock_hot_search_baidu（百度热搜 A股/港股）

示例：
  python sync_hot.py
  python sync_hot.py --sources eastmoney,baidu --limit 50
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from datetime import datetime
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any, Dict, List, Optional, Sequence, Tuple

import pymysql
from pymysql.cursors import DictCursor

try:
    from dotenv import load_dotenv
except ImportError:  # pragma: no cover
    load_dotenv = None

ROOT = Path(__file__).resolve().parent
ALL_SOURCES = ("eastmoney", "xueqiu", "baidu")


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


def normalize_code(raw: Any) -> Optional[str]:
    text = str(raw or "").strip().upper()
    if not text:
        return None
    # SZ300058 / SH600519 / HK01810
    m = re.match(r"^(SH|SZ|BJ|HK)?(\d{4,6})$", text.replace(".", ""))
    if m:
        digits = m.group(2)
        if len(digits) <= 5:
            return digits.zfill(5)
        return digits[-6:]
    digits = re.sub(r"\D", "", text)
    if len(digits) >= 6:
        return digits[-6:]
    if 4 <= len(digits) <= 5:
        return digits.zfill(5)
    return None


def parse_number(val: Any) -> Optional[Decimal]:
    if val is None:
        return None
    if isinstance(val, bool):
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


def load_name_map(conn) -> Dict[str, str]:
    """名称 -> 代码（优先非 ST、短名称精确匹配）"""
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
            # 去掉常见后缀再映射一次
            for suffix in ("－Ｗ", "-W", "Ｗ", "A", "B"):
                if name.endswith(suffix) and name[: -len(suffix)] not in mapping:
                    mapping[name[: -len(suffix)]] = code
    return mapping


def resolve_name_to_code(name: str, name_map: Dict[str, str]) -> Optional[str]:
    text = (name or "").strip()
    if not text:
        return None
    # 名称/代码 混写：如 600519 贵州茅台
    m = re.search(r"(\d{4,6})", text)
    if m and re.fullmatch(r"\d{4,6}", text.replace(" ", "")):
        return normalize_code(text)
    if text in name_map:
        return name_map[text]
    # 百度偶发「名称 代码」
    parts = re.split(r"[\s/|]", text)
    for p in parts:
        code = normalize_code(p)
        if code:
            return code
        if p in name_map:
            return name_map[p]
    # 模糊：名称包含
    for k, code in name_map.items():
        if k == text or text.startswith(k) or k.startswith(text):
            return code
    return None


def upsert_rows(conn, source: str, snapshot_time: datetime, rows: List[Tuple]) -> int:
    """
    rows: (rank_no, code, name, price, pct_chg, heat_score, heat_text, payload_json)
    先软删同 source 旧快照，再插入
    """
    with conn.cursor() as cur:
        cur.execute(
            "UPDATE market_hot SET deleted = 1, update_time = NOW() "
            "WHERE source = %s AND deleted = 0",
            (source,),
        )
        sql = """
        INSERT INTO market_hot (
          source, snapshot_time, rank_no, code, name, price, pct_chg,
          heat_score, heat_text, payload, create_time, update_time, deleted
        ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,NOW(),NOW(),0)
        """
        params = []
        for rank_no, code, name, price, pct_chg, heat_score, heat_text, payload in rows:
            params.append(
                (
                    source,
                    snapshot_time,
                    rank_no,
                    code,
                    name,
                    price,
                    pct_chg,
                    heat_score,
                    heat_text,
                    payload,
                )
            )
        if params:
            cur.executemany(sql, params)
    conn.commit()
    return len(params)


def sync_eastmoney(conn, limit: int, snapshot_time: datetime) -> int:
    import time
    import akshare as ak

    df = None
    mode = "人气榜"
    last_err = None
    for attempt in range(3):
        try:
            df = ak.stock_hot_rank_em()
            last_err = None
            break
        except Exception as ex:
            last_err = ex
            time.sleep(1.2 * (attempt + 1))

    # 人气榜被掐时，降级为东财全市场成交额榜（仍标记 eastmoney）
    if df is None or getattr(df, "empty", True):
        print(f"eastmoney 人气榜不可用，尝试成交额榜: {last_err}", file=sys.stderr)
        spot = None
        for attempt in range(3):
            try:
                spot = ak.stock_zh_a_spot_em()
                break
            except Exception as ex:
                last_err = ex
                time.sleep(1.5 * (attempt + 1))
        if spot is None or getattr(spot, "empty", True):
            raise RuntimeError(f"eastmoney 人气/成交额均失败: {last_err}")
        amount_col = "成交额" if "成交额" in spot.columns else None
        if amount_col:
            spot = spot.sort_values(amount_col, ascending=False)
        else:
            spot = spot.sort_values("涨跌幅", ascending=False)
        df = spot.head(limit).copy()
        mode = "成交额榜"

    rows: List[Tuple] = []
    records = df.to_dict(orient="records")
    for i, item in enumerate(records, 1):
        if i > limit:
            break
        if mode == "人气榜":
            code = normalize_code(item.get("代码"))
            name = str(item.get("股票名称") or "").strip() or None
            price = parse_number(item.get("最新价"))
            pct = parse_number(item.get("涨跌幅"))
            rank_no = int(parse_number(item.get("当前排名")) or i)
            heat = Decimal(str(max(limit - rank_no + 1, 1)))
            heat_text = f"人气第{rank_no}"
        else:
            code = normalize_code(item.get("代码"))
            name = str(item.get("名称") or "").strip() or None
            price = parse_number(item.get("最新价"))
            pct = parse_number(item.get("涨跌幅"))
            rank_no = i
            heat = parse_number(item.get("成交额"))
            heat_text = f"成交额第{rank_no}"
        payload = json.dumps({k: str(v) for k, v in item.items()}, ensure_ascii=False)
        rows.append((rank_no, code, name, price, pct, heat, heat_text, payload))
    return upsert_rows(conn, "eastmoney", snapshot_time, rows)


def sync_xueqiu(conn, limit: int, snapshot_time: datetime) -> int:
    import akshare as ak

    df = ak.stock_hot_follow_xq(symbol="最热门")
    rows: List[Tuple] = []
    if df is None or df.empty:
        return upsert_rows(conn, "xueqiu", snapshot_time, rows)
    # 已按关注降序
    for i, item in enumerate(df.to_dict(orient="records"), 1):
        if i > limit:
            break
        code = normalize_code(item.get("股票代码"))
        name = str(item.get("股票简称") or "").strip() or None
        price = parse_number(item.get("最新价"))
        heat = parse_number(item.get("关注"))
        payload = json.dumps({k: str(v) for k, v in item.items()}, ensure_ascii=False)
        heat_text = f"关注 {heat}" if heat is not None else None
        rows.append((i, code, name, price, None, heat, heat_text, payload))
    return upsert_rows(conn, "xueqiu", snapshot_time, rows)


def sync_baidu(conn, limit: int, snapshot_time: datetime, name_map: Dict[str, str]) -> int:
    import akshare as ak

    rows: List[Tuple] = []
    rank = 0
    for market_label in ("A股", "港股"):
        try:
            df = ak.stock_hot_search_baidu(symbol=market_label)
        except Exception as ex:
            print(f"baidu {market_label} FAIL {ex}", file=sys.stderr)
            continue
        if df is None or df.empty:
            continue
        for item in df.to_dict(orient="records"):
            if rank >= limit:
                break
            raw_name = str(item.get("名称/代码") or "").strip()
            code = resolve_name_to_code(raw_name, name_map)
            pct = parse_number(item.get("涨跌幅"))
            heat = parse_number(item.get("综合热度"))
            rank += 1
            payload = json.dumps(
                {**{k: str(v) for k, v in item.items()}, "market": market_label},
                ensure_ascii=False,
            )
            rows.append(
                (
                    rank,
                    code,
                    raw_name,
                    None,
                    pct,
                    heat,
                    f"{market_label}热度 {heat}" if heat is not None else market_label,
                    payload,
                )
            )
        if rank >= limit:
            break
    return upsert_rows(conn, "baidu", snapshot_time, rows)


def main() -> int:
    load_env()
    parser = argparse.ArgumentParser(description="同步多平台热点到 Apex")
    parser.add_argument(
        "--sources",
        default="eastmoney,xueqiu,baidu",
        help="逗号分隔：eastmoney,xueqiu,baidu",
    )
    parser.add_argument("--limit", type=int, default=50, help="每源最多条数")
    args = parser.parse_args()

    sources = [s.strip() for s in args.sources.split(",") if s.strip()]
    for s in sources:
        if s not in ALL_SOURCES:
            print(f"未知源: {s}", file=sys.stderr)
            return 1
    limit = max(5, min(int(args.limit or 50), 200))
    snapshot_time = datetime.now().replace(microsecond=0)

    conn = db_conn()
    try:
        name_map = load_name_map(conn) if "baidu" in sources else {}
        print(f"snapshot={snapshot_time.isoformat()} sources={sources} limit={limit}")
        for source in sources:
            try:
                if source == "eastmoney":
                    n = sync_eastmoney(conn, limit, snapshot_time)
                elif source == "xueqiu":
                    n = sync_xueqiu(conn, limit, snapshot_time)
                else:
                    n = sync_baidu(conn, limit, snapshot_time, name_map)
                print(f"{source} ok rows={n}")
            except Exception as ex:
                conn.rollback()
                print(f"{source} FAIL {ex}", file=sys.stderr)
        return 0
    finally:
        conn.close()


if __name__ == "__main__":
    raise SystemExit(main())
