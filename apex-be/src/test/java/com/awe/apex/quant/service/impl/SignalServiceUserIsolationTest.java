package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.dto.SignalRunReq;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.entity.StrategySignalEntity;
import com.awe.apex.quant.domain.entity.Watchlist;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.StrategySignalMapper;
import com.awe.apex.quant.mapper.WatchlistMapper;
import com.awe.apex.quant.service.IPortfolioService;
import com.awe.apex.quant.strategy.SignalSide;
import com.awe.apex.quant.strategy.Strategy;
import com.awe.apex.quant.strategy.StrategySignalResult;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SignalServiceUserIsolationTest {

    private final BarDailyMapper barDailyMapper = mock(BarDailyMapper.class);
    private final StrategySignalMapper strategySignalMapper = mock(StrategySignalMapper.class);
    private final WatchlistMapper watchlistMapper = mock(WatchlistMapper.class);
    private final ApexUserContext userContext = mock(ApexUserContext.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    private final IPortfolioService portfolioService = mock(IPortfolioService.class);
    private final Strategy strategy = mock(Strategy.class);
    private final SignalServiceImpl service = new SignalServiceImpl();

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, StrategySignalEntity.class);
        TableInfoHelper.initTableInfo(assistant, Watchlist.class);
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "barDailyMapper", barDailyMapper);
        ReflectionTestUtils.setField(service, "strategySignalMapper", strategySignalMapper);
        ReflectionTestUtils.setField(service, "watchlistMapper", watchlistMapper);
        ReflectionTestUtils.setField(service, "userContext", userContext);
        ReflectionTestUtils.setField(service, "transactionTemplate", transactionTemplate);
        ReflectionTestUtils.setField(service, "portfolioService", portfolioService);
        ReflectionTestUtils.setField(service, "strategies", List.of(strategy));
        when(userContext.currentUserId()).thenReturn(7L);
        when(portfolioService.listActiveHoldingCodes()).thenReturn(List.of());
        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    @Test
    void defaultRunLoadsOnlyCurrentUsersWatchlist() {
        when(watchlistMapper.selectList(any())).thenReturn(List.of(Watchlist.builder().code("600519").build()));
        when(barDailyMapper.selectList(any())).thenReturn(List.of());

        service.run(new SignalRunReq());

        ArgumentCaptor<Wrapper<Watchlist>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(watchlistMapper).selectList(queryCaptor.capture());
        assertUserFilter(queryCaptor.getValue());
    }

    @Test
    void runPersistsOwnerAndScopesDeduplication() {
        when(barDailyMapper.selectList(any())).thenReturn(buildBars());
        when(strategy.evaluate(any(), any())).thenReturn(StrategySignalResult.builder()
                .strategyId("S1")
                .code("600519")
                .signalDate(LocalDate.of(2026, 3, 1))
                .side(SignalSide.BUY)
                .score(new BigDecimal("80"))
                .reason(Map.of("trend", "up"))
                .build());
        SignalRunReq request = new SignalRunReq();
        request.setCodes(List.of("600519"));

        service.run(request);

        ArgumentCaptor<StrategySignalEntity> signalCaptor = ArgumentCaptor.forClass(StrategySignalEntity.class);
        verify(strategySignalMapper).insert(signalCaptor.capture());
        assertEquals(7L, signalCaptor.getValue().getUserId());
        ArgumentCaptor<Wrapper<StrategySignalEntity>> deleteCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(strategySignalMapper).delete(deleteCaptor.capture());
        assertUserFilter(deleteCaptor.getValue());
    }

    @Test
    void runRetriesSignalPersistenceAfterDeadlock() {
        when(barDailyMapper.selectList(any())).thenReturn(buildBars());
        when(strategy.evaluate(any(), any())).thenReturn(StrategySignalResult.builder()
                .strategyId("S1")
                .code("600519")
                .signalDate(LocalDate.of(2026, 3, 1))
                .side(SignalSide.BUY)
                .score(new BigDecimal("80"))
                .reason(Map.of("trend", "up"))
                .build());
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            if (attempts.getAndIncrement() == 0) {
                throw new DeadlockLoserDataAccessException("写入策略信号", new SQLException("deadlock"));
            }
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        SignalRunReq request = new SignalRunReq();
        request.setCodes(List.of("600519"));

        service.run(request);

        verify(transactionTemplate, times(2)).executeWithoutResult(any());
        verify(strategySignalMapper).insert(any(StrategySignalEntity.class));
    }

    @Test
    void allSignalReadModelsFilterCurrentUser() {
        when(strategySignalMapper.selectList(any())).thenReturn(List.of());

        service.latest(20, false);
        service.stats(5);
        service.confluence(5, 2);
        service.forwardEval(30, 5);

        ArgumentCaptor<Wrapper<StrategySignalEntity>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(strategySignalMapper, times(4)).selectList(queryCaptor.capture());
        for (Wrapper<StrategySignalEntity> query : queryCaptor.getAllValues()) {
            assertUserFilter(query);
        }
    }

    private List<BarDaily> buildBars() {
        List<BarDaily> bars = new ArrayList<>();
        for (int day = 0; day < 60; day++) {
            bars.add(BarDaily.builder()
                    .code("600519")
                    .tradeDate(LocalDate.of(2026, 1, 1).plusDays(day))
                    .openPrice(BigDecimal.TEN)
                    .highPrice(BigDecimal.TEN)
                    .lowPrice(BigDecimal.TEN)
                    .closePrice(BigDecimal.TEN)
                    .volume(BigDecimal.ONE)
                    .build());
        }
        return bars;
    }

    private void assertUserFilter(Wrapper<?> query) {
        assertTrue(query.getSqlSegment().contains("user_id"));
        AbstractWrapper<?, ?, ?> abstractQuery = (AbstractWrapper<?, ?, ?>) query;
        assertTrue(abstractQuery.getParamNameValuePairs().containsValue(7L));
    }
}
