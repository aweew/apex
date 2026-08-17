# Apex 策略实验室实现计划

## 任务 1：冻结滚动实验请求契约

- 文件：`apex-be/src/main/java/com/awe/apex/quant/domain/dto/RollingBacktestReq.java`
- 变更：定义代码、策略、日期、窗口模式、训练/测试/步长、初始资金和四项成本字段；所有字段补 Javadoc。
- 边界：窗口和成本的业务校验由评估器负责，DTO 不引入 `Map` 或动态字段。
- 验证：编译 DTO，并检查字段可以完整表达一次实验输入。

## 任务 2：冻结滚动实验响应契约

- 文件：`RollingBacktestResp.java`、`RollingBacktestFoldResp.java`、`BacktestCostResp.java`
- 变更：分别定义汇总、逐窗口和成本快照，回显策略参数、成交语义、复权口径及实际覆盖日期。
- 边界：历史持久化不在本次范围，响应只代表本次可复算快照。
- 验证：检查每个字段均有 Javadoc，且汇总值可由窗口数据复算。

## 任务 3：先锁定次日开盘成交语义

- 文件：`apex-be/src/test/java/com/awe/apex/quant/backtest/BacktestEngineExecutionTest.java`
- 场景：收盘信号只能在下一交易日开盘成交；最后一日信号不成交。
- 验证：旧引擎先失败，修复后交易日期和价格断言通过。

## 任务 4：阻断策略读取未来行情

- 文件：`BacktestEngineExecutionTest.java`、`apex-be/src/main/java/com/awe/apex/quant/strategy/BarSeries.java`
- 变更：为策略提供截至信号日的不可变 O(1) 历史前缀；构造尝试读取未来数据的测试策略。
- 验证：未来读取策略不能产生交易，历史前缀不可修改，改变信号日之后行情不影响此前判断。

## 任务 5：实现可清算窗口回测

- 文件：`BacktestEngine.java`、`BacktestEngineExecutionTest.java`
- 变更：增加评估起点和期末清算参数，买入资金预留佣金，窗口末卖出扣除佣金、印花税和滑点。
- 兼容：原四参数 `run` 保留，空序列继续返回空结果。
- 验证：期末持仓生成“窗口结束强平”成交，旧空序列行为不变。

## 任务 6：先定义窗口生成失败测试

- 文件：`apex-be/src/test/java/com/awe/apex/quant/backtest/RollingBacktestAnalyzerTest.java`
- 场景：固定窗、扩展窗、至少两个窗口、窗口上限、训练/测试最小长度和禁止重叠。
- 验证：实现前目标类缺失或断言失败；实现后窗口日期逐项匹配。

## 任务 7：实现固定窗与扩展窗

- 文件：`apex-be/src/main/java/com/awe/apex/quant/backtest/RollingBacktestAnalyzer.java`
- 变更：按训练日、测试日、步长构造窗口；训练期只用于预热和阶段对照，样本外资金独立起算。
- 边界：最多 50 个窗口，超限显式拒绝；步长小于测试窗显式拒绝。
- 验证：任务 6 的窗口测试通过。

## 任务 8：统一样本内外成本和清算口径

- 文件：`RollingBacktestAnalyzer.java`、`RollingBacktestAnalyzerTest.java`
- 变更：样本内与样本外均使用本次实验成本并在各自窗口末清算，年化收益衰减采用同口径结果。
- 验证：高成本结果低于零成本结果，样本内年化与引擎强平结果一致。

## 任务 9：实现基准边界和汇总指标

- 文件：`RollingBacktestAnalyzer.java`、`RollingBacktestAnalyzerTest.java`
- 变更：基准必须精确覆盖样本外首尾；复合窗口收益、基准、超额、正收益率、胜基准率、覆盖率和整体夏普。
- 边界：整体夏普拼接窗口内日收益计算，不把各窗口夏普均值冒充整体夏普。
- 验证：缺首尾基准时报业务异常，汇总收益和覆盖率可复算。

## 任务 10：接入服务与 API

- 文件：`IBacktestService.java`、`BacktestServiceImpl.java`、`BacktestController.java`
- 变更：新增 `POST /api/backtest/rolling-evaluate`，加载标的/基准日线，应用请求成本并回显策略参数。
- 兼容：保留旧 `/walk-forward` API，不改变既有调用方。
- 验证：`BacktestServiceRollingTest` 和 `BacktestControllerTest` 通过。

## 任务 11：实现前端参数和结果视图

- 文件：`apex-fe/src/api/backtest.js`、`apex-fe/src/utils/backtestLab.js`、`apex-fe/src/views/BacktestView.vue`
- 变更：增加窗口、基准、成本输入，使用独立 `labLoading`，展示实验元数据、九项汇总和逐窗口表格。
- 兼容：保留旧回测能力，移除容易误导的 70/30 页面入口但不删除后端接口。
- 验证：空状态不展示空图表/空表，滚动评估不锁住其他操作按钮。

## 任务 12：先验证前端数据转换

- 文件：`apex-fe/src/utils/backtestLab.test.mjs`
- 场景：页面百分数正确转换为后端小数比例；缺失值与零收益格式化结果不同。
- 验证：`node --test src/utils/backtestLab.test.mjs` 通过。

## 任务 13：桌面和移动端可用性验收

- 文件：`BacktestView.vue`
- 变更：桌面九列指标、窄屏两列指标，成本控件在 390px 单列，明细表只在自身容器横向滚动；回撤使用风险色。
- 验证：1440px 和 390x844 均无页面级横向溢出，按钮高度至少 44px，标签不截断。

## 任务 14：全量回归与交付记录

- 文件：`docs/plans/2026-08-16-strategy-lab-review.md`、`docs/plans/2026-08-16-strategy-lab-implementation-report.md`
- 验证：聚焦后端测试、前端全量测试、Node 20 构建、`git diff --check`；完整后端套件的既有阻塞单独记录。
- 交付：记录已解决风险、残余撮合边界、测试证据和未提交工作树范围。
