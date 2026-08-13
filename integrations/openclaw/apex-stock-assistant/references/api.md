# Apex Bot API

## Request

Send `POST /apex/bot/v1/ask` with JSON:

```json
{
  "question": "宁德时代现在风险大吗？"
}
```

`question` is required and must contain the user's original question. The endpoint is read-only and supports stock analysis, market summaries, portfolio risk, and current decision queries.

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
    "intent": "STOCK_ANALYSIS",
    "stockCode": "300750",
    "stockName": "宁德时代",
    "answer": "...",
    "dataAsOf": "2026-08-13",
    "dataLevel": "GREEN",
    "aiEnhanced": true
  },
  "msg": "成功"
}
```

Treat Apex as the authoritative source. Present `data.answer` and preserve `data.dataAsOf`, `data.dataLevel`, and the investment-risk notice in the answer. `aiEnhanced=false` means Apex returned its deterministic fallback answer; it is still valid and must not be replaced with an invented conclusion. On any non-2xx response, surface the returned error and do not invent a fallback conclusion.

`dataLevel` is `GREEN`, `YELLOW`, or `RED`. It describes data completeness and freshness, not a buy or sell signal.
