#!/usr/bin/env python3
"""
同步东财 F10 公司概况 → stock_company_profile

示例：
  python sync_company_profile.py --codes 000301,600519 --sleep 0.25
  python sync_company_profile.py --limit 20 --sleep 0.25
  python sync_company_profile.py --all --sleep 0.2
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.parse
import urllib.request
from datetime import datetime
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any, Dict, List, Optional, Sequence

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


def normalize_code(raw: str) -> Optional[str]:
    digits = "".join(ch for ch in str(raw or "") if ch.isdigit())
    if not digits:
        return None
    # PowerShell 可能把 000301 传成 301，这里补齐 6 位
    if len(digits) < 6:
        digits = digits.zfill(6)
    return digits[-6:]


def list_codes(conn, codes: Optional[Sequence[str]], limit: int) -> List[str]:
    if codes:
        out = []
        for raw in codes:
            code = normalize_code(raw)
            if code:
                out.append(code)
        return out
    sql = "SELECT code FROM stock_basic WHERE deleted = 0 ORDER BY code"
    if limit and limit > 0:
        sql += f" LIMIT {int(limit)}"
    with conn.cursor() as cur:
        cur.execute(sql)
        return [r["code"] for r in cur.fetchall()]


def http_get_json(url: str) -> Dict[str, Any]:
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            "Referer": "https://emweb.securities.eastmoney.com/",
            "Accept": "application/json,text/plain,*/*",
        },
    )
    with urllib.request.urlopen(req, timeout=20) as resp:
        return json.loads(resp.read().decode("utf-8", errors="replace"))


def fetch_profile(code: str) -> Dict[str, Any]:
    filt = urllib.parse.quote(f'(SECURITY_CODE="{code}")')
    url = (
        "https://datacenter.eastmoney.com/securities/api/data/v1/get"
        "?reportName=RPT_F10_ORG_BASICINFO"
        "&columns=ALL&quoteColumns="
        f"&filter={filt}"
        "&pageNumber=1&pageSize=1&sortTypes=&sortColumns=&source=HSF10&client=PC"
    )
    data = http_get_json(url)
    if not data.get("success"):
        raise RuntimeError(data.get("message") or "api fail")
    rows = ((data.get("result") or {}).get("data")) or []
    if not rows:
        raise RuntimeError("empty")
    return rows[0]


def _text(row: Dict[str, Any], *keys: str) -> Optional[str]:
    for key in keys:
        val = row.get(key)
        if val is None:
            continue
        text = str(val).strip()
        if text and text not in ("--", "null", "None"):
            return text
    return None


def _date(val: Optional[str]):
    if not val:
        return None
    try:
        return datetime.strptime(val[:10], "%Y-%m-%d").date()
    except Exception:
        return None


def _dec(val: Optional[str]):
    if not val:
        return None
    try:
        return Decimal(str(val).replace(",", "").strip())
    except (InvalidOperation, ValueError):
        return None


def _int(val: Optional[str]):
    d = _dec(val)
    return int(d) if d is not None else None


def to_row(code: str, raw: Dict[str, Any]) -> Dict[str, Any]:
    b1 = _text(raw, "BOARD_NAME_1LEVEL")
    b2 = _text(raw, "BOARD_NAME_2LEVEL")
    b3 = _text(raw, "BOARD_NAME_3LEVEL")
    board_path = "-".join([x for x in (b1, b2, b3) if x]) or None
    former = _text(raw, "FORMERNAME")
    if former:
        former = former.replace("→", ">").replace("->", ">")
    profile = _text(raw, "ORG_PROFIE")
    if profile:
        profile = profile.replace("\xa0", " ").strip()
    return {
        "code": code,
        "org_name": _text(raw, "ORG_NAME"),
        "org_name_en": _text(raw, "ORG_NAME_EN"),
        "former_name": former,
        "a_code": code,
        "a_name": _text(raw, "STR_NAMEA", "SECURITY_NAME_ABBR"),
        "region": _text(raw, "PROVINCE", "REGIONBK"),
        "area_board": _text(raw, "AREA_BOARD_NAME"),
        "industry_em": _text(raw, "EM2016"),
        "industry_csrc": _text(raw, "CSRC_INDUSTRY_NAME"),
        "board_path": board_path,
        "concepts": _text(raw, "BLGAINIAN"),
        "chairman": _text(raw, "CHAIRMAN"),
        "legal_person": _text(raw, "LEGAL_PERSON"),
        "president": _text(raw, "PRESIDENT"),
        "secretary": _text(raw, "SECRETARY"),
        "control_holder": _text(raw, "CONTROL_HOLDER"),
        "control_ratio": _text(raw, "CONTROL_DIRECT_RATIO"),
        "real_controller": _text(raw, "REAL_CONTROLER"),
        "real_controller_ratio": _text(raw, "REAL_DIRECT_RATIO"),
        "org_form": _text(raw, "ORG_FORM"),
        "found_date": _date(_text(raw, "FOUND_DATE")),
        "list_date": _date(_text(raw, "LISTING_DATE")),
        "reg_capital": _dec(_text(raw, "REG_CAPITAL")),
        "issue_price": _dec(_text(raw, "ISSUE_PRICE")),
        "employee_num": _int(_text(raw, "TOTAL_NUM")),
        "manager_num": _int(_text(raw, "TATOLNUMBER", "TOTALNUMBER")),
        "main_business": _text(raw, "MAIN_BUSINESS"),
        "org_profile": profile,
        "org_highlight": _text(raw, "ORG_PROFILE"),
        "business_scope": _text(raw, "BUSINESS_SCOPE"),
        "website": _text(raw, "ORG_WEB"),
        "email": _text(raw, "ORG_EMAIL"),
        "phone": _text(raw, "ORG_TEL"),
        "fax": _text(raw, "ORG_FAX"),
        "office_address": _text(raw, "ADDRESS"),
        "reg_address": _text(raw, "REG_ADDRESS"),
        "reg_num": _text(raw, "REG_NUM"),
        "trade_market": _text(raw, "TRADE_MARKET"),
        "payload": json.dumps(raw, ensure_ascii=False),
        "source": "eastmoney-f10",
    }


UPSERT_SQL = """
INSERT INTO stock_company_profile (
  code, org_name, org_name_en, former_name, a_code, a_name, region, area_board,
  industry_em, industry_csrc, board_path, concepts, chairman, legal_person, president,
  secretary, control_holder, control_ratio, real_controller, real_controller_ratio,
  org_form, found_date, list_date, reg_capital, issue_price, employee_num, manager_num,
  main_business, org_profile, org_highlight, business_scope, website, email, phone, fax,
  office_address, reg_address, reg_num, trade_market, payload, source,
  create_time, update_time, deleted
) VALUES (
  %(code)s, %(org_name)s, %(org_name_en)s, %(former_name)s, %(a_code)s, %(a_name)s,
  %(region)s, %(area_board)s, %(industry_em)s, %(industry_csrc)s, %(board_path)s,
  %(concepts)s, %(chairman)s, %(legal_person)s, %(president)s, %(secretary)s,
  %(control_holder)s, %(control_ratio)s, %(real_controller)s, %(real_controller_ratio)s,
  %(org_form)s, %(found_date)s, %(list_date)s, %(reg_capital)s, %(issue_price)s,
  %(employee_num)s, %(manager_num)s, %(main_business)s, %(org_profile)s, %(org_highlight)s,
  %(business_scope)s, %(website)s, %(email)s, %(phone)s, %(fax)s, %(office_address)s,
  %(reg_address)s, %(reg_num)s, %(trade_market)s, %(payload)s, %(source)s,
  NOW(), NOW(), 0
)
ON DUPLICATE KEY UPDATE
  org_name=VALUES(org_name), org_name_en=VALUES(org_name_en), former_name=VALUES(former_name),
  a_code=VALUES(a_code), a_name=VALUES(a_name), region=VALUES(region), area_board=VALUES(area_board),
  industry_em=VALUES(industry_em), industry_csrc=VALUES(industry_csrc), board_path=VALUES(board_path),
  concepts=VALUES(concepts), chairman=VALUES(chairman), legal_person=VALUES(legal_person),
  president=VALUES(president), secretary=VALUES(secretary), control_holder=VALUES(control_holder),
  control_ratio=VALUES(control_ratio), real_controller=VALUES(real_controller),
  real_controller_ratio=VALUES(real_controller_ratio), org_form=VALUES(org_form),
  found_date=VALUES(found_date), list_date=VALUES(list_date), reg_capital=VALUES(reg_capital),
  issue_price=VALUES(issue_price), employee_num=VALUES(employee_num), manager_num=VALUES(manager_num),
  main_business=VALUES(main_business), org_profile=VALUES(org_profile),
  org_highlight=VALUES(org_highlight), business_scope=VALUES(business_scope),
  website=VALUES(website), email=VALUES(email), phone=VALUES(phone), fax=VALUES(fax),
  office_address=VALUES(office_address), reg_address=VALUES(reg_address), reg_num=VALUES(reg_num),
  trade_market=VALUES(trade_market), payload=VALUES(payload), source=VALUES(source),
  update_time=NOW(), deleted=0
"""


def upsert(conn, row: Dict[str, Any]) -> None:
    with conn.cursor() as cur:
        cur.execute(UPSERT_SQL, row)
        # 个股默认行业回写为东财二级（board_path 第二段）
        industry_l2 = None
        board_path = row.get("board_path") or ""
        parts = [p.strip() for p in board_path.split("-") if p and p.strip()]
        if len(parts) >= 2:
            industry_l2 = parts[1]
        elif parts:
            industry_l2 = parts[0]
        elif row.get("industry_em"):
            industry_l2 = row["industry_em"]
        if industry_l2:
            cur.execute(
                "UPDATE stock_basic SET industry=%s, update_time=NOW() WHERE code=%s AND deleted=0",
                (industry_l2, row["code"]),
            )
    conn.commit()


def main() -> int:
    load_env()
    parser = argparse.ArgumentParser(description="同步公司概况")
    parser.add_argument("--codes", default="", help="逗号分隔代码")
    parser.add_argument("--limit", type=int, default=0, help="从 stock_basic 取前 N 只")
    parser.add_argument("--all", action="store_true", help="全市场")
    parser.add_argument("--sleep", type=float, default=0.25)
    args = parser.parse_args()

    code_list = [c.strip() for c in args.codes.split(",") if c.strip()] if args.codes else None
    if not code_list and not args.all and args.limit <= 0:
        print("请指定 --codes / --limit / --all", file=sys.stderr)
        return 2

    conn = db_conn()
    try:
        codes = list_codes(conn, code_list, 0 if args.all else args.limit)
        print(f"待处理 {len(codes)} 只")
        ok = fail = 0
        for i, code in enumerate(codes, 1):
            try:
                raw = fetch_profile(code)
                upsert(conn, to_row(code, raw))
                ok += 1
                print(f"[{i}/{len(codes)}] {code} {_text(raw, 'SECURITY_NAME_ABBR') or ''} OK")
            except Exception as ex:  # noqa: BLE001
                fail += 1
                print(f"[{i}/{len(codes)}] {code} FAIL {ex}")
            time.sleep(max(args.sleep, 0))
        print(f"done ok={ok} fail={fail}")
        return 0 if fail == 0 else 1
    finally:
        conn.close()


if __name__ == "__main__":
    raise SystemExit(main())
