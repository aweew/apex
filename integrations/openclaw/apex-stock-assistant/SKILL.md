---
name: apex-stock-assistant
description: Query the Apex read-only Bot API for A-share stock analysis, market summaries, portfolio risk, and current trading decisions. Use when an OpenClaw user asks about a stock, market movement, holdings, risk, stop-loss levels, or what to do today.
---

# Apex Stock Assistant

Use Apex as the source of market data and investment conclusions. Do not invent prices, positions, risks, or decisions.

## Ask Apex

1. Run `scripts/apex_ask.sh "<用户原始问题>"`.
2. Return Apex's answer faithfully and preserve its data time, completeness warning, and investment-risk notice.
3. If the request fails, report the API error. Do not substitute an unsupported market conclusion.

Configure `APEX_BOT_BASE_URL`, `APEX_BOT_CLIENT_KEY`, and `APEX_BOT_CLIENT_SECRET` in the OpenClaw runtime. Never print, persist, or send these credentials anywhere except the configured Apex endpoint.

Read [references/api.md](references/api.md) when diagnosing authentication, request, or response errors.
