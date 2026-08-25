#!/usr/bin/env python3
"""同步公开机构研报、可审计游资席位与已授权账号原帖。"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
import urllib.parse
import urllib.request
from datetime import date, datetime, timedelta
from email.utils import parsedate_to_datetime
from pathlib import Path
from typing import Any, Dict, List, Optional
from xml.etree import ElementTree

import pymysql
from pymysql.cursors import DictCursor

try:
    from dotenv import load_dotenv
except ImportError:  # pragma: no cover
    load_dotenv = None


ROOT = Path(__file__).resolve().parent
REPORT_LIST_URL = "https://reportapi.eastmoney.com/report/list"
ACTIVE_SEAT_URL = "https://datacenter-web.eastmoney.com/api/data/v1/get"
REGISTRY_PATH = ROOT / "market_opinion_registry.json"


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


def request_text(url: str) -> str:
    request = urllib.request.Request(
        url,
        headers={
            "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/122 Safari/537.36",
            "Accept": "application/atom+xml,application/rss+xml,application/xml,text/xml,*/*",
        },
    )
    with urllib.request.urlopen(request, timeout=20) as response:
        return response.read().decode("utf-8", "ignore")


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


def parse_feed_datetime(value: Any) -> Optional[datetime]:
    parsed = parse_datetime(value)
    if parsed is not None:
        return parsed
    try:
        published_at = datetime.fromisoformat(str(value or "").strip().replace("Z", "+00:00"))
        return published_at.replace(tzinfo=None)
    except ValueError:
        pass
    try:
        published_at = parsedate_to_datetime(str(value or "").strip())
    except (TypeError, ValueError):
        return None
    if published_at is None:
        return None
    return published_at.replace(tzinfo=None)


def external_id(source: str, source_key: str) -> str:
    return hashlib.md5(f"{source}|{source_key}".encode("utf-8")).hexdigest()


def direction_of_rating(rating: Any) -> str:
    text = str(rating or "").strip()
    if any(keyword in text for keyword in ("买入", "增持", "推荐", "看多")):
        return text
    if any(keyword in text for keyword in ("卖出", "减持", "回避", "看空")):
        return text
    return text or "未评级"


def load_registry(registry_path: Path = REGISTRY_PATH) -> Dict[str, List[Dict[str, Any]]]:
    if not registry_path.is_file():
        return {"actors": [], "seat_mappings": []}
    try:
        registry = json.loads(registry_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as ex:
        raise ValueError(f"市场主体白名单配置无效：{ex}") from ex
    if not isinstance(registry, dict):
        raise ValueError("市场主体白名单配置必须是对象")
    actors = registry.get("actors")
    seat_mappings = registry.get("seat_mappings")
    return {
        "actors": actors if isinstance(actors, list) else [],
        "seat_mappings": seat_mappings if isinstance(seat_mappings, list) else [],
    }


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
            "actor_name": None,
            "actor_type": None,
            "actor_confidence": None,
            "actor_evidence_url": None,
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
    start_day = (snapshot_time.date() - timedelta(days=5)).isoformat()
    end_day = snapshot_time.date().isoformat()
    payload = request_json(ACTIVE_SEAT_URL, {
        "sortColumns": "TOTAL_NETAMT,ONLIST_DATE,OPERATEDEPT_CODE",
        "sortTypes": "-1,-1,1", "pageSize": min(max(limit, 20), 100), "pageNumber": 1,
        "reportName": "RPT_OPERATEDEPT_ACTIVE", "columns": "ALL", "source": "WEB", "client": "WEB",
        "filter": f"(ONLIST_DATE>='{start_day}')(ONLIST_DATE<='{end_day}')",
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
            "actor_name": None,
            "actor_type": None,
            "actor_confidence": None,
            "actor_evidence_url": None,
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


def enrich_active_seat_rows(
    seat_rows: List[Dict[str, Any]],
    seat_mappings: List[Dict[str, Any]],
) -> List[Dict[str, Any]]:
    enriched_rows: List[Dict[str, Any]] = []
    for seat_row in seat_rows:
        enriched_row = dict(seat_row)
        enriched_row.setdefault("actor_name", None)
        enriched_row.setdefault("actor_type", None)
        enriched_row.setdefault("actor_confidence", None)
        enriched_row.setdefault("actor_evidence_url", None)
        seat_name = str(enriched_row.get("subject_name") or "").strip()
        for seat_mapping in seat_mappings:
            seat_keyword = str(seat_mapping.get("seat_keyword") or "").strip()
            evidence_url = str(seat_mapping.get("evidence_url") or "").strip()
            actor_name = str(seat_mapping.get("actor_name") or "").strip()
            if seat_keyword and evidence_url and actor_name and seat_keyword in seat_name:
                enriched_row["actor_name"] = actor_name
                enriched_row["actor_type"] = "SEAT"
                enriched_row["actor_confidence"] = str(seat_mapping.get("confidence") or "SEAT_LABEL").strip()
                enriched_row["actor_evidence_url"] = evidence_url
                break
        enriched_rows.append(enriched_row)
    return enriched_rows


def element_text(element: ElementTree.Element, tags: tuple[str, ...]) -> str:
    for child in element:
        tag_name = child.tag.rsplit("}", 1)[-1]
        if tag_name in tags:
            text = "".join(child.itertext()).strip()
            if text:
                return text
    return ""


def element_link(element: ElementTree.Element) -> str:
    fallback_url = ""
    for child in element:
        if child.tag.rsplit("}", 1)[-1] != "link":
            continue
        href = str(child.attrib.get("href") or "").strip()
        if href and not href.startswith(("https://", "http://")):
            continue
        if href and str(child.attrib.get("rel") or "alternate").strip() == "alternate":
            return href
        if href and not fallback_url:
            fallback_url = href
        text = "".join(child.itertext()).strip()
        if text.startswith(("https://", "http://")):
            return text
    return fallback_url


def parse_public_account_feed(
    account: Dict[str, Any],
    feed_xml: str,
    snapshot_time: datetime,
) -> List[Dict[str, Any]]:
    if str(account.get("source_status") or "").strip() != "READY":
        return []
    actor_code = str(account.get("actor_code") or "").strip()
    actor_name = str(account.get("actor_name") or "").strip()
    account_url = str(account.get("account_url") or "").strip()
    platform = str(account.get("platform") or "公开订阅源").strip()
    if not actor_code or not actor_name or not account_url.startswith(("https://", "http://")):
        return []
    try:
        root = ElementTree.fromstring(feed_xml)
    except ElementTree.ParseError:
        return []
    rows: List[Dict[str, Any]] = []
    for element in root.iter():
        if element.tag.rsplit("}", 1)[-1] not in ("item", "entry"):
            continue
        title = element_text(element, ("title",))
        url = element_link(element)
        published_at = parse_feed_datetime(element_text(element, ("published", "updated", "pubDate", "date")))
        if not title or not url or published_at is None:
            continue
        summary = element_text(element, ("summary", "content", "description"))
        rows.append({
            "opinion_type": "KOL",
            "source": f"PUBLIC_{platform}"[:32],
            "external_id": external_id("PUBLIC_ACCOUNT", f"{actor_code}|{url}"),
            "subject_name": actor_name,
            "actor_name": actor_name,
            "actor_type": "KOL",
            "actor_confidence": "OFFICIAL_ACCOUNT",
            "actor_evidence_url": account_url,
            "title": title,
            "summary": summary or "公开原帖未提供摘要",
            "direction": "公开观点",
            "related_code": None,
            "related_name": None,
            "topic": None,
            "net_amount": None,
            "url": url,
            "published_at": published_at,
            "snapshot_time": snapshot_time,
        })
    return rows


def fetch_public_account_posts(actors: List[Dict[str, Any]], snapshot_time: datetime) -> List[Dict[str, Any]]:
    rows: List[Dict[str, Any]] = []
    for actor in actors:
        if str(actor.get("actor_type") or "").strip() != "KOL":
            continue
        feed_url = str(actor.get("feed_url") or "").strip()
        if str(actor.get("source_status") or "").strip() != "READY" or not feed_url:
            continue
        try:
            rows.extend(parse_public_account_feed(actor, request_text(feed_url), snapshot_time))
        except Exception as ex:
            print(f"公开账号原帖抓取失败，主体={actor.get('actor_name')}，原因={ex}", file=sys.stderr)
    return rows


def upsert_actor_registry(connection, registry: Dict[str, List[Dict[str, Any]]]) -> None:
    actors = registry["actors"]
    actor_rows = []
    actor_names: Dict[str, str] = {}
    for actor in actors:
        actor_code = str(actor.get("actor_code") or "").strip()
        actor_name = str(actor.get("actor_name") or "").strip()
        actor_type = str(actor.get("actor_type") or "").strip()
        source_status = str(actor.get("source_status") or "").strip()
        if not actor_code or not actor_name or not actor_type or not source_status:
            continue
        actor_names[actor_code] = actor_name
        actor_rows.append((
            actor_code, actor_name, actor_type, str(actor.get("platform") or "").strip() or None,
            str(actor.get("account_url") or "").strip() or None,
            str(actor.get("feed_url") or "").strip() or None, source_status,
            str(actor.get("source_note") or "").strip() or None,
        ))
    if actor_rows:
        with connection.cursor() as cursor:
            cursor.executemany("""
                INSERT INTO market_actor (
                  actor_code, actor_name, actor_type, platform, account_url, feed_url, source_status, source_note,
                  create_time, update_time, deleted
                ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, NOW(), NOW(), 0)
                ON DUPLICATE KEY UPDATE
                  actor_name = VALUES(actor_name), actor_type = VALUES(actor_type), platform = VALUES(platform),
                  account_url = VALUES(account_url), feed_url = VALUES(feed_url), source_status = VALUES(source_status),
                  source_note = VALUES(source_note), update_time = NOW(), deleted = 0
            """, actor_rows)
    seat_rows = []
    for seat_mapping in registry["seat_mappings"]:
        actor_code = str(seat_mapping.get("actor_code") or "").strip()
        seat_keyword = str(seat_mapping.get("seat_keyword") or "").strip()
        evidence_url = str(seat_mapping.get("evidence_url") or "").strip()
        if actor_code not in actor_names or not seat_keyword or not evidence_url:
            continue
        seat_rows.append((
            actor_code, seat_keyword, str(seat_mapping.get("confidence") or "SEAT_LABEL").strip(), evidence_url,
            str(seat_mapping.get("source_note") or "").strip() or None,
        ))
    if seat_rows:
        with connection.cursor() as cursor:
            cursor.executemany("""
                INSERT INTO market_actor_seat (
                  actor_code, seat_keyword, confidence, evidence_url, source_note, create_time, update_time, deleted
                ) VALUES (%s, %s, %s, %s, %s, NOW(), NOW(), 0)
                ON DUPLICATE KEY UPDATE
                  confidence = VALUES(confidence), evidence_url = VALUES(evidence_url),
                  source_note = VALUES(source_note), update_time = NOW(), deleted = 0
            """, seat_rows)


def upsert_rows(connection, rows: List[Dict[str, Any]]) -> int:
    if not rows:
        return 0
    sql = """
    INSERT INTO market_opinion (
      opinion_type, source, external_id, subject_name, actor_name, actor_type, actor_confidence, actor_evidence_url,
      title, summary, direction,
      related_code, related_name, topic, net_amount, url, published_at, snapshot_time,
      create_time, update_time, deleted
    ) VALUES (
      %(opinion_type)s, %(source)s, %(external_id)s, %(subject_name)s, %(actor_name)s, %(actor_type)s,
      %(actor_confidence)s, %(actor_evidence_url)s, %(title)s, %(summary)s, %(direction)s,
      %(related_code)s, %(related_name)s, %(topic)s, %(net_amount)s, %(url)s, %(published_at)s, %(snapshot_time)s,
      NOW(), NOW(), 0
    ) ON DUPLICATE KEY UPDATE
      subject_name = VALUES(subject_name), actor_name = VALUES(actor_name), actor_type = VALUES(actor_type),
      actor_confidence = VALUES(actor_confidence), actor_evidence_url = VALUES(actor_evidence_url),
      title = VALUES(title), summary = VALUES(summary),
      direction = VALUES(direction), related_code = VALUES(related_code), related_name = VALUES(related_name),
      topic = VALUES(topic), net_amount = VALUES(net_amount), url = VALUES(url),
      published_at = VALUES(published_at), snapshot_time = VALUES(snapshot_time), update_time = NOW(), deleted = 0
    """
    with connection.cursor() as cursor:
        cursor.executemany(sql, rows)
    return len(rows)


def main() -> int:
    parser = argparse.ArgumentParser(description="同步公开机构研报、可审计游资席位与已授权账号原帖")
    parser.add_argument("--limit", type=int, default=80)
    args = parser.parse_args()
    load_env()
    snapshot_time = datetime.now()
    registry = load_registry()
    try:
        institution_rows = fetch_institution_reports(args.limit, snapshot_time)
        seat_rows = enrich_active_seat_rows(fetch_active_seats(args.limit, snapshot_time), registry["seat_mappings"])
        kol_rows = fetch_public_account_posts(registry["actors"], snapshot_time)
    except Exception as ex:
        print(f"市场观点抓取失败，异常={ex}", file=sys.stderr)
        return 1
    if not institution_rows and not seat_rows:
        print("市场观点抓取为空，未更新本地快照", file=sys.stderr)
        return 1
    connection = db_conn()
    try:
        upsert_actor_registry(connection, registry)
        institution_count = upsert_rows(connection, institution_rows)
        seat_count = upsert_rows(connection, seat_rows)
        kol_count = upsert_rows(connection, kol_rows)
        connection.commit()
        print(f"市场观点同步完成：机构研报 {institution_count} 条，活跃席位 {seat_count} 条，公开账号原帖 {kol_count} 条")
        return 0
    except Exception as ex:
        connection.rollback()
        print(f"市场观点入库失败，异常={ex}", file=sys.stderr)
        return 1
    finally:
        connection.close()


if __name__ == "__main__":
    sys.exit(main())
