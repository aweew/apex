# 看板大盘关键阻力位实施计划

1. 在 `MarketBriefingServiceImplTest` 增加波段阻力位计算用例。
2. 在 `DashboardServiceImplTest` 增加看板市场字段透传用例。
3. 在 `dashboardMorningBriefingLayout.test.mjs` 锁定市场立场卡片的信息来源和缓存版本。
4. 为市场简报与看板市场块增加上证指数关键阻力位字段。
5. 使用上证指数日线计算阻力位，并在实时指数覆盖时同步更新。
6. 在看板展示阻力点位、距离和确认动作，完成聚焦测试、前端全量测试、构建与差异检查。
