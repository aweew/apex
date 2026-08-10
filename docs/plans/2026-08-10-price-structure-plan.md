# 个股支撑压力与筹码分布实施计划

1. 在 `apex-fe/src/utils/priceStructure.js` 实现筹码衰减、分桶、峰值、集中度和均线趋势计算。
2. 在 `apex-fe/src/utils/priceStructure.test.mjs` 先覆盖支撑/压力互换、归一化、趋势和数据不足场景。
3. 新增 `ChipDistributionPanel.vue`，绘制筹码分布并输出可扫描的关键结论。
4. 接入 `StockView.vue`，在 K 线标记主要支撑与压力并渲染联动面板。
5. 补充筹码分布词条，运行前端测试与构建，并在桌面/移动视口做浏览器验收。
