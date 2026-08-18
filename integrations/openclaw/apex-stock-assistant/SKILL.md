---
name: apex-stock-assistant
description: Query Apex for A-share analysis, market summaries, portfolio status, observation-pool additions, and controlled named-portfolio holding imports. MUST use for questions about a named portfolio or its holdings; confirm writes only from Apex's matching success intent.
---

# Apex Stock Assistant

Use Apex as the source of market data and investment conclusions. Do not invent prices, positions, today's profit or loss, risks, or decisions. On an Apex tool error, return its error and request ID exactly; never claim that the Skill, network, or Apex data is unavailable unless the tool returned that error.

For questions such as `我今天亏多少`、`今天赚了多少`、`今日盈亏`、`我的持仓今天表现如何`、`针对疯锅的持仓有什么投资建议`, always run the Apex query with the original question. Do not say that the function is unavailable or unsupported, ask the user to list holdings, or replace the Apex result with a generic holding-risk response.

## Ask Apex

1. Run `scripts/apex_ask.sh "<用户原始问题>"`.
2. Return Apex's answer faithfully and preserve its data time, completeness warning, and investment-risk notice.
3. If the request fails, report the API error. Do not substitute an unsupported market conclusion.

## Add To Observation Pool

For natural-language requests such as `把贵州茅台加入观察池`、`将宁德时代加到观察池`、`帮我关注 600519`, run `scripts/apex_ask.sh` with the user's original request. Apex resolves only an exact six-digit code or an exact, unique stock name; never infer or substitute a stock code in OpenClaw.

The addition is confirmed only when the successful Apex response has `data.intent` exactly equal to `OBSERVE_ADD`. Return `data.answer` unchanged. `OBSERVE_ADD_UNRESOLVED`, `OBSERVE_ADD_AMBIGUOUS`, an API error, or an assistant statement does not prove persistence and must not be described as a successful addition.

## Structured Tools

For named portfolios and all holding-update workflows, run `scripts/apex_tool.sh` with a JSON request. Always pass the WeClaw sender ID as `userId` and WeClaw conversation ID as `conversationId`; never invent either value.

- Portfolio advice: `{"operation":"PORTFOLIO_ADVICE","userId":"<sender>","conversationId":"<conversation>","portfolioName":"疯锅"}`
- Portfolio status: `{"operation":"PORTFOLIO_STATUS","userId":"<sender>","conversationId":"<conversation>","portfolioName":"疯锅"}`

For a WeClaw image attachment or a text table, extract JSON with `code`, `name`, `quantity`, `costPrice`, and visible `marketValue` for each holding plus optional `totalMarketValue`. When the message explicitly provides them, also pass `tradePrice` and ISO-8601 `tradeTime`; otherwise leave them absent. Every row needs a positive quantity and a positive cost price. When a code is missing, pass the exact recognized stock name and leave `code` empty: Apex resolves it only by an exact, unique `stock_basic.name` match. Do not infer a code, trade price, or trade time yourself. If Apex cannot resolve the name or finds more than one match, state that the portfolio was not changed and return its error.

When the user sends a brokerage screenshot or a text table and says `更新郑十万的持仓` (or names another portfolio), directly import the complete, validated rows:

```json
{
  "operation": "HOLDING_IMPORT",
  "userId": "<sender>",
  "conversationId": "<conversation>",
  "portfolioName": "郑十万",
  "holdings": [
    {"code": "000063", "name": "中兴通讯", "quantity": 500, "costPrice": 34.21, "marketValue": 17540.00, "tradePrice": 35.08, "tradeTime": "2026-08-18T10:26:00"}
  ],
  "totalMarketValue": 17540.00
}
```

Call `scripts/apex_tool.sh` with this JSON and return `data.answer` unchanged. `HOLDING_IMPORT` treats the input as the complete named-portfolio state and applies only its additions, removals, and changed rows. Apex records quantity differences as trades, then refreshes prices and writes today's snapshot. Do not query the portfolio instead of calling this tool.

## Holding Update Completion Rule

A portfolio update is successful only when `scripts/apex_tool.sh` returns a successful Apex envelope whose `data.intent` is exactly `HOLDING_IMPORT`. Extraction, a rendered holding table, market observations, an agent-memory write, or an assistant statement are not evidence of a database update.

Before that successful response, never say or imply that the portfolio was updated, saved, recorded, or will be tracked. In particular, never say that data was saved to `memory/...`; agent memory is not the Apex portfolio database. On a validation error, tool error, unavailable attachment, or missing code, clearly state that the portfolio was not changed and return the Apex error when one exists.

Configure `APEX_BOT_BASE_URL`, `APEX_BOT_CLIENT_KEY`, `APEX_BOT_CLIENT_SECRET`, and `APEX_BOT_EXTERNAL_USER_ID` in the OpenClaw runtime. Never print, persist, or send these credentials anywhere except the configured Apex endpoint. The external user ID must be the actual sender identity bound to this HMAC client in Apex; never replace it with another sender ID.

## Record A Trade Event

When a user actively forwards a trading message to their own ClawBot, first extract a structured event. Call `scripts/apex_trade_event.sh` only for an event with explicit `traderName`, `eventType`, `confidence`, `source`, and `rawText`. Preserve unknown fields as `null`; never infer a stock code, quantity, price, or exact trade time.

For a confirmed transaction, require a six-digit `symbol`, `BUY`/`SELL`/`ADD`/`REDUCE` side, positive quantity, and positive price. Use a stable message identifier as `idempotencyKey`, so retries cannot create a second trade. Do not call this tool for recommendations or plans: send `eventType` as `IGNORE` or `TRADE_INTENT` instead. `CLEAR` remains an event only in this phase because Apex has not enabled Position Engine calculation.

Return Apex's status faithfully: `CONFIRMED` has created a formal ledger trade; `PENDING_CONFIRM` needs a user confirmation through Apex; `REJECTED` never creates a trade.

Read [references/api.md](references/api.md) when diagnosing authentication, request, or response errors.
