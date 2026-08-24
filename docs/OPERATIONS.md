# Apex 操作指引

本文覆盖开发机启动、数据准备、日常研究和运行检查。NAS 生产安装、升级和恢复请使用 [NAS 部署](NAS_DEPLOYMENT.md)。

## 本地初始化与启动

### 1. 准备依赖

- JDK 17+、Maven 3.8+、Node.js 20+
- Docker Desktop / Docker Compose（推荐，用于本地 MySQL 和 Redis）
- Python 3.10+（执行市场数据同步时需要）

### 2. 启动 MySQL 与 Redis

在仓库根目录执行：

```bash
docker compose up -d mysql redis
docker compose ps
```

开发 Compose 会在 MySQL 首次创建数据卷时执行 `apex-be/docs/sql` 下的初始化脚本，数据库为 `apex`，默认 root 密码为 `apex123`。已存在的数据卷不会重新执行这些脚本；需要重建本地测试库时，应先确认数据可丢弃，再按 Docker 的卷管理流程处理，不要对有价值的数据直接删除卷。

### 3. 配置并启动后端

```bash
cp apex-be/src/main/resources/application-local.yml.example \
   apex-be/src/main/resources/application-local.yml
cd apex-be
mvn spring-boot:run
```

将 `application-local.yml` 的数据源密码改为实际值。开发 Compose 默认使用 `apex123`。本地配置默认关闭 `apex.scheduler.enabled`，因此不会自动执行交易时段或夜间同步；“同步”页面和脚本手动执行不受影响。

后端就绪后检查：

```bash
curl --fail http://127.0.0.1:8080/apex/api/health
```

### 4. 启动前端

另开终端执行：

```bash
cd apex-fe
npm install
npm run dev
```

浏览器打开 <http://127.0.0.1:5173/>。Vite 会将 `/apex` 代理给后端；不要同时将前端环境变量指向其他地址，除非你确实在调试远端 API。

### 5. 登录与账户

系统不内置默认管理员密码，登录页使用手机号和密码。新成员必须使用管理员生成的一次性邀请令牌注册。当前登录/注册接口不提供自助创建首个管理员的入口；首次安装的管理员账户必须通过受控的初始化流程预先创建。没有有效账户时，不能绕过认证继续使用业务页面。

## 首轮数据准备

### 使用 Web 同步页

推荐从“工具 -> 同步”启动任务，逐项观察状态、最近完整成功时间、覆盖范围和日志。首次不要把全量长任务当作瞬时操作：日线、基本面和公司资料的全市场补齐可能持续数小时。

建议顺序：

1. 同步全 A 股票列表和日线，确认最近交易日和缺口数量。
2. 同步指数、板块、资金面、热点和资讯，形成市场研究上下文。
3. 按需要同步公司概况和基本面；先对少量代码试跑，再扩大范围。
4. 刷新共享股票池后再运行信号、策略或智能决策。

任务出现 `PARTIAL`、非零缺口或过期日期时，数据并不完整。先从任务日志定位失败数据源/代码并补跑，不能把一次有限批次视作全量完成。

### 使用命令行脚本

脚本位于 `scripts/market_data`。安装依赖和配置数据库后，可先小范围验证：

```bash
cd scripts/market_data
python -m pip install -r requirements.txt

# 先只同步证券列表，再用 5 只股票试跑日线
python sync_a_share.py --mode list
python sync_a_share.py --mode all --start 20180101 --limit 5 --sleep 0.4
```

全市场导入、断点续传、基本面、指数、新闻、热点、板块和资金面参数见 [行情脚本说明](../scripts/market_data/README.md)。脚本的数据源会受网络、接口限流和交易日影响，执行后仍应在“同步”或数据质量页面检查结果。

## 日常使用流程

| 时点 | 建议操作 | 判定依据 |
| --- | --- | --- |
| 开盘前 | 查看看板、晨报、资讯与前一日同步状态 | 数据日期、任务成功状态、未披露字段 |
| 盘中 | 查看行情、板块、资金面；维护观察池与组合 | 行情新鲜度、资金流时间、触发条件 |
| 收盘后 | 执行/确认收盘同步，刷新股票池和决策 | 同步日志、覆盖范围、候选说明 |
| 策略研究 | 在回测中对比区间、成本、回撤和基准 | 样本范围、参数、实验记录 |
| 复盘 | 生成日终清单，记录真实成交与理由 | 订单记录、持仓变化、复盘结论 |

自选、观察池、组合、模拟盘、回测任务和 AI 会话属于当前用户。指数、行情、板块、新闻和共享股票池由统一任务维护；不要将共享市场数据的更新误认为个人资产已同步。

## 自动任务

生产环境同时满足以下条件才会执行自动任务：

1. `APEX_SCHEDULER_ENABLED=true`，服务层面的定时任务已启用。
2. 系统配置 `auto_sync_enabled=true`，市场同步任务被允许执行。

典型任务包括盘中重点行情刷新、收盘数据包、凌晨数据补缺、盘前晨报、资金面更新和定时决策。它们受交易日、数据源可用性和任务互斥控制；任务提交成功不等于每个数据源均成功。请以同步状态、日志和数据日期为准。

## 验收检查

### 代码与构建

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

### 运行环境

```bash
# 本地后端
curl --fail http://127.0.0.1:8080/apex/api/health

# NAS 生产环境
docker compose --env-file .env.production -f docker-compose.prod.yml ps
curl --fail http://127.0.0.1:8088/apex/api/health
```

运行检查只能证明服务可达。还应使用已授权账户验证：登录、目标页面加载、关键接口返回、数据日期、同步状态和权限边界。不要把未登录截图、空库页面或构建成功当作真实数据验收。

## 常见问题

### 页面能打开，但请求失败或 502

本地先确认后端健康检查；生产先确认 `backend` 为 `healthy`。前端 API 必须使用 `/apex`，生产 Nginx 会代理给后端；不要让浏览器直连 NAS 的 `8080`。

### 后端启动时 Flyway 失败

先保留日志和数据库现场，确认 `flyway_schema_history`、当前数据库账号权限和待执行迁移版本。不要手动修改已应用迁移，也不要从历史 SQL 中挑选脚本重复执行。升级前先备份，结构差异需要通过新的 Flyway 迁移处理。

### 同步任务长时间运行或只有部分结果

查看“同步”页任务日志，核对任务类型、参数、进度文件、数据源和缺口数量。全量日线/基本面任务需要较长时间；可先缩小 `limit` 或指定代码确认数据源可用，再继续补齐。`PARTIAL` 和非零失败数都需要保留在运维结论中。

### NAS 一键决策超时

前端容器已将读取超时设为 600 秒；若 DSM 或最外层反向代理仍使用较短超时，需要在该实际入口配置至少 600 秒的代理响应超时。详情见 [NAS 部署的故障排查](NAS_DEPLOYMENT.md#一键决策约-60-秒后返回-504)。

### 不小心启用了自动同步

先将系统配置 `auto_sync_enabled` 设为 `false`，必要时再将 `APEX_SCHEDULER_ENABLED=false` 重启服务。停止新任务后检查已运行任务的状态和日志；不要通过删除数据库记录来伪造任务已完成。
