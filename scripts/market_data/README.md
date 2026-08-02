# 全 A 行情导入（AKShare → MySQL）

把全市场股票列表与日线历史写入 Apex 的 `stock_basic` / `bar_daily`，支持断点续传。

## 1. 安装依赖

```bash
cd D:\code\apex\scripts\market_data
python -m pip install -r requirements.txt
copy .env.example .env
```

按需修改 `.env` 中的 MySQL 账号（默认 `root/apex123`，库 `apex`）。

## 2. 使用

```bash
# 只同步全 A 列表
python sync_a_share.py --mode list

# 试跑：前 5 只，从 2018 年开始
python sync_a_share.py --mode all --start 20180101 --limit 5 --sleep 0.4

# 全市场日线（可过夜跑；中断后重跑会续传）
python sync_a_share.py --mode bars --start 20180101 --sleep 0.4

# 只补指定代码
python sync_a_share.py --mode bars --codes 300308,600519 --start 20240101 --sleep 0.3

# 自动分批补缺口（推荐过夜）
python sync_missing_bars.py --batch 80 --rounds 0 --start 20240101 --sleep 0.18

# 忽略进度、强制按 start 重拉
python sync_a_share.py --mode bars --start 20180101 --no-resume --full-refresh
```

进度文件：`.progress/bars_progress.json`

日线现含 `turnover_rate`（换手率%）。存量库先执行：

```bash
# MySQL
source apex-be/docs/sql/11_bar_daily_turnover.sql
```

再回补历史换手（只填空值；优先 BaoStock，东财不可用时自动降级）：

```bash
python -m pip install -r requirements.txt

# 试跑指定代码
python backfill_turnover.py --codes 600519,300308 --sleep 0.2

# 前 50 只缺换手的股票
python backfill_turnover.py --limit 50 --sleep 0.2

# 全市场缺换手（可过夜）
python backfill_turnover.py --all --sleep 0.15
```

## 3. 公司概况（F10 基本资料）

写入表：`stock_company_profile`（先执行 `apex-be/docs/sql/12_company_profile.sql`）  
数据源：东财 `RPT_F10_ORG_BASICINFO`（公司全称/曾用名/行业概念/高管/控股股东/实控人等）

```bash
python sync_company_profile.py --codes 000301,600519 --sleep 0.25
python sync_company_profile.py --limit 50 --sleep 0.25
python sync_company_profile.py --all --sleep 0.2
```

前端个股页 Tab「公司概况」；接口：`GET /api/stock/{code}/profile`、`POST /api/stock/{code}/profile/refresh`

## 4. 公司基本面（详细财报）

写入表：`stock_fin_indicator` / `stock_fin_abstract` / `stock_fin_report_item`  
（先执行 `apex-be/docs/sql/07_fundamental.sql`）

```bash
# 试跑 2 只（摘要 + 分析指标 + 三大报表）
python sync_fundamentals.py --mode all --codes "000001,600519" --sleep 0.8

# 从 stock_basic 取前 20 只
python sync_fundamentals.py --mode all --limit 20 --sleep 0.8

# 全市场（可过夜；中断后重跑续传）
python sync_fundamentals.py --mode all --sleep 0.8

# 只同步某一类
python sync_fundamentals.py --mode indicator --sleep 0.5
python sync_fundamentals.py --mode abstract --sleep 0.5
python sync_fundamentals.py --mode reports --sleep 0.8
```

进度文件：`.progress/fund_progress.json`  
前端个股页 Tab：财务摘要 / 分析指标 / 利润表 / 资产负债表 / 现金流量表  
接口：`GET /api/stock/{code}/fundamental`

## 5. 主流市场指数（大盘）

写入表：`index_bar`（先执行 `apex-be/docs/sql/14_index_bar.sql`）

| 市场 | 指数 |
|------|------|
| A股 | 上证指数、深证成指、创业板指、北证50、科创50 |
| 港股 | 恒生指数、恒生科技 |
| 日韩 | 日经225、韩国综指 |
| 美国 | 道琼斯、纳斯达克、标普500 |

```bash
# 回补历史（推荐）
python sync_index.py --start 20180101 --sleep 0.25

# 只刷美股+A股
python sync_index.py --regions CN,US --start 20200101
```

前端导航「大盘」；接口：`GET /api/index/board`、`GET /api/index/{code}/bars`、`POST /api/index/refresh`  
看板展示成交量较昨日放量/缩量（阈值 ±3%）。日韩部分源无成交量时显示「无数据」。

## 6. 多源新闻资讯

写入表：`market_news`（先执行 `apex-be/docs/sql/13_market_news.sql`）  
数据源：东财全球财经 / 财联社电报 / 同花顺快讯 / 新浪财经（可选央视联播）

```bash
python sync_news.py --sources eastmoney,cls,ths,sina --limit 80
python sync_news.py --sources eastmoney,cls --limit 50
# 含央视（较慢）
python sync_news.py --sources eastmoney,cls,ths,sina,cctv --limit 40
```

前端导航「资讯」；接口：`GET /api/news/overview`、`POST /api/news/refresh`  
后端配置：`apex.news.script-path` / `apex.news.python-cmd`

## 7. 多平台热点

写入表：`market_hot`（先执行 `apex-be/docs/sql/10_market_hot.sql`）

```bash
# 东财人气 + 雪球关注 + 百度热搜
python sync_hot.py --limit 50

# 快刷（跳过较慢的雪球）
python sync_hot.py --sources eastmoney,baidu --limit 40
```

前端导航「热点」；接口：`GET /api/hot/overview`、`POST /api/hot/refresh`  
智能决策会对「≥2 平台热点共振」加分；仪表盘「今日关注」也会展示热点共振。

说明：
- 东财人气不可用时自动降级：东财成交额 → 新浪成交额（仍写入 `source=eastmoney`）
- 某一源拉取为空时**不会覆盖**旧快照，避免网络抖动把榜单刷空
- 后端配置：`apex.hot.script-path` / `apex.hot.python-cmd`（见 `application.yml` / `application-local.yml`）
- 定时快刷：`system_config.auto_sync_enabled=true` 后，工作日 9/10/11/13/14 点 20 分自动刷东财+百度热点

## 8. 板块行情（行业 / 概念 / 题材）

写入表：`sector_basic` / `sector_quote` / `sector_constituent`（先执行 `15_sector_board.sql`，扩展字段执行 `17_sector_quote_ext.sql`）

| Tab | board_type | 数据源 |
|-----|------------|--------|
| 行业 | INDUSTRY | 东财二级行业板块行情 + 行业资金流 |
| 概念 | CONCEPT | 东财概念板块行情 + 概念资金流 |
| 题材 | THEME | 与概念同源双写，前端默认按净流入排序 |

同步时额外计算：3日/5日涨幅、涨跌原因；接口另提供主线识别 `GET /api/sector/mainline`。  
资金净流入单位：**元**（前端按亿元展示）。成交额单位：元。

```bash
# 同步三类榜单（行情+资金流）
python sync_sector.py --mode quote --types INDUSTRY,CONCEPT,THEME --sleep 0.35

# 只刷行业+概念
python sync_sector.py --mode quote --types INDUSTRY,CONCEPT

# 下钻成分股（按板块代码）
python sync_sector.py --mode cons --types CONCEPT --codes BK0655 --sleep 0.3

# 某类型前 5 个板块成分（试跑）
python sync_sector.py --mode cons --types INDUSTRY --limit 5 --sleep 0.3
```

前端导航「板块」；接口：`GET /api/sector/board`、`GET /api/sector/{code}/constituents`、`GET /api/sector/mainline`、`POST /api/sector/refresh`  
后端配置：`apex.sector.script-path` / `apex.sector.python-cmd`

## 9. Web 统一同步（推荐）

前端导航「数据同步」(`/sync`) 可启动/停止全部脚本任务并查看进度与日志。

接口：
- `GET /api/sync/overview`
- `POST /api/sync/jobs`（body: `{ taskType, limit, start, ... }`）
- `GET /api/sync/jobs/{id}`
- `POST /api/sync/jobs/{id}/stop`

先执行 `apex-be/docs/sql/16_sync_job.sql`。后端配置：`apex.sync.script-dir` / `apex.sync.python-cmd`。

说明：一键启动对长任务默认带较小 `limit`（如日线/基本面 20）避免误点跑爆；全量请用高级启动改参数。

## 10. 说明

- 日线数据源：AKShare（优先新浪日线，失败再试东财；前复权）
- 大盘指数：A股/港股/美股新浪日线（含量）；日韩新浪环球（量常缺）
- 公司概况：东财 F10 `RPT_F10_ORG_BASICINFO`
- 新闻资讯：东财 / 财联社 / 同花顺 / 新浪（可选央视）
- 基本面数据源：分析指标 + 同花顺摘要 + 新浪三大报表（科目级 EAV，payload 保留全量字段）
- 热点数据源：东财人气（新浪成交额兜底）/ 雪球关注 / 百度热搜
- 板块数据源：东财板块行情 + `stock_sector_fund_flow_rank` 资金流 + 板块成分
- 全 A 约 5000+ 只，首次导入往往需要数小时，请保持 `--sleep`
- 导入完成后，Apex 详情/回测/基本面直接读本地库即可
