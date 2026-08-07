# 智能决策优化实施计划

> 设计依据：`docs/plans/2026-08-07-intelligent-decision-design.md`
> 实施方式：每个任务遵循 Red-Green-Refactor；P0/P1 完成后再批准 P2 代码实现。

## 里程碑

| 里程碑 | 预计周期 | 退出条件 |
|---|---:|---|
| M1 可信决策 | 1-2 周 | 历史回放无当前数据污染，运行与特征可追溯 |
| M2 反馈闭环 | 2 周 | 1/3/5/10/20 日 outcome 自动生成，采纳/成交可关联 |
| M3 自适应排序 | 2-3 周 | Challenger 样本外优于静态规则且可影子运行 |
| M4 组合智能 | 2 周 | 输出满足全部约束的组合级目标权重 |
| M5 LLM 助手 | 1-2 周 | 有证据引用、可降级且不能覆盖硬风控 |

## M1：可信决策

### 任务 1：建立运行与特征快照表

**新增文件**

- `apex-be/docs/sql/26_intelligent_decision.sql`
- `apex-be/src/main/java/com/awe/apex/quant/domain/entity/DecisionRun.java`
- `apex-be/src/main/java/com/awe/apex/quant/domain/entity/DecisionFeatureSnapshot.java`
- `apex-be/src/main/java/com/awe/apex/quant/mapper/DecisionRunMapper.java`
- `apex-be/src/main/java/com/awe/apex/quant/mapper/DecisionFeatureSnapshotMapper.java`
- `apex-be/src/test/java/com/awe/apex/quant/config/MarketSchemaBootstrapTest.java`

**修改文件**

- `apex-be/src/main/java/com/awe/apex/quant/config/MarketSchemaBootstrap.java`
- `apex-be/src/main/java/com/awe/apex/quant/domain/entity/DailyAction.java`

**步骤**

1. 先写 Bootstrap 幂等测试，覆盖空库建表与重复启动。
2. 创建 `decision_run`、`decision_feature_snapshot`、`decision_outcome`。
3. 给 `daily_action` 增加 `run_id`、`rank_no`、`confidence`、`uncertainty`、`decision_status`。
4. 为 `run_no`、`run_id + code`、`action_id`、`action_date` 建唯一或查询索引。
5. Bootstrap 与编号 SQL 使用相同字段定义。

**验证**

```bash
cd apex-be && ./mvnw -Dtest=MarketSchemaBootstrapTest test
```

### 任务 2：引入 DecisionContext 与时点查询

**新增文件**

- `apex-be/src/main/java/com/awe/apex/quant/decision/DecisionContext.java`
- `apex-be/src/main/java/com/awe/apex/quant/decision/DecisionMode.java`
- `apex-be/src/main/java/com/awe/apex/quant/decision/DecisionDataPolicy.java`
- `apex-be/src/test/java/com/awe/apex/quant/decision/DecisionContextTest.java`
- `apex-be/src/test/java/com/awe/apex/quant/decision/DecisionReplayServiceTest.java`

**修改文件**

- `apex-be/src/main/java/com/awe/apex/quant/domain/dto/DecisionRunReq.java`
- `apex-be/src/main/java/com/awe/apex/quant/service/impl/DecisionServiceImpl.java`
- `apex-be/src/main/java/com/awe/apex/quant/service/impl/MarketBriefingServiceImpl.java`
- `apex-be/src/main/java/com/awe/apex/quant/service/impl/UniverseServiceImpl.java`
- `apex-be/src/main/java/com/awe/apex/quant/service/impl/SignalServiceImpl.java`
- `apex-be/src/main/java/com/awe/apex/quant/service/impl/ValuationServiceImpl.java`

**步骤**

1. 测试 REPLAY 禁止查询决策日之后的日线和简报。
2. `DecisionRunReq` 增加 `mode` 和可选 `asOfTime`。
3. 把 `actionDate`、`asOfTime` 从入口传入所有决策依赖，不在内部重新取 `now()`。
4. LIVE、REPLAY、SHADOW 使用明确的数据策略。
5. 当前持仓无法历史还原时，REPLAY 必须使用组合快照；缺失则标记降级，不能静默使用当前持仓。

**验证**

```bash
cd apex-be && ./mvnw -Dtest=DecisionContextTest,DecisionReplayServiceTest test
```

### 任务 3：拆分决策编排与特征构建

**新增文件**

- `apex-be/src/main/java/com/awe/apex/quant/decision/DecisionFeature.java`
- `apex-be/src/main/java/com/awe/apex/quant/decision/DecisionFeatureBuilder.java`
- `apex-be/src/main/java/com/awe/apex/quant/decision/DecisionRunManager.java`
- `apex-be/src/test/java/com/awe/apex/quant/decision/DecisionFeatureBuilderTest.java`

**修改文件**

- `apex-be/src/main/java/com/awe/apex/quant/service/impl/DecisionServiceImpl.java`
- `apex-be/src/main/java/com/awe/apex/quant/decision/DecisionScoreReq.java`

**步骤**

1. 用固定输入测试特征构建的确定性和缺失标记。
2. 将 `DecisionServiceImpl.run()` 中基本面、主线、热点、估值、市场和持仓拼装移入 builder。
3. 计算特征哈希并批量写入 snapshot。
4. `DecisionRunManager` 管理 RUNNING/SUCCESS/FAILED/PUBLISHED 状态。
5. 外部数据准备不放在长数据库事务内；最终发布使用短事务。

**验收**

- 同一快照重复评分结果一致。
- 每条 `daily_action` 都能定位运行和特征快照。
- 失败运行不覆盖当天已发布结果。

### 任务 4：修复订单前风险计算

**修改文件**

- `apex-be/src/main/java/com/awe/apex/quant/service/impl/RiskServiceImpl.java`
- `apex-be/src/test/java/com/awe/apex/quant/service/RiskServiceImplTest.java`

**步骤**

1. 先增加“已有 10% 仓位，再买 8%，单票上限 15%”失败测试。
2. 单票仓位改为原持仓市值加新订单金额。
3. 增加总仓位、行业仓位、无最新价和加仓四类回归测试。

## M2：反馈闭环

### 任务 5：多周期结果计算

**新增文件**

- `apex-be/src/main/java/com/awe/apex/quant/service/IDecisionOutcomeService.java`
- `apex-be/src/main/java/com/awe/apex/quant/service/impl/DecisionOutcomeServiceImpl.java`
- `apex-be/src/main/java/com/awe/apex/quant/domain/entity/DecisionOutcome.java`
- `apex-be/src/main/java/com/awe/apex/quant/mapper/DecisionOutcomeMapper.java`
- `apex-be/src/test/java/com/awe/apex/quant/decision/DecisionOutcomeCalculatorTest.java`

**步骤**

1. 以交易日序列而不是自然日计算 1/3/5/10/20 日窗口。
2. 计算绝对收益、沪深 300 超额收益、MFE、MAE、止损/止盈首次触发。
3. 对停牌、涨跌停无法成交、基准缺失分别记录质量状态。
4. 每日同步结束后幂等补算未成熟 outcome。

**验收**

- 构造行情下各周期、MFE/MAE 和触发日期计算精确。
- 未到期样本保持 PENDING，不错误计为 0。

### 任务 6：采纳与成交反馈

**修改文件**

- `apex-be/src/main/java/com/awe/apex/quant/domain/dto/JournalCreateReq.java`
- `apex-be/src/main/java/com/awe/apex/quant/service/impl/JournalServiceImpl.java`
- `apex-be/src/main/java/com/awe/apex/quant/controller/JournalController.java`
- `apex-fe/src/api/decision.js`
- `apex-fe/src/views/DecisionView.vue`

**新增能力**

- 对建议标记：已采纳、暂缓、忽略、调整仓位。
- 记录调整原因：风险、价格、主观判断、资金不足、信息变化。
- 成交后计算建议价与实际价滑点、建议权重与实际权重差异。

**验收**

- 清单操作不需要用户重复填写代码、方向和关联 ID。
- 采纳状态和成交状态分开，允许采纳后未成交。

### 任务 7：升级归因接口和页面

**新增 DTO**

- `DecisionOutcomeSummaryResp`
- `DecisionCalibrationBucket`
- `DecisionLiftResp`

**修改文件**

- `apex-be/src/main/java/com/awe/apex/quant/service/IDecisionService.java`
- `apex-be/src/main/java/com/awe/apex/quant/service/impl/DecisionServiceImpl.java`
- `apex-be/src/main/java/com/awe/apex/quant/controller/DecisionController.java`
- `apex-fe/src/api/decision.js`
- `apex-fe/src/views/DecisionView.vue`

**页面输出**

- 1/3/5/10/20 日切换。
- 按策略、市场状态、共振、主线、估值、评分分位归因。
- 胜率、超额收益、MFE/MAE、样本数和缺失率同时展示。
- 小样本明确提示，不展示伪精确结论。

## M3：自适应排序

### 任务 8：建立规则评分基线

**新增文件**

- `apex-be/src/main/java/com/awe/apex/quant/decision/DecisionModel.java`
- `apex-be/src/main/java/com/awe/apex/quant/decision/RuleDecisionModel.java`
- `apex-be/src/main/java/com/awe/apex/quant/decision/DecisionPrediction.java`

先让当前 `DecisionScorer` 通过统一模型接口输出，确保 Champion 行为不变。为每个测试样例保存基线分、仓位、风险旗标和可执行判定。

### 任务 9：离线训练与模型产物

**新增目录**

- `scripts/decision_model/`

**建议文件**

- `export_dataset.py`：只从时点快照和成熟 outcome 导出。
- `train.py`：时间序列 walk-forward、正则化逻辑回归/线性模型。
- `evaluate.py`：输出校准、Top-K lift、收益风险和分市场状态指标。
- `model.schema.json`：模型产物契约。

训练产物存储权重、截距、标准化参数、训练区间、特征版本、指标和 SHA-256。产物默认不提交真实训练数据。

### 任务 10：Challenger 影子评分

**新增文件**

- `apex-be/src/main/java/com/awe/apex/quant/decision/LinearDecisionModel.java`
- `apex-be/src/main/java/com/awe/apex/quant/decision/DecisionModelRegistry.java`
- `apex-be/src/main/java/com/awe/apex/quant/domain/entity/DecisionPredictionLog.java`

**要求**

- Champion 决定用户清单，Challenger 只落库不影响动作。
- 模型文件缺失、字段不匹配或哈希错误时自动回退 Champion。
- 影子运行满 20 个交易日且达到门槛后，才允许配置切换。

## M4：组合智能

### 任务 11：组合目标权重服务

**新增文件**

- `apex-be/src/main/java/com/awe/apex/quant/service/IDecisionPortfolioService.java`
- `apex-be/src/main/java/com/awe/apex/quant/service/impl/DecisionPortfolioServiceImpl.java`
- `apex-be/src/main/java/com/awe/apex/quant/domain/dto/DecisionRebalanceResp.java`
- `apex-be/src/test/java/com/awe/apex/quant/decision/DecisionPortfolioServiceTest.java`

覆盖波动率目标、已有持仓、现金、单票、行业、相关性和 100 股交易单位。测试必须证明任何输出都不突破硬约束。

### 任务 12：决策页组合视图

**修改文件**

- `apex-fe/src/views/DecisionView.vue`
- `apex-fe/src/api/decision.js`

展示当前权重、目标权重、差额、预计金额、拦截原因和组合风险变化；不提供自动提交全部订单。

## M5：LLM 决策助手

### 任务 13：结构化事件与证据引用

在现有 `KimiChatClient` 之上增加 JSON Schema 校验。所有事件必须引用 `market_news.id`，所有股票代码必须来自输入集合。模型输出不写入评分字段。

### 任务 14：解释和反方质询

决策页为每条清单提供：入选因素、拦截因素、最大不确定性、反方理由、改变结论的条件。无 API Key 或调用失败时由本地特征生成模板化解释。

## 全局测试与发布门槛

每个里程碑执行：

```bash
cd apex-be && ./mvnw test
cd ../apex-fe && npm test
npm run build
cd .. && bash scripts/smoke.sh
```

发布前额外检查：

1. REPLAY 不读取 action date 之后的数据。
2. 同运行快照重复计算结果一致。
3. 风控边界测试全部通过，违规数为 0。
4. Challenger 关闭时与现有 Champion 输出兼容。
5. 数据或模型不可用时页面可解释降级，不返回伪造分数。
6. 样本外指标、训练区间和模型哈希可审计。

## 首批迭代范围

第一批只实施任务 1-4。完成后进行一次代码审查和真实数据库回放验证，再进入任务 5-7。任务 8 之后的模型工作必须以 M2 的成熟 outcome 数据为前置条件。
