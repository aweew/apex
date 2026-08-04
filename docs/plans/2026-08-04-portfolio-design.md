# 组合模块设计（对标东财实盘组合）

> 确认口径：1C 手动+导入 · 2B 每日快照 · 3B 我的持仓=默认组合 · 4 MVP 同意  
> 日期：2026-08-04

## 1. 目标

在 Apex 中增加「组合」：可维护多个具名实盘仓（自己的或跟踪别人的），查看持仓明细与**每日浮盈浮亏历史**。现有「持仓」页升为默认组合「我的持仓」，旧 API 兼容。

## 2. 数据模型

### 2.1 `portfolio` 组合主表

| 字段 | 说明 |
|------|------|
| id | 主键 |
| name | 组合名（如「我的持仓」「某某实盘」） |
| note | 备注 |
| owner_label | 可选：实盘归属人标签（跟踪别人时用） |
| is_default | 1=默认组合（我的持仓），全局至多一个 |
| status | ACTIVE / ARCHIVED |
| sort_no | 排序 |
| create_time / update_time / deleted | 常规 |

唯一：活跃态下 `name` 不重复（软删后可复用）。

### 2.2 `portfolio_holding` 组合持仓

| 字段 | 说明 |
|------|------|
| id | 主键 |
| portfolio_id | 所属组合 |
| code / name | 证券 |
| quantity | 股数 |
| cost_price | 成本价 |
| stop_loss / take_profit / note | 可选，对齐现 my_holding |
| create_time / update_time / deleted | 常规 |

唯一：`(portfolio_id, code)`（未删除）。

### 2.3 `portfolio_daily` 每日快照

| 字段 | 说明 |
|------|------|
| id | 主键 |
| portfolio_id | 组合 |
| trade_date | 交易日 |
| market_value | 收盘/快照市值 |
| cost_value | 成本市值 |
| total_pnl | 累计浮盈（市值-成本） |
| today_pnl | 当日浮盈（相对昨收） |
| today_pct | 当日涨跌幅% |
| position_count | 持仓只数 |
| cash | 预留现金（MVP 默认 0） |
| payload | 可选 JSON：当日持仓明细快照 |
| create_time / update_time / deleted | 常规 |

唯一：`(portfolio_id, trade_date)`。

## 3. 与「我的持仓」关系（3B）

1. 启动/迁移：若不存在默认组合，创建 `name=我的持仓, is_default=1`。
2. 若 `my_holding` 有数据且默认组合持仓为空 → 复制到 `portfolio_holding`。
3. **双写过渡（MVP）**：
   - `/api/holding/*` 继续可用，读写落到**默认组合**的 `portfolio_holding`，并同步回写 `my_holding`（或只读 my_holding 作备份，以 portfolio 为准）。
   - 推荐：**以 `portfolio_holding` 为准**；`my_holding` 在迁移后只作兼容镜像（save/delete 时双写），后续版本可弃用表。
4. 前端「持仓」页：可保持现 URL，数据源改为默认组合；或详情跳进「组合」模块的默认项。MVP：**持仓页继续存在，内部调默认组合 API**；导航新增「组合」列表页。

## 4. API（`/api/portfolio`）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/list` | 组合列表（含今日浮盈摘要） |
| POST | `/save` | 新建/改名/备注/归档 |
| DELETE | `/{id}` | 删除（禁止删默认组合，或仅允许归档） |
| GET | `/{id}/detail` | 组合详情：持仓 enrich + 今日汇总 |
| POST | `/{id}/holding/save` | 增改持仓 |
| DELETE | `/{id}/holding/{holdingId}` | 删持仓 |
| POST | `/{id}/import` | 文本导入（code,qty,cost 每行） |
| POST | `/{id}/snapshot` | 手动打当日快照 |
| GET | `/{id}/daily` | 日收益序列（图表） |
| POST | `/snapshot-all` | 全部活跃组合打快照（日终可调） |

Enrich 复用 `MyHoldingServiceImpl` 现价/盈亏逻辑（抽公共方法或按持仓列表批量算）。

兼容：

| 原路径 | 行为 |
|--------|------|
| GET `/api/holding/list` | 默认组合 detail.holdings |
| POST `/api/holding/save` | 写入默认组合 |
| DELETE `/api/holding/{id}` | 删默认组合内持仓 |

## 5. 前端

### 5.1 导航

主线：`看板 / 决策 / 观察池 / 持仓 / **组合** / 模拟盘`

### 5.2 页面 `PortfolioView`

- **列表**：卡片/表格 — 名称、归属、持仓数、总市值、今日盈亏、累计浮盈；入口：新建、导入、归档
- **详情**（同页抽屉或子路由）：持仓表（代码/名称/数量/成本/现价/今日%/浮盈%）、日收益折线（`portfolio_daily`）
- **导入对话框**：粘贴 `代码,数量,成本` 或 Tab 分隔

### 5.3 持仓页

继续服务「我的持仓」；顶部可链到组合模块；数据读默认组合。

## 6. 每日浮盈（2B）

- **实时展示**：详情/列表用现价相对昨收算 `todayPnl` / `todayPct`（与现持仓页一致）。
- **落库**：`POST /snapshot` 或日终 `/snapshot-all` 写入 `portfolio_daily`；同日重复调用则 upsert。
- **曲线**：详情页 ECharts 展示近 N 日 `today_pct` 与累计市值。
- MVP 不强制挂定时任务；可在「日终」页加一键「组合快照」，或 sync 任务预留。

## 7. 导入格式（1C）

```
000001,1000,12.50
600519 100 1800
科华数据,1175,28.3   # 名称需能搜到代码，否则跳过并回报错行
```

解析规则：逗号/空白/Tab；第一列代码优先，否则按名称搜 `stock_basic`。

## 8. 非目标（MVP 不做）

- 东财组合链接自动同步
- 调仓流水/买卖账本
- 多用户权限与公开分享链接
- 组合排行榜/评论

## 9. 实现顺序建议

1. SQL + Entity/Mapper + Bootstrap 迁移默认组合  
2. PortfolioService（CRUD + enrich + snapshot + import）  
3. Holding 门面改接默认组合  
4. 前端 PortfolioView + 路由导航  
5. 日终一键快照入口  

## 10. 风险与假设

- 单机本地工具，无 user_id。
- 「别人的实盘」= 用户手工维护的镜像仓，非券商直连。
- 默认组合不可删除，可改备注。
