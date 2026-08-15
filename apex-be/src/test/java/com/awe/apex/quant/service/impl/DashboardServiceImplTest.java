package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.cache.RedisCacheService;
import com.awe.apex.quant.domain.dto.DecisionTodayResp;
import com.awe.apex.quant.domain.dto.MarketBriefingResp;
import com.awe.apex.quant.mapper.WatchlistMapper;
import com.awe.apex.quant.service.IDecisionService;
import com.awe.apex.quant.service.IMarketBriefingService;
import com.awe.apex.quant.service.IObservePoolService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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

    @Test
    void shouldReuseCachedBriefingWithoutRequestingLiveQuotes() {
        DashboardServiceImpl service = new DashboardServiceImpl();
        IMarketBriefingService marketBriefingService = mock(IMarketBriefingService.class);
        IDecisionService decisionService = mock(IDecisionService.class);
        IObservePoolService observePoolService = mock(IObservePoolService.class);
        RedisCacheService redisCacheService = mock(RedisCacheService.class);
        WatchlistMapper watchlistMapper = mock(WatchlistMapper.class);
        MarketBriefingResp briefing = MarketBriefingResp.builder()
                .asOf(LocalDate.of(2026, 8, 15))
                .stance("均衡")
                .dataLevel("GREEN")
                .build();

        ReflectionTestUtils.setField(service, "marketBriefingService", marketBriefingService);
        ReflectionTestUtils.setField(service, "decisionService", decisionService);
        ReflectionTestUtils.setField(service, "observePoolService", observePoolService);
        ReflectionTestUtils.setField(service, "redisCacheService", redisCacheService);
        ReflectionTestUtils.setField(service, "watchlistMapper", watchlistMapper);
        when(marketBriefingService.loadCachedBriefing()).thenReturn(briefing);
        when(decisionService.today(any(LocalDate.class), eq("我的自选"), same(briefing)))
                .thenReturn(DecisionTodayResp.builder().build());
        when(observePoolService.listReadyAlerts(6)).thenReturn(List.of());
        when(watchlistMapper.selectList(any())).thenReturn(List.of());

        service.home(null, "我的自选", false);

        verify(marketBriefingService).loadCachedBriefing();
        verify(marketBriefingService, never()).briefing();
        verify(marketBriefingService, never()).briefing(false);
        verify(decisionService).today(any(LocalDate.class), eq("我的自选"), same(briefing));
        verify(decisionService, never()).today(any(LocalDate.class), eq("我的自选"));
    }
}
