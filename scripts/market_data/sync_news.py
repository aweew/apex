#!/usr/bin/env python3
"""
多源财经新闻同步 → market_news

数据源：
  - eastmoney : 东财「要闻」栏目(np-listapi column=350) + ak.stock_info_global_em 快讯
  - cls       : ak.stock_info_global_cls（财联社电报）
  - ths       : ak.stock_info_global_ths（同花顺快讯）
  - sina      : ak.stock_info_global_sina（新浪财经）
  - cctv      : ak.news_cctv（央视新闻联播，较慢）

说明：
  App「要闻」≠「全球财经快讯」。旧逻辑只拉快讯，所以央行工作会议等要闻不会入库。

示例：
  python sync_news.py --sources eastmoney,cls,ths,sina --limit 80
  python sync_news.py --sources eastmoney,cls --limit 50
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
from datetime import date, datetime, time as dt_time, timedelta
from pathlib import Path
from typing import Any, Dict, List, Optional, Sequence, Tuple

import pymysql
from pymysql.cursors import DictCursor

try:
    from dotenv import load_dotenv
except ImportError:  # pragma: no cover
    load_dotenv = None

ROOT = Path(__file__).resolve().parent
ALL_SOURCES = ("eastmoney", "cls", "ths", "sina", "cctv")

POS_WORDS = ("上涨", "大涨", "增长", "突破", "利好", "盈利", "超预期", "创新高", "签署", "合作", "中标", "回购")
NEG_WORDS = ("下跌", "大跌", "亏损", "暴雷", "立案", "调查", "违规", "减持", "退市", "违约", "造假", "处罚")


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


def ext_id(source: str, title: str, published: Optional[datetime], url: Optional[str]) -> str:
    base = f"{source}|{url or ''}|{title}|{published.isoformat() if published else ''}"
    return hashlib.md5(base.encode("utf-8")).hexdigest()


def parse_dt(val: Any) -> Optional[datetime]:
    if val is None:
        return None
    if isinstance(val, datetime):
        return val
    if isinstance(val, date) and not isinstance(val, datetime):
        return datetime.combine(val, dt_time.min)
    if isinstance(val, dt_time):
        return datetime.combine(date.today(), val)
    text = str(val).strip()
    if not text or text in {"-", "--", "None", "nan"}:
        return None
    text = text.replace("/", "-").replace("T", " ")
    for fmt in ("%Y-%m-%d %H:%M:%S", "%Y-%m-%d %H:%M", "%Y-%m-%d", "%Y%m%d"):
        try:
            return datetime.strptime(text[:19] if len(text) >= 19 else text, fmt)
        except ValueError:
            continue
    return None


def combine_date_time(d: Any, t: Any) -> Optional[datetime]:
    dd = parse_dt(d)
    if dd is None:
        return parse_dt(t)
    if isinstance(t, dt_time):
        return datetime.combine(dd.date(), t)
    tt = parse_dt(t)
    if tt is None:
        return dd
    if tt.year > 1970 and (tt.hour or tt.minute or tt.second):
        return datetime.combine(dd.date(), tt.time())
    return dd


def http_get_json(url: str, referer: str = "https://finance.eastmoney.com/") -> Dict[str, Any]:
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": (
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
            ),
            "Referer": referer,
            "Accept": "application/json,text/plain,*/*",
        },
    )
    with urllib.request.urlopen(req, timeout=20) as resp:
        raw = resp.read().decode("utf-8", "ignore")
    data = json.loads(raw)
    return data if isinstance(data, dict) else {}


def fetch_eastmoney_yaowen(limit: int, snapshot_time: datetime) -> List[Dict[str, Any]]:
    """东财 App/网站「要闻」栏目（非 7x24 快讯）。"""
    # column=350 对应东财要闻流；与 App 要闻主头条高度重合
    page_size = max(10, min(int(limit or 40), 50))
    trace = str(int(time.time() * 1000))
    url = (
        "https://np-listapi.eastmoney.com/comm/web/getNewsByColumns"
        "?client=web&biz=web_news_col&column=350&order=1&needInteractData=0"
        f"&page_index=1&page_size={page_size}&req_trace={trace}"
        "&fields=code,showTime,title,mediaName,summary,url,uniqueUrl"
    )
    try:
        payload = http_get_json(url)
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError, ValueError) as ex:
        print(f"eastmoney yaowen: FAIL {ex}", file=sys.stderr)
        return []

    data = payload.get("data") if isinstance(payload.get("data"), dict) else {}
    lst = data.get("list") if isinstance(data, dict) else None
    if not isinstance(lst, list):
        return []

    rows: List[Dict[str, Any]] = []
    for item in lst:
        if not isinstance(item, dict):
            continue
        title = str(item.get("title") or "").strip()
        summary = str(item.get("summary") or "").strip() or None
        link = str(item.get("uniqueUrl") or item.get("url") or "").strip() or None
        published = parse_dt(item.get("showTime"))
        # 摘要加标记，前端可识别「要闻」
        tagged = ("【要闻】" + summary) if summary else "【要闻】"
        row = make_row("eastmoney", title, tagged, summary, link, published, snapshot_time)
        if row:
            # 与快讯区分 external_id，避免同标题互相覆盖错频道
            row["external_id"] = ext_id("eastmoney-yaowen", title, published, link)
            rows.append(row)
        if len(rows) >= limit:
            break
    return rows


def fetch_eastmoney_flash(limit: int, snapshot_time: datetime) -> List[Dict[str, Any]]:
    """东财全球财经快讯（7x24 风格，不是要闻）。"""
    import akshare as ak

    df = ak.stock_info_global_em()
    rows: List[Dict[str, Any]] = []
    if df is None or df.empty:
        return rows
    for _, r in df.head(limit).iterrows():
        title = str(r.get("标题") or "").strip()
        summary = str(r.get("摘要") or "").strip() or None
        url = str(r.get("链接") or "").strip() or None
        published = parse_dt(r.get("发布时间"))
        item = make_row("eastmoney", title, summary, summary, url, published, snapshot_time)
        if item:
            item["external_id"] = ext_id("eastmoney-flash", title, published, url)
            rows.append(item)
    return rows


def extract_codes(text: str) -> str:
    if not text:
        return ""
    found = []
    for m in re.finditer(r"(?<!\d)([036]\d{5})(?!\d)", text):
        code = m.group(1)
        if code not in found:
            found.append(code)
        if len(found) >= 6:
            break
    return ",".join(found)


def sentiment_of(text: str) -> str:
    body = text or ""
    pos = sum(1 for w in POS_WORDS if w in body)
    neg = sum(1 for w in NEG_WORDS if w in body)
    if pos > neg and pos > 0:
        return "利好"
    if neg > pos and neg > 0:
        return "利空"
    return "中性"


def clip(text: Optional[str], n: int) -> Optional[str]:
    if text is None:
        return None
    s = str(text).strip()
    if not s:
        return None
    return s if len(s) <= n else s[:n]


def title_key(title: Optional[str]) -> str:
    return (title or "").strip()


def dedupe_by_title(rows: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """标题一模一样只留一条；优先保留带【要闻】的。"""
    if not rows:
        return []
    best: Dict[str, Dict[str, Any]] = {}
    order: List[str] = []
    for row in rows:
        key = title_key(row.get("title"))
        if not key:
            continue
        prev = best.get(key)
        if prev is None:
            best[key] = row
            order.append(key)
            continue
        prev_yaowen = "【要闻】" in str(prev.get("summary") or "")
        cur_yaowen = "【要闻】" in str(row.get("summary") or "")
        if cur_yaowen and not prev_yaowen:
            best[key] = row
    return [best[k] for k in order]


def upsert_rows(conn, rows: List[Dict[str, Any]]) -> int:
    rows = dedupe_by_title(rows)
    if not rows:
        return 0
    sql = """
    INSERT INTO market_news (
      source, external_id, title, summary, content, url, published_at,
      related_codes, sentiment, snapshot_time, create_time, update_time, deleted
    ) VALUES (
      %(source)s, %(external_id)s, %(title)s, %(summary)s, %(content)s, %(url)s, %(published_at)s,
      %(related_codes)s, %(sentiment)s, %(snapshot_time)s, NOW(), NOW(), 0
    )
    ON DUPLICATE KEY UPDATE
      title=VALUES(title),
      summary=VALUES(summary),
      content=VALUES(content),
      url=VALUES(url),
      published_at=COALESCE(VALUES(published_at), published_at),
      related_codes=VALUES(related_codes),
      sentiment=VALUES(sentiment),
      snapshot_time=VALUES(snapshot_time),
      update_time=NOW(),
      deleted=0
    """
    with conn.cursor() as cur:
        cur.executemany(sql, rows)
    conn.commit()
    return len(rows)


def soft_delete_duplicate_titles(conn) -> int:
    """库内同标题多条时，只留最新一条，其余逻辑删除。"""
    sql = """
    UPDATE market_news t1
    INNER JOIN (
      SELECT title, MAX(id) AS keep_id
      FROM market_news
      WHERE deleted = 0
        AND title IS NOT NULL
        AND TRIM(title) <> ''
      GROUP BY title
      HAVING COUNT(*) > 1
    ) t2 ON t1.title = t2.title
    SET t1.deleted = 1, t1.update_time = NOW()
    WHERE t1.deleted = 0
      AND t1.id <> t2.keep_id
    """
    with conn.cursor() as cur:
        cur.execute(sql)
        n = cur.rowcount
    conn.commit()
    return n


def make_row(
    source: str,
    title: str,
    summary: Optional[str],
    content: Optional[str],
    url: Optional[str],
    published: Optional[datetime],
    snapshot_time: datetime,
) -> Optional[Dict[str, Any]]:
    title = clip(title, 500) or clip(summary, 80) or clip(content, 80)
    if not title:
        return None
    body = " ".join([x for x in (title, summary or "", content or "") if x])
    return {
        "source": source,
        "external_id": ext_id(source, title, published, url),
        "title": title,
        "summary": clip(summary, 2000),
        "content": clip(content, 8000),
        "url": clip(url, 1000),
        "published_at": published,
        "related_codes": extract_codes(body) or None,
        "sentiment": sentiment_of(body),
        "snapshot_time": snapshot_time,
    }


def fetch_eastmoney(limit: int, snapshot_time: datetime) -> List[Dict[str, Any]]:
    """要闻优先，再补全球快讯；按标题去重。"""
    cap = max(10, min(int(limit or 80), 200))
    yaowen_n = max(15, min(cap // 2, 40))
    flash_n = max(10, cap - yaowen_n)

    merged: List[Dict[str, Any]] = []
    seen_titles: set[str] = set()

    for item in fetch_eastmoney_yaowen(yaowen_n, snapshot_time):
        key = (item.get("title") or "").strip()
        if not key or key in seen_titles:
            continue
        seen_titles.add(key)
        merged.append(item)

    try:
        flash_rows = fetch_eastmoney_flash(flash_n, snapshot_time)
    except Exception as ex:  # noqa: BLE001
        print(f"eastmoney flash: FAIL {ex}", file=sys.stderr)
        flash_rows = []

    for item in flash_rows:
        key = (item.get("title") or "").strip()
        if not key or key in seen_titles:
            continue
        seen_titles.add(key)
        merged.append(item)
        if len(merged) >= cap:
            break

    print(f"eastmoney: yaowen+flash merged={len(merged)} (cap={cap})")
    return merged[:cap]


def fetch_cls(limit: int, snapshot_time: datetime) -> List[Dict[str, Any]]:
    import akshare as ak

    df = ak.stock_info_global_cls()
    rows: List[Dict[str, Any]] = []
    if df is None or df.empty:
        return rows
    for _, r in df.head(limit).iterrows():
        title = str(r.get("标题") or "").strip()
        content = str(r.get("内容") or "").strip() or None
        published = combine_date_time(r.get("发布日期"), r.get("发布时间"))
        item = make_row("cls", title, content, content, None, published, snapshot_time)
        if item:
            rows.append(item)
    return rows


def fetch_ths(limit: int, snapshot_time: datetime) -> List[Dict[str, Any]]:
    import akshare as ak

    df = ak.stock_info_global_ths()
    rows: List[Dict[str, Any]] = []
    if df is None or df.empty:
        return rows
    for _, r in df.head(limit).iterrows():
        title = str(r.get("标题") or "").strip()
        content = str(r.get("内容") or "").strip() or None
        url = str(r.get("链接") or "").strip() or None
        published = parse_dt(r.get("发布时间"))
        item = make_row("ths", title, content, content, url, published, snapshot_time)
        if item:
            rows.append(item)
    return rows


def fetch_sina(limit: int, snapshot_time: datetime) -> List[Dict[str, Any]]:
    import akshare as ak

    df = ak.stock_info_global_sina()
    rows: List[Dict[str, Any]] = []
    if df is None or df.empty:
        return rows
    for _, r in df.head(limit).iterrows():
        content = str(r.get("内容") or "").strip()
        published = parse_dt(r.get("时间"))
        title = content[:80] if content else ""
        item = make_row("sina", title, content, content, None, published, snapshot_time)
        if item:
            rows.append(item)
    return rows


def fetch_cctv(limit: int, snapshot_time: datetime) -> List[Dict[str, Any]]:
    import akshare as ak

    # 取近 2 日联播，避免单日为空
    rows: List[Dict[str, Any]] = []
    for offset in range(0, 3):
        day = (date.today() - timedelta(days=offset)).strftime("%Y%m%d")
        try:
            df = ak.news_cctv(date=day)
        except Exception:
            continue
        if df is None or df.empty:
            continue
        for _, r in df.iterrows():
            title = str(r.get("title") or "").strip()
            content = str(r.get("content") or "").strip() or None
            published = parse_dt(r.get("date")) or parse_dt(day)
            item = make_row("cctv", title, clip(content, 300), content, None, published, snapshot_time)
            if item:
                rows.append(item)
            if len(rows) >= limit:
                return rows
    return rows


FETCHERS = {
    "eastmoney": fetch_eastmoney,
    "cls": fetch_cls,
    "ths": fetch_ths,
    "sina": fetch_sina,
    "cctv": fetch_cctv,
}


def source_result_exit_code(success_count: int) -> int:
    return 0 if success_count > 0 else 1


def cleanup_old(conn, keep_days: int = 14) -> int:
    with conn.cursor() as cur:
        cur.execute(
            """
            UPDATE market_news
            SET deleted = 1, update_time = NOW()
            WHERE deleted = 0
              AND published_at IS NOT NULL
              AND published_at < DATE_SUB(NOW(), INTERVAL %s DAY)
            """,
            (keep_days,),
        )
        n = cur.rowcount
    conn.commit()
    return n


def main() -> int:
    load_env()
    parser = argparse.ArgumentParser(description="同步多源财经新闻")
    parser.add_argument(
        "--sources",
        default="eastmoney,cls,ths,sina",
        help="逗号分隔：eastmoney,cls,ths,sina,cctv",
    )
    parser.add_argument("--limit", type=int, default=80, help="每源最多条数")
    parser.add_argument("--keep-days", type=int, default=14, help="保留天数")
    args = parser.parse_args()

    sources = [s.strip().lower() for s in args.sources.split(",") if s.strip()]
    sources = [s for s in sources if s in ALL_SOURCES]
    if not sources:
        print("无有效来源", file=sys.stderr)
        return 2

    limit = max(10, min(int(args.limit or 80), 200))
    snapshot_time = datetime.now().replace(microsecond=0)
    conn = db_conn()
    ok = fail = total = 0
    all_rows: List[Dict[str, Any]] = []
    try:
        for source in sources:
            try:
                rows = FETCHERS[source](limit, snapshot_time)
                all_rows.extend(rows)
                ok += 1
                print(f"{source}: fetched={len(rows)}")
            except Exception as ex:  # noqa: BLE001
                fail += 1
                print(f"{source}: FAIL {ex}", file=sys.stderr)
        before = len(all_rows)
        all_rows = dedupe_by_title(all_rows)
        total = upsert_rows(conn, all_rows)
        dup_removed = soft_delete_duplicate_titles(conn)
        cleaned = cleanup_old(conn, args.keep_days)
        print(
            f"done sources_ok={ok} fail={fail} fetched={before} unique_title={len(all_rows)} "
            f"upsert={total} title_dup_soft_del={dup_removed} cleaned={cleaned} snapshot={snapshot_time}"
        )
        return source_result_exit_code(ok)
    finally:
        conn.close()


if __name__ == "__main__":
    raise SystemExit(main())
