# Apex ClawBot 接入设计

## 目标

让微信 ClawBot 可以安全查询 Apex 的个股研判、市场简报、持仓风控和当日决策，并在配置主动通道后接收重要股票告警。Apex 继续作为行情和业务结论的唯一来源，ClawBot 只负责微信收发与会话。

## 接入边界

- 新增只读接口 `POST /apex/bot/v1/ask`，不开放持仓修改、同步触发、模拟下单等写操作。
- Bot API 使用独立 HMAC 凭据，不复用网页登录账号或 Sa-Token。
- 生产环境继续不发布后端容器端口。ClawBot 与 Apex 同机时走 Docker 内网；跨机器时只反向代理 `/apex/bot/**`。
- Kimi 仅用于将 Apex 结构化结果整理成自然语言。AI 不可用时返回规则生成的答案。

## HMAC 协议

请求头：

- `X-Apex-Key`：客户端标识。
- `X-Apex-Timestamp`：Unix 秒。
- `X-Apex-Nonce`：每次请求唯一随机值。
- `X-Apex-Content-Sha256`：请求体 SHA-256 小写十六进制。
- `X-Apex-Signature`：HMAC-SHA256 小写十六进制。

签名原文：

```text
HTTP_METHOD\nSERVLET_PATH\nTIMESTAMP\nNONCE\nCONTENT_SHA256
```

服务端校验客户端标识、时间窗口、请求体摘要、签名和 Nonce。Nonce 在有效窗口内不可重复使用。

## 问答路由

1. 问题包含明确股票代码或可识别股票名称时，调用个股综合研判。
2. 市场、大盘、热点类问题调用市场简报。
3. 持仓、仓位、止损、风险类问题汇总持仓和风控概览。
4. 今日操作、买卖、决策类问题读取最新决策建议。
5. 无法识别时返回支持的提问示例，不让大模型自行猜测股票或行情。

所有回答必须包含数据时间或决策时间、数据完整度提示和非投资建议声明。

## 主动推送

主动出口兼容 WeClaw HTTP API：`POST {baseUrl}/api/send`，请求体包含 `to` 和 `text`。通道默认关闭，只有同时配置地址和收件人后才运行。

盘中扫描仅在 A 股交易日和交易时段运行，刷新自选及持仓行情后检查：

- 自选涨跌幅超过配置阈值。
- 观察池进入 `NEAR` 或 `TRIGGERED`。
- 持仓出现 `CRITICAL` 或 `WARN` 风控。

相同事件使用事件指纹做内存冷却去重。服务重启后可再次提醒，不引入首期不必要的消息表。智能决策任务成功后再推送决策摘要，禁止按 cron 到点提前推送。

## 部署配置

所有凭据通过环境变量注入：

- `APEX_BOT_ENABLED`
- `APEX_BOT_CLIENT_KEY`
- `APEX_BOT_CLIENT_SECRET`
- `APEX_BOT_WECLAW_ENABLED`
- `APEX_BOT_WECLAW_BASE_URL`
- `APEX_BOT_WECLAW_RECIPIENT`
- `APEX_BOT_WECLAW_API_TOKEN`

OpenClaw Skill 内提供签名调用脚本。用户只需把 Skill 放入 OpenClaw 工作区并为脚本配置 Apex 地址、Key 和 Secret。
