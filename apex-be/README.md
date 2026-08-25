# 灵极 Apex｜洞见·观变 后端

Spring Boot 3.5 / Java 17 后端，启动类为 `com.awe.apex.ApexApplication`。默认端口 `8080`、上下文路径 `/apex`。

## 目录与分层

| 目录 | 职责 |
| --- | --- |
| `common` | 通用响应、异常、安全、配置、过滤器和工具 |
| `core`、`manager` | 通用用户、角色、菜单等基础管理能力 |
| `quant/controller` | 面向 Web 和小程序的量化业务接口 |
| `quant/service`、`quant/mapper`、`quant/domain` | 业务编排、数据库访问和业务对象 |
| `quant/market`、`quant/indicator`、`quant/factor` | 行情、指标与因子计算 |
| `quant/strategy`、`quant/screener`、`quant/backtest` | 选股策略、股票池和回测引擎 |
| `quant/decision`、`quant/paper`、`quant/holding` | 决策、模拟盘和持仓资产 |
| `quant/sync`、`quant/job` | 数据同步任务与定时调度 |
| `src/main/java/db/migration` | Flyway Java 迁移，新增结构变更从新版本号开始 |

系统架构和数据隔离说明见仓库根目录的 [架构设计](../docs/ARCHITECTURE.md)。

## 本地运行

```bash
# 在仓库根目录启动 MySQL 和 Redis
docker compose up -d mysql redis

# 配置本地数据库账号和可选 AI Key
cp src/main/resources/application-local.yml.example \
   src/main/resources/application-local.yml

# 启动服务
mvn spring-boot:run
```

本地默认连接 `127.0.0.1:3306/apex`。若使用根目录的开发 Compose，MySQL 初始密码为 `apex123`；应只用于本地开发。`application-local.yml` 含凭据，不能提交。

## 主要接口域

所有业务接口均位于 `/apex` 上下文下。

| 接口前缀 | 能力 |
| --- | --- |
| `/api/auth` | 登录、当前用户、邀请和密码管理 |
| `/api/watchlist`、`/api/observe`、`/api/holding`、`/api/portfolio`、`/api/paper` | 用户自选、观察、持仓、组合和模拟盘 |
| `/api/market`、`/api/index`、`/api/sector`、`/api/capital-flow`、`/api/news`、`/api/limit-up` | 市场、指数、板块、资金流、资讯和连板数据 |
| `/api/stock`、`/api/screener`、`/api/universe`、`/api/signal`、`/api/factors`、`/api/valuation` | 个股研究、选股、股票池、信号、因子和估值 |
| `/api/decision`、`/api/backtest`、`/api/risk`、`/api/daily`、`/api/journal` | 决策、回测、风控和复盘 |
| `/api/sync`、`/api/data/quality`、`/api/health` | 同步任务、数据质量和健康检查 |
| `/bot/v1` | OpenClaw 受控 Bot 接口，需明确启用及配置 HMAC 凭据 |

本地 Swagger 默认可用：<http://127.0.0.1:8080/apex/swagger-ui.html>。生产环境默认关闭，只有显式设置 `APEX_API_DOCS_ENABLED=true` 才会开放。

## 数据库迁移

新库由根目录 `docker-compose.yml` 在首次创建 MySQL 数据卷时执行 `docs/sql` 初始化脚本。已有数据库由 Flyway 接管：基线版本为 43，应用启动时执行后续 `V44+` Java 迁移并记录到 `flyway_schema_history`。

不要修改已执行的迁移，也不要把新结构变更塞进启动逻辑或历史 SQL。新增变更应添加新的 `Vxx__Description.java` 迁移，并在目标库验证 `flyway_schema_history` 和 `information_schema`。

## 测试与打包

```bash
mvn test
mvn package
```

生产镜像构建在仓库根目录执行：`docker compose --env-file .env.production -f docker-compose.prod.yml build backend`。
