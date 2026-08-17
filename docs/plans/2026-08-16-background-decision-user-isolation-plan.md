# 后台智能决策用户隔离实施计划

1. 为 `ApexUserContext` 编写优先级、嵌套恢复和异常清理测试，再实现最小上下文组件。
2. 为 `DataSyncJobServiceImpl` 编写任务 owner、逐用户去重、查询隔离和共享任务兼容测试。
3. 为 `DecisionScheduler` 编写启用用户逐个提交及单用户失败不中断测试。
4. 为 `DecisionRunManager` 和 `DecisionActionPublisher` 编写 owner 写入、同日发布不跨用户覆盖测试。
5. 新增迁移 `35_background_decision_user_isolation.sql`，为 `sync_job`、`decision_run`、`daily_action` 增加可空 `user_id` 和组合索引，不回填旧数据。
6. 实现后台上下文、任务所有权、逐用户调度和共享收盘任务的逐用户后处理。
7. 将决策查询、回放快照和策略表现聚合限定到当前用户；移除全局智能决策主动通知。
8. 修复前端旧结果、防重复操作和后端测试静态 ObjectMapper 污染。
9. 依次运行聚焦测试、后端全量、前端全量、Node 20 生产构建和 `git diff --check`，最后按隔离边界复审。
