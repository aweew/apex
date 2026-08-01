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

## 3. 公司基本面（详细财报）

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

## 4. 多平台热点

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

## 5. 说明

- 日线数据源：AKShare（优先新浪日线，失败再试东财；前复权）
- 基本面数据源：分析指标 + 同花顺摘要 + 新浪三大报表（科目级 EAV，payload 保留全量字段）
- 热点数据源：东财人气（新浪成交额兜底）/ 雪球关注 / 百度热搜
- 全 A 约 5000+ 只，首次导入往往需要数小时，请保持 `--sleep`
- 导入完成后，Apex 详情/回测/基本面直接读本地库即可
