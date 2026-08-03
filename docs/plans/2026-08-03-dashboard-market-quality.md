# 看板大盘数据质量加固计划

## 目标

一小时内把看板「大盘」块做到：**口径清晰、实时可刷新、与权威源同向、自测可回归**。  
用户不应再靠「到处点刷新」碰运气。

## 已确认事实（2026-08-03）

| 字段 | 权威口径 | 当前策略 |
|------|----------|----------|
| 指数点位/涨跌 | WeStock / 东财 ulist | 每次返回前用东财实时覆盖 IndexBar |
| 成交额 | 两市≈19974亿；三市≈2.01万亿 | 东财 ulist `f6`（禁止 `f48`） |
| 涨跌家数 | 上涨占优≈72%，与指数下跌可并存 | 优先沪+深 `f104/f105/f106` |
| 涨跌停 | 东财 ZT/DT 池 `tc` | 实时覆盖，禁止过期 stock_basic 把跌停算 0 |
| 分化日 | 指数跌、个股涨 = 权重拖累 | 广度不加「偏多」分；FE 展示提示 |

## 质量问题清单（按优先级）

### P0 — 必须修

1. **刷新语义混乱**：顶栏多按钮，真正生效的只有 `forceRefresh`；大盘「刷新」与顶栏需统一并清缓存。
2. **量能口径**：缺 5 日均额时只显示「实时」过弱；缩量/放量需在有 MA 时稳定给出；标签勿与「未同步」混淆。
3. **立场评分**：分化日曾把广度当偏多；需回归测试锁死。
4. **缓存**：`forceRefresh` 必须清空简报 + 实时额/指数/涨跌停短缓存；成功路径可测。
5. **自测缺失**：无单测覆盖成交额字段、广度解析、分化评分。

### P1 — 应修

6. 简报快照写回后，下次冷启动勿用昨日量能顶今日。
7. 看板顶栏收敛：保留「刷新行情」为主入口；收盘同步跳转同步中心。
8. 大盘块文案：涨跌家数、成交额、分化提示可读。

### P2 — 有余力

9. DevTools 热重启 `StrategyParams` 类加载噪声排查（不阻塞主线）。
10. 涨停池本地表与实时 `tc` 偏差提示。

## 执行步骤

1. 审计 `MarketBriefingServiceImpl` / `DashboardServiceImpl` / `DashboardView.vue` 全链路。
2. 抽出可测的纯逻辑（广度聚合、量能标签、分化评分），补 JUnit。
3. 修 P0 代码；编译 + 单测 + 打 `/apex/api/dashboard/home?forceRefresh=true`。
4. 对照 WeStock `changedist` / `market-overview` 做验收记录。
5. 收敛 FE 刷新入口与展示。
6. 输出验收报告到本文件末尾「验收记录」。

## 验收标准

- [x] `forceRefresh=true` 后：`indexVolume` > 0，指数 `pctChg` 非空，涨跌家数非空，跌停可为 0 但不可因过期库误为 0（有实时源时）
- [x] 指数全跌且上涨家数 > 下跌×1.2 时：广度信号不为「偏多」加分；FE 有分化提示
- [x] 单测通过：`mvn -Dtest=MarketBriefingMathTest test`
- [x] 看板仅保留一个明确的「刷新行情」主按钮（其它为导航/收盘任务）

## 验收记录（2026-08-03 执行）

### 单测

```
mvn -Dtest=MarketBriefingMathTest test  → PASS
```

覆盖：fenbu 聚合、沪深广度相加、分化日不加偏多分、f6 优先、量能标签、上涨占比。

### API 实测（`/apex/api/dashboard/home?forceRefresh=true`）

脚本：`scripts/verify_dashboard_market.py` → **PASS**

| 项 | 结果 |
|----|------|
| 成交额 | ≈2.01万亿（>0） |
| 广度 | 3912/64/1299，与东财 ulist 一致 |
| 上证 | 3809.66 / -0.59，与东财一致 |
| 涨跌停 | 75 / 8 |
| 分化日立场 | score≈23，防守（非进攻） |
| dataLevel | GREEN |

### WeStock 对照

- 指数涨跌与点位一致
- 两市成交额 19973.86 亿 ≈ 看板沪深京 2.01 万亿（含北证）
- 全市场上涨占比约 72%，与「指数跌、个股涨」一致

### 代码变更要点

1. `briefing(forceRebuild)`：强制刷新时同步完整重建，跳过脏快照秒回
2. `MarketBriefingMath` + 单测锁死口径
3. 看板仅顶栏「刷新行情」；去掉大盘重复刷新与死代码同步
4. 简报失败不再伪装「均衡」
5. 验收脚本可回归

### 事故与恢复（同日续）

- PowerShell `Set-Content` / 不当替换曾把 `MarketBriefingServiceImpl` / 部分 Vue 中文写成 `?`
- 已整文件 UTF-8 重写简报服务；看板用 `scripts/patch_dashboard_view.py` 安全打补丁
- **约束**：此后禁止用 PowerShell 写含中文的源码；局部改中文文件优先 Python UTF-8 或 IDE Write

## 持续优化日志

| 时间点 | 事项 |
|--------|------|
| 续作 | CLOSE_BUNDLE 失败也清简报缓存 |
| 续作 | sync_index 优先东财日线（带成交额） |
| 续作 | 跌停池 URL 补 sort/pagesize（否则 tc 空→显示 0） |
| 续作 | 决策页 dataLevel 中文；健康检查异常 YELLOW |
| 续作 | 看板强制刷新 + 分化提示 + 沪深京文案 |
| 续作 | 大盘详情页 cnStaleHint 过期/休市提示 |
| 事故 | 禁止 PowerShell 写中文源码；简报服务已 UTF-8 整文件恢复 |
| 验收 | MathTest + compile + verify_dashboard_market.py PASS（涨停75/跌停8） |
