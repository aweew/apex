# Apex 常用短线选股模板实现计划

## 任务 1：锁定模板和指标行为

- 文件：`apex-be/src/test/java/com/awe/apex/quant/screener/ScreenerStrategyTemplateRegistryTest.java`
- 文件：`apex-be/src/test/java/com/awe/apex/quant/screener/ScreenerMetricCalculatorTest.java`
- 描述：先为四个模板和五个日线指标编写失败测试。
- 验证：聚焦测试先因模板或方法不存在而失败。

## 任务 2：实现后端规则闭环

- 文件：`apex-be/src/main/java/com/awe/apex/quant/domain/enums/ScreenerRuleTypeEnum.java`
- 文件：`apex-be/src/main/java/com/awe/apex/quant/screener/ScreenerMetricCalculator.java`
- 文件：`apex-be/src/main/java/com/awe/apex/quant/screener/ScreenerStrategyRuleEvaluator.java`
- 文件：`apex-be/src/main/java/com/awe/apex/quant/screener/ScreenerStrategyTemplateRegistry.java`
- 文件：`apex-be/src/main/java/com/awe/apex/quant/domain/bo/ScreenerCandidateBO.java`
- 文件：`apex-be/src/main/java/com/awe/apex/quant/domain/dto/ScreenerStrategyMatchResp.java`
- 文件：`apex-be/src/main/java/com/awe/apex/quant/service/impl/ScreenerStrategyExecutionServiceImpl.java`
- 文件：`apex-be/src/main/java/com/awe/apex/quant/service/impl/ScreenerStrategyServiceImpl.java`
- 描述：新增规则类型、指标计算、执行阶段分类、结果字段、摘要单位及四个模板。
- 验证：后端聚焦测试通过。

## 任务 3：补齐前端自定义规则

- 文件：`apex-fe/src/views/ScreenerView.vue`
- 描述：将五个规则加入策略编辑器目录，正确设置数值、布尔和回看参数。
- 验证：前端测试与生产构建通过。

## 任务 4：审查与交付

- 文件：`docs/plans/2026-08-18-short-term-screener-review.md`
- 文件：`docs/plans/2026-08-18-short-term-screener-final-report.md`
- 描述：检查行为、数据不足路径、用户已有修改和文档一致性。
- 验证：`git diff --check` 通过，报告记录实际测试结果和剩余边界。
