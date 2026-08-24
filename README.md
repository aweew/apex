# Apex 本地量化平台

A 股日频研究与模拟交易平台，围绕“数据同步 -> 市场研判 -> 选股/决策 -> 回测 -> 组合与复盘”组织工作流。系统只用于个人研究和模拟，不构成投资建议。

## 文档导航

| 文档 | 说明 |
| --- | --- |
| [架构设计](docs/ARCHITECTURE.md) | 系统边界、数据流、存储与用户隔离 |
| [操作指引](docs/OPERATIONS.md) | 本地启动、数据初始化、日常使用、验证与排障 |
| [NAS 部署](docs/NAS_DEPLOYMENT.md) | 生产 Docker/NAS 的配置、发布与恢复 |
| [行情脚本](scripts/market_data/README.md) | AKShare 数据导入脚本及数据口径 |
| [后端说明](apex-be/README.md) | 后端分层、接口域和数据库迁移 |
| [前端说明](apex-fe/README.md) | 前端目录、开发命令和接口代理 |

## 模块分类

| 分类 | 前端入口 | 主要能力 |
| --- | --- | --- |
| 工作台 | 看板、决策、小灵 | 市场摘要、当日决策、AI 研究问答与晨报 |
| 个人资产 | 自选、观察池、组合、模拟盘 | 自选分组、触发观察、组合持仓、模拟交易与交易记录 |
| 市场研究 | 行情、股票、板块、资金面、连板天梯、资讯 | 指数与热力图、个股详情、行业/概念、资金流、连板和新闻 |
| 策略研究 | 信号、回测、参数 | 股票池、策略信号、回测对比、策略参数与风控规则 |
| 数据运维 | 同步、数据质量、日终清单 | 任务编排、进度与日志、完整性检查、收盘后复盘清单 |
| 外部接入 | OpenClaw、微信小程序 | 受控 Bot API 与轻量移动端查看，按各自配置启用 |

完整的模块边界、接口域和数据归属见 [架构设计](docs/ARCHITECTURE.md#模块边界)。

## 快速开始

前置条件：JDK 17+、Maven 3.8+、Node.js 20+、Python 3.10+（数据同步时需要）和 Docker Compose（推荐用于本地 MySQL、Redis）。

```bash
# 1. 启动本地基础服务。首次启动会初始化 apex 数据库结构。
docker compose up -d mysql redis

# 2. 配置后端。将数据库密码改为本地实际值；使用上一步默认容器时为 apex123。
cp apex-be/src/main/resources/application-local.yml.example \
   apex-be/src/main/resources/application-local.yml

# 3. 启动后端。
cd apex-be
mvn spring-boot:run

# 4. 另开终端启动前端。
cd apex-fe
npm install
npm run dev
```

- 应用：<http://127.0.0.1:5173/>
- 后端健康检查：<http://127.0.0.1:8080/apex/api/health>
- Swagger：<http://127.0.0.1:8080/apex/swagger-ui.html>

首次打开需使用已创建账户登录；系统不内置默认管理员密码。开发环境默认关闭定时任务，手动同步仍可使用。完整的初始化、登录和首轮数据导入步骤见 [操作指引](docs/OPERATIONS.md#本地初始化与启动)。

## 推荐使用流

1. 在“数据同步”完成股票列表和日线的首轮导入，确认数据质量与任务状态。
2. 导入或维护“自选”，在行情、板块、资金面和资讯中建立研究上下文。
3. 刷新共享股票池，运行信号或智能决策；数据不足时先补同步，不把结果当作完整覆盖。
4. 在“回测”检验策略，明确交易成本、样本区间和回撤，再决定是否纳入观察。
5. 用“观察池”和“组合”记录计划与实际持仓；模拟盘仅用于演练。
6. 收盘后生成日终清单，记录真实成交和复盘结论。

## 常用验证

```bash
cd apex-fe
npm test
npm run build

cd ../apex-be
mvn test
mvn package

cd ..
git diff --check
bash scripts/deploy-nas.test.sh
```

各测试覆盖的是源代码和构建产物。要确认实际行情、登录权限和同步结果，还需要在已配置的运行环境中执行 [操作指引](docs/OPERATIONS.md#验收检查)。

## 生产部署

生产环境由 `docker-compose.prod.yml` 运行前端、后端和 Redis；MySQL 是现有外部服务，不由本项目创建或销毁。前端默认发布到 `8088`，并将 `/apex` 反向代理到后端。生产配置应从 `.env.production.example` 复制为未入库的 `.env.production`，设置数据库、Redis 和 JWT 凭据后再部署。

```bash
sh scripts/deploy-nas.sh --check
sh scripts/deploy-nas.sh
```

请按 [NAS 部署](docs/NAS_DEPLOYMENT.md) 完成网络、密钥、备份和 DSM 反向代理配置；不要把 MySQL、Redis、JWT 或 AI 密钥提交到仓库。

## 免责声明

本系统仅供个人研究与模拟。市场数据可能延迟、缺失或部分失败；历史回测和模型输出不代表未来收益，也不构成任何投资建议。
