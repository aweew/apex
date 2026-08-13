---
name: stock-trade-tracker
description: Record user-authorized A-share trade messages into Apex Smart Trader. Use only when a user actively forwards trade information or asks a factual question about Smart Trader positions, rankings, profiles, or Smart Money. Never infer missing stock codes, quantities, prices, or times.
---

# Stock Trade Tracker

For a forwarded trade message, produce a `TradeEvent` with the original text preserved. Use `WECHAT_TEXT` for text and `WECHAT_IMAGE` only after a vision model has extracted visibly supported fields. Do not read or monitor any chat automatically.

Submit the event through the existing `apex-stock-assistant/scripts/apex_trade_event.sh` script. Include a stable upstream message ID as `idempotencyKey`. `TRADE` may create a formal ledger record only with a valid six-digit stock code, BUY/SELL/ADD/REDUCE side, quantity, price, and sufficient confidence. `TRADE_INTENT`, `IGNORE`, `POSITION`, and `CLEAR` remain events; never invent a completed trade from them.

For factual questions, query Apex APIs only: `/api/ranking`, `/api/traders/{id}/position`, `/api/traders/{id}/portfolio`, `/api/traders/{id}/performance`, and `/api/smart-money/factors`. Explain returned facts, timestamps, and data limitations; do not calculate financial conclusions outside Apex.

Read [rules/trade.md](rules/trade.md) before parsing and use [prompts/trade-parser.md](prompts/trade-parser.md) as the extraction contract. For screenshots, retain the user-provided image URL in `imageUrl`; Apex stores it as TradeEvidence and does not download third-party content.
