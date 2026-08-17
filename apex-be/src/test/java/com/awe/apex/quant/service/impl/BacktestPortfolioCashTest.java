package com.awe.apex.quant.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.backtest.BacktestCostConfig;
import com.awe.apex.quant.backtest.BacktestEngine;
import com.awe.apex.quant.backtest.BacktestEquityPoint;
import com.awe.apex.quant.backtest.BacktestResult;
import com.awe.apex.quant.domain.dto.PortfolioBacktestReq;
import com.awe.apex.quant.domain.dto.PortfolioBacktestResp;
import com.awe.apex.quant.domain.entity.BacktestEquity;
import com.awe.apex.quant.domain.entity.BacktestJob;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.mapper.BacktestEquityMapper;
import com.awe.apex.quant.mapper.BacktestJobMapper;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.market.DailyBarClient;
import com.awe.apex.quant.service.IConfigService;
import com.awe.apex.quant.strategy.Strategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BacktestPortfolioCashTest {

    private final BacktestJobMapper backtestJobMapper = mock(BacktestJobMapper.class);
    private final BacktestEquityMapper backtestEquityMapper = mock(BacktestEquityMapper.class);
    private final BarDailyMapper barDailyMapper = mock(BarDailyMapper.class);
    private final BacktestEngine backtestEngine = mock(BacktestEngine.class);
    private final IConfigService configService = mock(IConfigService.class);
    private final Strategy strategy = mock(Strategy.class);
    private final BacktestServiceImpl service = new BacktestServiceImpl();

    private MockedStatic<StpUtil> stpUtil;

    @BeforeEach
    void setUp() {
        stpUtil = mockStatic(StpUtil.class);
        stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
        ReflectionTestUtils.setField(service, "backtestJobMapper", backtestJobMapper);
        ReflectionTestUtils.setField(service, "backtestEquityMapper", backtestEquityMapper);
        ReflectionTestUtils.setField(service, "barDailyMapper", barDailyMapper);
        ReflectionTestUtils.setField(service, "backtestEngine", backtestEngine);
        ReflectionTestUtils.setField(service, "configService", configService);
        ReflectionTestUtils.setField(service, "strategies", List.of(strategy));
        when(strategy.strategyId()).thenReturn("S1");
        when(strategy.strategyName()).thenReturn("均线策略");
        when(strategy.logicVersion()).thenReturn("S1_V1");
        when(configService.getDecimal(anyString(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(backtestJobMapper.insert(any(BacktestJob.class))).thenAnswer(invocation -> {
            BacktestJob job = invocation.getArgument(0);
            job.setId(101L);
            return 1;
        });
    }

    @AfterEach
    void tearDown() {
        stpUtil.close();
    }

    @Test
    void rejectsPortfolioWhenFewerThanTwoLegsCanRun() {
        when(barDailyMapper.selectList(any())).thenReturn(buildBars(60), buildBars(20));
        when(backtestEngine.run(any(), any(), any(), any())).thenReturn(buildResult("100000", "100000"));
        PortfolioBacktestReq request = buildRequest(List.of("600519", "000001"), new BigDecimal("200000"));

        assertThrows(BusinessException.class, () -> service.portfolioRun(request));

        verify(backtestJobMapper, never()).insert(any(BacktestJob.class));
    }

    @Test
    void preservesFailedLegAllocationAsPortfolioCash() {
        when(barDailyMapper.selectList(any())).thenReturn(
                buildBars(60), buildBars(20), buildBars(60), buildBars(60));
        when(backtestEngine.run(any(), any(), any(), any()))
                .thenReturn(buildResult("100000", "110000"), buildResult("100000", "100000"));
        PortfolioBacktestReq request = buildRequest(
                List.of("600519", "000001", "000858"), new BigDecimal("300000"));

        PortfolioBacktestResp response = service.portfolioRun(request);

        assertEquals(new BigDecimal("310000.00"), response.getJob().getFinalCash());
        assertEquals(new BigDecimal("300000.00"), response.getEquities().get(0).getEquity());
        assertEquals(new BigDecimal("310000.00"),
                response.getEquities().get(response.getEquities().size() - 1).getEquity());
    }

    @Test
    void neverAllocatesMoreCashThanThePortfolioOwns() {
        when(barDailyMapper.selectList(any())).thenReturn(buildBars(60));
        when(backtestEngine.run(any(), any(), any(), any())).thenAnswer(invocation -> {
            BigDecimal legCash = invocation.getArgument(2);
            return buildResult(legCash.toPlainString(), legCash.toPlainString());
        });
        BigDecimal initCash = new BigDecimal("100.01");
        PortfolioBacktestReq request = buildRequest(
                List.of("600519", "000001", "000858", "600036", "601318", "600900"), initCash);

        PortfolioBacktestResp response = service.portfolioRun(request);

        ArgumentCaptor<BigDecimal> legCashCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(backtestEngine, times(6)).run(any(), any(), legCashCaptor.capture(), any());
        BigDecimal allocatedCash = BigDecimal.ZERO;
        for (BigDecimal legCash : legCashCaptor.getAllValues()) {
            allocatedCash = allocatedCash.add(legCash);
        }
        assertTrue(allocatedCash.compareTo(initCash) <= 0);
        assertEquals(new BigDecimal("100.01"), response.getEquities().get(0).getEquity());
        assertEquals(new BigDecimal("100.01"),
                response.getEquities().get(response.getEquities().size() - 1).getEquity());
    }

    @Test
    void rejectsNonPositivePortfolioInitialCash() {
        PortfolioBacktestReq request = buildRequest(List.of("600519", "000001"), BigDecimal.ZERO);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.portfolioRun(request));

        assertEquals("初始资金必须大于0", exception.getMessage());
        verify(barDailyMapper, never()).selectList(any());
    }

    @Test
    void rejectsDuplicatePortfolioCodesBeforeReadingMarketData() {
        PortfolioBacktestReq request = buildRequest(
                List.of("600519", "SH.600519"), new BigDecimal("100000"));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.portfolioRun(request));

        assertEquals("组合回测至少需要 2 只股票，请先刷新股票池或传入 codes", exception.getMessage());
        verify(barDailyMapper, never()).selectList(any());
        verify(backtestEngine, never()).run(any(), any(), any(), any());
    }

    @Test
    void freezesOneCostSnapshotForEveryPortfolioLeg() {
        when(barDailyMapper.selectList(any())).thenReturn(buildBars(60));
        when(backtestEngine.run(any(), any(), any(), any())).thenAnswer(invocation -> {
            BigDecimal legCash = invocation.getArgument(2);
            return buildResult(legCash.toPlainString(), legCash.toPlainString());
        });
        PortfolioBacktestReq request = buildRequest(
                List.of("600519", "000001"), new BigDecimal("200000"));

        service.portfolioRun(request);

        verify(configService).getDecimal(eq("commission_rate"), any());
        verify(configService).getDecimal(eq("stamp_tax_rate"), any());
        verify(configService).getDecimal(eq("buy_slippage"), any());
        verify(configService).getDecimal(eq("sell_slippage"), any());
        ArgumentCaptor<BacktestCostConfig> costCaptor = ArgumentCaptor.forClass(BacktestCostConfig.class);
        verify(backtestEngine, times(2)).run(any(), any(), any(), costCaptor.capture());
        assertSame(costCaptor.getAllValues().get(0), costCaptor.getAllValues().get(1));
        ArgumentCaptor<BacktestJob> jobCaptor = ArgumentCaptor.forClass(BacktestJob.class);
        verify(backtestJobMapper).insert(jobCaptor.capture());
        BacktestJob savedJob = jobCaptor.getValue();
        assertEquals("version=S1_V1, name=均线策略", savedJob.getStrategyParameters());
        assertEquals(new BigDecimal("0.00050000"), savedJob.getCommissionRate());
        assertEquals(new BigDecimal("0.00050000"), savedJob.getStampTaxRate());
        assertEquals(new BigDecimal("0.00100000"), savedJob.getBuySlippage());
        assertEquals(new BigDecimal("0.00100000"), savedJob.getSellSlippage());
        assertEquals(BacktestEngine.EXECUTION_MODEL_VERSION, savedJob.getExecutionModelVersion());
        assertEquals(BacktestEngine.PRICE_ADJUSTMENT, savedJob.getPriceAdjustment());
        assertNotNull(savedJob.getDataFingerprint());
        assertEquals(64, savedJob.getDataFingerprint().length());
    }

    @Test
    void rejectsInvalidPortfolioCostBeforeReadingMarketData() {
        when(configService.getDecimal(eq("commission_rate"), any()))
                .thenReturn(new BigDecimal("-0.001"));
        PortfolioBacktestReq request = buildRequest(
                List.of("600519", "000001"), new BigDecimal("200000"));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.portfolioRun(request));

        assertEquals("成本和滑点必须在0到5%之间", exception.getMessage());
        verify(barDailyMapper, never()).selectList(any());
        verify(backtestEngine, never()).run(any(), any(), any(), any());
    }

    @Test
    void propagatesPortfolioSystemFailure() {
        when(barDailyMapper.selectList(any())).thenThrow(new IllegalStateException("database unavailable"));
        PortfolioBacktestReq request = buildRequest(
                List.of("600519", "000001", "000858"), new BigDecimal("300000"));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class, () -> service.portfolioRun(request));

        assertEquals("database unavailable", exception.getMessage());
        verify(backtestJobMapper, never()).insert(any(BacktestJob.class));
    }

    @Test
    void keepsPortfolioBusinessFailureOnItsOwnLeg() {
        when(barDailyMapper.selectList(any())).thenReturn(buildBars(60));
        when(backtestEngine.run(any(), any(), any(), any()))
                .thenThrow(new BusinessException("标的停牌区间不可执行"))
                .thenReturn(buildResult("100000", "100000"), buildResult("100000", "100000"));
        PortfolioBacktestReq request = buildRequest(
                List.of("600519", "000001", "000858"), new BigDecimal("300000"));

        PortfolioBacktestResp response = service.portfolioRun(request);

        assertEquals(3, response.getLegs().size());
        assertEquals("标的停牌区间不可执行", response.getLegs().get(0).getError());
        assertEquals(2, response.getCodes().size());
    }

    @Test
    void rejectsPortfolioLegWithoutFullRequestedCoverage() {
        List<BarDaily> truncatedBars = buildBars(60);
        for (BarDaily bar : truncatedBars) {
            bar.setTradeDate(bar.getTradeDate().plusDays(30));
        }
        when(barDailyMapper.selectList(any())).thenReturn(truncatedBars, buildBars(60), buildBars(60));
        when(backtestEngine.run(any(), any(), any(), any()))
                .thenReturn(buildResult("100000", "100000"));
        PortfolioBacktestReq request = buildRequest(
                List.of("600519", "000001", "000858"), new BigDecimal("300000"));

        PortfolioBacktestResp response = service.portfolioRun(request);

        assertEquals("日线未完整覆盖请求区间: 600519", response.getLegs().get(0).getError());
        assertEquals(2, response.getCodes().size());
        verify(backtestEngine, times(2)).run(any(), any(), any(), any());
    }

    @Test
    void rejectsPortfolioLegWithoutVerifiedQfqSource() {
        List<BarDaily> unadjustedBars = buildBars(60);
        unadjustedBars.get(10).setSource(DailyBarClient.SOURCE_SINA);
        when(barDailyMapper.selectList(any())).thenReturn(unadjustedBars, buildBars(60), buildBars(60));
        when(backtestEngine.run(any(), any(), any(), any()))
                .thenReturn(buildResult("100000", "100000"));
        PortfolioBacktestReq request = buildRequest(
                List.of("600519", "000001", "000858"), new BigDecimal("300000"));

        PortfolioBacktestResp response = service.portfolioRun(request);

        assertEquals("日线复权口径不一致: 600519", response.getLegs().get(0).getError());
        assertEquals(2, response.getCodes().size());
        verify(backtestEngine, times(2)).run(any(), any(), any(), any());
    }

    @Test
    void rejectsPortfolioLegWithInvalidPriceData() {
        List<BarDaily> invalidBars = buildBars(60);
        invalidBars.get(10).setHighPrice(new BigDecimal("9"));
        when(barDailyMapper.selectList(any())).thenReturn(invalidBars, buildBars(60), buildBars(60));
        when(backtestEngine.run(any(), any(), any(), any()))
                .thenReturn(buildResult("100000", "100000"));
        PortfolioBacktestReq request = buildRequest(
                List.of("600519", "000001", "000858"), new BigDecimal("300000"));

        PortfolioBacktestResp response = service.portfolioRun(request);

        assertEquals("日线价格数据无效: 600519, 2026-01-31", response.getLegs().get(0).getError());
        assertEquals(2, response.getCodes().size());
        verify(backtestEngine, times(2)).run(any(), any(), any(), any());
    }

    @Test
    void rejectsIncompletePortfolioBenchmarkBeforeSavingJob() {
        when(barDailyMapper.selectList(any())).thenReturn(buildBars(60), buildBars(60), buildBars(20));
        when(backtestEngine.run(any(), any(), any(), any()))
                .thenReturn(buildResult("100000", "100000"));
        PortfolioBacktestReq request = buildRequest(
                List.of("600519", "000001"), new BigDecimal("200000"));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.portfolioRun(request));

        assertEquals("日线数量不足，无法覆盖研究窗口: 000300", exception.getMessage());
        verify(backtestJobMapper, never()).insert(any(BacktestJob.class));
        verify(backtestEquityMapper, never()).insert(any(BacktestEquity.class));
    }

    @Test
    void includesBenchmarkBarsInPortfolioDataFingerprint() {
        List<BarDaily> firstBenchmarkBars = buildBars(60);
        List<BarDaily> changedBenchmarkBars = buildBars(60);
        changedBenchmarkBars.get(10).setClosePrice(new BigDecimal("10.5"));
        when(barDailyMapper.selectList(any())).thenReturn(
                buildBars(60), buildBars(60), firstBenchmarkBars,
                buildBars(60), buildBars(60), changedBenchmarkBars);
        when(backtestEngine.run(any(), any(), any(), any()))
                .thenReturn(buildResult("100000", "100000"));
        PortfolioBacktestReq request = buildRequest(
                List.of("600519", "000001"), new BigDecimal("200000"));

        service.portfolioRun(request);
        service.portfolioRun(request);

        ArgumentCaptor<BacktestJob> jobCaptor = ArgumentCaptor.forClass(BacktestJob.class);
        verify(backtestJobMapper, times(2)).insert(jobCaptor.capture());
        assertNotEquals(jobCaptor.getAllValues().get(0).getDataFingerprint(),
                jobCaptor.getAllValues().get(1).getDataFingerprint());
    }

    private PortfolioBacktestReq buildRequest(List<String> codes, BigDecimal initCash) {
        PortfolioBacktestReq request = new PortfolioBacktestReq();
        request.setCodes(codes);
        request.setStrategyId("S1");
        request.setBeginDate("2026-01-01");
        request.setEndDate("2026-06-30");
        request.setInitCash(initCash);
        request.setLimit(codes.size());
        return request;
    }

    private BacktestResult buildResult(String initialEquity, String finalEquity) {
        return BacktestResult.builder()
                .finalCash(new BigDecimal(finalEquity))
                .totalReturn(BigDecimal.ZERO)
                .maxDrawdown(BigDecimal.ZERO)
                .sharpe(BigDecimal.ZERO)
                .sortino(BigDecimal.ZERO)
                .tradeCount(0)
                .equities(List.of(
                        BacktestEquityPoint.builder()
                                .tradeDate(LocalDate.of(2026, 1, 1))
                                .equity(new BigDecimal(initialEquity))
                                .build(),
                        BacktestEquityPoint.builder()
                                .tradeDate(LocalDate.of(2026, 3, 1))
                                .equity(new BigDecimal(finalEquity))
                                .build()))
                .build();
    }

    private List<BarDaily> buildBars(int count) {
        List<BarDaily> bars = new ArrayList<>();
        LocalDate beginDate = LocalDate.of(2026, 1, 1);
        LocalDate endDate = LocalDate.of(2026, 6, 30);
        long daySpan = endDate.toEpochDay() - beginDate.toEpochDay();
        for (int i = 0; i < count; i++) {
            long dayOffset = count > 1 ? daySpan * i / (count - 1) : 0;
            bars.add(BarDaily.builder()
                    .code("600519")
                    .tradeDate(beginDate.plusDays(dayOffset))
                    .openPrice(BigDecimal.TEN)
                    .highPrice(new BigDecimal("11"))
                    .lowPrice(new BigDecimal("9"))
                    .closePrice(BigDecimal.TEN)
                    .volume(new BigDecimal("1000000"))
                    .source(DailyBarClient.SOURCE_EASTMONEY)
                    .build());
        }
        return bars;
    }
}
