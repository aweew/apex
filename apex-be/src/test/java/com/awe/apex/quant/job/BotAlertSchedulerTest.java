package com.awe.apex.quant.job;

import com.awe.apex.quant.bot.config.ApexBotProperties;
import com.awe.apex.quant.bot.service.IBotHoldingRiskService;
import com.awe.apex.quant.bot.service.IBotNotificationService;
import com.awe.apex.quant.domain.dto.BotHoldingRiskResp;
import com.awe.apex.quant.domain.dto.WatchlistMoverResp;
import com.awe.apex.quant.service.IMyHoldingService;
import com.awe.apex.quant.service.IObservePoolService;
import com.awe.apex.quant.service.IWatchlistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BotAlertSchedulerTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private BotAlertScheduler scheduler;
    private ApexBotProperties properties;
    private IWatchlistService watchlistService;
    private IMyHoldingService myHoldingService;
    private IObservePoolService observePoolService;
    private IBotHoldingRiskService botHoldingRiskService;
    private IBotNotificationService notificationService;

    @BeforeEach
    void setUp() {
        scheduler = new BotAlertScheduler();
        properties = new ApexBotProperties();
        properties.getWeclaw().setEnabled(true);
        watchlistService = mock(IWatchlistService.class);
        myHoldingService = mock(IMyHoldingService.class);
        observePoolService = mock(IObservePoolService.class);
        botHoldingRiskService = mock(IBotHoldingRiskService.class);
        notificationService = mock(IBotNotificationService.class);
        ReflectionTestUtils.setField(scheduler, "properties", properties);
        ReflectionTestUtils.setField(scheduler, "watchlistService", watchlistService);
        ReflectionTestUtils.setField(scheduler, "myHoldingService", myHoldingService);
        ReflectionTestUtils.setField(scheduler, "observePoolService", observePoolService);
        ReflectionTestUtils.setField(scheduler, "botHoldingRiskService", botHoldingRiskService);
        ReflectionTestUtils.setField(scheduler, "notificationService", notificationService);
    }

    @Test
    void scansUsingLightweightQuotesDuringTradingSession() {
        setTime("2026-08-13T02:00:00Z");
        when(myHoldingService.listHoldingCodes()).thenReturn(List.of("600000"));
        when(watchlistService.movers(anyString(), any(), anyInt())).thenReturn(new WatchlistMoverResp());
        when(observePoolService.listReadyAlerts(anyInt())).thenReturn(List.of());
        when(botHoldingRiskService.analyze()).thenReturn(new BotHoldingRiskResp());

        scheduler.scanMarketAlerts();

        verify(watchlistService).refreshQuotes("我的自选", 20, false);
        verify(myHoldingService).listHoldingCodes();
        verify(myHoldingService, never()).listHoldings();
        verify(myHoldingService).refreshQuotesForCodes(List.of("600000"), false);
        verify(myHoldingService, never()).refreshQuotes(false);
        verify(notificationService).notifyMarketAlerts(any(), eq(List.of()), any());
    }

    @Test
    void skipsDuringLunchBreak() {
        setTime("2026-08-13T04:00:00Z");

        scheduler.scanMarketAlerts();

        verify(watchlistService, never()).refreshQuotes(anyString(), anyInt(), any());
        verify(notificationService, never()).notifyMarketAlerts(any(), any(), any());
    }

    private void setTime(String instant) {
        ReflectionTestUtils.setField(scheduler, "clock", Clock.fixed(Instant.parse(instant), SHANGHAI));
    }
}
