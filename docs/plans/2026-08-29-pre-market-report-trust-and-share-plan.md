# 盘前研报可信度与分享效率实施计划

1. 在后端测试中覆盖情绪档位统一、市场状态去重、上一交易日方向变化和可核验机会条件。
2. 扩展 `DailyPreMarketReportResp`，增加方向变化字段；调整报告缓存版本并保存用户级上一份报告快照。
3. 调整 `DailyPreMarketReportServiceImpl`，统一情绪档位、生成方向变化并收紧规则版机会条件。
4. 在前端布局测试中覆盖时效摘要、方向变化、持仓处理顺序和精简分享图。
5. 调整 `PreMarketReportView.vue`、`PreMarketReportShareSheet.vue` 和 `PreMarketReportSections.vue`，完成新信息层级与分享密度。
6. 运行后端聚焦测试、前端聚焦测试、完整前端测试和构建，修复回归。
7. 启动本地前端并对桌面、390px 移动端和分享图进行截图验收，最后执行代码审查和差异检查。
