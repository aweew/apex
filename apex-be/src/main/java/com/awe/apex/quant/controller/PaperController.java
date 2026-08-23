package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.domain.dto.CorrelationMatrixResp;
import com.awe.apex.quant.domain.dto.AtrStopResp;
import com.awe.apex.quant.domain.dto.BetaTargetResp;
import com.awe.apex.quant.domain.dto.EquityQualityResp;
import com.awe.apex.quant.domain.dto.FactorExposureResp;
import com.awe.apex.quant.domain.dto.FillQualityResp;
import com.awe.apex.quant.domain.dto.GapRiskResp;
import com.awe.apex.quant.domain.dto.HoldBucketResp;
import com.awe.apex.quant.domain.dto.MonteCarloResp;
import com.awe.apex.quant.domain.dto.PaperHealthResp;
import com.awe.apex.quant.domain.dto.ReturnHistResp;
import com.awe.apex.quant.domain.dto.StopCoverageResp;
import com.awe.apex.quant.domain.dto.TradeCalendarResp;
import com.awe.apex.quant.domain.dto.VolTargetResp;
import com.awe.apex.quant.domain.dto.WeekdayPnlResp;
import com.awe.apex.quant.domain.dto.KellySuggestResp;
import com.awe.apex.quant.domain.dto.MonthlyReturnResp;
import com.awe.apex.quant.domain.dto.PaperCostResp;
import com.awe.apex.quant.domain.dto.PaperExposureResp;
import com.awe.apex.quant.domain.dto.PaperOpenReq;
import com.awe.apex.quant.domain.dto.PaperOrderReq;
import com.awe.apex.quant.domain.dto.PaperPerformanceResp;
import com.awe.apex.quant.domain.dto.PositionStopsReq;
import com.awe.apex.quant.domain.dto.PositionSuggestResp;
import com.awe.apex.quant.domain.dto.RebalanceSuggestResp;
import com.awe.apex.quant.domain.entity.PaperAccount;
import com.awe.apex.quant.domain.entity.PaperOrder;
import com.awe.apex.quant.domain.entity.PaperPosition;
import com.awe.apex.quant.service.IPaperService;
import com.awe.apex.quant.service.ApexUserAuthService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 模拟盘接口
 */
@RestController
@RequestMapping("/api/paper")
public class PaperController {

    @Resource
    private IPaperService paperService;

    @Resource
    private ApexUserAuthService apexUserAuthService;

    /**
     * 开户或入金
     */
    @PostMapping("/open")
    public Result<PaperAccount> open(@Valid @RequestBody PaperOpenReq req) {
        throw new BusinessException("模拟账户由邀请注册自动创建");
    }

    /**
     * 默认账户
     */
    @GetMapping("/account")
    public Result<PaperAccount> account() {
        return Result.success(paperService.getAccount(apexUserAuthService.currentPaperAccountId()));
    }

    /**
     * 下单
     */
    @PostMapping("/order")
    public Result<PaperOrder> order(@Valid @RequestBody PaperOrderReq req) {
        req.setAccountId(apexUserAuthService.currentPaperAccountId());
        return Result.success(paperService.placeOrder(req));
    }

    /**
     * 持仓
     */
    @GetMapping("/positions")
    public Result<List<PaperPosition>> positions(@RequestParam(required = false) Long accountId) {
        return Result.success(paperService.listPositions(apexUserAuthService.currentPaperAccountId()));
    }

    /**
     * 订单
     */
    @GetMapping("/orders")
    public Result<List<PaperOrder>> orders(@RequestParam(required = false) Long accountId) {
        return Result.success(paperService.listOrders(apexUserAuthService.currentPaperAccountId()));
    }

    /**
     * 建议买入数量（风控单票上限）
     */
    @GetMapping("/suggest")
    public Result<PositionSuggestResp> suggest(@RequestParam String code,
                                               @RequestParam(required = false) Long accountId,
                                               @RequestParam(required = false) BigDecimal targetWeight) {
        return Result.success(paperService.suggestPosition(accountId, code, targetWeight));
    }

    /**
     * 一键平仓
     */
    @PostMapping("/close-all")
    public Result<List<PaperOrder>> closeAll(@RequestParam(required = false) Long accountId) {
        return Result.success(paperService.closeAll(accountId));
    }

    /**
     * 信号转模拟单
     */
    @PostMapping("/from-signal")
    public Result<PaperOrder> fromSignal(@RequestParam Long signalId,
                                         @RequestParam(required = false) Long accountId,
                                         @RequestParam(required = false) BigDecimal targetWeight) {
        return Result.success(paperService.orderFromSignal(signalId, accountId, targetWeight));
    }

    /**
     * 决策转模拟买入单
     */
    @PostMapping("/from-decision")
    public Result<PaperOrder> fromDecision(@RequestParam Long decisionActionId,
                                           @RequestParam(required = false) Long accountId,
                                           @RequestParam(required = false) BigDecimal targetWeight) {
        return Result.success(paperService.orderFromDecision(decisionActionId, accountId, targetWeight));
    }

    /**
     * 刷新持仓市价与浮盈
     */
    @PostMapping("/refresh-marks")
    public Result<List<PaperPosition>> refreshMarks(@RequestParam(required = false) Long accountId) {
        return Result.success(paperService.refreshMarks(accountId));
    }

    /**
     * 相对基准绩效
     */
    @GetMapping("/performance")
    public Result<PaperPerformanceResp> performance(@RequestParam(required = false) Long accountId,
                                                    @RequestParam(required = false, defaultValue = "000300") String benchmarkCode,
                                                    @RequestParam(required = false, defaultValue = "000905") String altBenchmarkCode) {
        return Result.success(paperService.performance(accountId, benchmarkCode, altBenchmarkCode));
    }

    /**
     * 持仓暴露与集中度
     */
    @GetMapping("/exposure")
    public Result<PaperExposureResp> exposure(@RequestParam(required = false) Long accountId) {
        return Result.success(paperService.exposure(accountId));
    }

    /**
     * 更新止损止盈
     */
    @PostMapping("/stops")
    public Result<PaperPosition> updateStops(@Valid @RequestBody PositionStopsReq req) {
        req.setAccountId(apexUserAuthService.currentPaperAccountId());
        return Result.success(paperService.updateStops(req));
    }

    /**
     * 平仓已触发止损/止盈
     */
    @PostMapping("/close-triggered")
    public Result<List<PaperOrder>> closeTriggered(@RequestParam(required = false) Long accountId,
                                                   @RequestParam(required = false, defaultValue = "BOTH") String type) {
        return Result.success(paperService.closeTriggered(accountId, type));
    }

    /**
     * 股票池等权再平衡建议
     */
    @GetMapping("/rebalance-suggest")
    public Result<RebalanceSuggestResp> rebalanceSuggest(@RequestParam(required = false) Long accountId,
                                                         @RequestParam(required = false, defaultValue = "8") Integer limit) {
        return Result.success(paperService.rebalanceSuggest(accountId, limit));
    }

    /**
     * 高分 BUY 信号批量买入建议
     */
    @GetMapping("/signal-buy-suggest")
    public Result<RebalanceSuggestResp> signalBuySuggest(@RequestParam(required = false) Long accountId,
                                                         @RequestParam(required = false, defaultValue = "5") Integer limit,
                                                         @RequestParam(required = false) BigDecimal minScore) {
        return Result.success(paperService.signalBuySuggest(accountId, limit, minScore));
    }

    /**
     * 纸面月度收益
     */
    @GetMapping("/monthly")
    public Result<List<MonthlyReturnResp>> monthly(@RequestParam(required = false) Long accountId) {
        return Result.success(paperService.monthlyReturns(accountId));
    }

    /**
     * 持仓相关性
     */
    @GetMapping("/correlation")
    public Result<CorrelationMatrixResp> correlation(@RequestParam(required = false) Long accountId,
                                                     @RequestParam(required = false, defaultValue = "60") Integer lookback) {
        return Result.success(paperService.positionCorrelation(accountId, lookback));
    }

    /**
     * 交易成本汇总
     */
    @GetMapping("/cost")
    public Result<PaperCostResp> cost(@RequestParam(required = false) Long accountId) {
        return Result.success(paperService.costSummary(accountId));
    }

    /**
     * Kelly 仓位建议
     */
    @GetMapping("/kelly")
    public Result<KellySuggestResp> kelly(@RequestParam(required = false) Long accountId) {
        return Result.success(paperService.kellySuggest(accountId));
    }

    /**
     * 成交质量
     */
    @GetMapping("/fill-quality")
    public Result<FillQualityResp> fillQuality(@RequestParam(required = false) Long accountId,
                                               @RequestParam(required = false, defaultValue = "30") Integer limit) {
        return Result.success(paperService.fillQuality(accountId, limit));
    }

    /**
     * 持仓隔夜缺口风险
     */
    @GetMapping("/gap-risk")
    public Result<GapRiskResp> gapRisk(@RequestParam(required = false) Long accountId) {
        return Result.success(paperService.gapRisk(accountId));
    }

    /**
     * 持仓周期分桶
     */
    @GetMapping("/hold-buckets")
    public Result<HoldBucketResp> holdBuckets(@RequestParam(required = false) Long accountId) {
        return Result.success(paperService.holdBuckets(accountId));
    }

    /**
     * 周几盈亏
     */
    @GetMapping("/weekday-pnl")
    public Result<WeekdayPnlResp> weekdayPnl(@RequestParam(required = false) Long accountId) {
        return Result.success(paperService.weekdayPnl(accountId));
    }

    /**
     * 蒙特卡洛压力
     */
    @GetMapping("/monte-carlo")
    public Result<MonteCarloResp> monteCarlo(@RequestParam(required = false) Long accountId,
                                             @RequestParam(required = false, defaultValue = "500") Integer paths,
                                             @RequestParam(required = false, defaultValue = "20") Integer horizonDays) {
        return Result.success(paperService.monteCarlo(accountId, paths, horizonDays));
    }

    /**
     * 因子暴露
     */
    @GetMapping("/factor-exposure")
    public Result<FactorExposureResp> factorExposure(@RequestParam(required = false) Long accountId) {
        return Result.success(paperService.factorExposure(accountId));
    }

    /**
     * ATR 止损建议
     */
    @GetMapping("/atr-stops")
    public Result<AtrStopResp> atrStops(@RequestParam(required = false) Long accountId) {
        return Result.success(paperService.atrStopSuggest(accountId));
    }

    /**
     * 应用 ATR 止损
     */
    @PostMapping("/atr-stops/apply")
    public Result<Integer> applyAtrStops(@RequestParam(required = false) Long accountId) {
        return Result.success(paperService.applyAtrStops(accountId));
    }

    /**
     * 闭合收益分布
     */
    @GetMapping("/return-hist")
    public Result<ReturnHistResp> returnHist(@RequestParam(required = false) Long accountId) {
        return Result.success(paperService.returnHistogram(accountId));
    }

    /**
     * 波动目标缩放
     */
    @GetMapping("/vol-target")
    public Result<VolTargetResp> volTarget(@RequestParam(required = false) Long accountId) {
        return Result.success(paperService.volTarget(accountId));
    }

    /**
     * 成交日历
     */
    @GetMapping("/trade-calendar")
    public Result<TradeCalendarResp> tradeCalendar(@RequestParam(required = false) Long accountId,
                                                   @RequestParam(required = false, defaultValue = "60") Integer days) {
        return Result.success(paperService.tradeCalendar(accountId, days));
    }

    /**
     * 止损覆盖率
     */
    @GetMapping("/stop-coverage")
    public Result<StopCoverageResp> stopCoverage(@RequestParam(required = false) Long accountId) {
        return Result.success(paperService.stopCoverage(accountId));
    }

    /**
     * Beta 目标
     */
    @GetMapping("/beta-target")
    public Result<BetaTargetResp> betaTarget(@RequestParam(required = false) Long accountId) {
        return Result.success(paperService.betaTarget(accountId));
    }

    /**
     * 模拟盘健康分
     */
    @GetMapping("/health-score")
    public Result<PaperHealthResp> healthScore(@RequestParam(required = false) Long accountId) {
        return Result.success(paperService.healthScore(accountId));
    }

    /**
     * 权益曲线质量
     */
    @GetMapping("/equity-quality")
    public Result<EquityQualityResp> equityQuality(@RequestParam(required = false) Long accountId) {
        return Result.success(paperService.equityQuality(accountId));
    }
}
