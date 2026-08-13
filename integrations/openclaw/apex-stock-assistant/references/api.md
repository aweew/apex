# Apex Bot API

## Request

Send `POST /apex/bot/v1/ask` with JSON:

```json
{
  "question": "宁德时代现在风险大吗？"
}
```

`question` is required and must contain the user's original question. The endpoint is read-only and supports stock analysis, market summaries, default-portfolio today's profit or loss, portfolio risk, and current decision queries. For example, `我今天亏多少` returns the default portfolio's current-day profit or loss when holdings and quotes are available.

`requestId`, `userId`, and `conversationId` are optional request fields. The bundled script intentionally sends only `question`; Apex generates a request ID when one is not supplied.

## Authentication

Set these headers on every request:

| Header | Value |
| --- | --- |
| `X-Apex-Key` | Client key |
| `X-Apex-Timestamp` | Current Unix time in seconds |
| `X-Apex-Nonce` | Unique random value for this request |
| `X-Apex-Content-Sha256` | Lowercase SHA-256 hex digest of the exact request bytes |
| `X-Apex-Signature` | Lowercase HMAC-SHA256 hex signature |

Build the canonical string without a trailing newline:

```text
METHOD\nSERVLET_PATH\nTIMESTAMP\nNONCE\nCONTENT_SHA256
```

For this endpoint, `METHOD` is `POST` and `SERVLET_PATH` is `/apex/bot/v1/ask`. Sign the UTF-8 canonical string with `APEX_BOT_CLIENT_SECRET`. Do not sign the base URL, query string, or reverse-proxy host.

## Runtime configuration

| Environment variable | Example |
| --- | --- |
| `APEX_BOT_BASE_URL` | `https://apex.example.com` |
| `APEX_BOT_CLIENT_KEY` | Client key issued by Apex |
| `APEX_BOT_CLIENT_SECRET` | Client secret issued by Apex |

Keep the key and secret in OpenClaw's secret or environment configuration. Do not place real credentials in this skill.

## Response handling

The response uses the Apex result envelope:

```json
{
  "code": 0,
  "data": {
    "requestId": "generated-request-id",
    "intent": "PORTFOLIO_TODAY_PNL",
    "answer": "今日持仓盈亏...",
    "dataAsOf": "2026-08-13",
    "dataLevel": "GREEN",
    "aiEnhanced": false
  },
  "msg": "成功"
}
```

Treat Apex as the authoritative source. Present `data.answer` and preserve `data.dataAsOf`, `data.dataLevel`, and the investment-risk notice in the answer. `aiEnhanced=false` means Apex returned its deterministic fallback answer; it is still valid and must not be replaced with an invented conclusion. On any non-2xx response, surface the returned error and do not invent a fallback conclusion.

`dataLevel` is `GREEN`, `YELLOW`, or `RED`. It describes data completeness and freshness, not a buy or sell signal.

## Structured tool endpoint

Send signed `POST /apex/bot/v1/tool` requests using the same HMAC headers. `operation` is one of `PORTFOLIO_ADVICE`, `PORTFOLIO_STATUS`, `HOLDING_IMPORT`, `SMART_TRADER_RANKING`, `SMART_TRADER_POSITION`, `SMART_TRADER_PORTFOLIO`, `SMART_TRADER_PROFILE`, or `SMART_MONEY_FACTORS`.

All tool requests require the original WeClaw `userId` and `conversationId`. `HOLDING_IMPORT` accepts only structured holding rows with a six-digit code, positive quantity, and positive cost price. It immediately replaces the named portfolio, refreshes its quotes, and writes today's snapshot.

Smart Trader tool requests remain read-only. `SMART_TRADER_RANKING` accepts an optional `rankingType` of `TOTAL`, `DAILY`, or `STEADY`; position, portfolio, and profile tools require `traderId`. `SMART_MONEY_FACTORS` returns the newest computed factor snapshot. These tools must not be used to infer missing trades or mutate a trader account.

## Trade Event Ingest

Send a signed `POST /apex/api/trade-events/ingest` request with the same HMAC headers. Use `scripts/apex_trade_event.sh` so the canonical path remains correct.

```json
{
  "traderName": "张三",
  "eventType": "TRADE",
  "side": "BUY",
  "symbol": "300750",
  "stockName": "宁德时代",
  "quantity": 500,
  "price": 378.5,
  "tradeTime": "2026-08-13 09:42:00",
  "confidence": 0.96,
  "source": "WECHAT_TEXT",
  "rawText": "张三刚刚买入500股宁德时代，378.5",
  "idempotencyKey": "stable-upstream-message-id"
}
```

`confidence >= 0.95` automatically confirms a complete trade event. `0.80 <= confidence < 0.95` creates `PENDING_CONFIRM`; lower confidence, plans, and opinions are recorded as `REJECTED` and never create a formal trade. Apex checks `symbol` against its local `stock_basic` data and rejects unknown codes. The confirmation endpoint is `POST /apex/api/trade-events/{id}/confirm`; rejection is `POST /apex/api/trade-events/{id}/reject`. Both require the same HMAC headers.
