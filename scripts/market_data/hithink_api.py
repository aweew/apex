#!/usr/bin/env python3
"""同花顺金融数据服务 REST 客户端。"""

from __future__ import annotations

import json
import os
import time
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo
from typing import Any, Dict, Iterable, List, Optional
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen


class HithinkApiError(RuntimeError):
    """同花顺接口调用或业务响应失败。"""


class HithinkApiClient:
    """使用环境变量配置的同花顺金融数据服务客户端。"""

    def __init__(
        self,
        base_url: Optional[str] = None,
        api_key: Optional[str] = None,
        timeout_seconds: Optional[float] = None,
    ):
        self.base_url = (base_url or os.getenv(
            "APEX_HITHINK_BASE_URL", "https://fuyao.aicubes.cn"
        )).rstrip("/")
        self.api_key = (api_key or os.getenv("HITHINK_FINANCE_API_KEY", "")).strip()
        self.timeout_seconds = timeout_seconds or max(
            1.0, float(os.getenv("APEX_HITHINK_TIMEOUT_MS", "10000")) / 1000
        )

    @property
    def enabled(self) -> bool:
        """判断环境是否开启且具备 API Key。"""
        return os.getenv("APEX_HITHINK_ENABLED", "false").lower() == "true" and bool(self.api_key)

    def get(self, path: str, params: Dict[str, Any]) -> Dict[str, Any]:
        """调用 GET 端点并检查统一响应信封。"""
        if not self.api_key:
            raise HithinkApiError("HITHINK_FINANCE_API_KEY 未配置")
        query = urlencode({key: value for key, value in params.items() if value is not None})
        url = f"{self.base_url}{path}?{query}" if query else f"{self.base_url}{path}"
        max_attempts = 3
        for attempt in range(1, max_attempts + 1):
            request = Request(
                url,
                headers={
                    "X-api-key": self.api_key,
                    "Accept": "application/json",
                    "User-Agent": "Apex/market-data",
                },
                method="GET",
            )
            try:
                with urlopen(request, timeout=self.timeout_seconds) as response:
                    payload = json.loads(response.read().decode("utf-8"))
            except (HTTPError, URLError, TimeoutError, json.JSONDecodeError) as error:
                if attempt == max_attempts:
                    raise HithinkApiError(f"同花顺 HTTP 请求失败: {error}") from error
                time.sleep(0.5 * attempt)
                continue

            response_code = payload.get("code")
            if response_code == 0:
                return payload.get("data") or {}
            try:
                numeric_code = int(response_code)
            except (TypeError, ValueError):
                numeric_code = -1
            error = HithinkApiError(
                f"同花顺接口失败，code={response_code}，"
                f"request_id={payload.get('request_id', '')}"
            )
            if numeric_code != 4001 and numeric_code < 5000:
                raise error
            if attempt == max_attempts:
                raise error
            time.sleep(0.5 * attempt)

        raise HithinkApiError("同花顺请求失败")

    def list_a_share(self, max_items: Optional[int] = None) -> List[Dict[str, Any]]:
        """分页获取 A 股代码表，可在达到指定数量后提前停止。"""
        rows: List[Dict[str, Any]] = []
        page_size = max(1, min(int(max_items or 1000), 10000))
        offset = 0
        while True:
            data = self.get(
                "/api/meta/tickers/list",
                {
                    "exchange": "SH,SZ,BJ",
                    "asset_type": "a-share",
                    "limit": page_size,
                    "offset": offset,
                },
            )
            page = data.get("item") or []
            rows.extend(page)
            if max_items and len(rows) >= max_items:
                return rows[:max_items]
            if len(page) < page_size:
                return rows
            offset += page_size

    def snapshot(self, thscodes: Iterable[str], batch_size: int = 100) -> List[Dict[str, Any]]:
        """批量获取 A 股行情快照。"""
        codes = list(dict.fromkeys(str(code).strip().upper() for code in thscodes if str(code).strip()))
        rows: List[Dict[str, Any]] = []
        actual_batch_size = max(1, min(int(batch_size), 100))
        for start in range(0, len(codes), actual_batch_size):
            data = self.get(
                "/api/a-share/prices/snapshot",
                {"thscodes": ",".join(codes[start:start + actual_batch_size])},
            )
            rows.extend(data.get("item") or [])
        return rows

    def historical(
        self,
        thscode: str,
        start: str,
        end: str,
        adjust: str = "forward",
    ) -> List[Dict[str, Any]]:
        """获取单只股票历史日线，自动按十年限制切分请求。"""
        start_date = _parse_day(start)
        end_date = _parse_day(end)
        if start_date > end_date:
            raise HithinkApiError("历史行情开始日期不能晚于结束日期")

        rows: List[Dict[str, Any]] = []
        chunk_start = start_date
        while chunk_start <= end_date:
            chunk_end = min(chunk_start + timedelta(days=3650), end_date)
            data = self.get(
                "/api/a-share/prices/historical",
                {
                    "thscode": thscode,
                    "interval": "1d",
                    "start": _day_timestamp(chunk_start.isoformat()),
                    "end": _day_timestamp(chunk_end.isoformat(), end_of_day=True),
                    "adjust": adjust,
                },
            )
            rows.extend(data.get("item") or [])
            chunk_start = chunk_end + timedelta(days=1)
        return rows

    def valuations(self, thscodes: Iterable[str]) -> List[Dict[str, Any]]:
        """批量获取最新估值快照。"""
        data = self.get(
            "/api/a-share/valuations/snapshot",
            {"thscodes": ",".join(dict.fromkeys(thscodes))},
        )
        return data.get("item") or []

    def financials(self, kind: str, thscode: str, period: str = "annual", limit: int = 4) -> List[Dict[str, Any]]:
        """获取利润表、资产负债表或现金流量表。"""
        paths = {
            "income": "/api/a-share/financials/income-statements",
            "balance": "/api/a-share/financials/balance-sheets",
            "cashflow": "/api/a-share/financials/cash-flow-statements",
        }
        if kind not in paths:
            raise HithinkApiError(f"不支持的财务报表类型: {kind}")
        data = self.get(
            paths[kind],
            {"thscode": thscode, "period": period, "limit": max(1, min(int(limit), 20))},
        )
        return data.get("item") or []


def hithink_report_rows(rows: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """将同花顺报告期毫秒时间戳转换为 EAV 写入器可识别的行。"""
    converted = []
    for row in rows:
        period_end_ms = row.get("period_end_ms")
        if not period_end_ms:
            continue
        report_date = datetime.fromtimestamp(
            int(period_end_ms) / 1000, tz=ZoneInfo("Asia/Shanghai")
        ).strftime("%Y-%m-%d")
        converted.append({
            "报告期": report_date,
            **{
                key: value
                for key, value in row.items()
                if key not in {"thscode", "period_end_ms"}
            },
        })
    return converted


def _day_timestamp(value: str, end_of_day: bool = False) -> int:
    parsed = _parse_day(value)
    if end_of_day:
        parsed = parsed.replace(hour=23, minute=59, second=59)
    return int(parsed.replace(tzinfo=ZoneInfo("Asia/Shanghai")).timestamp() * 1000)


def _parse_day(value: str) -> datetime:
    return datetime.strptime(str(value).replace("-", "")[:8], "%Y%m%d")
