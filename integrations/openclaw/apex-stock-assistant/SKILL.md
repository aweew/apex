---
name: apex-stock-assistant
description: Query the Apex read-only Bot API for A-share stock analysis, market summaries, portfolio holdings including today's profit or loss amount, risk, stop-loss levels, portfolio investment advice, and current trading decisions. MUST use when an OpenClaw user asks "我今天亏多少", "今天赚了多少", "今日盈亏", "针对疯锅的持仓有什么投资建议", or any question about a named portfolio or their holdings, instead of answering from general knowledge.
---

# Apex Stock Assistant

Use Apex as the source of market data and investment conclusions. Do not invent prices, positions, today's profit or loss, risks, or decisions. On an Apex tool error, return its error and request ID exactly; never claim that the Skill, network, or Apex data is unavailable unless the tool returned that error.

For questions such as `我今天亏多少`、`今天赚了多少`、`今日盈亏`、`我的持仓今天表现如何`、`针对疯锅的持仓有什么投资建议`, always run the Apex query with the original question. Do not say that the function is unavailable or unsupported, ask the user to list holdings, or replace the Apex result with a generic holding-risk response.

## Ask Apex

1. Run `scripts/apex_ask.sh "<用户原始问题>"`.
2. Return Apex's answer faithfully and preserve its data time, completeness warning, and investment-risk notice.
3. If the request fails, report the API error. Do not substitute an unsupported market conclusion.

## Structured Tools

For named portfolios and all holding-update workflows, run `scripts/apex_tool.sh` with a JSON request. Always pass the WeClaw sender ID as `userId` and WeClaw conversation ID as `conversationId`; never invent either value.

- Portfolio advice: `{"operation":"PORTFOLIO_ADVICE","userId":"<sender>","conversationId":"<conversation>","portfolioName":"疯锅"}`
- Portfolio status: `{"operation":"PORTFOLIO_STATUS","userId":"<sender>","conversationId":"<conversation>","portfolioName":"疯锅"}`

For a WeClaw image attachment, use the configured vision model only to produce JSON with `code`, `name`, `quantity`, `costPrice`, and visible `marketValue` for each holding plus optional `totalMarketValue`. Do not infer a code from a non-unique Chinese fund name. If any code, quantity, or cost price is missing or uncertain, ask the user to correct it and do not call a write tool.

When the user sends a brokerage screenshot and says `更新郑十万的持仓` (or names another portfolio), extract the visible rows and directly import them:

```json
{
  "operation": "HOLDING_IMPORT",
  "userId": "<sender>",
  "conversationId": "<conversation>",
  "portfolioName": "郑十万",
  "holdings": [
    {"code": "000063", "name": "中兴通讯", "quantity": 500, "costPrice": 34.21, "marketValue": 17540.00}
  ],
  "totalMarketValue": 17540.00
}
```

Call `scripts/apex_tool.sh` with this JSON and return its answer unchanged. `HOLDING_IMPORT` replaces the entire named portfolio immediately, then refreshes prices and writes today's snapshot. Do not query the portfolio instead of calling this tool.

Configure `APEX_BOT_BASE_URL`, `APEX_BOT_CLIENT_KEY`, and `APEX_BOT_CLIENT_SECRET` in the OpenClaw runtime. Never print, persist, or send these credentials anywhere except the configured Apex endpoint.

Read [references/api.md](references/api.md) when diagnosing authentication, request, or response errors.
