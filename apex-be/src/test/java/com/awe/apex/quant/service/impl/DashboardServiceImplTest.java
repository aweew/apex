package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.cache.RedisCacheService;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.dto.DecisionTodayResp;
import com.awe.apex.quant.domain.dto.DashboardHomeResp;
import com.awe.apex.quant.domain.dto.MarketBriefingResp;
import com.awe.apex.quant.domain.dto.MorningBriefingResp;
import com.awe.apex.quant.domain.entity.Watchlist;
import com.awe.apex.quant.mapper.WatchlistMapper;
import com.awe.apex.quant.service.IDecisionService;
import com.awe.apex.quant.service.IMarketBriefingService;
import com.awe.apex.quant.service.IMorningBriefingService;
import com.awe.apex.quant.service.IObservePoolService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Watchlist.class);
    }

    @Test
    void shouldReuseCachedBriefingWithoutRequestingLiveQuotes() {
        DashboardServiceImpl service = new DashboardServiceImpl();
        IMarketBriefingService marketBriefingService = mock(IMarketBriefingService.class);
        IDecisionService decisionService = mock(IDecisionService.class);
        IObservePoolService observePoolService = mock(IObservePoolService.class);
        IMorningBriefingService morningBriefingService = mock(IMorningBriefingService.class);
        RedisCacheService redisCacheService = mock(RedisCacheService.class);
        WatchlistMapper watchlistMapper = mock(WatchlistMapper.class);
        ApexUserContext userContext = new ApexUserContext();
        MarketBriefingResp briefing = MarketBriefingResp.builder()
                .asOf(LocalDate.of(2026, 8, 15))
                .stance("均衡")
                .dataLevel("GREEN")
                .limitUpCount(1)
                .build();
        MorningBriefingResp morningBriefing = MorningBriefingResp.builder()
                .dataLevel("GREEN")
                .build();

        ReflectionTestUtils.setField(service, "marketBriefingService", marketBriefingService);
        ReflectionTestUtils.setField(service, "decisionService", decisionService);
        ReflectionTestUtils.setField(service, "observePoolService", observePoolService);
        ReflectionTestUtils.setField(service, "morningBriefingService", morningBriefingService);
        ReflectionTestUtils.setField(service, "redisCacheService", redisCacheService);
        ReflectionTestUtils.setField(service, "watchlistMapper", watchlistMapper);
        ReflectionTestUtils.setField(service, "userContext", userContext);
        when(marketBriefingService.loadCachedBriefing()).thenReturn(briefing);
        when(decisionService.today(any(LocalDate.class), eq("我的自选"), same(briefing)))
                .thenReturn(DecisionTodayResp.builder().build());
        when(observePoolService.listReadyAlerts(6)).thenReturn(List.of());
        when(morningBriefingService.latest()).thenReturn(morningBriefing);
        when(watchlistMapper.selectList(any())).thenReturn(List.of());

        DashboardHomeResp response = userContext.runAsUser(7L,
                () -> service.home(null, "我的自选", false));

        verify(marketBriefingService).loadCachedBriefing();
        verify(marketBriefingService, never()).briefing();
        verify(marketBriefingService, never()).briefing(false);
        verify(decisionService).today(any(LocalDate.class), eq("我的自选"), same(briefing));
        verify(decisionService, never()).today(any(LocalDate.class), eq("我的自选"));
        assertSame(morningBriefing, response.getMorningBriefing());
    }

    @Test
    void homeScopesCacheAndAsyncPrivateBlocksToCurrentUser() {
        DashboardServiceImpl service = new DashboardServiceImpl();
        IMarketBriefingService marketBriefingService = mock(IMarketBriefingService.class);
        IDecisionService decisionService = mock(IDecisionService.class);
        IObservePoolService observePoolService = mock(IObservePoolService.class);
        IMorningBriefingService morningBriefingService = mock(IMorningBriefingService.class);
        RedisCacheService redisCacheService = mock(RedisCacheService.class);
        WatchlistMapper watchlistMapper = mock(WatchlistMapper.class);
        ApexUserContext userContext = new ApexUserContext();
        AtomicLong decisionUserId = new AtomicLong();
        AtomicLong alertUserId = new AtomicLong();
        MarketBriefingResp briefing = MarketBriefingResp.builder()
                .asOf(LocalDate.of(2026, 8, 16))
                .stance("均衡")
                .dataLevel("GREEN")
                .build();
        ReflectionTestUtils.setField(service, "marketBriefingService", marketBriefingService);
        ReflectionTestUtils.setField(service, "decisionService", decisionService);
        ReflectionTestUtils.setField(service, "observePoolService", observePoolService);
        ReflectionTestUtils.setField(service, "morningBriefingService", morningBriefingService);
        ReflectionTestUtils.setField(service, "redisCacheService", redisCacheService);
        ReflectionTestUtils.setField(service, "watchlistMapper", watchlistMapper);
        ReflectionTestUtils.setField(service, "userContext", userContext);
        when(marketBriefingService.loadCachedBriefing()).thenReturn(briefing);
        when(decisionService.today(any(LocalDate.class), eq("我的自选"), same(briefing))).thenAnswer(invocation -> {
            decisionUserId.set(userContext.currentUserId());
            return DecisionTodayResp.builder().build();
        });
        when(observePoolService.listReadyAlerts(6)).thenAnswer(invocation -> {
            alertUserId.set(userContext.currentUserId());
            return List.of();
        });
        when(watchlistMapper.selectList(any())).thenReturn(List.of());

        userContext.runAsUser(7L, () -> service.home(11L, "我的自选", false));

        ArgumentCaptor<String> cacheKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisCacheService).get(cacheKeyCaptor.capture(), eq(com.awe.apex.quant.domain.dto.DashboardHomeResp.class));
        assertTrue(cacheKeyCaptor.getValue().contains(":7:"));
        assertEquals(7L, decisionUserId.get());
        assertEquals(7L, alertUserId.get());
        ArgumentCaptor<Wrapper<Watchlist>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(watchlistMapper).selectList(queryCaptor.capture());
        assertTrue(queryCaptor.getValue().getSqlSegment().contains("user_id"));
        AbstractWrapper<?, ?, ?> query = (AbstractWrapper<?, ?, ?>) queryCaptor.getValue();
        assertTrue(query.getParamNameValuePairs().containsValue(7L));
    }
}
