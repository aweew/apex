# 智能决策优化 M1 实施报告

## 交付范围

本次完成首批任务 1-4：决策运行与特征快照、日终时点回放、决策编排与短事务发布、加仓风险修正。

## 已实现

- 新增 `decision_run`、`decision_feature_snapshot`、`decision_outcome`，并为 `daily_action` 增加运行追踪字段；编号 SQL 与启动时幂等 DDL 保持一致。
- 每次决策记录运行编号、模式、数据截止时间、规则/模型/特征版本和策略配置快照；每条动作保存可复算特征与 SHA-256 哈希。
- 新增 LIVE、REPLAY、SHADOW 模式。历史日期默认 REPLAY，当前仅允许 `23:59:59` 日终回放，未来日期和盘中回放直接拒绝。
- 回放行情、股票池和策略信号均按决策日截断；禁止使用当前持仓、热点、估值、基本面和行业元数据。缺少历史市场简报快照时明确失败，不使用当前数据回填。
- REPLAY 数据质量固定标记为 YELLOW；SHADOW 保存运行和特征，但不发布 `daily_action`、市场简报或观察池。
- 最终动作替换、旧运行取消发布和新运行发布在同一短事务完成，并按决策日加行锁防止并发覆盖。
- 风控将已有同票持仓计入加仓后单票敞口；证券代码先标准化；持仓缺最新价时单票和行业校验均失败关闭。

## 审查结论

已处理首轮审查中的高风险问题：发布原子性、回放当前数据污染、盘中截止时间被忽略、特征输入不完整、缺价格放行、SHADOW 发布污染和消息字段长度不一致。新增回放边界、模式发布、特征快照、DDL 和加仓风险回归测试。

当前 M1 未实现历史持仓重建。REPLAY 会明确禁用持仓相关决策并降级为 YELLOW，避免以当前持仓伪装历史结果。真实历史持仓决策应在后续组合快照完备后启用。

## 验证结果

- `mvn -q -Dtest='!ApexApplicationTests' test`：通过。
- `mvn -q test`：97 项中 96 项通过；`ApexApplicationTests.contextLoads` 因本机 MySQL `localhost:3306` 未启动而失败，断言失败为 0。
- `npm test`：8/8 通过。
- Node 20 下 `npm run build`：通过；保留既有大包体积警告。
- `git diff --check`：通过。

## 上线前检查

1. 在可用 MySQL 环境执行 `apex-be/docs/sql/26_intelligent_decision.sql`，或启动应用触发幂等 Bootstrap。
2. 使用真实历史简报和日线运行至少一个 REPLAY，核对 `decision_run`、特征快照及发布动作的运行关联。
3. 并发触发同一日期的两次 LIVE 运行，确认只保留一个 published run，且 `daily_action.run_id` 全部指向该运行。
4. 数据库验证完成后再进入 M2 outcome 和采纳反馈闭环。
