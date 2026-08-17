# 组合交易记录实施计划

执行状态：2026-08-18 已全部完成。

## 任务 1：锁定数据契约与迁移（已完成）

- 文件：`apex-be/docs/sql/42_portfolio_trade_record.sql`
- 文件：`apex-be/src/main/java/com/awe/apex/quant/config/MarketSchemaBootstrap.java`
- 文件：`apex-be/src/test/java/com/awe/apex/quant/config/MarketSchemaBootstrapTest.java`
- 内容：扩展 `journal_trade` 字段和索引，补齐启动时幂等迁移。
- 验证：Bootstrap 测试先失败，再通过；SQL 每个表和字段都有数据库 `COMMENT`。

## 任务 2：实现统一交易流水服务（已完成）

- 文件：`apex-be/src/main/java/com/awe/apex/quant/domain/entity/JournalTrade.java`
- 文件：`apex-be/src/main/java/com/awe/apex/quant/domain/enums/PortfolioTrade*.java`
- 文件：`apex-be/src/main/java/com/awe/apex/quant/domain/dto/TradeRecordResp.java`
- 文件：`apex-be/src/main/java/com/awe/apex/quant/service/IPortfolioTradeRecordService.java`
- 文件：`apex-be/src/main/java/com/awe/apex/quant/service/impl/PortfolioTradeRecordServiceImpl.java`
- 内容：数量差写入、价格口径、分页筛选、用户隔离、Bot 幂等和卖后指标。
- 验证：新增服务单测覆盖买入、加仓、减仓、清仓、无变化、估算、隔离、卖后表现。

## 任务 3：接入持仓与 Bot 链路（已完成）

- 文件：`apex-be/src/main/java/com/awe/apex/quant/service/IPortfolioService.java`
- 文件：`apex-be/src/main/java/com/awe/apex/quant/service/impl/PortfolioServiceImpl.java`
- 文件：`apex-be/src/main/java/com/awe/apex/quant/bot/service/impl/BotToolServiceImpl.java`
- 文件：`apex-be/src/main/java/com/awe/apex/quant/domain/dto/PortfolioHoldingSaveReq.java`
- 文件：`apex-be/src/main/java/com/awe/apex/quant/domain/dto/MyHoldingSaveReq.java`
- 内容：在持仓事务内记录流水；Bot 改为差异更新并传来源引用；支持可选成交价和时间。
- 验证：Portfolio 与 Bot 聚焦测试先失败，再通过；重试不产生伪交易。

## 任务 4：提供查询与 K 线标记 API（已完成）

- 文件：`apex-be/src/main/java/com/awe/apex/quant/controller/TradeRecordController.java`
- 文件：`apex-be/src/test/java/com/awe/apex/quant/controller/TradeRecordControllerTest.java`
- 内容：当前用户分页查询和按代码查询标记。
- 验证：接口参数边界、证券代码规范化和响应字段测试通过。

## 任务 5：实现交易记录工作区（已完成）

- 文件：`apex-fe/src/views/TradeRecordView.vue`
- 文件：`apex-fe/src/api/trade.js`
- 文件：`apex-fe/src/router/index.js`
- 文件：`apex-fe/src/views/PortfolioView.vue`
- 内容：增加交易记录入口、筛选器、响应式表格/移动卡片和卖后表现。
- 验证：静态布局测试、前端全量测试和构建通过。

## 任务 6：实现 K 线 B/S 点（已完成）

- 文件：`apex-fe/src/utils/tradeMarkers.js`
- 文件：`apex-fe/src/utils/tradeMarkers.test.mjs`
- 文件：`apex-fe/src/views/StockView.vue`
- 内容：加载当前用户标记，按周期映射并合并同日同方向记录，标签显示归属人或组合。
- 验证：日/周/月映射、同日聚合、缺失价格回退测试通过；个股页构建通过。

## 任务 7：回归、评审与交付（已完成）

- 文件：`docs/plans/2026-08-17-portfolio-trade-record-review.md`
- 文件：`docs/plans/2026-08-17-portfolio-trade-record-final-report.md`
- 内容：检查用户隔离、事务、估算标识、Bot 重试和响应式布局。
- 验证：后端测试、前端测试/构建、`git diff --check`；可用时进行已登录浏览器验收。
