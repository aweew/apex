package com.awe.apex.quant.service.impl;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.dto.BarSyncReq;
import com.awe.apex.quant.domain.dto.BarSyncResp;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.mapper.DataSyncLogMapper;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.domain.entity.Watchlist;
import com.awe.apex.quant.mapper.WatchlistMapper;
import com.awe.apex.quant.market.DailyBarClient;
import com.awe.apex.quant.market.TradingCalendar;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BarDailyServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Watchlist.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BarDaily.class);
    }

    @Test
    void syncBarsShouldRejectOversizedRequest() {
        BarDailyServiceImpl barDailyService = new BarDailyServiceImpl();
        BarSyncReq request = new BarSyncReq();
        List<String> codes = new ArrayList<>();
        for (int index = 0; index < 81; index++) {
            codes.add(String.valueOf(600000 + index));
        }
        request.setCodes(codes);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> barDailyService.syncBars(request));

        assertEquals("单次最多同步 80 个证券代码，全 A 日线请使用同步中心任务", exception.getMessage());
    }

    @Test
    void syncBarsShouldProcessCodesWithBoundedConcurrency() {
        BarDailyServiceImpl barDailyService = new BarDailyServiceImpl();
        DailyBarClient dailyBarClient = mock(DailyBarClient.class);
        StockBasicMapper stockBasicMapper = mock(StockBasicMapper.class);
        DataSyncLogMapper dataSyncLogMapper = mock(DataSyncLogMapper.class);
        AtomicInteger activeTasks = new AtomicInteger();
        AtomicInteger maxActiveTasks = new AtomicInteger();
        AtomicInteger callCount = new AtomicInteger();
        when(dailyBarClient.fetchDailyBars(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            callCount.incrementAndGet();
            int currentActive = activeTasks.incrementAndGet();
            maxActiveTasks.accumulateAndGet(currentActive, Math::max);
            try {
                Thread.sleep(30);
                return List.of();
            } finally {
                activeTasks.decrementAndGet();
            }
        });
        when(stockBasicMapper.selectOne(any())).thenReturn(null);
        ReflectionTestUtils.setField(barDailyService, "dailyBarClient", dailyBarClient);
        ReflectionTestUtils.setField(barDailyService, "stockBasicMapper", stockBasicMapper);
        ReflectionTestUtils.setField(barDailyService, "dataSyncLogMapper", dataSyncLogMapper);
        ReflectionTestUtils.setField(barDailyService, "syncTimeoutSeconds", 5);
        BarSyncReq request = new BarSyncReq();
        request.setCodes(List.of("600000", "600001", "600002"));

        BarSyncResp response = barDailyService.syncBars(request);

        assertEquals(3, callCount.get());
        assertTrue(maxActiveTasks.get() <= 2);
        assertEquals(3, response.getSuccessCount());
        assertEquals(3, response.getDetails().size());
    }

    @Test
    void syncBarsShouldContinueLaterCodesAfterEarlierGroupTimesOut() {
        BarDailyServiceImpl barDailyService = new BarDailyServiceImpl();
        DailyBarClient dailyBarClient = mock(DailyBarClient.class);
        StockBasicMapper stockBasicMapper = mock(StockBasicMapper.class);
        DataSyncLogMapper dataSyncLogMapper = mock(DataSyncLogMapper.class);
        AtomicInteger callCount = new AtomicInteger();
        when(dailyBarClient.fetchDailyBars(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            callCount.incrementAndGet();
            String code = invocation.getArgument(0);
            if ("600000".equals(code) || "600001".equals(code)) {
                Thread.sleep(5000L);
            }
            return List.of();
        });
        when(stockBasicMapper.selectOne(any())).thenReturn(null);
        ReflectionTestUtils.setField(barDailyService, "dailyBarClient", dailyBarClient);
        ReflectionTestUtils.setField(barDailyService, "stockBasicMapper", stockBasicMapper);
        ReflectionTestUtils.setField(barDailyService, "dataSyncLogMapper", dataSyncLogMapper);
        ReflectionTestUtils.setField(barDailyService, "syncTimeoutSeconds", 1);
        BarSyncReq request = new BarSyncReq();
        request.setCodes(List.of("600000", "600001", "600002", "600003"));

        long startedAt = System.nanoTime();
        BarSyncResp response = barDailyService.syncBars(request);
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

        assertTrue(elapsedMillis < 3000, "整批同步不应逐批累加超时时间");
        assertEquals(4, callCount.get());
        assertEquals(2, response.getSuccessCount());
        assertEquals(2, response.getFailCount());
        assertEquals(List.of("600000 TIMEOUT", "600001 TIMEOUT"),
                response.getDetails().stream().filter(detail -> detail.endsWith("TIMEOUT")).toList());
    }

    @Test
    void syncStaleCodesShouldDeduplicateAndSkipFreshCodes() {
        BarDailyServiceImpl barDailyService = new BarDailyServiceImpl();
        DailyBarClient dailyBarClient = mock(DailyBarClient.class);
        BarDailyMapper barDailyMapper = mock(BarDailyMapper.class);
        StockBasicMapper stockBasicMapper = mock(StockBasicMapper.class);
        DataSyncLogMapper dataSyncLogMapper = mock(DataSyncLogMapper.class);
        LocalDate latestTradingDay = TradingCalendar.latestTradingDayOnOrBefore(LocalDate.now());
        when(barDailyMapper.selectMaps(any())).thenReturn(List.of(Map.of(
                "code", "600000",
                "tradeDate", latestTradingDay.toString(),
                "cnt", 100)));
        when(dailyBarClient.fetchDailyBars(eq("600001"), anyString(), anyString())).thenReturn(List.of());
        when(stockBasicMapper.selectOne(any())).thenReturn(null);
        ReflectionTestUtils.setField(barDailyService, "baseMapper", barDailyMapper);
        ReflectionTestUtils.setField(barDailyService, "dailyBarClient", dailyBarClient);
        ReflectionTestUtils.setField(barDailyService, "stockBasicMapper", stockBasicMapper);
        ReflectionTestUtils.setField(barDailyService, "dataSyncLogMapper", dataSyncLogMapper);
        ReflectionTestUtils.setField(barDailyService, "syncTimeoutSeconds", 5);

        BarSyncResp response = barDailyService.syncStaleCodes(List.of("600000", "600001", "600001"));

        assertEquals(1, response.getSuccessCount());
        verify(dailyBarClient).fetchDailyBars(eq("600001"), anyString(), anyString());
        verify(dailyBarClient, never()).fetchDailyBars(eq("600000"), anyString(), anyString());
    }

    @Test
    void watchlistSyncQueriesFilterCurrentUser() {
        BarDailyServiceImpl service = new BarDailyServiceImpl();
        WatchlistMapper watchlistMapper = mock(WatchlistMapper.class);
        ApexUserContext userContext = mock(ApexUserContext.class);
        ReflectionTestUtils.setField(service, "watchlistMapper", watchlistMapper);
        ReflectionTestUtils.setField(service, "userContext", userContext);
        when(userContext.currentUserId()).thenReturn(7L);
        when(watchlistMapper.selectList(any())).thenReturn(List.of());

        assertThrows(BusinessException.class,
                () -> service.syncWatchlistGroup("我的自选", null, null));
        assertThrows(BusinessException.class,
                () -> service.syncStaleWatchlist("我的自选", 40));

        ArgumentCaptor<Wrapper<Watchlist>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(watchlistMapper, times(2)).selectList(queryCaptor.capture());
        for (Wrapper<Watchlist> query : queryCaptor.getAllValues()) {
            assertTrue(query.getSqlSegment().contains("user_id"));
            AbstractWrapper<?, ?, ?> abstractQuery = (AbstractWrapper<?, ?, ?>) query;
            assertTrue(abstractQuery.getParamNameValuePairs().containsValue(7L));
        }
    }
}
