package com.awe.apex.quant.service.impl;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.backtest.BacktestCostConfig;
import com.awe.apex.quant.backtest.BacktestEngine;
import com.awe.apex.quant.backtest.BacktestResult;
import com.awe.apex.quant.domain.dto.BacktestRunReq;
import com.awe.apex.quant.domain.dto.ParamSweepItemResp;
import com.awe.apex.quant.domain.dto.ParamSweepReq;
import com.awe.apex.quant.domain.dto.RollingBacktestReq;
import com.awe.apex.quant.domain.dto.RollingBacktestResp;
import com.awe.apex.quant.domain.dto.RollingStrategyConfig;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.market.DailyBarClient;
import com.awe.apex.quant.service.IBarDailyService;
import com.awe.apex.quant.service.IBacktestExperimentService;
import com.awe.apex.quant.service.IConfigService;
import com.awe.apex.quant.strategy.BarSeries;
import com.awe.apex.quant.strategy.Strategy;
import com.awe.apex.quant.strategy.StrategyParams;
import com.awe.apex.quant.strategy.impl.ParamMaStrategy;
import com.awe.apex.quant.strategy.impl.S1MaTrendStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BacktestServiceRollingTest {

    private final BarDailyMapper barDailyMapper = mock(BarDailyMapper.class);
    private final IConfigService configService = mock(IConfigService.class);
    private final IBarDailyService barDailyService = mock(IBarDailyService.class);
    private final IBacktestExperimentService backtestExperimentService = mock(IBacktestExperimentService.class);
    private final StrategyParams strategyParams = mock(StrategyParams.class);
    private final BacktestServiceImpl service = new BacktestServiceImpl();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "strategies", List.of(
                new ParamMaStrategy(5, 20), new S1MaTrendStrategy()));
        ReflectionTestUtils.setField(service, "barDailyMapper", barDailyMapper);
        ReflectionTestUtils.setField(service, "backtestEngine", new BacktestEngine());
        ReflectionTestUtils.setField(service, "configService", configService);
        ReflectionTestUtils.setField(service, "barDailyService", barDailyService);
        ReflectionTestUtils.setField(service, "backtestExperimentService", backtestExperimentService);
        ReflectionTestUtils.setField(service, "strategyParams", strategyParams);
        when(configService.getDecimal(anyString(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(backtestExperimentService.save(any(), any())).thenReturn(42L);
    }

    @Test
    void shouldApplyRequestCostAndReturnReproducibleSnapshot() {
        List<BarDaily> stockBars = buildBars("600519", 500, 100, 0.08);
        List<BarDaily> benchmarkBars = buildBars("000300", 500, 100, 0.03);
        when(barDailyMapper.selectCount(any())).thenReturn(500L);
        when(barDailyMapper.selectList(any())).thenReturn(benchmarkBars, stockBars, benchmarkBars);
        RollingBacktestReq req = request();
        req.setCommissionRate(new BigDecimal("0.002"));
        req.setBuySlippage(BigDecimal.ZERO);

        RollingBacktestResp result = service.rollingEvaluate(req);

        assertEquals(new BigDecimal("0.00200000"), result.getCost().getCommissionRate());
        assertEquals(new BigDecimal("0.00000000"), result.getCost().getBuySlippage());
        assertEquals("NEXT_OPEN_V4", result.getExecutionModelVersion());
        assertEquals("QFQ", result.getPriceAdjustment());
        assertTrue(result.getStrategyParameters().contains("fast=5"));
        assertEquals("000300", result.getBenchmarkCode());
        assertEquals(42L, result.getExperimentId());
        ArgumentCaptor<RollingBacktestReq> requestCaptor = ArgumentCaptor.forClass(RollingBacktestReq.class);
        verify(backtestExperimentService).save(requestCaptor.capture(), any(RollingBacktestResp.class));
        RollingBacktestReq effectiveRequest = requestCaptor.getValue();
        assertEquals(result.getDataBeginDate().toString(), effectiveRequest.getBeginDate());
        assertEquals(result.getDataEndDate().toString(), effectiveRequest.getEndDate());
        assertEquals(result.getTrainDays(), effectiveRequest.getTrainDays());
        assertEquals(result.getCost().getStampTaxRate(), effectiveRequest.getStampTaxRate());
        assertEquals("PARAM_MA_V1", effectiveRequest.getStrategyConfig().getLogicVersion());
    }

    @Test
    void shouldPreserveEightDecimalCostAcrossExecutionAndExperimentSnapshot() {
        BacktestEngine engine = spy(new BacktestEngine());
        ReflectionTestUtils.setField(service, "backtestEngine", engine);
        List<BarDaily> stockBars = buildBars("600519", 500, 100, 0.08);
        List<BarDaily> benchmarkBars = buildBars("000300", 500, 100, 0.03);
        when(barDailyMapper.selectCount(any())).thenReturn(500L);
        when(barDailyMapper.selectList(any())).thenReturn(benchmarkBars, stockBars, benchmarkBars);
        RollingBacktestReq req = request();
        req.setCommissionRate(new BigDecimal("0.00050049"));

        RollingBacktestResp result = service.rollingEvaluate(req);

        ArgumentCaptor<BacktestCostConfig> executionCostCaptor = ArgumentCaptor.forClass(BacktestCostConfig.class);
        verify(engine, atLeastOnce()).run(any(Strategy.class), any(BarSeries.class), any(BigDecimal.class),
                executionCostCaptor.capture(), anyInt(), eq(true));
        assertEquals(new BigDecimal("0.00050049"),
                executionCostCaptor.getAllValues().get(0).getCommissionRate());
        assertEquals(new BigDecimal("0.00050049"), result.getCost().getCommissionRate());

        ArgumentCaptor<RollingBacktestReq> requestCaptor = ArgumentCaptor.forClass(RollingBacktestReq.class);
        ArgumentCaptor<RollingBacktestResp> resultCaptor = ArgumentCaptor.forClass(RollingBacktestResp.class);
        verify(backtestExperimentService).save(requestCaptor.capture(), resultCaptor.capture());
        assertEquals(new BigDecimal("0.00050049"), requestCaptor.getValue().getCommissionRate());
        assertEquals(new BigDecimal("0.00050049"), resultCaptor.getValue().getCost().getCommissionRate());
    }

    @Test
    void shouldNotReadCurrentCostDefaultsWhenExperimentProvidesCompleteSnapshot() {
        List<BarDaily> stockBars = buildBars("600519", 500, 100, 0.08);
        List<BarDaily> benchmarkBars = buildBars("000300", 500, 100, 0.03);
        when(barDailyMapper.selectCount(any())).thenReturn(500L);
        when(barDailyMapper.selectList(any())).thenReturn(benchmarkBars, stockBars, benchmarkBars);
        RollingBacktestReq req = request();
        req.setCommissionRate(new BigDecimal("0.00040000"));
        req.setStampTaxRate(new BigDecimal("0.00050000"));
        req.setBuySlippage(new BigDecimal("0.00080000"));
        req.setSellSlippage(new BigDecimal("0.00090000"));

        RollingBacktestResp result = service.rollingEvaluate(req);

        assertEquals(new BigDecimal("0.00040000"), result.getCost().getCommissionRate());
        assertEquals(new BigDecimal("0.00050000"), result.getCost().getStampTaxRate());
        assertEquals(new BigDecimal("0.00080000"), result.getCost().getBuySlippage());
        assertEquals(new BigDecimal("0.00090000"), result.getCost().getSellSlippage());
        verify(configService, never()).getDecimal(anyString(), any());
    }

    @Test
    void shouldRejectInvalidDateRangesBeforeLoadingBars() {
        RollingBacktestReq reversedRequest = request();
        reversedRequest.setBeginDate("2026-08-01");
        reversedRequest.setEndDate("2025-01-01");
        RollingBacktestReq futureRequest = request();
        futureRequest.setEndDate("2099-01-01");

        BusinessException reversedException = assertThrows(
                BusinessException.class, () -> service.rollingEvaluate(reversedRequest));
        BusinessException futureException = assertThrows(
                BusinessException.class, () -> service.rollingEvaluate(futureRequest));

        assertEquals("开始日期不能晚于结束日期", reversedException.getMessage());
        assertEquals("结束日期不能晚于今天", futureException.getMessage());
        verify(barDailyMapper, never()).selectCount(any());
        verify(barDailyService, never()).syncBars(any());
    }

    @Test
    void shouldRejectInvalidWindowBeforeLoadingBars() {
        RollingBacktestReq req = request();
        req.setTrainDays(59);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.rollingEvaluate(req));

        assertEquals("样本内窗口不能少于60个交易日", exception.getMessage());
        verify(barDailyMapper, never()).selectCount(any());
        verify(barDailyService, never()).syncBars(any());
    }

    @Test
    void shouldRejectInvalidWindowModeBeforeLoadingBars() {
        RollingBacktestReq req = request();
        req.setWindowMode("FUTURE");

        BusinessException exception = assertThrows(BusinessException.class, () -> service.rollingEvaluate(req));

        assertEquals("窗口模式仅支持 ROLLING 或 EXPANDING", exception.getMessage());
        verify(barDailyMapper, never()).selectCount(any());
        verify(barDailyService, never()).syncBars(any());
    }

    @Test
    void shouldRejectInvalidSecurityAndBenchmarkCodesBeforeLoadingBars() {
        RollingBacktestReq invalidSecurityRequest = request();
        invalidSecurityRequest.setCode("not-a-code");
        RollingBacktestReq stockBenchmarkRequest = request();
        stockBenchmarkRequest.setBenchmarkCode("000001");

        BusinessException securityException = assertThrows(
                BusinessException.class, () -> service.rollingEvaluate(invalidSecurityRequest));
        BusinessException benchmarkException = assertThrows(
                BusinessException.class, () -> service.rollingEvaluate(stockBenchmarkRequest));

        assertEquals("证券代码格式无效", securityException.getMessage());
        assertEquals("基准仅支持已登记指数代码", benchmarkException.getMessage());
        verify(barDailyMapper, never()).selectCount(any());
        verify(barDailyService, never()).syncBars(any());
    }

    @Test
    void shouldRejectInvalidCashAndCostBeforeLoadingBars() {
        RollingBacktestReq invalidCashRequest = request();
        invalidCashRequest.setInitCash(BigDecimal.ZERO);
        RollingBacktestReq invalidCostRequest = request();
        invalidCostRequest.setCommissionRate(new BigDecimal("-0.001"));

        BusinessException cashException = assertThrows(BusinessException.class,
                () -> service.rollingEvaluate(invalidCashRequest));
        BusinessException costException = assertThrows(BusinessException.class,
                () -> service.rollingEvaluate(invalidCostRequest));

        assertEquals("初始资金必须大于0", cashException.getMessage());
        assertEquals("成本和滑点必须在0到5%之间", costException.getMessage());
        verify(barDailyMapper, never()).selectCount(any());
        verify(barDailyService, never()).syncBars(any());
    }

    @Test
    void shouldRejectCashWithMoreThanTwoDecimalsBeforeLoadingBars() {
        RollingBacktestReq req = request();
        req.setInitCash(new BigDecimal("1000000.001"));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.rollingEvaluate(req));

        assertEquals("初始资金最多支持2位小数", exception.getMessage());
        verify(barDailyMapper, never()).selectCount(any());
        verify(barDailyService, never()).syncBars(any());
    }

    @Test
    void shouldRejectCostWithMoreThanEightDecimalsBeforeLoadingBars() {
        RollingBacktestReq req = request();
        req.setCommissionRate(new BigDecimal("0.000500491"));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.rollingEvaluate(req));

        assertEquals("成本和滑点最多支持8位小数", exception.getMessage());
        verify(barDailyMapper, never()).selectCount(any());
        verify(barDailyService, never()).syncBars(any());
    }

    @Test
    void shouldReuseSavedS1ParametersAfterSystemDefaultsChange() {
        List<BarDaily> stockBars = buildBars("600519", 500, 100, 0.08);
        List<BarDaily> benchmarkBars = buildBars("000300", 500, 100, 0.03);
        when(barDailyMapper.selectCount(any())).thenReturn(500L);
        when(barDailyMapper.selectList(any())).thenReturn(benchmarkBars, stockBars, benchmarkBars);
        when(strategyParams.s1FastMa()).thenReturn(30);
        when(strategyParams.s1SlowMa()).thenReturn(120);
        when(strategyParams.s1VolMa()).thenReturn(30);
        RollingBacktestReq req = request();
        req.setStrategyId("S1");
        req.setStrategyConfig(RollingStrategyConfig.builder()
                .strategyId("S1")
                .logicVersion("S1_V1")
                .s1FastMa(5)
                .s1SlowMa(20)
                .s1VolumeMa(5)
                .build());

        RollingBacktestResp result = service.rollingEvaluate(req);

        assertEquals("version=S1_V1, fastMa=5, slowMa=20, volumeMa=5", result.getStrategyParameters());
        ArgumentCaptor<RollingBacktestReq> requestCaptor = ArgumentCaptor.forClass(RollingBacktestReq.class);
        verify(backtestExperimentService).save(requestCaptor.capture(), any(RollingBacktestResp.class));
        RollingStrategyConfig savedConfig = requestCaptor.getValue().getStrategyConfig();
        assertEquals("S1", savedConfig.getStrategyId());
        assertEquals("S1_V1", savedConfig.getLogicVersion());
        assertEquals(5, savedConfig.getS1FastMa());
        assertEquals(20, savedConfig.getS1SlowMa());
        assertEquals(5, savedConfig.getS1VolumeMa());
    }

    @Test
    void shouldRejectStrategySnapshotForAnotherStrategy() {
        RollingBacktestReq req = request();
        req.setStrategyId("S1");
        req.setStrategyConfig(RollingStrategyConfig.builder().strategyId("S2").build());

        BusinessException exception = assertThrows(BusinessException.class, () -> service.rollingEvaluate(req));

        assertEquals("策略参数快照与所选策略不一致", exception.getMessage());
    }

    @Test
    void shouldRejectOutdatedStrategyVersionBeforeLoadingBars() {
        RollingBacktestReq missingVersionRequest = request();
        missingVersionRequest.setStrategyId("S1");
        missingVersionRequest.setStrategyConfig(RollingStrategyConfig.builder()
                .strategyId("S1")
                .s1FastMa(5)
                .s1SlowMa(20)
                .s1VolumeMa(5)
                .build());
        RollingBacktestReq req = request();
        req.setStrategyId("S1");
        req.setStrategyConfig(RollingStrategyConfig.builder()
                .strategyId("S1")
                .logicVersion("S1_V0")
                .s1FastMa(5)
                .s1SlowMa(20)
                .s1VolumeMa(5)
                .build());

        BusinessException missingVersionException = assertThrows(
                BusinessException.class, () -> service.rollingEvaluate(missingVersionRequest));
        BusinessException outdatedVersionException = assertThrows(
                BusinessException.class, () -> service.rollingEvaluate(req));

        assertEquals("策略参数快照缺少逻辑版本，无法精确复跑", missingVersionException.getMessage());
        assertEquals("策略逻辑版本已更新，无法按原版本复跑", outdatedVersionException.getMessage());
        verify(barDailyMapper, never()).selectCount(any());
        verify(barDailyService, never()).syncBars(any());
    }

    @Test
    void shouldRejectIncompleteExactReplayBeforeLoadingBars() {
        RollingBacktestReq req = request();
        req.setExactReplay(true);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.rollingEvaluate(req));

        assertEquals("精确复跑缺少完整实验快照", exception.getMessage());
        verify(barDailyMapper, never()).selectCount(any());
        verify(barDailyService, never()).syncBars(any());
    }

    @Test
    void shouldRejectOutdatedExecutionModelBeforeLoadingBars() {
        RollingBacktestReq req = exactReplayRequest();
        req.setExpectedExecutionModelVersion("NEXT_OPEN_V3");

        BusinessException exception = assertThrows(BusinessException.class, () -> service.rollingEvaluate(req));

        assertEquals("回测执行模型版本已更新，无法按原版本复跑", exception.getMessage());
        verify(barDailyMapper, never()).selectCount(any());
        verify(barDailyService, never()).syncBars(any());
    }

    @Test
    void shouldRejectChangedDataFingerprintBeforeSavingExactReplay() {
        List<BarDaily> stockBars = buildBars("600519", 500, 100, 0.08);
        List<BarDaily> benchmarkBars = buildBars("000300", 500, 100, 0.03);
        when(barDailyMapper.selectCount(any())).thenReturn(500L);
        when(barDailyMapper.selectList(any())).thenReturn(stockBars, benchmarkBars);
        RollingBacktestReq req = exactReplayRequest();

        BusinessException exception = assertThrows(BusinessException.class, () -> service.rollingEvaluate(req));

        assertEquals("行情数据已变化，无法精确复跑", exception.getMessage());
        verify(backtestExperimentService, never()).save(any(), any());
    }

    @Test
    void shouldSyncWhenExistingBarsCannotProduceTwoFolds() {
        List<BarDaily> stockBars = buildBars("600519", 500, 100, 0.08);
        List<BarDaily> benchmarkBars = buildBars("000300", 500, 100, 0.03);
        when(barDailyMapper.selectCount(any())).thenReturn(260L);
        when(barDailyMapper.selectList(any())).thenReturn(stockBars, benchmarkBars);

        service.rollingEvaluate(request());

        verify(barDailyService, times(2)).syncBars(any());
    }

    @Test
    void shouldSyncWhenCountIsEnoughButRequestedEndIsStale() {
        List<BarDaily> stockBars = buildBars("600519", 500, 100, 0.08);
        List<BarDaily> benchmarkBars = buildBars("000300", 500, 100, 0.03);
        when(barDailyMapper.selectCount(any())).thenReturn(500L, 500L, 0L);
        when(barDailyMapper.selectOne(any())).thenReturn(
                boundaryBar("600519", LocalDate.of(2023, 1, 2)),
                boundaryBar("600519", LocalDate.of(2025, 6, 30)),
                boundaryBar("000300", LocalDate.of(2023, 1, 2)),
                boundaryBar("000300", LocalDate.of(2025, 12, 31)));
        when(barDailyMapper.selectList(any())).thenReturn(benchmarkBars, stockBars, benchmarkBars);

        service.rollingEvaluate(request());

        verify(barDailyService).syncBars(any());
    }

    @Test
    void shouldRejectTruncatedBarsInsteadOfSavingShortenedExperiment() {
        List<BarDaily> truncatedStockBars = new ArrayList<>(
                buildBars("600519", 500, 100, 0.08).subList(200, 500));
        List<BarDaily> truncatedBenchmarkBars = new ArrayList<>(
                buildBars("000300", 500, 100, 0.03).subList(200, 500));
        when(barDailyMapper.selectCount(any())).thenReturn(0L);
        when(barDailyMapper.selectList(any())).thenReturn(truncatedStockBars, truncatedBenchmarkBars);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.rollingEvaluate(request()));

        assertEquals("日线未完整覆盖请求区间: 600519", exception.getMessage());
        verify(backtestExperimentService, never()).save(any(), any());
    }

    @Test
    void shouldRejectBarsWithoutVerifiedQfqSource() {
        List<BarDaily> stockBars = buildBars("600519", 500, 100, 0.08);
        List<BarDaily> benchmarkBars = buildBars("000300", 500, 100, 0.03);
        for (BarDaily stockBar : stockBars) {
            stockBar.setSource(DailyBarClient.SOURCE_SINA);
        }
        when(barDailyMapper.selectCount(any())).thenReturn(500L);
        when(barDailyMapper.selectList(any())).thenReturn(stockBars, benchmarkBars);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.rollingEvaluate(request()));

        assertEquals("日线复权口径不一致: 600519", exception.getMessage());
        verify(backtestExperimentService, never()).save(any(), any());
    }

    @Test
    void shouldRejectIncompleteOhlcBeforeSavingExperiment() {
        List<BarDaily> stockBars = buildBars("600519", 500, 100, 0.08);
        List<BarDaily> benchmarkBars = buildBars("000300", 500, 100, 0.03);
        stockBars.get(260).setClosePrice(null);
        when(barDailyMapper.selectCount(any())).thenReturn(500L);
        when(barDailyMapper.selectList(any())).thenReturn(stockBars, benchmarkBars);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.rollingEvaluate(request()));

        assertEquals("日线价格数据无效: 600519, " + stockBars.get(260).getTradeDate(), exception.getMessage());
        verify(backtestExperimentService, never()).save(any(), any());
    }

    @Test
    void walkForwardKeepsTrainingHistoryForOutSampleEvaluation() {
        BacktestEngine engine = mock(BacktestEngine.class);
        ReflectionTestUtils.setField(service, "backtestEngine", engine);
        List<BarDaily> bars = buildBars("600519", 200, 100, 0.08);
        when(barDailyMapper.selectCount(any())).thenReturn(0L);
        when(barDailyMapper.selectList(any())).thenReturn(bars);
        BacktestResult result = BacktestResult.builder()
                .totalReturn(BigDecimal.ZERO)
                .maxDrawdown(BigDecimal.ZERO)
                .sharpe(BigDecimal.ZERO)
                .build();
        when(engine.run(any(), any(), any(), any())).thenReturn(result);
        when(engine.run(any(), any(), any(), any(), anyInt())).thenReturn(result);
        BacktestRunReq req = new BacktestRunReq();
        req.setCode("600519");
        req.setStrategyId("MA_5_20");
        req.setBeginDate("2023-01-02");
        req.setEndDate("2025-12-31");
        req.setInitCash(new BigDecimal("1000000"));

        service.walkForward(req, new BigDecimal("0.7"));

        ArgumentCaptor<BarSeries> seriesCaptor = ArgumentCaptor.forClass(BarSeries.class);
        ArgumentCaptor<BacktestCostConfig> costCaptor = ArgumentCaptor.forClass(BacktestCostConfig.class);
        verify(engine).run(any(), any(), any(), costCaptor.capture());
        verify(engine).run(any(), seriesCaptor.capture(), any(), costCaptor.capture(), eq(140));
        assertEquals(200, seriesCaptor.getValue().size());
        assertSame(costCaptor.getAllValues().get(0), costCaptor.getAllValues().get(1));
        verify(configService).getDecimal(eq("commission_rate"), any());
        verify(configService).getDecimal(eq("stamp_tax_rate"), any());
        verify(configService).getDecimal(eq("buy_slippage"), any());
        verify(configService).getDecimal(eq("sell_slippage"), any());
    }

    @Test
    void walkForwardRejectsInvalidRatioBeforeReadingCosts() {
        BacktestRunReq req = new BacktestRunReq();
        req.setCode("600519");
        req.setStrategyId("MA_5_20");

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.walkForward(req, new BigDecimal("0.95")));

        assertEquals("样本内比例必须在0.5到0.9之间", exception.getMessage());
        verify(configService, never()).getDecimal(anyString(), any());
        verify(barDailyMapper, never()).selectCount(any());
        verify(barDailyMapper, never()).selectList(any());
    }

    @Test
    void walkForwardRejectsBarsWithoutRequestedBoundaryCoverage() {
        when(barDailyMapper.selectCount(any())).thenReturn(0L);
        when(barDailyMapper.selectList(any())).thenReturn(buildBars("600519", 200, 100, 0.08));
        BacktestRunReq req = new BacktestRunReq();
        req.setCode("600519");
        req.setStrategyId("MA_5_20");
        req.setBeginDate("2022-01-02");
        req.setEndDate("2025-12-31");

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.walkForward(req, new BigDecimal("0.7")));

        assertEquals("日线未完整覆盖请求区间: 600519", exception.getMessage());
    }

    @Test
    void parameterSweepFreezesCostSnapshotAcrossCandidates() {
        BacktestEngine engine = mock(BacktestEngine.class);
        ReflectionTestUtils.setField(service, "backtestEngine", engine);
        when(barDailyMapper.selectCount(any())).thenReturn(0L);
        when(barDailyMapper.selectList(any())).thenReturn(buildBars("600519", 200, 100, 0.08));
        BacktestResult result = BacktestResult.builder()
                .totalReturn(BigDecimal.ZERO)
                .maxDrawdown(BigDecimal.ZERO)
                .sharpe(BigDecimal.ZERO)
                .build();
        when(engine.run(any(), any(), any(), any())).thenReturn(result);
        ParamSweepReq request = new ParamSweepReq();
        request.setCode("600519");
        request.setBeginDate("2023-01-02");
        request.setEndDate("2025-12-31");
        request.setInitCash(new BigDecimal("1000000"));
        request.setFastPeriods("5,10");
        request.setSlowPeriods("20");

        service.paramSweep(request);

        ArgumentCaptor<BacktestCostConfig> costCaptor = ArgumentCaptor.forClass(BacktestCostConfig.class);
        verify(engine, times(2)).run(any(), any(), any(), costCaptor.capture());
        assertSame(costCaptor.getAllValues().get(0), costCaptor.getAllValues().get(1));
        verify(configService).getDecimal(eq("commission_rate"), any());
        verify(configService).getDecimal(eq("stamp_tax_rate"), any());
        verify(configService).getDecimal(eq("buy_slippage"), any());
        verify(configService).getDecimal(eq("sell_slippage"), any());
    }

    @Test
    void parameterSweepRejectsInvalidPeriodBeforeReadingCosts() {
        ParamSweepReq request = new ParamSweepReq();
        request.setCode("600519");
        request.setFastPeriods("5,abc");
        request.setSlowPeriods("20");

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.paramSweep(request));

        assertEquals("均线周期必须是2到250之间的整数", exception.getMessage());
        verify(configService, never()).getDecimal(anyString(), any());
        verify(barDailyMapper, never()).selectCount(any());
        verify(barDailyMapper, never()).selectList(any());
    }

    @Test
    void parameterSweepRejectsEmptyCandidateSetBeforeReadingCosts() {
        ParamSweepReq request = new ParamSweepReq();
        request.setCode("600519");
        request.setFastPeriods("60,120");
        request.setSlowPeriods("20");

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.paramSweep(request));

        assertEquals("没有可用的均线参数组合，快线周期必须小于慢线周期", exception.getMessage());
        verify(configService, never()).getDecimal(anyString(), any());
        verify(barDailyMapper, never()).selectCount(any());
        verify(barDailyMapper, never()).selectList(any());
    }

    @Test
    void parameterSweepRejectsTooManyCandidatesBeforeReadingCosts() {
        ParamSweepReq request = new ParamSweepReq();
        request.setCode("600519");
        request.setFastPeriods("2,3,4,5,6,7,8,9,10,11");
        request.setSlowPeriods("100,101,102,103,104,105,106,107,108,109,110");

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.paramSweep(request));

        assertEquals("均线参数扫描最多支持100组候选", exception.getMessage());
        verify(configService, never()).getDecimal(anyString(), any());
        verify(barDailyMapper, never()).selectCount(any());
        verify(barDailyMapper, never()).selectList(any());
    }

    @Test
    void parameterSweepRejectsBarsWithoutRequestedBoundaryCoverage() {
        when(barDailyMapper.selectCount(any())).thenReturn(0L);
        when(barDailyMapper.selectList(any())).thenReturn(buildBars("600519", 200, 100, 0.08));
        ParamSweepReq request = new ParamSweepReq();
        request.setCode("600519");
        request.setBeginDate("2022-01-02");
        request.setEndDate("2025-12-31");
        request.setFastPeriods("5");
        request.setSlowPeriods("20");

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.paramSweep(request));

        assertEquals("日线未完整覆盖请求区间: 600519", exception.getMessage());
    }

    @Test
    void parameterSweepPropagatesSystemFailure() {
        BacktestEngine engine = mock(BacktestEngine.class);
        ReflectionTestUtils.setField(service, "backtestEngine", engine);
        when(barDailyMapper.selectCount(any())).thenReturn(0L);
        when(barDailyMapper.selectList(any())).thenReturn(buildBars("600519", 200, 100, 0.08));
        when(engine.run(any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("engine failure"));
        ParamSweepReq request = new ParamSweepReq();
        request.setCode("600519");
        request.setBeginDate("2023-01-02");
        request.setEndDate("2025-12-31");
        request.setFastPeriods("5");
        request.setSlowPeriods("20");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class, () -> service.paramSweep(request));

        assertEquals("engine failure", exception.getMessage());
    }

    @Test
    void parameterSweepKeepsBusinessFailureOnItsOwnCandidate() {
        BacktestEngine engine = mock(BacktestEngine.class);
        ReflectionTestUtils.setField(service, "backtestEngine", engine);
        when(barDailyMapper.selectCount(any())).thenReturn(0L);
        when(barDailyMapper.selectList(any())).thenReturn(buildBars("600519", 200, 100, 0.08));
        BacktestResult result = BacktestResult.builder()
                .totalReturn(BigDecimal.ZERO)
                .maxDrawdown(BigDecimal.ZERO)
                .sharpe(BigDecimal.ZERO)
                .build();
        when(engine.run(any(), any(), any(), any()))
                .thenThrow(new BusinessException("候选参数不可执行"))
                .thenReturn(result);
        ParamSweepReq request = new ParamSweepReq();
        request.setCode("600519");
        request.setBeginDate("2023-01-02");
        request.setEndDate("2025-12-31");
        request.setFastPeriods("5,10");
        request.setSlowPeriods("20");

        List<ParamSweepItemResp> results = service.paramSweep(request);

        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(item -> "候选参数不可执行".equals(item.getError())));
        assertTrue(results.stream().anyMatch(item -> Objects.isNull(item.getError())));
    }

    private RollingBacktestReq request() {
        RollingBacktestReq req = new RollingBacktestReq();
        req.setCode("600519");
        req.setStrategyId("MA_5_20");
        req.setBeginDate("2023-01-02");
        req.setEndDate("2025-12-31");
        req.setInitCash(new BigDecimal("1000000"));
        req.setBenchmarkCode("000300");
        req.setWindowMode("ROLLING");
        req.setTrainDays(200);
        req.setTestDays(50);
        req.setStepDays(50);
        return req;
    }

    private RollingBacktestReq exactReplayRequest() {
        RollingBacktestReq req = request();
        req.setStrategyConfig(RollingStrategyConfig.builder()
                .strategyId("MA_5_20")
                .logicVersion(ParamMaStrategy.LOGIC_VERSION)
                .build());
        req.setExactReplay(true);
        req.setCommissionRate(new BigDecimal("0.00050000"));
        req.setStampTaxRate(new BigDecimal("0.00050000"));
        req.setBuySlippage(new BigDecimal("0.00100000"));
        req.setSellSlippage(new BigDecimal("0.00100000"));
        req.setExpectedExecutionModelVersion(BacktestEngine.EXECUTION_MODEL_VERSION);
        req.setExpectedPriceAdjustment(BacktestEngine.PRICE_ADJUSTMENT);
        req.setExpectedDataFingerprint("0".repeat(64));
        return req;
    }

    private List<BarDaily> buildBars(String code, int count, double basePrice, double trend) {
        List<BarDaily> bars = new ArrayList<>();
        LocalDate beginDate = LocalDate.of(2023, 1, 2);
        long dateSpan = ChronoUnit.DAYS.between(beginDate, LocalDate.of(2025, 12, 31));
        for (int index = 0; index < count; index++) {
            BigDecimal closePrice = BigDecimal.valueOf(
                            basePrice + index * trend + Math.sin(index * Math.PI / 15) * 8)
                    .setScale(4, RoundingMode.HALF_UP);
            bars.add(BarDaily.builder()
                    .code(code)
                    .tradeDate(beginDate.plusDays(dateSpan * index / Math.max(count - 1, 1)))
                    .openPrice(closePrice)
                    .highPrice(closePrice.add(BigDecimal.ONE))
                    .lowPrice(closePrice.subtract(BigDecimal.ONE))
                    .closePrice(closePrice)
                    .volume(BigDecimal.valueOf(1000000L + index * 1000L))
                    .source(DailyBarClient.SOURCE_EASTMONEY)
                    .build());
        }
        return bars;
    }

    private BarDaily boundaryBar(String code, LocalDate tradeDate) {
        return BarDaily.builder()
                .code(code)
                .tradeDate(tradeDate)
                .openPrice(BigDecimal.TEN)
                .highPrice(BigDecimal.TEN)
                .lowPrice(BigDecimal.TEN)
                .closePrice(BigDecimal.TEN)
                .build();
    }
}
