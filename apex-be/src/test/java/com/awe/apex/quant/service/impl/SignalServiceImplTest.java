package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.dto.SignalConfluenceResp;
import com.awe.apex.quant.domain.dto.SignalRunReq;
import com.awe.apex.quant.domain.dto.SignalStatsResp;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.domain.entity.StrategySignalEntity;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.mapper.StrategySignalMapper;
import com.awe.apex.quant.service.IPortfolioService;
import com.awe.apex.quant.strategy.SignalSide;
import com.awe.apex.quant.strategy.Strategy;
import com.awe.apex.quant.strategy.StrategySignalResult;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SignalServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, StrategySignalEntity.class);
        TableInfoHelper.initTableInfo(assistant, StockBasic.class);
    }

    @Test
    void runShouldEvaluateEachBarBatchBeforeLoadingTheNextBatch() {
        SignalServiceImpl signalService = new SignalServiceImpl();
        BarDailyMapper barDailyMapper = mock(BarDailyMapper.class);
        StrategySignalMapper strategySignalMapper = mock(StrategySignalMapper.class);
        Strategy strategy = mock(Strategy.class);
        IPortfolioService portfolioService = mock(IPortfolioService.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        AtomicInteger queryCount = new AtomicInteger();
        AtomicInteger evaluatedCount = new AtomicInteger();
        List<Integer> reportedProgress = new ArrayList<>();

        when(barDailyMapper.selectList(any())).thenAnswer(invocation -> {
            int batchIndex = queryCount.getAndIncrement();
            assertEquals(batchIndex * 40, evaluatedCount.get());
            int batchSize = batchIndex < 2 ? 40 : 1;
            return buildBars(batchIndex * 40, batchSize);
        });
        when(strategy.evaluate(any(), any())).thenAnswer(invocation -> {
            evaluatedCount.incrementAndGet();
            return null;
        });
        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        ReflectionTestUtils.setField(signalService, "barDailyMapper", barDailyMapper);
        ReflectionTestUtils.setField(signalService, "strategySignalMapper", strategySignalMapper);
        ReflectionTestUtils.setField(signalService, "strategies", List.of(strategy));
        ReflectionTestUtils.setField(signalService, "portfolioService", portfolioService);
        ReflectionTestUtils.setField(signalService, "transactionTemplate", transactionTemplate);
        when(portfolioService.listActiveHoldingCodes()).thenReturn(List.of());
        SignalRunReq request = new SignalRunReq();
        List<String> codes = new ArrayList<>();
        for (int index = 0; index < 81; index++) {
            codes.add(String.format("%06d", index));
        }
        request.setCodes(codes);

        signalService.run(request, (completed, total, message) -> {
            assertEquals(81, total);
            reportedProgress.add(completed);
        });

        assertEquals(3, queryCount.get());
        assertEquals(81, evaluatedCount.get());
        assertEquals(List.of(40, 80, 81), reportedProgress);
    }

    @Test
    void scanReturnsSignalsWithoutWritingUserSignalTable() {
        SignalServiceImpl signalService = new SignalServiceImpl();
        BarDailyMapper barDailyMapper = mock(BarDailyMapper.class);
        StrategySignalMapper strategySignalMapper = mock(StrategySignalMapper.class);
        Strategy strategy = mock(Strategy.class);
        when(barDailyMapper.selectList(any())).thenReturn(buildBars(List.of("600519")));
        when(strategy.evaluate(any(), any())).thenReturn(signal("600519", "S1", SignalSide.BUY));
        ReflectionTestUtils.setField(signalService, "barDailyMapper", barDailyMapper);
        ReflectionTestUtils.setField(signalService, "strategySignalMapper", strategySignalMapper);
        ReflectionTestUtils.setField(signalService, "strategies", List.of(strategy));
        SignalRunReq request = new SignalRunReq();
        request.setCodes(List.of("600519"));
        request.setSellCodes(List.of());

        List<StrategySignalEntity> signals = signalService.scan(request, null);

        assertEquals(1, signals.size());
        assertEquals("BUY", signals.get(0).getSide());
        verifyNoInteractions(strategySignalMapper);
    }

    @Test
    void runKeepsBuyPoolAndRestrictsSellSignalsToActivePortfolioHoldings() {
        SignalServiceImpl signalService = new SignalServiceImpl();
        BarDailyMapper barDailyMapper = mock(BarDailyMapper.class);
        StrategySignalMapper strategySignalMapper = mock(StrategySignalMapper.class);
        IPortfolioService portfolioService = mock(IPortfolioService.class);
        ApexUserContext userContext = mock(ApexUserContext.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        Strategy buyStrategy = mock(Strategy.class);
        Strategy sellStrategy = mock(Strategy.class);
        when(barDailyMapper.selectList(any())).thenReturn(buildBars(List.of("600519", "000001")));
        when(portfolioService.listActiveHoldingCodes()).thenReturn(List.of("000001"));
        when(userContext.currentUserId()).thenReturn(7L);
        when(buyStrategy.evaluate(any(), any())).thenAnswer(invocation -> signal(
                invocation.getArgument(0), "BUY_STRATEGY", SignalSide.BUY));
        when(sellStrategy.evaluate(any(), any())).thenAnswer(invocation -> signal(
                invocation.getArgument(0), "SELL_STRATEGY", SignalSide.SELL));
        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        ReflectionTestUtils.setField(signalService, "barDailyMapper", barDailyMapper);
        ReflectionTestUtils.setField(signalService, "strategySignalMapper", strategySignalMapper);
        ReflectionTestUtils.setField(signalService, "portfolioService", portfolioService);
        ReflectionTestUtils.setField(signalService, "userContext", userContext);
        ReflectionTestUtils.setField(signalService, "transactionTemplate", transactionTemplate);
        ReflectionTestUtils.setField(signalService, "strategies", List.of(buyStrategy, sellStrategy));
        SignalRunReq request = new SignalRunReq();
        request.setCodes(List.of("600519"));

        List<StrategySignalEntity> saved = signalService.run(request);

        assertEquals(2, saved.size());
        assertTrue(saved.stream().anyMatch(item -> "600519".equals(item.getCode())
                && "BUY".equals(item.getSide())));
        assertTrue(saved.stream().anyMatch(item -> "000001".equals(item.getCode())
                && "SELL".equals(item.getSide())));
        assertTrue(saved.stream().noneMatch(item -> "600519".equals(item.getCode())
                && "SELL".equals(item.getSide())));
        assertTrue(saved.stream().noneMatch(item -> "000001".equals(item.getCode())
                && "BUY".equals(item.getSide())));
        ArgumentCaptor<StrategySignalEntity> signalCaptor = ArgumentCaptor.forClass(StrategySignalEntity.class);
        verify(strategySignalMapper, times(2)).insert(signalCaptor.capture());
        assertEquals(2, signalCaptor.getAllValues().size());
    }

    @Test
    void currentReadModelsHideSellSignalsOutsideActivePortfolioHoldings() {
        SignalServiceImpl signalService = new SignalServiceImpl();
        StrategySignalMapper strategySignalMapper = mock(StrategySignalMapper.class);
        StockBasicMapper stockBasicMapper = mock(StockBasicMapper.class);
        IPortfolioService portfolioService = mock(IPortfolioService.class);
        ApexUserContext userContext = mock(ApexUserContext.class);
        LocalDate today = LocalDate.now();
        List<StrategySignalEntity> signals = List.of(
                storedSignal(1L, "300750", "BUY", "S1", today),
                storedSignal(2L, "000001", "SELL", "S1", today),
                storedSignal(3L, "000001", "SELL", "S2", today),
                storedSignal(4L, "600519", "SELL", "S1", today),
                storedSignal(5L, "600519", "SELL", "S2", today));
        when(strategySignalMapper.selectList(any())).thenReturn(signals);
        when(portfolioService.listActiveHoldingCodes()).thenReturn(List.of("000001"));
        when(userContext.currentUserId()).thenReturn(7L);
        ReflectionTestUtils.setField(signalService, "strategySignalMapper", strategySignalMapper);
        ReflectionTestUtils.setField(signalService, "stockBasicMapper", stockBasicMapper);
        ReflectionTestUtils.setField(signalService, "portfolioService", portfolioService);
        ReflectionTestUtils.setField(signalService, "userContext", userContext);

        List<StrategySignalEntity> latest = signalService.latest(20, false);
        SignalStatsResp stats = signalService.stats(5);
        SignalConfluenceResp confluence = signalService.confluence(5, 2);

        assertEquals(3, latest.size());
        assertEquals(3, stats.getTotal());
        assertEquals(1, stats.getBuyCount());
        assertEquals(2, stats.getSellCount());
        assertEquals(1, confluence.getItems().size());
        assertEquals("000001", confluence.getItems().get(0).getCode());
        assertEquals("SELL", confluence.getItems().get(0).getSide());
    }

    @Test
    void historicalConfluenceDoesNotUseCurrentPortfolioScope() {
        SignalServiceImpl signalService = new SignalServiceImpl();
        StrategySignalMapper strategySignalMapper = mock(StrategySignalMapper.class);
        StockBasicMapper stockBasicMapper = mock(StockBasicMapper.class);
        IPortfolioService portfolioService = mock(IPortfolioService.class);
        ApexUserContext userContext = mock(ApexUserContext.class);
        LocalDate historyDate = LocalDate.of(2026, 7, 1);
        when(strategySignalMapper.selectList(any())).thenReturn(List.of(
                storedSignal(1L, "600519", "SELL", "S1", historyDate),
                storedSignal(2L, "600519", "SELL", "S2", historyDate)));
        when(userContext.currentUserId()).thenReturn(7L);
        ReflectionTestUtils.setField(signalService, "strategySignalMapper", strategySignalMapper);
        ReflectionTestUtils.setField(signalService, "stockBasicMapper", stockBasicMapper);
        ReflectionTestUtils.setField(signalService, "portfolioService", portfolioService);
        ReflectionTestUtils.setField(signalService, "userContext", userContext);

        SignalConfluenceResp confluence = signalService.confluence(5, 2, historyDate);

        assertEquals(1, confluence.getItems().size());
        assertEquals("600519", confluence.getItems().get(0).getCode());
        verifyNoInteractions(portfolioService);
    }

    private List<BarDaily> buildBars(int startIndex, int codeCount) {
        List<BarDaily> bars = new ArrayList<>();
        LocalDate firstDate = LocalDate.of(2026, 1, 1);
        for (int codeIndex = startIndex; codeIndex < startIndex + codeCount; codeIndex++) {
            String code = String.format("%06d", codeIndex);
            for (int day = 0; day < 60; day++) {
                bars.add(BarDaily.builder()
                        .code(code)
                        .tradeDate(firstDate.plusDays(day))
                        .openPrice(BigDecimal.TEN)
                        .highPrice(BigDecimal.TEN)
                        .lowPrice(BigDecimal.TEN)
                        .closePrice(BigDecimal.TEN)
                        .volume(BigDecimal.ONE)
                        .build());
            }
        }
        return bars;
    }

    private List<BarDaily> buildBars(List<String> codes) {
        List<BarDaily> bars = new ArrayList<>();
        LocalDate firstDate = LocalDate.of(2026, 1, 1);
        for (String code : codes) {
            for (int day = 0; day < 60; day++) {
                bars.add(BarDaily.builder()
                        .code(code)
                        .tradeDate(firstDate.plusDays(day))
                        .openPrice(BigDecimal.TEN)
                        .highPrice(BigDecimal.TEN)
                        .lowPrice(BigDecimal.TEN)
                        .closePrice(BigDecimal.TEN)
                        .volume(BigDecimal.ONE)
                        .build());
            }
        }
        return bars;
    }

    private StrategySignalResult signal(String code, String strategyId, SignalSide side) {
        return StrategySignalResult.builder()
                .strategyId(strategyId)
                .code(code)
                .signalDate(LocalDate.of(2026, 3, 1))
                .side(side)
                .score(new BigDecimal("80"))
                .reason(Map.of("scope", "test"))
                .build();
    }

    private StrategySignalEntity storedSignal(Long id, String code, String side,
                                              String strategyId, LocalDate signalDate) {
        return StrategySignalEntity.builder()
                .id(id)
                .userId(7L)
                .code(code)
                .side(side)
                .strategyId(strategyId)
                .signalDate(signalDate)
                .score(new BigDecimal("80"))
                .build();
    }
}
