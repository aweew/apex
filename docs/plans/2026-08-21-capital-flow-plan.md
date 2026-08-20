# 资金面与龙虎榜实施计划

1. 为采集脚本补列名兼容、金额转换、空结果保留和模式参数测试，再实现 `sync_capital_flow.py`。
2. 新增 Flyway V48、实体、Mapper、DTO、Service 和 Controller，并用服务测试覆盖最新快照、北向缺值和板块复用。
3. 在统一同步注册表中增加 `CAPITAL_FLOW` 与 `DRAGON_TIGER`，在调度器中实现盘中半小时、15:10、17:30、18:20 的交易日任务并补测试。
4. 新增前端 API、`CapitalFlowView.vue`、路由和主导航入口，先写结构与移动端回归测试。
5. 运行 Python、后端和前端测试及构建；审查工作区差异，记录设计符合性、残余风险和验证结果。
