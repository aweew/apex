# Apex Bot API

## Request

Send `POST /apex/bot/v1/ask` with JSON:

```json
{
  "userId": "trusted-wechat-user-id",
  "question": "宁德时代现在风险大吗？"
}
```

`question` and `userId` are required. `question` must contain the user's original question, while `userId` must match the external identity bound to this HMAC client in Apex. The endpoint supports stock analysis, market summaries, default-portfolio today's profit or loss, portfolio risk, current decision queries, and controlled observation-pool additions. For example, `我今天亏多少` returns the default portfolio's current-day profit or loss when holdings and quotes are available. `把贵州茅台加入观察池` adds the stock only when Apex resolves an exact six-digit code or an exact, unique stock name.

`requestId` and `conversationId` are optional request fields. The bundled script reads `userId` from `APEX_BOT_EXTERNAL_USER_ID`; Apex generates a request ID when one is not supplied.

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
| `APEX_BOT_EXTERNAL_USER_ID` | External user bound to this client in Apex |

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

An observation-pool addition is confirmed only when a successful response has `data.intent: "OBSERVE_ADD"`. `OBSERVE_ADD_UNRESOLVED` means the stock was not found by an exact code or name, and `OBSERVE_ADD_AMBIGUOUS` means more than one exact name matched. Both outcomes leave the observation pool unchanged. Never infer a code or claim persistence for either outcome.

`dataLevel` is `GREEN`, `YELLOW`, or `RED`. It describes data completeness and freshness, not a buy or sell signal.

## Structured tool endpoint

Send signed `POST /apex/bot/v1/tool` requests using the same HMAC headers. `operation` is one of `PORTFOLIO_ADVICE`, `PORTFOLIO_STATUS`, `HOLDING_BUY`, `HOLDING_SELL`, `HOLDING_IMPORT`, `SMART_TRADER_RANKING`, `SMART_TRADER_POSITION`, `SMART_TRADER_PORTFOLIO`, `SMART_TRADER_PROFILE`, or `SMART_MONEY_FACTORS`.

All tool requests require the original WeClaw `userId` and `conversationId`. For `HOLDING_BUY`, `HOLDING_SELL`, and `HOLDING_IMPORT`, `portfolioName` is optional: omit it to use the bound user's unique active default portfolio. An explicit exact portfolio name remains supported when it is globally unique. An owner/default alias such as `Awe`、`我的组合`、`我的持仓` or `默认组合` is resolved only among the bound user's active default portfolios; no match or multiple matches rejects the request without changing holdings. `HOLDING_BUY` accepts one or more explicit buy transactions in `trades`, each containing a positive `quantity` and `tradePrice`; it adds to the named securities and recalculates their weighted cost. `HOLDING_SELL` accepts the same fields and decreases only the named securities; a full sell removes only that stock. Use these for explicit `买入`、`加仓`、`卖出`、`减仓` or `清仓` instructions, and for a single broker `成交详情` image. Neither operation deletes unrelated holdings. `HOLDING_IMPORT` accepts a complete portfolio list with a positive quantity and positive cost price for every row, and must be used only for explicit full-state synchronization with `fullReplace: true`; otherwise Apex rejects the request without changing the portfolio. A single transaction/成交详情 image must use `HOLDING_BUY` or `HOLDING_SELL`, not import. A row may include `tradePrice` and ISO-8601 `tradeTime` only when the user explicitly provided them. A six-digit code is preferred; when it is absent, Apex resolves the exact stock name against `stock_basic` and imports only if exactly one code matches. Apex applies additions, removals, and changed rows, records quantity differences as user trades, refreshes quotes, and writes today's snapshot. An unrecognized or non-unique name rejects the entire import without changing the portfolio.

A holding mutation is confirmed only when the successful response has the matching `data.intent`: `HOLDING_BUY`, `HOLDING_SELL`, or `HOLDING_IMPORT`. Use `data.answer` verbatim as the user-facing confirmation. Do not treat model extraction, an agent-memory write, or a generated portfolio summary as persistence.

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
