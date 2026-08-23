# 决策可执行性审查

## 结果

未发现 Critical 或 Major 问题。

## 已检查

- `canPaperBuy` 仅接受 BUY、`executableHint=true` 和 GREEN/YELLOW 市场数据；缺失或 RED 数据均拒绝下单。
- 桌面和移动端按钮均使用相同的禁用条件，`onPaperOrder` 在事件入口再次验证。
- 行动计划仅复用现有仓位、参考价、止损止盈和离场规则，没有生成新的价格区间或收益判断。
- 新测试已加入 `npm test` 的显式清单。
- 决策买入不再复用通用手工下单或信号下单接口；`/api/paper/from-decision` 服务端以当前用户重新读取 `DailyAction` 和 `DecisionRun`，校验当日、LIVE、SUCCESS、已发布、GREEN/YELLOW 与 `executableHint=1` 后才进入既有模拟盘风控。
- 服务端拒绝测试覆盖历史日期、REPLAY、未发布、RED 数据、风控未通过和非法目标仓位，且均在账户读取与订单写入之前退出。

## 验证

- `npm test`：308 项通过。
- `npm run build`：通过。
- 后端聚焦测试：`PaperDecisionOrderServiceTest`、`PaperAccountIsolationTest` 通过。
- 本地 Vite 服务：`http://localhost:5174/` 返回应用入口。

## 剩余风险

未进行登录后的真实决策数据视觉验收；本地服务可用于该检查。专用接口只约束“按决策买入”，通用模拟盘手工下单仍保持原有产品能力。
