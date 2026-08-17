package com.awe.apex.quant.backtest;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.domain.dto.RollingBacktestFoldResp;
import com.awe.apex.quant.domain.dto.RollingBacktestReq;
import com.awe.apex.quant.domain.dto.RollingBacktestResp;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.strategy.BarSeries;
import com.awe.apex.quant.strategy.Strategy;
import com.awe.apex.quant.strategy.impl.ParamMaStrategy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RollingBacktestAnalyzerTest {

    private final RollingBacktestAnalyzer analyzer = new RollingBacktestAnalyzer(new BacktestEngine());

    @Test
    void shouldBuildNonOverlappingRollingWindowsAndAggregateOutOfSampleReturns() {
        List<BarDaily> stockBars = buildBars("600519", 600, 100, 0.08);
        List<BarDaily> benchmarkBars = buildBars("000300", 600, 100, 0.03);
        RollingBacktestReq req = buildReq("ROLLING", 252, 63, 63);

        RollingBacktestResp result = analyzer.evaluate(
                "600519",
                new ParamMaStrategy(5, 20),
                stockBars,
                benchmarkBars,
                req,
                BacktestCostConfig.defaults());

        assertEquals(5, result.getFoldCount());
        assertEquals(stockBars.get(0).getTradeDate(), result.getFolds().get(0).getTrainBeginDate());
        assertEquals(stockBars.get(251).getTradeDate(), result.getFolds().get(0).getTrainEndDate());
        assertEquals(stockBars.get(252).getTradeDate(), result.getFolds().get(0).getTestBeginDate());
        assertEquals(stockBars.get(314).getTradeDate(), result.getFolds().get(0).getTestEndDate());
        assertEquals(stockBars.get(63).getTradeDate(), result.getFolds().get(1).getTrainBeginDate());
        assertTrue(result.getFolds().get(0).getTestEndDate()
                .isBefore(result.getFolds().get(1).getTestBeginDate()));

        BigDecimal compoundedReturn = BigDecimal.ONE;
        for (RollingBacktestFoldResp fold : result.getFolds()) {
            compoundedReturn = compoundedReturn.multiply(BigDecimal.ONE.add(fold.getOutSampleReturn()));
        }
        assertEquals(
                compoundedReturn.subtract(BigDecimal.ONE).setScale(6, RoundingMode.HALF_UP),
                result.getCompoundedOutSampleReturn());
        assertEquals(new BigDecimal("1000000"), result.getInitCash());
        assertEquals(new BigDecimal("0.00050000"), result.getCost().getCommissionRate());
        assertEquals("NEXT_OPEN_V4", result.getExecutionModelVersion());
    }

    @Test
    void shouldKeepFirstTrainingDateForExpandingWindows() {
        List<BarDaily> stockBars = buildBars("600519", 500, 80, 0.07);
        RollingBacktestReq req = buildReq("EXPANDING", 200, 50, 50);

        RollingBacktestResp result = analyzer.evaluate(
                "600519",
                new ParamMaStrategy(5, 20),
                stockBars,
                buildBars("000300", 500, 100, 0.02),
                req,
                BacktestCostConfig.defaults());

        assertTrue(result.getFoldCount() > 1);
        LocalDate firstTrainingDate = result.getFolds().get(0).getTrainBeginDate();
        for (RollingBacktestFoldResp fold : result.getFolds()) {
            assertEquals(firstTrainingDate, fold.getTrainBeginDate());
        }
    }

    @Test
    void shouldRejectOverlappingOutOfSampleWindows() {
        RollingBacktestReq req = buildReq("ROLLING", 200, 60, 20);

        BusinessException exception = assertThrows(BusinessException.class, () -> analyzer.evaluate(
                "600519",
                new ParamMaStrategy(5, 20),
                buildBars("600519", 500, 100, 0.08),
                buildBars("000300", 500, 100, 0.03),
                req,
                BacktestCostConfig.defaults()));

        assertEquals("步长不能小于样本外窗口，避免重复统计", exception.getMessage());
    }

    @Test
    void shouldApplyExperimentCostToEveryOutOfSampleWindow() {
        List<BarDaily> stockBars = buildBars("600519", 500, 100, 0.12);
        List<BarDaily> benchmarkBars = buildBars("000300", 500, 100, 0.02);
        RollingBacktestReq req = buildReq("ROLLING", 200, 50, 50);
        BacktestCostConfig zeroCost = BacktestCostConfig.of(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        BacktestCostConfig highCost = BacktestCostConfig.of(
                new BigDecimal("0.01"), new BigDecimal("0.01"),
                new BigDecimal("0.01"), new BigDecimal("0.01"));

        RollingBacktestResp zeroCostResult = analyzer.evaluate(
                "600519", new ParamMaStrategy(5, 20), stockBars, benchmarkBars, req, zeroCost);
        RollingBacktestResp highCostResult = analyzer.evaluate(
                "600519", new ParamMaStrategy(5, 20), stockBars, benchmarkBars, req, highCost);

        assertTrue(zeroCostResult.getCompoundedOutSampleReturn()
                .compareTo(highCostResult.getCompoundedOutSampleReturn()) > 0);
        assertEquals(new BigDecimal("0.01000000"), highCostResult.getCost().getBuySlippage());
    }

    @Test
    void shouldRejectBenchmarkWithoutExactWindowBoundary() {
        List<BarDaily> benchmarkBars = buildBars("000300", 500, 100, 0.03);
        benchmarkBars.remove(200);

        BusinessException exception = assertThrows(BusinessException.class, () -> analyzer.evaluate(
                "600519",
                new ParamMaStrategy(5, 20),
                buildBars("600519", 500, 100, 0.08),
                benchmarkBars,
                buildReq("ROLLING", 200, 50, 50),
                BacktestCostConfig.defaults()));

        assertEquals("基准日线未完整覆盖样本外窗口", exception.getMessage());
    }

    @Test
    void shouldReportCoverageAndOverallOutOfSampleSharpe() {
        RollingBacktestResp result = analyzer.evaluate(
                "600519",
                new ParamMaStrategy(5, 20),
                buildBars("600519", 600, 100, 0.08),
                buildBars("000300", 600, 100, 0.03),
                buildReq("ROLLING", 200, 50, 75),
                BacktestCostConfig.defaults());

        assertTrue(result.getCoverageRate().compareTo(BigDecimal.ONE) < 0);
        assertEquals(result.getFolds().get(0).getTestBeginDate(), result.getOutSampleBeginDate());
        assertEquals(result.getFolds().get(result.getFoldCount() - 1).getTestEndDate(), result.getOutSampleEndDate());
        assertTrue(result.getOutSampleSharpe().abs().compareTo(new BigDecimal("100")) < 0);
    }

    @Test
    void shouldUseLiquidationCostsForInSampleAnnualReturn() {
        Strategy strategy = mock(Strategy.class);
        when(strategy.strategyId()).thenReturn("TEST");
        when(strategy.strategyName()).thenReturn("测试策略");
        when(strategy.shouldEnter(any(), anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(1, Integer.class) == 0);
        when(strategy.shouldExit(any(), anyInt(), anyInt(), any())).thenReturn(false);
        List<BarDaily> stockBars = buildBars("600519", 260, 100, 0.08);
        BacktestCostConfig highCost = BacktestCostConfig.of(
                new BigDecimal("0.01"), new BigDecimal("0.01"),
                new BigDecimal("0.01"), new BigDecimal("0.01"));

        BacktestResult expectedInSample = new BacktestEngine().run(
                strategy,
                BarSeries.from(stockBars.subList(0, 100)),
                new BigDecimal("1000000"),
                highCost,
                0,
                true);
        RollingBacktestResp result = analyzer.evaluate(
                "600519",
                strategy,
                stockBars,
                buildBars("000300", 260, 100, 0.03),
                buildReq("ROLLING", 100, 50, 50),
                highCost);

        assertEquals(expectedInSample.getAnnualReturn(), result.getFolds().get(0).getInSampleAnnualReturn());
    }

    @Test
    void shouldStartBenchmarkAtFirstOutSampleOpen() {
        List<BarDaily> benchmarkBars = buildBars("000300", 160, 100, 0.03);
        benchmarkBars.get(60).setOpenPrice(new BigDecimal("100"));
        benchmarkBars.get(60).setClosePrice(new BigDecimal("200"));
        benchmarkBars.get(60).setHighPrice(new BigDecimal("200"));
        benchmarkBars.get(60).setLowPrice(new BigDecimal("100"));
        benchmarkBars.get(79).setClosePrice(new BigDecimal("220"));
        benchmarkBars.get(79).setHighPrice(new BigDecimal("220"));

        RollingBacktestResp result = analyzer.evaluate(
                "600519",
                new ParamMaStrategy(5, 20),
                buildBars("600519", 160, 100, 0.08),
                benchmarkBars,
                buildReq("ROLLING", 60, 20, 20),
                BacktestCostConfig.defaults());

        assertEquals(new BigDecimal("1.200000"), result.getFolds().get(0).getBenchmarkReturn());
    }

    @Test
    void shouldRejectBenchmarkWhenFirstOutSampleOpenIsMissing() {
        List<BarDaily> benchmarkBars = buildBars("000300", 160, 100, 0.03);
        benchmarkBars.get(60).setOpenPrice(null);

        BusinessException exception = assertThrows(BusinessException.class, () -> analyzer.evaluate(
                "600519",
                new ParamMaStrategy(5, 20),
                buildBars("600519", 160, 100, 0.08),
                benchmarkBars,
                buildReq("ROLLING", 60, 20, 20),
                BacktestCostConfig.defaults()));

        assertEquals("基准日线未完整覆盖样本外窗口", exception.getMessage());
    }

    @Test
    void shouldFingerprintExactMarketDataInputs() {
        List<BarDaily> stockBars = buildBars("600519", 160, 100, 0.08);
        List<BarDaily> benchmarkBars = buildBars("000300", 160, 100, 0.03);
        RollingBacktestReq request = buildReq("ROLLING", 60, 20, 20);

        RollingBacktestResp first = analyzer.evaluate(
                "600519", new ParamMaStrategy(5, 20), stockBars, benchmarkBars,
                request, BacktestCostConfig.defaults());
        RollingBacktestResp same = analyzer.evaluate(
                "600519", new ParamMaStrategy(5, 20), stockBars, benchmarkBars,
                request, BacktestCostConfig.defaults());
        RollingBacktestResp differentLimitRegime = analyzer.evaluate(
                "600519", new ParamMaStrategy(5, 20), stockBars, benchmarkBars,
                request, BacktestCostConfig.defaults(), true);
        stockBars.get(80).setClosePrice(stockBars.get(80).getClosePrice().add(new BigDecimal("0.01")));
        RollingBacktestResp changed = analyzer.evaluate(
                "600519", new ParamMaStrategy(5, 20), stockBars, benchmarkBars,
                request, BacktestCostConfig.defaults());

        assertEquals(64, first.getDataFingerprint().length());
        assertEquals(first.getDataFingerprint(), same.getDataFingerprint());
        assertNotEquals(first.getDataFingerprint(), differentLimitRegime.getDataFingerprint());
        assertNotEquals(first.getDataFingerprint(), changed.getDataFingerprint());
    }

    @Test
    void shouldRejectNonPositiveInitialCash() {
        RollingBacktestReq req = buildReq("ROLLING", 200, 50, 50);
        req.setInitCash(BigDecimal.ZERO);

        BusinessException exception = assertThrows(BusinessException.class, () -> analyzer.evaluate(
                "600519",
                new ParamMaStrategy(5, 20),
                buildBars("600519", 500, 100, 0.08),
                buildBars("000300", 500, 100, 0.03),
                req,
                BacktestCostConfig.defaults()));

        assertEquals("初始资金必须大于0", exception.getMessage());
    }

    @Test
    void shouldRejectNegativeExperimentCost() {
        BacktestCostConfig invalidCost = BacktestCostConfig.of(
                new BigDecimal("-0.001"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        BacktestCostConfig incompleteCost = BacktestCostConfig.builder()
                .commissionRate(null)
                .build();

        BusinessException exception = assertThrows(BusinessException.class, () -> analyzer.evaluate(
                "600519",
                new ParamMaStrategy(5, 20),
                buildBars("600519", 500, 100, 0.08),
                buildBars("000300", 500, 100, 0.03),
                buildReq("ROLLING", 200, 50, 50),
                invalidCost));
        BusinessException incompleteCostException = assertThrows(BusinessException.class, () -> analyzer.evaluate(
                "600519",
                new ParamMaStrategy(5, 20),
                buildBars("600519", 500, 100, 0.08),
                buildBars("000300", 500, 100, 0.03),
                buildReq("ROLLING", 200, 50, 50),
                incompleteCost));

        assertEquals("成本和滑点必须在0到5%之间", exception.getMessage());
        assertEquals("成本和滑点必须在0到5%之间", incompleteCostException.getMessage());
    }

    @Test
    void shouldRejectCostWithMoreThanEightDecimalsBeforeExecution() {
        BacktestEngine engine = mock(BacktestEngine.class);
        RollingBacktestAnalyzer isolatedAnalyzer = new RollingBacktestAnalyzer(engine);
        BacktestCostConfig invalidCost = BacktestCostConfig.of(
                new BigDecimal("0.000500491"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        BusinessException exception = assertThrows(BusinessException.class, () -> isolatedAnalyzer.evaluate(
                "600519",
                new ParamMaStrategy(5, 20),
                buildBars("600519", 500, 100, 0.08),
                buildBars("000300", 500, 100, 0.03),
                buildReq("ROLLING", 200, 50, 50),
                invalidCost));

        assertEquals("成本和滑点最多支持8位小数", exception.getMessage());
        verify(engine, never()).run(any(), any(), any(), any(), anyInt(), anyBoolean());
    }

    @Test
    void shouldRejectMoreThanFiftyOutOfSampleWindows() {
        List<BarDaily> stockBars = buildBars("600519", 1100, 100, 0.08);

        BusinessException exception = assertThrows(BusinessException.class, () -> analyzer.evaluate(
                "600519",
                new ParamMaStrategy(5, 20),
                stockBars,
                buildBars("000300", 1100, 100, 0.03),
                buildReq("ROLLING", 60, 20, 20),
                BacktestCostConfig.defaults()));

        assertEquals("样本外窗口超过50个，请缩短日期范围或增大窗口", exception.getMessage());
    }

    private RollingBacktestReq buildReq(String windowMode, int trainDays, int testDays, int stepDays) {
        RollingBacktestReq req = new RollingBacktestReq();
        req.setWindowMode(windowMode);
        req.setTrainDays(trainDays);
        req.setTestDays(testDays);
        req.setStepDays(stepDays);
        req.setInitCash(new BigDecimal("1000000"));
        req.setBenchmarkCode("000300");
        return req;
    }

    private List<BarDaily> buildBars(String code, int count, double basePrice, double trend) {
        List<BarDaily> bars = new ArrayList<>();
        LocalDate beginDate = LocalDate.of(2023, 1, 2);
        for (int index = 0; index < count; index++) {
            double cycle = Math.sin(index * Math.PI / 15) * 8;
            BigDecimal closePrice = BigDecimal.valueOf(basePrice + index * trend + cycle)
                    .setScale(4, RoundingMode.HALF_UP);
            bars.add(BarDaily.builder()
                    .code(code)
                    .tradeDate(beginDate.plusDays(index))
                    .openPrice(closePrice)
                    .highPrice(closePrice.add(BigDecimal.ONE))
                    .lowPrice(closePrice.subtract(BigDecimal.ONE))
                    .closePrice(closePrice)
                    .volume(BigDecimal.valueOf(1000000L + index * 1000L))
                    .build());
        }
        return bars;
    }
}
