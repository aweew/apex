#!/usr/bin/env python3
"""同步公开机构研报与龙虎榜活跃席位到 market_opinion。"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
import urllib.parse
import urllib.request
from datetime import date, datetime, timedelta
from pathlib import Path
from typing import Any, Dict, List, Optional

import pymysql
from pymysql.cursors import DictCursor

try:
    from dotenv import load_dotenv
except ImportError:  # pragma: no cover
    load_dotenv = None


ROOT = Path(__file__).resolve().parent
REPORT_LIST_URL = "https://reportapi.eastmoney.com/report/list"
ACTIVE_SEAT_URL = "https://datacenter-web.eastmoney.com/api/data/v1/get"


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


def request_json(url: str, params: Dict[str, Any]) -> Dict[str, Any]:
    query = urllib.parse.urlencode(params)
    request = urllib.request.Request(
        f"{url}?{query}",
        headers={
            "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/122 Safari/537.36",
            "Referer": "https://data.eastmoney.com/",
            "Accept": "application/json,text/plain,*/*",
        },
    )
    with urllib.request.urlopen(request, timeout=20) as response:
        payload = json.loads(response.read().decode("utf-8", "ignore"))
    return payload if isinstance(payload, dict) else {}


def parse_datetime(value: Any) -> Optional[datetime]:
    if value is None:
        return None
    if isinstance(value, datetime):
        return value
    text = str(value).strip().replace("T", " ")
    if not text:
        return None
    for fmt in ("%Y-%m-%d %H:%M:%S.%f", "%Y-%m-%d %H:%M:%S", "%Y-%m-%d"):
        try:
            return datetime.strptime(text[:23], fmt)
        except ValueError:
            continue
    return None


def external_id(source: str, source_key: str) -> str:
    return hashlib.md5(f"{source}|{source_key}".encode("utf-8")).hexdigest()


def direction_of_rating(rating: Any) -> str:
    text = str(rating or "").strip()
    if any(keyword in text for keyword in ("买入", "增持", "推荐", "看多")):
        return text
    if any(keyword in text for keyword in ("卖出", "减持", "回避", "看空")):
        return text
    return text or "未评级"


def fetch_institution_reports(limit: int, snapshot_time: datetime) -> List[Dict[str, Any]]:
    start_day = (snapshot_time.date() - timedelta(days=4)).isoformat()
    end_day = (snapshot_time.date() + timedelta(days=1)).isoformat()
    payload = request_json(REPORT_LIST_URL, {
        "industryCode": "*", "pageSize": min(max(limit, 20), 100), "industry": "*",
        "rating": "*", "ratingChange": "*", "beginTime": start_day, "endTime": end_day,
        "pageNo": 1, "fields": "", "qType": 0, "orgCode": "", "code": "", "rcode": "",
        "p": 1, "pageNum": 1, "pageNumber": 1,
    })
    records = payload.get("data") if isinstance(payload.get("data"), list) else []
    rows: List[Dict[str, Any]] = []
    for record in records:
        if not isinstance(record, dict):
            continue
        title = str(record.get("title") or "").strip()
        subject_name = str(record.get("orgSName") or record.get("orgName") or "").strip()
        info_code = str(record.get("infoCode") or "").strip()
        published_at = parse_datetime(record.get("publishDate"))
        if not title or not subject_name or not info_code or published_at is None:
            continue
        rating = direction_of_rating(record.get("sRatingName") or record.get("emRatingName"))
        topic = str(record.get("indvInduName") or "").strip()
        related_name = str(record.get("stockName") or "").strip()
        rows.append({
            "opinion_type": "INSTITUTION",
            "source": "EASTMONEY_REPORT",
            "external_id": external_id("EASTMONEY_REPORT", info_code),
            "subject_name": subject_name,
            "title": title,
            "summary": f"{rating} · {topic or '行业未披露'}",
            "direction": rating,
            "related_code": str(record.get("stockCode") or "").strip() or None,
            "related_name": related_name or None,
            "topic": topic or None,
            "net_amount": None,
            "url": f"https://pdf.dfcfw.com/pdf/H3_{info_code}_1.pdf",
            "published_at": published_at,
            "snapshot_time": snapshot_time,
        })
    return rows


def fetch_active_seats(limit: int, snapshot_time: datetime) -> List[Dict[str, Any]]:
    target_day = snapshot_time.date().isoformat()
    payload = request_json(ACTIVE_SEAT_URL, {
        "sortColumns": "TOTAL_NETAMT,ONLIST_DATE,OPERATEDEPT_CODE",
        "sortTypes": "-1,-1,1", "pageSize": min(max(limit, 20), 100), "pageNumber": 1,
        "reportName": "RPT_OPERATEDEPT_ACTIVE", "columns": "ALL", "source": "WEB", "client": "WEB",
        "filter": f"(ONLIST_DATE>='{target_day}')(ONLIST_DATE<='{target_day}')",
    })
    result = payload.get("result") if isinstance(payload.get("result"), dict) else {}
    records = result.get("data") if isinstance(result.get("data"), list) else []
    rows: List[Dict[str, Any]] = []
    for record in records:
        if not isinstance(record, dict):
            continue
        subject_name = str(record.get("OPERATEDEPT_NAME") or "").strip()
        seat_code = str(record.get("OPERATEDEPT_CODE") or "").strip()
        published_at = parse_datetime(record.get("ONLIST_DATE"))
        if not subject_name or not seat_code or published_at is None:
            continue
        stock_names = str(record.get("SECURITY_NAME_ABBR") or "").strip()
        rows.append({
            "opinion_type": "ACTIVE_SEAT",
            "source": "EASTMONEY_ACTIVE_SEAT",
            "external_id": external_id("EASTMONEY_ACTIVE_SEAT", f"{seat_code}|{published_at.date()}"),
            "subject_name": subject_name,
            "title": "龙虎榜活跃席位",
            "summary": f"涉及：{stock_names or '未披露股票名称'}",
            "direction": "活跃席位",
            "related_code": None,
            "related_name": None,
            "topic": "龙虎榜",
            "net_amount": record.get("TOTAL_NETAMT"),
            "url": "https://data.eastmoney.com/stock/hyyyb.html",
            "published_at": published_at,
            "snapshot_time": snapshot_time,
        })
    return rows


def upsert_rows(connection, rows: List[Dict[str, Any]]) -> int:
    if not rows:
        return 0
    sql = """
    INSERT INTO market_opinion (
      opinion_type, source, external_id, subject_name, title, summary, direction,
      related_code, related_name, topic, net_amount, url, published_at, snapshot_time,
      create_time, update_time, deleted
    ) VALUES (
      %(opinion_type)s, %(source)s, %(external_id)s, %(subject_name)s, %(title)s, %(summary)s, %(direction)s,
      %(related_code)s, %(related_name)s, %(topic)s, %(net_amount)s, %(url)s, %(published_at)s, %(snapshot_time)s,
      NOW(), NOW(), 0
    ) ON DUPLICATE KEY UPDATE
      subject_name = VALUES(subject_name), title = VALUES(title), summary = VALUES(summary),
      direction = VALUES(direction), related_code = VALUES(related_code), related_name = VALUES(related_name),
      topic = VALUES(topic), net_amount = VALUES(net_amount), url = VALUES(url),
      published_at = VALUES(published_at), snapshot_time = VALUES(snapshot_time), update_time = NOW(), deleted = 0
    """
    with connection.cursor() as cursor:
        cursor.executemany(sql, rows)
    return len(rows)


def main() -> int:
    parser = argparse.ArgumentParser(description="同步公开机构研报与龙虎榜活跃席位")
    parser.add_argument("--limit", type=int, default=80)
    args = parser.parse_args()
    load_env()
    snapshot_time = datetime.now()
    try:
        institution_rows = fetch_institution_reports(args.limit, snapshot_time)
        seat_rows = fetch_active_seats(args.limit, snapshot_time)
    except Exception as ex:
        print(f"市场观点抓取失败，异常={ex}", file=sys.stderr)
        return 1
    if not institution_rows and not seat_rows:
        print("市场观点抓取为空，未更新本地快照", file=sys.stderr)
        return 1
    connection = db_conn()
    try:
        institution_count = upsert_rows(connection, institution_rows)
        seat_count = upsert_rows(connection, seat_rows)
        connection.commit()
        print(f"市场观点同步完成：机构研报 {institution_count} 条，活跃席位 {seat_count} 条")
        return 0
    except Exception as ex:
        connection.rollback()
        print(f"市场观点入库失败，异常={ex}", file=sys.stderr)
        return 1
    finally:
        connection.close()


if __name__ == "__main__":
    sys.exit(main())
