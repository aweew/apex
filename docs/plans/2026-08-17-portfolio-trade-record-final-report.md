# 组合交易记录交付报告

## 1. 交付结果

Apex 已形成用户级统一交易记录。组合、真实持仓、文本导入和微信 Bot 的数量变化会自动生成买卖流水；交易记录可独立筛选查看，也会在个股日、周、月 K 线上显示 B/S 点和组合归属标签。

卖出记录同时返回卖出后至今涨跌、卖后最高涨幅和卖后最低跌幅。缺少实际成交价时使用估算参考价，记录和界面都会明确标识，不把持仓成本冒充成交价。

## 2. 主要能力

- 扩展 `journal_trade`，保存组合、归属人、变动前后数量、来源、价格来源和幂等引用。
- 数量差自动映射为 `BUY/SELL` 与 `OPEN/ADD/REDUCE/CLEAR`。
- Bot `HOLDING_IMPORT` 按差异应用完整清单，并用 `requestId:code` 防止重复记账。
- 提供 `GET /api/trades` 和 `GET /api/trades/markers`。
- 新增 `/trades` 响应式交易记录工作区。
- 个股 K 线展示 B/S 点；同周期同方向合并，标签优先显示归属人。
- OpenClaw 持仓导入契约支持可选 `tradePrice` 和 `tradeTime`，禁止模型猜测成交信息。

## 3. 关键文件

- 数据库：`apex-be/docs/sql/42_portfolio_trade_record.sql`
- 后端服务：`PortfolioTradeRecordServiceImpl.java`
- 后端接口：`TradeRecordController.java`
- 前端页面：`apex-fe/src/views/TradeRecordView.vue`
- K 线映射：`apex-fe/src/utils/tradeMarkers.js`
- 个股接入：`apex-fe/src/views/StockView.vue`
- Bot 契约：`integrations/openclaw/apex-stock-assistant/SKILL.md`

## 4. 验证结果

- 后端聚焦测试 18 个通过。
- 后端全量测试 473 个通过。
- 本地 MySQL 启动及统一交易记录迁移通过。
- 前端全量测试 175 个通过。
- 前端生产构建通过。
- 最终差异格式检查通过。

## 5. 上线与验收边界

- 上线时需要执行迁移 `42_portfolio_trade_record.sql`，启动时的幂等结构检查也会补齐字段和索引。
- 不补造上线前的历史交易；首条自动交易从功能上线后的持仓数量变化开始。
- OpenClaw 文档已更新，但运行中的 NAS Skill 仍需按既有发布流程同步后才会生效。
- 已登录交易页和 K 线标记的桌面、移动端视觉验收仍待具备登录态后完成。
- 本次未提交、未推送代码，工作区中的其它日志、决策进度和界面改动保持原状。
