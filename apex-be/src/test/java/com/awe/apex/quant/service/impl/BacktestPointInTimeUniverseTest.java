package com.awe.apex.quant.service.impl;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.backtest.BacktestCostConfig;
import com.awe.apex.quant.backtest.BacktestRunContext;
import com.awe.apex.quant.domain.dto.BacktestRunReq;
import com.awe.apex.quant.domain.dto.BatchBacktestItemResp;
import com.awe.apex.quant.domain.dto.BatchBacktestReq;
import com.awe.apex.quant.domain.dto.PortfolioBacktestReq;
import com.awe.apex.quant.domain.dto.StrategyCompareItemResp;
import com.awe.apex.quant.domain.entity.BacktestJob;
import com.awe.apex.quant.service.IConfigService;
import com.awe.apex.quant.service.IUniverseService;
import com.awe.apex.quant.strategy.Strategy;
import com.awe.apex.quant.strategy.StrategyParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BacktestPointInTimeUniverseTest {

    private final IUniverseService universeService = mock(IUniverseService.class);
    private final IConfigService configService = mock(IConfigService.class);
    private final Strategy strategyS1 = mock(Strategy.class);
    private final Strategy strategyS2 = mock(Strategy.class);
    private final Strategy strategyS3 = mock(Strategy.class);
    private final StrategyParams strategyParams = mock(StrategyParams.class);
    private final BacktestServiceImpl service = spy(new BacktestServiceImpl());

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "universeService", universeService);
        ReflectionTestUtils.setField(service, "configService", configService);
        ReflectionTestUtils.setField(service, "strategies", List.of(strategyS1, strategyS2, strategyS3));
        ReflectionTestUtils.setField(service, "strategyParams", strategyParams);
        when(strategyS1.strategyId()).thenReturn("S1");
        when(strategyS2.strategyId()).thenReturn("S2");
        when(strategyS3.strategyId()).thenReturn("S3");
        when(strategyParams.s1FastMa()).thenReturn(20);
        when(strategyParams.s1SlowMa()).thenReturn(60);
        when(strategyParams.s1VolMa()).thenReturn(20);
        when(strategyParams.s2Ma()).thenReturn(60);
        when(strategyParams.s2RsiPeriod()).thenReturn(14);
        when(strategyParams.s2RsiOversold()).thenReturn(new BigDecimal("30"));
        when(strategyParams.s2RsiRebound()).thenReturn(new BigDecimal("35"));
        when(strategyParams.s2RsiOverbought()).thenReturn(new BigDecimal("70"));
        when(strategyParams.s3Lookback()).thenReturn(20);
        when(strategyParams.s3VolumeRatio()).thenReturn(new BigDecimal("1.5"));
        when(configService.getDecimal(anyString(), any())).thenAnswer(invocation -> invocation.getArgument(1));
    }

    @Test
    void shouldRejectBatchBacktestWhenHistoricalUniverseIsMissing() {
        LocalDate beginDate = LocalDate.of(2024, 1, 2);
        LocalDate endDate = LocalDate.of(2024, 12, 31);
        when(universeService.latestAsOf(beginDate)).thenReturn(List.of());
        BatchBacktestReq request = new BatchBacktestReq();
        request.setBeginDate("2024-01-02");
        request.setEndDate("2024-12-31");

        BusinessException exception = assertThrows(BusinessException.class, () -> service.batchRun(request));

        assertEquals("回测开始日没有可用股票池快照，请传入 codes 或先按该日期刷新股票池", exception.getMessage());
        verify(universeService).latestAsOf(beginDate);
        verify(universeService, never()).latest();
    }

    @Test
    void shouldRejectPortfolioBacktestWhenHistoricalUniverseIsMissing() {
        LocalDate beginDate = LocalDate.of(2024, 1, 2);
        LocalDate endDate = LocalDate.of(2024, 12, 31);
        when(universeService.latestAsOf(beginDate)).thenReturn(List.of());
        PortfolioBacktestReq request = new PortfolioBacktestReq();
        request.setStrategyId("S1");
        request.setBeginDate("2024-01-02");
        request.setEndDate("2024-12-31");

        BusinessException exception = assertThrows(BusinessException.class, () -> service.portfolioRun(request));

        assertEquals("回测开始日没有可用股票池快照，请传入 codes 或先按该日期刷新股票池", exception.getMessage());
        verify(universeService).latestAsOf(beginDate);
        verify(universeService, never()).latest();
    }

    @Test
    void shouldApplyBatchLimitAfterNormalizingDuplicateCodes() {
        doReturn(BacktestJob.builder().id(1L).build()).when(service)
                .runWithCost(any(BacktestRunReq.class), any(BacktestCostConfig.class));
        BatchBacktestReq request = new BatchBacktestReq();
        request.setCodes(List.of("600519", "SH.600519", "000001"));
        request.setStrategyId("S1");
        request.setLimit(2);

        List<BatchBacktestItemResp> results = service.batchRun(request);

        ArgumentCaptor<BacktestRunReq> runRequestCaptor = ArgumentCaptor.forClass(BacktestRunReq.class);
        ArgumentCaptor<BacktestCostConfig> costCaptor = ArgumentCaptor.forClass(BacktestCostConfig.class);
        verify(service, times(2)).runWithCost(runRequestCaptor.capture(), costCaptor.capture());
        assertEquals(2, results.size());
        assertEquals("600519", runRequestCaptor.getAllValues().get(0).getCode());
        assertEquals("000001", runRequestCaptor.getAllValues().get(1).getCode());
        assertSame(costCaptor.getAllValues().get(0), costCaptor.getAllValues().get(1));
        verify(configService).getDecimal(eq("commission_rate"), any());
        verify(configService).getDecimal(eq("stamp_tax_rate"), any());
        verify(configService).getDecimal(eq("buy_slippage"), any());
        verify(configService).getDecimal(eq("sell_slippage"), any());
    }

    @Test
    void shouldFreezeCostsAcrossStrategyComparison() {
        doReturn(BacktestJob.builder().id(1L).build()).when(service)
                .runComparedWithCost(any(BacktestRunReq.class), any(BacktestCostConfig.class),
                        any(BacktestRunContext.class));
        BacktestRunReq request = new BacktestRunReq();
        request.setCode("SH.600519");

        service.compareStrategies(request);

        ArgumentCaptor<BacktestRunReq> runRequestCaptor = ArgumentCaptor.forClass(BacktestRunReq.class);
        ArgumentCaptor<BacktestCostConfig> costCaptor = ArgumentCaptor.forClass(BacktestCostConfig.class);
        ArgumentCaptor<BacktestRunContext> contextCaptor = ArgumentCaptor.forClass(BacktestRunContext.class);
        verify(service, times(3)).runComparedWithCost(runRequestCaptor.capture(), costCaptor.capture(),
                contextCaptor.capture());
        assertEquals(List.of("600519", "600519", "600519"),
                List.of(runRequestCaptor.getAllValues().get(0).getCode(),
                        runRequestCaptor.getAllValues().get(1).getCode(),
                        runRequestCaptor.getAllValues().get(2).getCode()));
        assertSame(costCaptor.getAllValues().get(0), costCaptor.getAllValues().get(1));
        assertSame(costCaptor.getAllValues().get(0), costCaptor.getAllValues().get(2));
        List<BacktestRunContext> contexts = contextCaptor.getAllValues();
        assertFalse(contexts.get(0).getComparisonBatchId().isBlank());
        assertEquals(contexts.get(0).getComparisonBatchId(), contexts.get(1).getComparisonBatchId());
        assertEquals(contexts.get(0).getComparisonBatchId(), contexts.get(2).getComparisonBatchId());
        assertEquals(List.of("S1,S2,S3", "S1,S2,S3", "S1,S2,S3"),
                contexts.stream().map(BacktestRunContext::getComparisonStrategyIds).toList());
        assertEquals(List.of(
                        "version=S1_V1, fastMa=20, slowMa=60, volumeMa=20",
                        "version=S2_V1, ma=60, rsiPeriod=14, oversold=30, rebound=35, overbought=70",
                        "version=S3_V1, lookback=20, volumeRatio=1.5"),
                contexts.stream().map(BacktestRunContext::getStrategyParameters).toList());
        assertEquals(64, contexts.get(0).getComparisonConfigFingerprint().length());
        assertEquals(contexts.get(0).getComparisonConfigFingerprint(),
                contexts.get(1).getComparisonConfigFingerprint());
        assertEquals(contexts.get(0).getComparisonConfigFingerprint(),
                contexts.get(2).getComparisonConfigFingerprint());
        verify(configService).getDecimal(eq("commission_rate"), any());
        verify(configService).getDecimal(eq("stamp_tax_rate"), any());
        verify(configService).getDecimal(eq("buy_slippage"), any());
        verify(configService).getDecimal(eq("sell_slippage"), any());
    }

    @Test
    void shouldRejectInvalidStrategyComparisonCodeBeforeReadingCosts() {
        BacktestRunReq request = new BacktestRunReq();
        request.setCode("123");

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.compareStrategies(request));

        assertEquals("证券代码格式无效", exception.getMessage());
        verify(service, never()).runComparedWithCost(any(BacktestRunReq.class), any(BacktestCostConfig.class),
                any(BacktestRunContext.class));
        verify(configService, never()).getDecimal(anyString(), any());
    }

    @Test
    void shouldRejectBatchWithoutAnyValidCodeBeforeReadingCosts() {
        BatchBacktestReq request = new BatchBacktestReq();
        request.setCodes(List.of(" ", "--", "123"));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.batchRun(request));

        assertEquals("批量回测至少需要 1 只有效股票", exception.getMessage());
        verify(service, never()).runWithCost(any(BacktestRunReq.class), any(BacktestCostConfig.class));
        verify(configService, never()).getDecimal(anyString(), any());
    }

    @Test
    void shouldRejectUnknownBatchStrategyBeforeRunningItems() {
        BatchBacktestReq request = new BatchBacktestReq();
        request.setCodes(List.of("600519", "000001"));
        request.setStrategyId("UNKNOWN");

        BusinessException exception = assertThrows(BusinessException.class, () -> service.batchRun(request));

        assertEquals("未知策略: UNKNOWN", exception.getMessage());
        verify(service, never()).runWithCost(any(BacktestRunReq.class), any(BacktestCostConfig.class));
        verify(configService, never()).getDecimal(anyString(), any());
    }

    @Test
    void shouldRejectBatchDateRangeBeforeReadingHistoricalUniverse() {
        BatchBacktestReq request = new BatchBacktestReq();
        request.setBeginDate("2026-08-01");
        request.setEndDate("2025-01-01");

        BusinessException exception = assertThrows(BusinessException.class, () -> service.batchRun(request));

        assertEquals("开始日期不能晚于结束日期", exception.getMessage());
        verify(universeService, never()).latestAsOf(any());
        verify(service, never()).runComparedWithCost(any(BacktestRunReq.class), any(BacktestCostConfig.class),
                any(BacktestRunContext.class));
    }

    @Test
    void shouldRejectStrategyComparisonDateRangeAsTaskError() {
        BacktestRunReq request = new BacktestRunReq();
        request.setCode("600519");
        request.setBeginDate("2026-08-01");
        request.setEndDate("2025-01-01");

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.compareStrategies(request));

        assertEquals("开始日期不能晚于结束日期", exception.getMessage());
        verify(service, never()).runWithCost(any(BacktestRunReq.class), any(BacktestCostConfig.class));
        verify(configService, never()).getDecimal(anyString(), any());
    }

    @Test
    void shouldRejectStrategyComparisonCashAsTaskError() {
        BacktestRunReq request = new BacktestRunReq();
        request.setCode("600519");
        request.setInitCash(BigDecimal.ZERO);

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.compareStrategies(request));

        assertEquals("初始资金必须大于0", exception.getMessage());
        verify(service, never()).runComparedWithCost(any(BacktestRunReq.class), any(BacktestCostConfig.class),
                any(BacktestRunContext.class));
        verify(configService, never()).getDecimal(anyString(), any());
    }

    @Test
    void shouldPropagateBatchSystemFailureInsteadOfReturningPartialSuccess() {
        doThrow(new IllegalStateException("database unavailable")).when(service)
                .runWithCost(any(BacktestRunReq.class), any(BacktestCostConfig.class));
        BatchBacktestReq request = new BatchBacktestReq();
        request.setCodes(List.of("600519", "000001"));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> service.batchRun(request));

        assertEquals("database unavailable", exception.getMessage());
    }

    @Test
    void shouldKeepBatchBusinessFailureOnItsOwnItem() {
        doAnswer(invocation -> {
            BacktestRunReq runRequest = invocation.getArgument(0);
            if ("600519".equals(runRequest.getCode())) {
                throw new BusinessException("日线不足 60 根");
            }
            return BacktestJob.builder().id(2L).totalReturn(new BigDecimal("0.10")).build();
        }).when(service).runWithCost(any(BacktestRunReq.class), any(BacktestCostConfig.class));
        BatchBacktestReq request = new BatchBacktestReq();
        request.setCodes(List.of("600519", "000001"));

        List<BatchBacktestItemResp> results = service.batchRun(request);

        assertEquals(2, results.size());
        assertEquals("000001", results.get(0).getCode());
        assertEquals("600519", results.get(1).getCode());
        assertEquals("日线不足 60 根", results.get(1).getError());
    }

    @Test
    void shouldPropagateStrategyComparisonSystemFailure() {
        doThrow(new IllegalStateException("database unavailable")).when(service)
                .runComparedWithCost(any(BacktestRunReq.class), any(BacktestCostConfig.class),
                        any(BacktestRunContext.class));
        BacktestRunReq request = new BacktestRunReq();
        request.setCode("600519");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class, () -> service.compareStrategies(request));

        assertEquals("database unavailable", exception.getMessage());
    }

    @Test
    void shouldKeepStrategyBusinessFailureOnItsOwnItem() {
        doAnswer(invocation -> {
            BacktestRunReq runRequest = invocation.getArgument(0);
            if ("S2".equals(runRequest.getStrategyId())) {
                throw new BusinessException("策略条件不足");
            }
            return BacktestJob.builder().id(2L).totalReturn(new BigDecimal("0.10")).build();
        }).when(service).runComparedWithCost(any(BacktestRunReq.class), any(BacktestCostConfig.class),
                any(BacktestRunContext.class));
        BacktestRunReq request = new BacktestRunReq();
        request.setCode("600519");

        List<StrategyCompareItemResp> results = service.compareStrategies(request);

        assertEquals(3, results.size());
        assertEquals("S2", results.get(1).getStrategyId());
        assertEquals("策略条件不足", results.get(1).getError());
    }
}
