package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.AtrStopResp;
import com.awe.apex.quant.domain.dto.BetaTargetResp;
import com.awe.apex.quant.domain.dto.CorrelationMatrixResp;
import com.awe.apex.quant.domain.dto.EquityQualityResp;
import com.awe.apex.quant.domain.dto.FactorExposureResp;
import com.awe.apex.quant.domain.dto.FillQualityResp;
import com.awe.apex.quant.domain.dto.GapRiskResp;
import com.awe.apex.quant.domain.dto.HoldBucketResp;
import com.awe.apex.quant.domain.dto.KellySuggestResp;
import com.awe.apex.quant.domain.dto.MonteCarloResp;
import com.awe.apex.quant.domain.dto.MonthlyReturnResp;
import com.awe.apex.quant.domain.dto.PaperCostResp;
import com.awe.apex.quant.domain.dto.PaperExposureResp;
import com.awe.apex.quant.domain.dto.PaperHealthResp;
import com.awe.apex.quant.domain.dto.PaperOpenReq;
import com.awe.apex.quant.domain.dto.PaperOrderReq;
import com.awe.apex.quant.domain.dto.PaperPerformanceResp;
import com.awe.apex.quant.domain.dto.PositionStopsReq;
import com.awe.apex.quant.domain.dto.PositionSuggestResp;
import com.awe.apex.quant.domain.dto.RebalanceSuggestResp;
import com.awe.apex.quant.domain.dto.ReturnHistResp;
import com.awe.apex.quant.domain.dto.StopCoverageResp;
import com.awe.apex.quant.domain.dto.TradeCalendarResp;
import com.awe.apex.quant.domain.dto.VolTargetResp;
import com.awe.apex.quant.domain.dto.WeekdayPnlResp;
import com.awe.apex.quant.domain.entity.PaperAccount;
import com.awe.apex.quant.domain.entity.PaperOrder;
import com.awe.apex.quant.domain.entity.PaperPosition;

import java.math.BigDecimal;
import java.util.List;

/**
 * 模拟盘服务
 */
public interface IPaperService {

    /**
     * 根据ID获取模拟账户
     *
     * @param accountId 账户ID
     * @return 模拟账户
     */
    PaperAccount getAccount(Long accountId);

    /**
     * 开户或入金
     *
     * @param req 请求
     * @return 账户
     */
    PaperAccount openOrDeposit(PaperOpenReq req);

    /**
     * 默认账户（没有则创建）
     *
     * @return 账户
     */
    PaperAccount defaultAccount();

    /**
     * 下模拟单
     *
     * @param req 请求
     * @return 订单
     */
    PaperOrder placeOrder(PaperOrderReq req);

    /**
     * 持仓
     *
     * @param accountId 账户
     * @return 持仓
     */
    List<PaperPosition> listPositions(Long accountId);

    /**
     * 订单
     *
     * @param accountId 账户
     * @return 订单
     */
    List<PaperOrder> listOrders(Long accountId);

    /**
     * 按风控上限建议买入数量
     *
     * @param accountId    账户
     * @param code         代码
     * @param targetWeight 目标仓位，可空（默认取单票上限）
     * @return 建议
     */
    PositionSuggestResp suggestPosition(Long accountId, String code, BigDecimal targetWeight);

    /**
     * 一键平仓全部持仓
     *
     * @param accountId 账户
     * @return 生成的卖出订单
     */
    List<PaperOrder> closeAll(Long accountId);

    /**
     * 按信号一键下模拟单（BUY 默认按单票上限，SELL 默认全平）
     *
     * @param signalId     信号ID
     * @param accountId    账户，可空
     * @param targetWeight 买入目标仓位，可空
     * @return 订单
     */
    PaperOrder orderFromSignal(Long signalId, Long accountId, BigDecimal targetWeight);

    /**
     * 按当日正式决策创建模拟买入单
     *
     * @param decisionActionId 决策动作ID
     * @param accountId        账户，可空
     * @param targetWeight     买入目标仓位，可空
     * @return 订单
     */
    PaperOrder orderFromDecision(Long decisionActionId, Long accountId, BigDecimal targetWeight);

    /**
     * 刷新持仓市价与浮盈
     *
     * @param accountId 账户
     * @return 持仓
     */
    List<PaperPosition> refreshMarks(Long accountId);

    /**
     * 相对基准绩效（默认沪深300）
     *
     * @param accountId     账户
     * @param benchmarkCode 基准
     * @return 绩效
     */
    PaperPerformanceResp performance(Long accountId, String benchmarkCode, String altBenchmarkCode);

    /**
     * 持仓暴露与集中度
     *
     * @param accountId 账户
     * @return 暴露
     */
    PaperExposureResp exposure(Long accountId);

    /**
     * 更新止损止盈
     *
     * @param req 请求
     * @return 持仓
     */
    PaperPosition updateStops(PositionStopsReq req);

    /**
     * 平仓已触发止损/止盈的持仓
     *
     * @param accountId 账户
     * @param type      STOP / TAKE / BOTH
     * @return 卖出订单
     */
    List<PaperOrder> closeTriggered(Long accountId, String type);

    /**
     * 股票池等权再平衡建议（不下单）
     *
     * @param accountId 账户
     * @param limit     目标成分数
     * @return 建议
     */
    RebalanceSuggestResp rebalanceSuggest(Long accountId, Integer limit);

    /**
     * 按高分 BUY 信号生成批量买入建议（不下单）
     *
     * @param accountId 账户
     * @param limit     信号数
     * @param minScore  最低评分
     * @return 建议
     */
    RebalanceSuggestResp signalBuySuggest(Long accountId, Integer limit, BigDecimal minScore);

    /**
     * 纸面月度收益（基于 MTM 权益曲线）
     *
     * @param accountId 账户
     * @return 月度收益
     */
    List<MonthlyReturnResp> monthlyReturns(Long accountId);

    /**
     * 持仓日收益相关性
     *
     * @param accountId 账户
     * @param lookback  回看天数
     * @return 矩阵
     */
    CorrelationMatrixResp positionCorrelation(Long accountId, Integer lookback);

    /**
     * 交易成本汇总
     *
     * @param accountId 账户
     * @return 费用
     */
    PaperCostResp costSummary(Long accountId);

    /**
     * Kelly 仓位建议（基于历史闭合交易）
     *
     * @param accountId 账户
     * @return 建议
     */
    KellySuggestResp kellySuggest(Long accountId);

    /**
     * 成交质量（相对当日收盘滑点）
     *
     * @param accountId 账户
     * @param limit     明细条数
     * @return 质量
     */
    FillQualityResp fillQuality(Long accountId, Integer limit);

    /**
     * 持仓隔夜缺口风险（开盘相对昨收）
     *
     * @param accountId 账户
     * @return 缺口
     */
    GapRiskResp gapRisk(Long accountId);

    /**
     * 闭合交易持仓周期分桶
     *
     * @param accountId 账户
     * @return 分桶
     */
    HoldBucketResp holdBuckets(Long accountId);

    /**
     * 按卖出星期几的盈亏分布
     *
     * @param accountId 账户
     * @return 分布
     */
    WeekdayPnlResp weekdayPnl(Long accountId);

    /**
     * 权益日收益 Bootstrap 蒙特卡洛
     *
     * @param accountId   账户
     * @param paths       路径数
     * @param horizonDays 每路径交易日
     * @return 分布
     */
    MonteCarloResp monteCarlo(Long accountId, Integer paths, Integer horizonDays);

    /**
     * 组合简易因子暴露
     *
     * @param accountId 账户
     * @return 暴露
     */
    FactorExposureResp factorExposure(Long accountId);

    /**
     * ATR 止损/止盈建议
     *
     * @param accountId 账户
     * @return 建议
     */
    AtrStopResp atrStopSuggest(Long accountId);

    /**
     * 按 ATR 建议写入止损止盈
     *
     * @param accountId 账户
     * @return 更新条数
     */
    Integer applyAtrStops(Long accountId);

    /**
     * 闭合交易收益分布直方图
     *
     * @param accountId 账户
     * @return 分布
     */
    ReturnHistResp returnHistogram(Long accountId);

    /**
     * 波动目标仓位缩放
     *
     * @param accountId 账户
     * @return 缩放建议
     */
    VolTargetResp volTarget(Long accountId);

    /**
     * 成交日历热力
     *
     * @param accountId 账户
     * @param days      回看自然日
     * @return 日历
     */
    TradeCalendarResp tradeCalendar(Long accountId, Integer days);

    /**
     * 止损止盈覆盖率
     *
     * @param accountId 账户
     * @return 覆盖
     */
    StopCoverageResp stopCoverage(Long accountId);

    /**
     * 组合 Beta 目标缩放
     *
     * @param accountId 账户
     * @return 建议
     */
    BetaTargetResp betaTarget(Long accountId);

    /**
     * 模拟盘健康分
     *
     * @param accountId 账户
     * @return 健康分
     */
    PaperHealthResp healthScore(Long accountId);

    /**
     * 权益曲线质量
     *
     * @param accountId 账户
     * @return 质量
     */
    EquityQualityResp equityQuality(Long accountId);
}
