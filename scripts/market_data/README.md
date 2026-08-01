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

# 忽略进度、强制按 start 重拉
python sync_a_share.py --mode bars --start 20180101 --no-resume --full-refresh
```

进度文件：`.progress/bars_progress.json`

## 3. 说明

- 数据源：AKShare（优先新浪日线，失败再试东财；前复权）
- 全 A 约 5000+ 只，十年级历史首次导入往往需要数小时，请保持 `--sleep`
- 导入完成后，Apex 详情/回测直接读本地库即可
