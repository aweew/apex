# Apex Backend

包名：`com.awe.apex`  
启动类：`com.awe.apex.ApexApplication`  
端口：`8080`，上下文：`/apex`

## 启动

1. 全新 MySQL 创建库并执行 `docs/sql/*.sql`；存量数据库由 Flyway 从版本 43 接管
2. 配置 `application-local.yml`
3. `mvn spring-boot:run`

数据库迁移记录保存在 `flyway_schema_history`。首次接管既有数据库时执行 V44 兼容迁移，
后续结构变更必须新增 Flyway 版本，不能再放入应用启动逻辑。

## 主要 API

| 模块 | 路径 |
|------|------|
| 自选 | `/api/watchlist` |
| 日线 | `/api/data/bars` |
| 股票池 | `/api/universe` |
| 信号 | `/api/signal` |
| 回测 | `/api/backtest` |
| 模拟盘 | `/api/paper` |
| 风控 | `/api/risk` |
| 日终清单 | `/api/daily` |
| 成交日记 | `/api/journal` |
| 看板 | `/api/dashboard` |
| 配置 | `/api/config` |
| 导出 | `/api/export` |
| 本地登录 | `/api/auth/local-login` |
