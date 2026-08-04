# 组合模块实现计划

> 基于 `2026-08-04-portfolio-design.md`，用户已确认。

## 任务

1. SQL `24_portfolio.sql` + `MarketSchemaBootstrap` 建表 + 默认组合迁移
2. Entity/Mapper：Portfolio / PortfolioHolding / PortfolioDaily
3. DTO + IPortfolioService + 实现（CRUD、持仓、导入、快照、enrich）
4. PortfolioController
5. MyHolding 双写默认组合（save/remove/list 迁移后同步）
6. 前端 api/portfolio.js + PortfolioView + 路由/导航
7. 日终页或组合页「打快照」按钮
8. 编译/接口冒烟

## 约定

- 默认组合不可删
- `portfolio_holding` 为准，`my_holding` 双写兼容
- 表名：portfolio / portfolio_holding / portfolio_daily
