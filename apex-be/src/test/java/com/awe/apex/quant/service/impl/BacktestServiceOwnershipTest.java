package com.awe.apex.quant.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.backtest.BacktestEngine;
import com.awe.apex.quant.backtest.BacktestResult;
import com.awe.apex.quant.domain.dto.BacktestRunReq;
import com.awe.apex.quant.domain.dto.MonthlyReturnResp;
import com.awe.apex.quant.domain.entity.BacktestEquity;
import com.awe.apex.quant.domain.entity.BacktestJob;
import com.awe.apex.quant.domain.entity.BacktestTrade;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.mapper.BacktestEquityMapper;
import com.awe.apex.quant.mapper.BacktestJobMapper;
import com.awe.apex.quant.mapper.BacktestTradeMapper;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.market.DailyBarClient;
import com.awe.apex.quant.service.IConfigService;
import com.awe.apex.quant.strategy.Strategy;
import com.awe.apex.quant.strategy.StrategyParams;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BacktestServiceOwnershipTest {

    private static final Long CURRENT_USER_ID = 7L;

    private final BacktestJobMapper backtestJobMapper = mock(BacktestJobMapper.class);
    private final BacktestTradeMapper backtestTradeMapper = mock(BacktestTradeMapper.class);
    private final BacktestEquityMapper backtestEquityMapper = mock(BacktestEquityMapper.class);
    private final BarDailyMapper barDailyMapper = mock(BarDailyMapper.class);
    private final BacktestEngine backtestEngine = mock(BacktestEngine.class);
    private final IConfigService configService = mock(IConfigService.class);
    private final Strategy strategy = mock(Strategy.class);
    private final StrategyParams strategyParams = mock(StrategyParams.class);
    private final BacktestServiceImpl service = new BacktestServiceImpl();

    private MockedStatic<StpUtil> stpUtil;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, BacktestJob.class);
    }

    @BeforeEach
    void setUp() {
        stpUtil = mockStatic(StpUtil.class);
        stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(CURRENT_USER_ID);
        ReflectionTestUtils.setField(service, "backtestJobMapper", backtestJobMapper);
        ReflectionTestUtils.setField(service, "backtestTradeMapper", backtestTradeMapper);
        ReflectionTestUtils.setField(service, "backtestEquityMapper", backtestEquityMapper);
        ReflectionTestUtils.setField(service, "barDailyMapper", barDailyMapper);
        ReflectionTestUtils.setField(service, "backtestEngine", backtestEngine);
        ReflectionTestUtils.setField(service, "configService", configService);
        ReflectionTestUtils.setField(service, "strategies", List.of(strategy));
        ReflectionTestUtils.setField(service, "strategyParams", strategyParams);
        when(configService.getDecimal(anyString(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(strategyParams.s1FastMa()).thenReturn(20);
        when(strategyParams.s1SlowMa()).thenReturn(60);
        when(strategyParams.s1VolMa()).thenReturn(20);
    }

    @AfterEach
    void tearDown() {
        stpUtil.close();
    }

    @Test
    void shouldPersistCurrentUserOnNewJob() {
        when(strategy.strategyId()).thenReturn("S1");
        when(barDailyMapper.selectList(any())).thenReturn(buildBars());
        when(backtestEngine.run(any(), any(), any(), any())).thenReturn(BacktestResult.builder()
                .finalCash(new BigDecimal("1010000"))
                .totalReturn(new BigDecimal("0.01"))
                .annualReturn(new BigDecimal("0.02"))
                .maxDrawdown(new BigDecimal("0.03"))
                .sharpe(new BigDecimal("1.1"))
                .sortino(new BigDecimal("1.2"))
                .winRate(new BigDecimal("0.5"))
                .profitFactor(new BigDecimal("1.3"))
                .avgHoldDays(new BigDecimal("5"))
                .tradeCount(0)
                .trades(List.of())
                .equities(List.of())
                .disclaimer("仅供研究")
                .build());
        BacktestRunReq request = new BacktestRunReq();
        request.setCode("600519");
        request.setStrategyId("S1");

        service.run(request);

        ArgumentCaptor<BacktestJob> jobCaptor = ArgumentCaptor.forClass(BacktestJob.class);
        verify(backtestJobMapper).insert(jobCaptor.capture());
        BacktestJob savedJob = jobCaptor.getValue();
        assertEquals(CURRENT_USER_ID, savedJob.getUserId());
        assertNull(savedJob.getComparisonBatchId());
        assertNull(savedJob.getComparisonStrategyIds());
        assertEquals(new BigDecimal("0.00050000"), savedJob.getCommissionRate());
        assertEquals(new BigDecimal("0.00050000"), savedJob.getStampTaxRate());
        assertEquals(new BigDecimal("0.00100000"), savedJob.getBuySlippage());
        assertEquals(new BigDecimal("0.00100000"), savedJob.getSellSlippage());
        assertEquals("NEXT_OPEN_V4", savedJob.getExecutionModelVersion());
        assertEquals("QFQ", savedJob.getPriceAdjustment());
        assertEquals(64, savedJob.getDataFingerprint().length());
        assertEquals("version=S1_V1, fastMa=20, slowMa=60, volumeMa=20",
                savedJob.getStrategyParameters());
        assertNull(savedJob.getComparisonConfigFingerprint());
    }

    @Test
    void shouldRejectNonPositiveInitialCashBeforeLoadingBars() {
        when(strategy.strategyId()).thenReturn("S1");
        BacktestRunReq request = new BacktestRunReq();
        request.setCode("600519");
        request.setStrategyId("S1");
        request.setInitCash(BigDecimal.ZERO);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.run(request));

        assertEquals("初始资金必须大于0", exception.getMessage());
        verify(barDailyMapper, never()).selectList(any());
    }

    @Test
    void shouldRejectMissingRequestFieldsBeforeReadingCostsOrBars() {
        BacktestRunReq missingCodeRequest = new BacktestRunReq();
        missingCodeRequest.setStrategyId("S1");
        BacktestRunReq missingStrategyRequest = new BacktestRunReq();
        missingStrategyRequest.setCode("600519");

        BusinessException missingRequestException = assertThrows(BusinessException.class, () -> service.run(null));
        BusinessException missingCodeException = assertThrows(
                BusinessException.class, () -> service.run(missingCodeRequest));
        BusinessException missingStrategyException = assertThrows(
                BusinessException.class, () -> service.run(missingStrategyRequest));

        assertEquals("回测请求不能为空", missingRequestException.getMessage());
        assertEquals("code 不能为空", missingCodeException.getMessage());
        assertEquals("strategyId 不能为空", missingStrategyException.getMessage());
        verify(configService, never()).getDecimal(anyString(), any());
        verify(barDailyMapper, never()).selectList(any());
    }

    @Test
    void shouldRejectInvalidSecurityCodeBeforeLoadingBars() {
        BacktestRunReq request = new BacktestRunReq();
        request.setCode("123");
        request.setStrategyId("S1");

        BusinessException exception = assertThrows(BusinessException.class, () -> service.run(request));

        assertEquals("证券代码格式无效", exception.getMessage());
        verify(barDailyMapper, never()).selectList(any());
    }

    @Test
    void shouldRejectIncompatibleBarSourceBeforeExecution() {
        when(strategy.strategyId()).thenReturn("S1");
        List<BarDaily> bars = buildBars();
        bars.get(30).setSource("LEGACY");
        when(barDailyMapper.selectList(any())).thenReturn(bars);
        BacktestRunReq request = new BacktestRunReq();
        request.setCode("600519");
        request.setStrategyId("S1");

        BusinessException exception = assertThrows(BusinessException.class, () -> service.run(request));

        assertEquals("日线复权口径不一致: 600519", exception.getMessage());
        verify(backtestEngine, never()).run(any(), any(), any(), any());
    }

    @Test
    void shouldRejectInvalidDateRangesBeforeLoadingBars() {
        when(strategy.strategyId()).thenReturn("S1");
        BacktestRunReq reversedRequest = request("2026-08-01", "2025-01-01");
        BacktestRunReq futureRequest = request("2025-01-01", LocalDate.now().plusDays(1).toString());

        BusinessException reversedException = assertThrows(
                BusinessException.class, () -> service.run(reversedRequest));
        BusinessException futureException = assertThrows(
                BusinessException.class, () -> service.run(futureRequest));

        assertEquals("开始日期不能晚于结束日期", reversedException.getMessage());
        assertEquals("结束日期不能晚于今天", futureException.getMessage());
        verify(barDailyMapper, never()).selectList(any());
    }

    @Test
    void shouldReportInvalidDateFormatAsBusinessError() {
        when(strategy.strategyId()).thenReturn("S1");
        BacktestRunReq request = request("2026-02-30", "2026-08-01");

        BusinessException exception = assertThrows(BusinessException.class, () -> service.run(request));

        assertEquals("日期格式应为 yyyy-MM-dd 或 yyyyMMdd", exception.getMessage());
        verify(barDailyMapper, never()).selectList(any());
    }

    @Test
    void shouldCalculateTradeExpectancyAfterBothSideFees() {
        when(backtestJobMapper.selectOne(any())).thenReturn(BacktestJob.builder().id(8L).build());
        when(backtestTradeMapper.selectList(any())).thenReturn(List.of(
                BacktestTrade.builder()
                        .id(1L)
                        .tradeDate(LocalDate.of(2026, 1, 2))
                        .side("BUY")
                        .price(new BigDecimal("10"))
                        .quantity(100)
                        .fee(new BigDecimal("5"))
                        .build(),
                BacktestTrade.builder()
                        .id(2L)
                        .tradeDate(LocalDate.of(2026, 1, 3))
                        .side("SELL")
                        .price(new BigDecimal("11"))
                        .quantity(100)
                        .fee(new BigDecimal("6"))
                        .build()));

        BigDecimal expectancy = service.tradeExpectancy(8L);

        assertEquals(new BigDecimal("89.00"), expectancy);
    }

    @Test
    void shouldCalculateTradeExpectancyWithoutIntermediateRounding() {
        when(backtestJobMapper.selectOne(any())).thenReturn(BacktestJob.builder().id(9L).build());
        when(backtestTradeMapper.selectList(any())).thenReturn(List.of(
                BacktestTrade.builder()
                        .id(1L)
                        .tradeDate(LocalDate.of(2026, 1, 2))
                        .side("BUY")
                        .price(new BigDecimal("10"))
                        .quantity(100)
                        .fee(BigDecimal.ZERO)
                        .build(),
                BacktestTrade.builder()
                        .id(2L)
                        .tradeDate(LocalDate.of(2026, 1, 3))
                        .side("SELL")
                        .price(new BigDecimal("110"))
                        .quantity(100)
                        .fee(BigDecimal.ZERO)
                        .build(),
                BacktestTrade.builder()
                        .id(3L)
                        .tradeDate(LocalDate.of(2026, 1, 4))
                        .side("BUY")
                        .price(new BigDecimal("110"))
                        .quantity(100)
                        .fee(BigDecimal.ZERO)
                        .build(),
                BacktestTrade.builder()
                        .id(4L)
                        .tradeDate(LocalDate.of(2026, 1, 5))
                        .side("SELL")
                        .price(new BigDecimal("10"))
                        .quantity(100)
                        .fee(BigDecimal.ZERO)
                        .build(),
                BacktestTrade.builder()
                        .id(5L)
                        .tradeDate(LocalDate.of(2026, 1, 6))
                        .side("BUY")
                        .price(new BigDecimal("110"))
                        .quantity(100)
                        .fee(BigDecimal.ZERO)
                        .build(),
                BacktestTrade.builder()
                        .id(6L)
                        .tradeDate(LocalDate.of(2026, 1, 7))
                        .side("SELL")
                        .price(new BigDecimal("10"))
                        .quantity(100)
                        .fee(BigDecimal.ZERO)
                        .build()));

        BigDecimal expectancy = service.tradeExpectancy(9L);

        assertEquals(new BigDecimal("-3333.33"), expectancy);
    }

    @Test
    void shouldFilterJobListByCurrentUser() {
        when(backtestJobMapper.selectList(any())).thenReturn(List.of());

        service.listJobs(20);

        LambdaQueryWrapper<BacktestJob> query = captureJobListQuery();
        assertTrue(query.getSqlSegment().contains("user_id"));
        assertTrue(query.getParamNameValuePairs().containsValue(CURRENT_USER_ID));
    }

    @Test
    void shouldFilterLeaderboardByCurrentUser() {
        when(backtestJobMapper.selectList(any())).thenReturn(List.of());

        service.strategyLeaderboard(100);

        LambdaQueryWrapper<BacktestJob> query = captureJobListQuery();
        assertTrue(query.getSqlSegment().contains("user_id"));
        assertTrue(query.getParamNameValuePairs().containsValue(CURRENT_USER_ID));
    }

    @Test
    void shouldCalculateFirstMonthlyReturnFromJobInitialCash() {
        when(backtestJobMapper.selectOne(any())).thenReturn(BacktestJob.builder()
                .id(11L)
                .userId(CURRENT_USER_ID)
                .initCash(new BigDecimal("100.00"))
                .build());
        when(backtestEquityMapper.selectList(any())).thenReturn(List.of(
                BacktestEquity.builder()
                        .tradeDate(LocalDate.of(2026, 1, 2))
                        .equity(new BigDecimal("100.00"))
                        .build(),
                BacktestEquity.builder()
                        .tradeDate(LocalDate.of(2026, 1, 30))
                        .equity(new BigDecimal("110.00"))
                        .build(),
                BacktestEquity.builder()
                        .tradeDate(LocalDate.of(2026, 2, 27))
                        .equity(new BigDecimal("121.00"))
                        .build()));

        List<MonthlyReturnResp> returns = service.monthlyReturns(11L);

        assertEquals(2, returns.size());
        assertEquals(new BigDecimal("0.100000"), returns.get(0).getMonthReturn());
        assertEquals(new BigDecimal("0.100000"), returns.get(1).getMonthReturn());
    }

    @Test
    void shouldRejectAllTaskReadsWhenJobIsNotOwned() {
        when(backtestJobMapper.selectOne(any())).thenReturn(null);

        assertMissing(() -> service.getJob(99L));
        assertMissing(() -> service.listTrades(99L));
        assertMissing(() -> service.listEquities(99L));
        assertMissing(() -> service.monthlyReturns(99L));
        assertMissing(() -> service.stressTest(99L, 100, 20));
        assertMissing(() -> service.tradeExpectancy(99L));

        verify(backtestJobMapper, never()).selectById(any());
        verify(backtestTradeMapper, never()).selectList(any());
        verify(backtestEquityMapper, never()).selectList(any());
    }

    private LambdaQueryWrapper<BacktestJob> captureJobListQuery() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<BacktestJob>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(backtestJobMapper).selectList(queryCaptor.capture());
        return (LambdaQueryWrapper<BacktestJob>) queryCaptor.getValue();
    }

    private void assertMissing(Runnable query) {
        BusinessException exception = assertThrows(BusinessException.class, query::run);
        assertEquals("回测任务不存在", exception.getMessage());
    }

    private BacktestRunReq request(String beginDate, String endDate) {
        BacktestRunReq request = new BacktestRunReq();
        request.setCode("600519");
        request.setStrategyId("S1");
        request.setBeginDate(beginDate);
        request.setEndDate(endDate);
        return request;
    }

    private List<BarDaily> buildBars() {
        List<BarDaily> bars = new ArrayList<>();
        LocalDate beginDate = LocalDate.of(2025, 1, 2);
        for (int index = 0; index < 60; index++) {
            BigDecimal price = BigDecimal.valueOf(100L + index);
            bars.add(BarDaily.builder()
                    .code("600519")
                    .tradeDate(beginDate.plusDays(index))
                    .openPrice(price)
                    .highPrice(price)
                    .lowPrice(price)
                    .closePrice(price)
                    .volume(BigDecimal.valueOf(1000000))
                    .source(DailyBarClient.SOURCE_EASTMONEY)
                    .build());
        }
        return bars;
    }
}
