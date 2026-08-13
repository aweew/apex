# Trade Parsing Rules

Return exactly one JSON object. Retain the original message as `rawText` and set unavailable fields to `null`.

- A completed `BUY`, `SELL`, `ADD`, or `REDUCE` requires an explicit completed action. A plan is `TRADE_INTENT`; an opinion is `IGNORE`.
- Resolve stock code only from a visible code or an unambiguous Apex lookup. Never derive a code from a vague short name.
- Preserve `tradeTime` as `null` unless the message or forwarding metadata identifies it. Relative times may use the forwarding timestamp only when the upstream metadata is present.
- `CLEAR` must not include a guessed quantity. It is recorded for later Position Engine reconciliation.
- A price such as `380左右` is not a price. Keep `price=null`.
- Use `WECHAT_IMAGE` only for a user-provided screenshot. Preserve the image URL and only extract visible text.
- Confidence is 0.95 or higher only when trader, code, completed side, quantity, price, and time context are explicit. Missing quantity, price, code ambiguity, or inferred time must reduce confidence below 0.95.
