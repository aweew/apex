# 因子中心实现计划

1. 新增 `FactorCalculatorTest`，覆盖区间收益、量比、年化波动率和 Alpha 权重归一化。
2. 新增因子 DTO、`FactorCalculator`、`IFactorCenterService` 和实现，聚合本地行情、财务及市场快照。
3. 新增 `/api/factors/{code}` 只读接口，并验证异常与缺失数据行为。
4. 新增前端 API、`FactorCenterView.vue`、路由和市场导航入口。
5. 新增页面结构测试，验证六类因子、固定权重和移动端布局。
6. 运行后端测试、前端测试与构建，完成代码审查和浏览器验收。
