#!/usr/bin/env python3
"""同步北向资金、个股资金流和龙虎榜快照到 Apex MySQL。"""

from __future__ import annotations

import argparse
import math
import os
import re
import sys
import time as time_module
from datetime import date, datetime, time, timedelta
from decimal import Decimal, InvalidOperation, ROUND_HALF_UP
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional, Sequence, Tuple

import pymysql
from pymysql.cursors import DictCursor

try:
    from dotenv import load_dotenv
except ImportError:  # pragma: no cover
    load_dotenv = None


ROOT = Path(__file__).resolve().parent
YUAN_PER_YI = Decimal("100000000")
MONEY_PRECISION = Decimal("0.01")
SOURCE_REQUEST_ATTEMPTS = 3
DRAGON_TIGER_PUBLISH_TIME = time(17, 30)


def load_env() -> None:
    """加载采集脚本的本地数据库配置。"""
    if load_dotenv is not None:
        load_dotenv(ROOT / ".env")
        load_dotenv(ROOT / ".env.example", override=False)


def db_conn():
    """创建非自动提交的 MySQL 连接。"""
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


def parse_decimal(value: Any) -> Optional[Decimal]:
    """解析普通数值或百分数字符串。"""
    if value is None or isinstance(value, bool):
        return None
    if isinstance(value, float) and (math.isnan(value) or math.isinf(value)):
        return None
    text = str(value).strip().replace(",", "").replace("%", "")
    if not text or text.lower() in {"-", "--", "none", "nan", "nat", "null"}:
        return None
    try:
        number = Decimal(text)
    except (InvalidOperation, ValueError):
        return None
    return number if number.is_finite() else None


def parse_amount(value: Any, default_unit: Decimal = Decimal("1")) -> Optional[Decimal]:
    """将带万/亿单位或指定默认单位的金额转换为元。"""
    if value is None or isinstance(value, bool):
        return None
    text = str(value).strip().replace(",", "")
    unit = default_unit
    if text.endswith("亿元"):
        unit = YUAN_PER_YI
        text = text[:-2]
    elif text.endswith("亿"):
        unit = YUAN_PER_YI
        text = text[:-1]
    elif text.endswith("万元"):
        unit = Decimal("10000")
        text = text[:-2]
    elif text.endswith("万"):
        unit = Decimal("10000")
        text = text[:-1]
    number = parse_decimal(text)
    if number is None:
        return None
    return (number * unit).quantize(MONEY_PRECISION, rounding=ROUND_HALF_UP)


def parse_date(value: Any) -> Optional[date]:
    """兼容日期对象和东财常见日期字符串。"""
    if value is None:
        return None
    if isinstance(value, datetime):
        return value.date()
    if isinstance(value, date):
        return value
    if hasattr(value, "date"):
        parsed_date = value.date()
        if isinstance(parsed_date, date):
            return parsed_date
    text = str(value).strip()
    if not text or text.lower() in {"none", "nan", "nat", "null"}:
        return None
    for date_format in ("%Y-%m-%d", "%Y%m%d", "%Y/%m/%d"):
        try:
            return datetime.strptime(text[:10], date_format).date()
        except ValueError:
            continue
    return None


def normalize_code(value: Any) -> Optional[str]:
    """规范为六位 A 股代码。"""
    text = str(value or "").strip().upper()
    match = re.search(r"(\d{6})", text)
    return match.group(1) if match else None


def first_value(row: Any, aliases: Sequence[str]) -> Any:
    """按优先级读取数据源兼容列。"""
    for column_name in aliases:
        if column_name in row:
            return row.get(column_name)
    return None


def parse_northbound_rows(frame: Any) -> List[Dict[str, Any]]:
    """解析沪深港通历史数据，接口数值单位为亿元。"""
    rows: List[Dict[str, Any]] = []
    for _, source_row in frame.iterrows():
        trade_date = parse_date(first_value(source_row, ("日期", "交易日期", "trade_date")))
        if trade_date is None:
            continue
        net_buy_amount = parse_amount(
            first_value(source_row, ("当日成交净买额", "当日净买额", "净买额", "net_buy_amount")),
            YUAN_PER_YI,
        )
        rows.append({
            "trade_date": trade_date,
            "net_buy_amount": net_buy_amount,
            "buy_amount": parse_amount(
                first_value(source_row, ("买入成交额", "买入额", "buy_amount")),
                YUAN_PER_YI,
            ),
            "sell_amount": parse_amount(
                first_value(source_row, ("卖出成交额", "卖出额", "sell_amount")),
                YUAN_PER_YI,
            ),
            "cumulative_net_buy_amount": parse_amount(
                first_value(source_row, ("历史累计净买额", "累计净买额", "cumulative_net_buy_amount")),
                YUAN_PER_YI,
            ),
            "data_status": "PUBLISHED" if net_buy_amount is not None else "NOT_DISCLOSED",
        })
    return rows


def parse_stock_fund_flow_rows(frame: Any, trade_date: date) -> List[Dict[str, Any]]:
    """解析个股资金流排名，金额源单位为元。"""
    rows: List[Dict[str, Any]] = []
    for _, source_row in frame.iterrows():
        code = normalize_code(first_value(source_row, ("股票代码", "代码", "code")))
        name = str(first_value(source_row, ("股票简称", "名称", "name")) or "").strip()
        if code is None or not name:
            continue
        rows.append({
            "code": code,
            "name": name,
            "trade_date": trade_date,
            "pct_chg": parse_decimal(first_value(source_row, ("今日涨跌幅", "涨跌幅", "pct_chg"))),
            "main_net_inflow": parse_amount(first_value(source_row, (
                "今日主力净流入-净额", "主力净流入-净额", "主力净流入", "main_net_inflow",
            ))),
            "main_net_inflow_pct": parse_decimal(first_value(source_row, (
                "今日主力净流入-净占比", "主力净流入-净占比", "主力净占比", "main_net_inflow_pct",
            ))),
            "super_large_net_inflow": parse_amount(first_value(source_row, (
                "今日超大单净流入-净额", "超大单净流入-净额", "超大单净流入", "super_large_net_inflow",
            ))),
            "large_net_inflow": parse_amount(first_value(source_row, (
                "今日大单净流入-净额", "大单净流入-净额", "大单净流入", "large_net_inflow",
            ))),
            "medium_net_inflow": parse_amount(first_value(source_row, (
                "今日中单净流入-净额", "中单净流入-净额", "中单净流入", "medium_net_inflow",
            ))),
            "small_net_inflow": parse_amount(first_value(source_row, (
                "今日小单净流入-净额", "小单净流入-净额", "小单净流入", "small_net_inflow",
            ))),
        })
    return rows


def parse_dragon_tiger_rows(
    frame: Any,
    fallback_trade_date: Optional[date] = None,
) -> List[Dict[str, Any]]:
    """解析龙虎榜明细，金额源单位为元。"""
    rows: List[Dict[str, Any]] = []
    for _, source_row in frame.iterrows():
        code = normalize_code(first_value(source_row, ("代码", "股票代码", "code")))
        name = str(first_value(source_row, ("名称", "股票简称", "name")) or "").strip()
        trade_date = parse_date(first_value(source_row, ("上榜日", "上榜日期", "交易日期", "日期")))
        trade_date = trade_date or fallback_trade_date
        reason = str(first_value(source_row, ("上榜原因", "解读", "原因", "reason")) or "").strip()
        if code is None or not name or trade_date is None or not reason:
            continue
        rows.append({
            "code": code,
            "name": name,
            "trade_date": trade_date,
            "reason": reason,
            "close_price": parse_decimal(first_value(source_row, ("收盘价", "close_price"))),
            "pct_chg": parse_decimal(first_value(source_row, ("涨跌幅", "pct_chg"))),
            "turnover_rate": parse_decimal(first_value(source_row, ("换手率", "turnover_rate"))),
            "net_buy_amount": parse_amount(first_value(source_row, (
                "龙虎榜净买额", "龙虎榜净买入额", "净买额", "net_buy_amount",
            ))),
            "buy_amount": parse_amount(first_value(source_row, (
                "龙虎榜买入额", "买入额", "buy_amount",
            ))),
            "sell_amount": parse_amount(first_value(source_row, (
                "龙虎榜卖出额", "卖出额", "sell_amount",
            ))),
            "amount": parse_amount(first_value(source_row, (
                "龙虎榜成交额", "成交额", "amount",
            ))),
        })
    return rows


def recent_trade_date(today: Optional[date] = None) -> date:
    """按项目统一 A 股交易日规则解析最近交易日。"""
    from sync_sector import resolve_trade_date

    return resolve_trade_date(today)


def resolve_dragon_tiger_trade_date(now: Optional[datetime] = None) -> date:
    """返回已发布龙虎榜对应的最近交易日。"""
    current_time = now or datetime.now()
    if current_time.time() < DRAGON_TIGER_PUBLISH_TIME:
        return recent_trade_date(current_time.date() - timedelta(days=1))
    return recent_trade_date(current_time.date())


def fetch_with_retry(fetch_action, source_name: str):
    """重试可恢复的行情源网络异常。"""
    for attempt in range(1, SOURCE_REQUEST_ATTEMPTS + 1):
        try:
            return fetch_action()
        except (ConnectionError, TimeoutError, OSError) as ex:
            if attempt == SOURCE_REQUEST_ATTEMPTS:
                raise
            print(f"{source_name} 请求失败，第 {attempt} 次重试：{ex}", file=sys.stderr)
            time_module.sleep(attempt)


def is_empty_dragon_tiger_response(ex: Exception) -> bool:
    """识别东财未发布榜单时 AkShare 的空响应异常。"""
    return isinstance(ex, TypeError) and str(ex) == "'NoneType' object is not subscriptable"


def _upsert_northbound(conn: Any, rows: Iterable[Dict[str, Any]], synced_at: datetime) -> None:
    sql = """
        INSERT INTO northbound_flow
          (trade_date, net_buy_amount, buy_amount, sell_amount, cumulative_net_buy_amount,
           data_status, synced_at, deleted)
        VALUES (%s, %s, %s, %s, %s, %s, %s, 0)
        ON DUPLICATE KEY UPDATE
          net_buy_amount = VALUES(net_buy_amount),
          buy_amount = VALUES(buy_amount),
          sell_amount = VALUES(sell_amount),
          cumulative_net_buy_amount = VALUES(cumulative_net_buy_amount),
          data_status = VALUES(data_status),
          synced_at = VALUES(synced_at),
          deleted = 0
    """
    params = [(
        row["trade_date"], row["net_buy_amount"], row["buy_amount"], row["sell_amount"],
        row["cumulative_net_buy_amount"], row["data_status"], synced_at,
    ) for row in rows]
    with conn.cursor() as cursor:
        cursor.executemany(sql, params)


def _upsert_stock_fund_flow(conn: Any, rows: Iterable[Dict[str, Any]], synced_at: datetime) -> None:
    sql = """
        INSERT INTO stock_fund_flow
          (code, name, trade_date, pct_chg, main_net_inflow, main_net_inflow_pct,
           super_large_net_inflow, large_net_inflow, medium_net_inflow, small_net_inflow,
           synced_at, deleted)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 0)
        ON DUPLICATE KEY UPDATE
          name = VALUES(name),
          pct_chg = VALUES(pct_chg),
          main_net_inflow = VALUES(main_net_inflow),
          main_net_inflow_pct = VALUES(main_net_inflow_pct),
          super_large_net_inflow = VALUES(super_large_net_inflow),
          large_net_inflow = VALUES(large_net_inflow),
          medium_net_inflow = VALUES(medium_net_inflow),
          small_net_inflow = VALUES(small_net_inflow),
          synced_at = VALUES(synced_at),
          deleted = 0
    """
    params = [(
        row["code"], row["name"], row["trade_date"], row["pct_chg"], row["main_net_inflow"],
        row["main_net_inflow_pct"], row["super_large_net_inflow"], row["large_net_inflow"],
        row["medium_net_inflow"], row["small_net_inflow"], synced_at,
    ) for row in rows]
    with conn.cursor() as cursor:
        cursor.executemany(sql, params)


def _upsert_dragon_tiger(conn: Any, rows: Iterable[Dict[str, Any]], synced_at: datetime) -> None:
    sql = """
        INSERT INTO dragon_tiger_item
          (code, name, trade_date, reason, close_price, pct_chg, turnover_rate, net_buy_amount,
           buy_amount, sell_amount, amount, synced_at, deleted)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 0)
        ON DUPLICATE KEY UPDATE
          name = VALUES(name),
          close_price = VALUES(close_price),
          pct_chg = VALUES(pct_chg),
          turnover_rate = VALUES(turnover_rate),
          net_buy_amount = VALUES(net_buy_amount),
          buy_amount = VALUES(buy_amount),
          sell_amount = VALUES(sell_amount),
          amount = VALUES(amount),
          synced_at = VALUES(synced_at),
          deleted = 0
    """
    params = [(
        row["code"], row["name"], row["trade_date"], row["reason"], row["close_price"],
        row["pct_chg"], row["turnover_rate"], row["net_buy_amount"], row["buy_amount"],
        row["sell_amount"], row["amount"], synced_at,
    ) for row in rows]
    with conn.cursor() as cursor:
        cursor.executemany(sql, params)


def sync_northbound_flow(conn: Any, akshare_client: Any) -> int:
    """同步北向资金历史快照，空结果不修改数据库。"""
    try:
        frame = akshare_client.stock_hsgt_hist_em(symbol="北向资金")
        rows = parse_northbound_rows(frame)
        if not rows:
            print("北向资金结果为空，保留旧快照")
            return 0
        latest_row = max(rows, key=lambda row: row["trade_date"])
        rows = [latest_row]
        _upsert_northbound(conn, rows, datetime.now())
        conn.commit()
        return len(rows)
    except Exception:
        conn.rollback()
        raise


def sync_stock_fund_flow(
    conn: Any,
    akshare_client: Any,
    trade_date: Optional[date] = None,
) -> int:
    """同步当日个股资金流排名，空结果不修改数据库。"""
    try:
        frame = fetch_with_retry(
            lambda: akshare_client.stock_individual_fund_flow_rank(indicator="今日"),
            "个股资金流",
        )
        rows = parse_stock_fund_flow_rows(frame, trade_date or recent_trade_date())
        if not rows:
            print("个股资金流结果为空，保留旧快照")
            return 0
        _upsert_stock_fund_flow(conn, rows, datetime.now())
        conn.commit()
        return len(rows)
    except Exception:
        conn.rollback()
        raise


def sync_dragon_tiger(
    conn: Any,
    akshare_client: Any,
    trade_date: Optional[date] = None,
) -> int:
    """同步指定交易日龙虎榜，空结果不修改数据库。"""
    target_date = trade_date or resolve_dragon_tiger_trade_date()
    date_text = target_date.strftime("%Y%m%d")
    try:
        frame = fetch_with_retry(
            lambda: akshare_client.stock_lhb_detail_em(start_date=date_text, end_date=date_text),
            "龙虎榜",
        )
    except Exception as ex:
        if is_empty_dragon_tiger_response(ex):
            print(f"龙虎榜 {target_date} 尚未发布，保留旧快照")
            return 0
        raise
    try:
        rows = parse_dragon_tiger_rows(frame, target_date)
        if not rows:
            print(f"龙虎榜 {target_date} 结果为空，保留旧快照")
            return 0
        _upsert_dragon_tiger(conn, rows, datetime.now())
        conn.commit()
        return len(rows)
    except Exception:
        conn.rollback()
        raise


def dataset_names(mode: str) -> Tuple[str, ...]:
    """返回模式对应的数据集，保持固定执行顺序。"""
    if mode == "stock":
        return ("stock_fund_flow",)
    if mode == "flow":
        return "northbound_flow", "stock_fund_flow"
    if mode == "lhb":
        return ("dragon_tiger",)
    if mode == "all":
        return "northbound_flow", "stock_fund_flow", "dragon_tiger"
    raise ValueError(f"未知同步模式: {mode}")


def run_mode(conn: Any, akshare_client: Any, mode: str) -> Dict[str, Any]:
    """按模式逐数据集同步；单源失败不会阻断后续数据集。"""
    sync_actions = {
        "northbound_flow": sync_northbound_flow,
        "stock_fund_flow": sync_stock_fund_flow,
        "dragon_tiger": sync_dragon_tiger,
    }
    counts: Dict[str, int] = {}
    errors: Dict[str, str] = {}
    for dataset_name in dataset_names(mode):
        try:
            row_count = sync_actions[dataset_name](conn, akshare_client)
            counts[dataset_name] = row_count
            print(f"{dataset_name} 同步完成，写入数={row_count}")
        except Exception as ex:
            errors[dataset_name] = str(ex)
            print(f"{dataset_name} 同步失败，异常={ex}", file=sys.stderr)
    print(f"成功数据源数={len(counts)}，失败数据源数={len(errors)}")
    return {"counts": counts, "errors": errors}


def main() -> int:
    """命令行入口。"""
    parser = argparse.ArgumentParser(description="同步北向资金、个股资金流和龙虎榜")
    parser.add_argument("--mode", choices=("stock", "flow", "lhb", "all"), default="all")
    args = parser.parse_args()

    load_env()
    import akshare as ak

    conn = db_conn()
    try:
        result = run_mode(conn, ak, args.mode)
    finally:
        conn.close()

    selected_count = len(dataset_names(args.mode))
    return 1 if len(result["errors"]) == selected_count else 0


if __name__ == "__main__":
    raise SystemExit(main())
