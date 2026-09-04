package com.awe.apex.quant.job;

import cn.dev33.satoken.stp.StpUtil;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.dto.SyncJobResp;
import com.awe.apex.quant.market.TradingCalendar;
import com.awe.apex.quant.service.ApexUserAuthService;
import com.awe.apex.quant.service.IConfigService;
import com.awe.apex.quant.service.IDataSyncJobService;
import com.awe.apex.quant.service.IMyHoldingService;
import com.awe.apex.quant.service.IObservePoolService;
import com.awe.apex.quant.service.IPortfolioIntradayService;
import com.awe.apex.quant.service.IPortfolioService;
import com.awe.apex.quant.service.IWatchlistService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class DataSyncSchedulerTest {

    @Test
    void schedulersRequireTheCurrentInstanceToBeEnabled() {
        for (Class<?> schedulerClass : List.of(
                DataSyncScheduler.class,
                DecisionScheduler.class,
                MorningBriefingScheduler.class,
                SmartTraderScheduler.class,
                BotAlertScheduler.class)) {
            ConditionalOnProperty condition = schedulerClass.getAnnotation(ConditionalOnProperty.class);
            assertEquals("apex.scheduler", condition.prefix());
            assertEquals(List.of("enabled"), List.of(condition.name()));
            assertEquals("true", condition.havingValue());
        }
    }

    @Test
    void schedulesFocusQuoteRefreshEveryFiveMinutes() throws Exception {
        Method method = DataSyncScheduler.class.getMethod("refreshFocusQuotesIntraday");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertEquals("0 */5 9-11,13-15 * * MON-FRI", scheduled.cron());
        assertEquals("Asia/Shanghai", scheduled.zone());
    }

    @Test
    void refreshesDistinctHoldingAndObserveCodesForAllEnabledUsers() {
        IConfigService configService = mock(IConfigService.class);
        IMyHoldingService myHoldingService = mock(IMyHoldingService.class);
        IObservePoolService observePoolService = mock(IObservePoolService.class);
        IPortfolioService portfolioService = mock(IPortfolioService.class);
        IPortfolioIntradayService portfolioIntradayService = mock(IPortfolioIntradayService.class);
        ApexUserAuthService userAuthService = mock(ApexUserAuthService.class);
        ApexUserContext userContext = new ApexUserContext();
        List<Long> evaluatedUserIds = new ArrayList<>();
        when(configService.getString("auto_sync_enabled", "false")).thenReturn("true");
        when(userAuthService.listEnabledUserIds()).thenReturn(List.of(11L, 22L));
        when(myHoldingService.listHoldingCodes()).thenAnswer(invocation ->
                userContext.currentUserId() == 11L
                        ? List.of("600000", "000001")
                        : List.of("000001", "300750"));
        when(observePoolService.listActiveCodes()).thenAnswer(invocation ->
                userContext.currentUserId() == 11L
                        ? List.of("300750", "600519")
                        : List.of("600519", "000858"));
        when(portfolioService.listActiveHoldingCodes()).thenAnswer(invocation ->
                userContext.currentUserId() == 11L
                        ? List.of("688981")
                        : List.of("002594", "688981"));
        when(observePoolService.refresh()).thenAnswer(invocation -> {
            evaluatedUserIds.add(userContext.currentUserId());
            return Map.of();
        });
        DataSyncScheduler scheduler = new DataSyncScheduler();
        ReflectionTestUtils.setField(scheduler, "configService", configService);
        ReflectionTestUtils.setField(scheduler, "myHoldingService", myHoldingService);
        ReflectionTestUtils.setField(scheduler, "observePoolService", observePoolService);
        ReflectionTestUtils.setField(scheduler, "portfolioService", portfolioService);
        ReflectionTestUtils.setField(scheduler, "portfolioIntradayService", portfolioIntradayService);
        ReflectionTestUtils.setField(scheduler, "userAuthService", userAuthService);
        ReflectionTestUtils.setField(scheduler, "userContext", userContext);

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(99L);

            scheduler.refreshFocusQuotes(LocalDate.of(2026, 8, 13), LocalTime.of(10, 0));

            verify(myHoldingService).refreshRealtimeQuotesForCodes(
                    List.of("600000", "000001", "300750", "600519", "688981", "000858", "002594"), false);
            verify(myHoldingService, never()).refreshQuotesForCodes(org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any());
            verify(myHoldingService, never()).refreshQuotes(false);
            assertEquals(List.of(11L, 22L), evaluatedUserIds);
            verify(portfolioIntradayService, org.mockito.Mockito.times(2))
                    .snapshotAll(LocalDate.of(2026, 8, 13).atTime(10, 0));
            assertEquals(99L, userContext.currentUserId());
        }
    }

    @Test
    void intradaySnapshotFailureDoesNotBlockObservePoolRefresh() {
        IConfigService configService = mock(IConfigService.class);
        IMyHoldingService myHoldingService = mock(IMyHoldingService.class);
        IObservePoolService observePoolService = mock(IObservePoolService.class);
        IPortfolioService portfolioService = mock(IPortfolioService.class);
        IPortfolioIntradayService portfolioIntradayService = mock(IPortfolioIntradayService.class);
        ApexUserAuthService userAuthService = mock(ApexUserAuthService.class);
        ApexUserContext userContext = new ApexUserContext();
        when(configService.getString("auto_sync_enabled", "false")).thenReturn("true");
        when(userAuthService.listEnabledUserIds()).thenReturn(List.of(11L));
        when(myHoldingService.listHoldingCodes()).thenReturn(List.of("600000"));
        when(observePoolService.listActiveCodes()).thenReturn(List.of());
        when(portfolioService.listActiveHoldingCodes()).thenReturn(List.of());
        when(myHoldingService.refreshRealtimeQuotesForCodes(List.of("600000"), false))
                .thenReturn(Map.of("success", 1, "fail", 0));
        doThrow(new IllegalStateException("database unavailable"))
                .when(portfolioIntradayService).snapshotAll(LocalDate.of(2026, 8, 13).atTime(10, 0));
        DataSyncScheduler scheduler = new DataSyncScheduler();
        ReflectionTestUtils.setField(scheduler, "configService", configService);
        ReflectionTestUtils.setField(scheduler, "myHoldingService", myHoldingService);
        ReflectionTestUtils.setField(scheduler, "observePoolService", observePoolService);
        ReflectionTestUtils.setField(scheduler, "portfolioService", portfolioService);
        ReflectionTestUtils.setField(scheduler, "portfolioIntradayService", portfolioIntradayService);
        ReflectionTestUtils.setField(scheduler, "userAuthService", userAuthService);
        ReflectionTestUtils.setField(scheduler, "userContext", userContext);

        scheduler.refreshFocusQuotes(LocalDate.of(2026, 8, 13), LocalTime.of(10, 0));

        verify(observePoolService).refresh();
    }

    @Test
    void afternoonRefreshDeduplicatesQuotesAcrossUsersAndOnlySnapshotsPerUser() {
        IConfigService configService = mock(IConfigService.class);
        IDataSyncJobService dataSyncJobService = mock(IDataSyncJobService.class);
        IWatchlistService watchlistService = mock(IWatchlistService.class);
        IMyHoldingService myHoldingService = mock(IMyHoldingService.class);
        IPortfolioService portfolioService = mock(IPortfolioService.class);
        ApexUserAuthService userAuthService = mock(ApexUserAuthService.class);
        ApexUserContext userContext = new ApexUserContext();
        when(configService.getString("auto_sync_enabled", "false")).thenReturn("true");
        when(configService.getString("auto_sync_group", "我的自选")).thenReturn("我的自选");
        when(dataSyncJobService.isTaskRunning("CLOSE_BUNDLE")).thenReturn(false);
        when(userAuthService.listEnabledUserIds()).thenReturn(List.of(11L, 22L));
        when(watchlistService.listWatchlistCodes("我的自选")).thenAnswer(invocation ->
                userContext.currentUserId() == 11L
                        ? List.of("600000", "000001") : List.of("000001", "300750"));
        when(myHoldingService.listHoldingCodes()).thenAnswer(invocation ->
                userContext.currentUserId() == 11L ? List.of("600519") : List.of("600519", "000858"));
        when(portfolioService.listActiveHoldingCodes()).thenAnswer(invocation ->
                userContext.currentUserId() == 11L ? List.of("600519") : List.of("000858", "002594"));
        when(myHoldingService.refreshQuotesForCodes(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(false))).thenReturn(Map.of("success", 7, "fail", 0));
        when(portfolioService.snapshotAll()).thenReturn(1);
        DataSyncScheduler scheduler = new DataSyncScheduler();
        ReflectionTestUtils.setField(scheduler, "configService", configService);
        ReflectionTestUtils.setField(scheduler, "dataSyncJobService", dataSyncJobService);
        ReflectionTestUtils.setField(scheduler, "watchlistService", watchlistService);
        ReflectionTestUtils.setField(scheduler, "myHoldingService", myHoldingService);
        ReflectionTestUtils.setField(scheduler, "portfolioService", portfolioService);
        ReflectionTestUtils.setField(scheduler, "userAuthService", userAuthService);
        ReflectionTestUtils.setField(scheduler, "userContext", userContext);

        scheduler.refreshQuotesAfternoon();

        verify(myHoldingService).refreshQuotesForCodes(
                List.of("600000", "000001", "600519", "300750", "000858", "002594"), false);
        verify(portfolioService, org.mockito.Mockito.times(2)).snapshotAll();
        verify(watchlistService, never()).refreshQuotes(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(portfolioService, never()).refreshQuotesAll(false);
    }

    @Test
    void afternoonRefreshSkipsWhenCloseBundleIsRunning() {
        IConfigService configService = mock(IConfigService.class);
        IDataSyncJobService dataSyncJobService = mock(IDataSyncJobService.class);
        ApexUserAuthService userAuthService = mock(ApexUserAuthService.class);
        when(configService.getString("auto_sync_enabled", "false")).thenReturn("true");
        when(dataSyncJobService.isTaskRunning("CLOSE_BUNDLE")).thenReturn(true);
        DataSyncScheduler scheduler = new DataSyncScheduler();
        ReflectionTestUtils.setField(scheduler, "configService", configService);
        ReflectionTestUtils.setField(scheduler, "dataSyncJobService", dataSyncJobService);
        ReflectionTestUtils.setField(scheduler, "userAuthService", userAuthService);

        scheduler.refreshQuotesAfternoon();

        verifyNoInteractions(userAuthService);
    }

    @Test
    void afternoonRefreshStillSnapshotsWhenSharedQuoteRefreshFails() {
        IConfigService configService = mock(IConfigService.class);
        IDataSyncJobService dataSyncJobService = mock(IDataSyncJobService.class);
        IWatchlistService watchlistService = mock(IWatchlistService.class);
        IMyHoldingService myHoldingService = mock(IMyHoldingService.class);
        IPortfolioService portfolioService = mock(IPortfolioService.class);
        ApexUserAuthService userAuthService = mock(ApexUserAuthService.class);
        ApexUserContext userContext = new ApexUserContext();
        when(configService.getString("auto_sync_enabled", "false")).thenReturn("true");
        when(configService.getString("auto_sync_group", "我的自选")).thenReturn("我的自选");
        when(dataSyncJobService.isTaskRunning("CLOSE_BUNDLE")).thenReturn(false);
        when(userAuthService.listEnabledUserIds()).thenReturn(List.of(11L));
        when(watchlistService.listWatchlistCodes("我的自选")).thenReturn(List.of("600000"));
        when(myHoldingService.listHoldingCodes()).thenReturn(List.of());
        when(portfolioService.listActiveHoldingCodes()).thenReturn(List.of());
        when(myHoldingService.refreshQuotesForCodes(List.of("600000"), false))
                .thenThrow(new IllegalStateException("行情源不可用"));
        when(portfolioService.snapshotAll()).thenReturn(1);
        DataSyncScheduler scheduler = new DataSyncScheduler();
        ReflectionTestUtils.setField(scheduler, "configService", configService);
        ReflectionTestUtils.setField(scheduler, "dataSyncJobService", dataSyncJobService);
        ReflectionTestUtils.setField(scheduler, "watchlistService", watchlistService);
        ReflectionTestUtils.setField(scheduler, "myHoldingService", myHoldingService);
        ReflectionTestUtils.setField(scheduler, "portfolioService", portfolioService);
        ReflectionTestUtils.setField(scheduler, "userAuthService", userAuthService);
        ReflectionTestUtils.setField(scheduler, "userContext", userContext);

        scheduler.refreshQuotesAfternoon();

        verify(portfolioService).snapshotAll();
    }

    @Test
    void skipsFocusQuoteRefreshOutsideTradingSession() {
        IConfigService configService = mock(IConfigService.class);
        IMyHoldingService myHoldingService = mock(IMyHoldingService.class);
        ApexUserAuthService userAuthService = mock(ApexUserAuthService.class);
        DataSyncScheduler scheduler = new DataSyncScheduler();
        ReflectionTestUtils.setField(scheduler, "configService", configService);
        ReflectionTestUtils.setField(scheduler, "myHoldingService", myHoldingService);
        ReflectionTestUtils.setField(scheduler, "userAuthService", userAuthService);
        when(configService.getString("auto_sync_enabled", "false")).thenReturn("true");

        scheduler.refreshFocusQuotes(LocalDate.of(2026, 8, 13), LocalTime.of(12, 0));

        verifyNoInteractions(myHoldingService, userAuthService);
    }

    @Test
    void skipsFocusQuoteRefreshOnNonTradingDay() {
        IConfigService configService = mock(IConfigService.class);
        IMyHoldingService myHoldingService = mock(IMyHoldingService.class);
        DataSyncScheduler scheduler = new DataSyncScheduler();
        ReflectionTestUtils.setField(scheduler, "configService", configService);
        ReflectionTestUtils.setField(scheduler, "myHoldingService", myHoldingService);
        when(configService.getString("auto_sync_enabled", "false")).thenReturn("true");

        scheduler.refreshFocusQuotes(LocalDate.of(2026, 8, 15), LocalTime.of(10, 0));

        verifyNoInteractions(myHoldingService);
    }

    @Test
    void skipsFocusQuoteRefreshWhenAutomaticSyncIsDisabled() {
        IConfigService configService = mock(IConfigService.class);
        IMyHoldingService myHoldingService = mock(IMyHoldingService.class);
        ApexUserAuthService userAuthService = mock(ApexUserAuthService.class);
        DataSyncScheduler scheduler = new DataSyncScheduler();
        ReflectionTestUtils.setField(scheduler, "configService", configService);
        ReflectionTestUtils.setField(scheduler, "myHoldingService", myHoldingService);
        ReflectionTestUtils.setField(scheduler, "userAuthService", userAuthService);
        when(configService.getString("auto_sync_enabled", "false")).thenReturn("false");

        scheduler.refreshFocusQuotes(LocalDate.of(2026, 8, 13), LocalTime.of(10, 0));

        verifyNoInteractions(myHoldingService, userAuthService);
    }

    @Test
    void schedulesObservePoolRefreshDuringTradingSessions() throws Exception {
        Method method = DataSyncScheduler.class.getMethod("refreshObservePoolIntraday");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertEquals("0 5 10,11,14,15 * * MON-FRI", scheduled.cron());
        assertEquals("Asia/Shanghai", scheduled.zone());
    }

    @Test
    void refreshesObservePoolForEachEnabledUserAndRestoresContext() {
        IObservePoolService observePoolService = mock(IObservePoolService.class);
        ApexUserAuthService userAuthService = mock(ApexUserAuthService.class);
        ApexUserContext userContext = new ApexUserContext();
        List<Long> observedUserIds = new ArrayList<>();
        when(userAuthService.listEnabledUserIds()).thenReturn(List.of(11L, 22L));
        when(observePoolService.refresh()).thenAnswer(invocation -> {
            observedUserIds.add(userContext.currentUserId());
            return Map.of();
        });
        DataSyncScheduler scheduler = new DataSyncScheduler();
        ReflectionTestUtils.setField(scheduler, "observePoolService", observePoolService);
        ReflectionTestUtils.setField(scheduler, "userAuthService", userAuthService);
        ReflectionTestUtils.setField(scheduler, "userContext", userContext);

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(99L);

            scheduler.refreshObservePool(LocalDate.of(2026, 8, 13));

            assertEquals(List.of(11L, 22L), observedUserIds);
            assertEquals(99L, userContext.currentUserId());
        }
    }

    @Test
    void skipsObservePoolRefreshOnNonTradingDay() {
        IObservePoolService observePoolService = mock(IObservePoolService.class);
        DataSyncScheduler scheduler = new DataSyncScheduler();
        ReflectionTestUtils.setField(scheduler, "observePoolService", observePoolService);

        scheduler.refreshObservePool(LocalDate.of(2026, 8, 15));

        verify(observePoolService, never()).refresh();
    }

    @Test
    void closeBundleUsesTrackedSyncJob() {
        IConfigService configService = mock(IConfigService.class);
        IDataSyncJobService dataSyncJobService = mock(IDataSyncJobService.class);
        DataSyncScheduler scheduler = new DataSyncScheduler();
        ReflectionTestUtils.setField(scheduler, "configService", configService);
        ReflectionTestUtils.setField(scheduler, "dataSyncJobService", dataSyncJobService);
        when(configService.getString("auto_sync_enabled", "false")).thenReturn("true");
        when(dataSyncJobService.startSystemTask(org.mockito.ArgumentMatchers.any())).thenReturn(
                SyncJobResp.builder().id(301L).status("PENDING").build());

        try (MockedStatic<TradingCalendar> tradingCalendar = mockStatic(TradingCalendar.class)) {
            tradingCalendar.when(() -> TradingCalendar.isTradingDay(LocalDate.now())).thenReturn(true);

            scheduler.closeBundleAfternoon();

            verify(dataSyncJobService).startSystemTask(org.mockito.ArgumentMatchers.argThat(request ->
                    "CLOSE_BUNDLE".equals(request.getTaskType())
                            && "INDUSTRY,CONCEPT,THEME".equals(request.getTypes())));
        }
    }

    @Test
    void schedulesAsiaPacificIndexSyncAfterJapanAndKoreaOpen() throws Exception {
        Scheduled scheduled = DataSyncScheduler.class.getMethod("syncAsiaPacificMorning")
                .getAnnotation(Scheduled.class);

        assertEquals("0 5 8 * * MON-FRI", scheduled.cron());
        assertEquals("Asia/Shanghai", scheduled.zone());
    }

    @Test
    void syncsOnlyJapanAndKoreaIndexesInTheMorning() {
        IConfigService configService = mock(IConfigService.class);
        IDataSyncJobService dataSyncJobService = mock(IDataSyncJobService.class);
        DataSyncScheduler scheduler = new DataSyncScheduler();
        ReflectionTestUtils.setField(scheduler, "configService", configService);
        ReflectionTestUtils.setField(scheduler, "dataSyncJobService", dataSyncJobService);
        when(configService.getString("auto_sync_enabled", "false")).thenReturn("true");
        when(dataSyncJobService.isTaskRunning("INDEX")).thenReturn(false);
        when(dataSyncJobService.startSystemTask(org.mockito.ArgumentMatchers.any())).thenReturn(
                SyncJobResp.builder().id(305L).status("PENDING").build());

        scheduler.syncAsiaPacificMorning();

        verify(dataSyncJobService).startSystemTask(org.mockito.ArgumentMatchers.argThat(request ->
                "INDEX".equals(request.getTaskType())
                        && "JP_N225,KR_KOSPI".equals(request.getCodes())));
    }

    @Test
    void skipsAsiaPacificIndexSyncWhenIndexJobIsRunning() {
        IConfigService configService = mock(IConfigService.class);
        IDataSyncJobService dataSyncJobService = mock(IDataSyncJobService.class);
        DataSyncScheduler scheduler = new DataSyncScheduler();
        ReflectionTestUtils.setField(scheduler, "configService", configService);
        ReflectionTestUtils.setField(scheduler, "dataSyncJobService", dataSyncJobService);
        when(configService.getString("auto_sync_enabled", "false")).thenReturn("true");
        when(dataSyncJobService.isTaskRunning("INDEX")).thenReturn(true);

        scheduler.syncAsiaPacificMorning();

        verify(dataSyncJobService, never()).startSystemTask(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void intradayHotRefreshUsesTrackedSyncJob() {
        IConfigService configService = mock(IConfigService.class);
        IDataSyncJobService dataSyncJobService = mock(IDataSyncJobService.class);
        DataSyncScheduler scheduler = new DataSyncScheduler();
        ReflectionTestUtils.setField(scheduler, "configService", configService);
        ReflectionTestUtils.setField(scheduler, "dataSyncJobService", dataSyncJobService);
        when(configService.getString("auto_sync_enabled", "false")).thenReturn("true");
        when(dataSyncJobService.startSystemTask(org.mockito.ArgumentMatchers.any())).thenReturn(
                SyncJobResp.builder().id(303L).status("PENDING").build());

        scheduler.refreshHotIntraday();

        verify(dataSyncJobService).startSystemTask(org.mockito.ArgumentMatchers.argThat(request ->
                "HOT".equals(request.getTaskType())
                        && "eastmoney,baidu".equals(request.getSources())
                        && Integer.valueOf(40).equals(request.getLimit())));
    }

    @Test
    void intradaySectorRefreshUsesTrackedSyncJob() {
        IConfigService configService = mock(IConfigService.class);
        IDataSyncJobService dataSyncJobService = mock(IDataSyncJobService.class);
        DataSyncScheduler scheduler = new DataSyncScheduler();
        ReflectionTestUtils.setField(scheduler, "configService", configService);
        ReflectionTestUtils.setField(scheduler, "dataSyncJobService", dataSyncJobService);
        ReflectionTestUtils.setField(scheduler, "clock", java.time.Clock.fixed(
                java.time.ZonedDateTime.of(2026, 8, 18, 10, 20, 0, 0,
                        java.time.ZoneId.of("Asia/Shanghai")).toInstant(),
                java.time.ZoneId.of("Asia/Shanghai")));
        when(configService.getString("auto_sync_enabled", "false")).thenReturn("true");
        when(dataSyncJobService.startSystemTask(org.mockito.ArgumentMatchers.any())).thenReturn(
                SyncJobResp.builder().id(304L).status("PENDING").build());

        scheduler.refreshSectorIntraday();

        verify(dataSyncJobService).startSystemTask(org.mockito.ArgumentMatchers.argThat(request ->
                "SECTOR_QUOTE".equals(request.getTaskType())
                        && "INDUSTRY,CONCEPT,THEME".equals(request.getTypes())));
    }

    @Test
    void sectorRefreshRunsEveryTenMinutesDuringTradingHours() throws Exception {
        Method method = DataSyncScheduler.class.getMethod("refreshSectorIntraday");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertEquals("0 */10 9-11,13-15 * * MON-FRI", scheduled.cron());
        assertEquals("Asia/Shanghai", scheduled.zone());
    }

    @Test
    void intradaySectorRefreshSkipsOutsideTradingSession() {
        IConfigService configService = mock(IConfigService.class);
        IDataSyncJobService dataSyncJobService = mock(IDataSyncJobService.class);
        DataSyncScheduler scheduler = new DataSyncScheduler();
        ReflectionTestUtils.setField(scheduler, "configService", configService);
        ReflectionTestUtils.setField(scheduler, "dataSyncJobService", dataSyncJobService);
        ReflectionTestUtils.setField(scheduler, "clock", java.time.Clock.fixed(
                java.time.ZonedDateTime.of(2026, 8, 18, 12, 0, 0, 0,
                        java.time.ZoneId.of("Asia/Shanghai")).toInstant(),
                java.time.ZoneId.of("Asia/Shanghai")));

        scheduler.refreshSectorIntraday();

        verifyNoInteractions(configService, dataSyncJobService);
    }

    @Test
    void schedulesNightlyRepairAtTwoTen() throws Exception {
        Method method = DataSyncScheduler.class.getMethod("repairMarketDataNightly");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertEquals("0 10 2 * * *", scheduled.cron());
        assertEquals("Asia/Shanghai", scheduled.zone());
    }

    @Test
    void nightlyRepairUsesPreviousAvailableTradingDay() {
        IConfigService configService = mock(IConfigService.class);
        IDataSyncJobService dataSyncJobService = mock(IDataSyncJobService.class);
        DataSyncScheduler scheduler = new DataSyncScheduler();
        ReflectionTestUtils.setField(scheduler, "configService", configService);
        ReflectionTestUtils.setField(scheduler, "dataSyncJobService", dataSyncJobService);
        when(configService.getString("auto_sync_enabled", "false")).thenReturn("true");
        when(dataSyncJobService.startSystemTask(org.mockito.ArgumentMatchers.any())).thenReturn(
                SyncJobResp.builder().id(302L).status("PENDING").build());

        scheduler.repairMarketDataNightly(LocalDate.of(2026, 8, 23));

        verify(dataSyncJobService).startSystemTask(org.mockito.ArgumentMatchers.argThat(request ->
                "NIGHTLY_REPAIR".equals(request.getTaskType())
                        && "2026-08-21".equals(request.getExpectedDate())));
    }

    @Test
    void skipsNightlyRepairWhenAutomaticSyncIsDisabled() {
        IConfigService configService = mock(IConfigService.class);
        IDataSyncJobService dataSyncJobService = mock(IDataSyncJobService.class);
        DataSyncScheduler scheduler = new DataSyncScheduler();
        ReflectionTestUtils.setField(scheduler, "configService", configService);
        ReflectionTestUtils.setField(scheduler, "dataSyncJobService", dataSyncJobService);
        when(configService.getString("auto_sync_enabled", "false")).thenReturn("false");

        scheduler.repairMarketDataNightly(LocalDate.of(2026, 8, 18));

        verify(dataSyncJobService, never()).startSystemTask(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void schedulesCapitalFlowAtRecommendedTimes() throws Exception {
        assertEquals("0 5,35 9-14 * * MON-FRI",
                DataSyncScheduler.class.getMethod("refreshCapitalFlowIntraday")
                        .getAnnotation(Scheduled.class).cron());
        assertEquals("0 5 15 * * MON-FRI",
                DataSyncScheduler.class.getMethod("refreshCapitalFlowIntradayClose")
                        .getAnnotation(Scheduled.class).cron());
        assertEquals("0 10 15 * * MON-FRI",
                DataSyncScheduler.class.getMethod("refreshCapitalFlowClose")
                        .getAnnotation(Scheduled.class).cron());
        assertEquals("0 30 17 * * MON-FRI",
                DataSyncScheduler.class.getMethod("refreshDragonTiger")
                        .getAnnotation(Scheduled.class).cron());
        assertEquals("0 20 18 * * MON-FRI",
                DataSyncScheduler.class.getMethod("refreshCapitalFlowAll")
                        .getAnnotation(Scheduled.class).cron());
    }

    @Test
    void capitalFlowScheduleUsesModesAndSkipsDuplicateTasks() {
        IConfigService configService = mock(IConfigService.class);
        IDataSyncJobService dataSyncJobService = mock(IDataSyncJobService.class);
        DataSyncScheduler scheduler = new DataSyncScheduler();
        ReflectionTestUtils.setField(scheduler, "configService", configService);
        ReflectionTestUtils.setField(scheduler, "dataSyncJobService", dataSyncJobService);
        when(configService.getString("auto_sync_enabled", "false")).thenReturn("true");
        when(dataSyncJobService.isTaskRunning("CAPITAL_FLOW")).thenReturn(false);
        when(dataSyncJobService.isTaskRunning("DRAGON_TIGER")).thenReturn(true);
        when(dataSyncJobService.isTaskRunning("SECTOR_QUOTE")).thenReturn(false);
        when(dataSyncJobService.startSystemTask(org.mockito.ArgumentMatchers.any())).thenReturn(
                SyncJobResp.builder().id(401L).status("PENDING").build());

        scheduler.refreshCapitalFlow(LocalDate.of(2026, 8, 20), "flow");
        scheduler.refreshCapitalFlow(LocalDate.of(2026, 8, 20), "lhb");

        verify(dataSyncJobService).startSystemTask(org.mockito.ArgumentMatchers.argThat(request ->
                "CAPITAL_FLOW".equals(request.getTaskType()) && "flow".equals(request.getMode())));
        verify(dataSyncJobService).startSystemTask(org.mockito.ArgumentMatchers.argThat(request ->
                "SECTOR_QUOTE".equals(request.getTaskType())
                        && "INDUSTRY,CONCEPT,THEME".equals(request.getTypes())));
        verify(dataSyncJobService, org.mockito.Mockito.times(2))
                .startSystemTask(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void intradayCapitalFlowFiltersAuctionAndLunchBreakCronTicks() {
        IConfigService configService = mock(IConfigService.class);
        IDataSyncJobService dataSyncJobService = mock(IDataSyncJobService.class);
        DataSyncScheduler scheduler = new DataSyncScheduler();
        ReflectionTestUtils.setField(scheduler, "configService", configService);
        ReflectionTestUtils.setField(scheduler, "dataSyncJobService", dataSyncJobService);
        ReflectionTestUtils.setField(scheduler, "clock", java.time.Clock.fixed(
                java.time.ZonedDateTime.of(2026, 8, 20, 9, 5, 0, 0,
                        java.time.ZoneId.of("Asia/Shanghai")).toInstant(),
                java.time.ZoneId.of("Asia/Shanghai")));

        scheduler.refreshCapitalFlowIntraday();

        ReflectionTestUtils.setField(scheduler, "clock", java.time.Clock.fixed(
                java.time.ZonedDateTime.of(2026, 8, 20, 12, 35, 0, 0,
                        java.time.ZoneId.of("Asia/Shanghai")).toInstant(),
                java.time.ZoneId.of("Asia/Shanghai")));
        scheduler.refreshCapitalFlowIntraday();

        verifyNoInteractions(configService, dataSyncJobService);
    }

    @Test
    void intradayCapitalFlowKeepsElevenThirtyFiveClosingSnapshot() {
        IConfigService configService = mock(IConfigService.class);
        IDataSyncJobService dataSyncJobService = mock(IDataSyncJobService.class);
        DataSyncScheduler scheduler = new DataSyncScheduler();
        ReflectionTestUtils.setField(scheduler, "configService", configService);
        ReflectionTestUtils.setField(scheduler, "dataSyncJobService", dataSyncJobService);
        ReflectionTestUtils.setField(scheduler, "clock", java.time.Clock.fixed(
                java.time.ZonedDateTime.of(2026, 8, 20, 11, 35, 0, 0,
                        java.time.ZoneId.of("Asia/Shanghai")).toInstant(),
                java.time.ZoneId.of("Asia/Shanghai")));
        when(configService.getString("auto_sync_enabled", "false")).thenReturn("true");
        when(dataSyncJobService.isTaskRunning(org.mockito.ArgumentMatchers.any())).thenReturn(false);
        when(dataSyncJobService.startSystemTask(org.mockito.ArgumentMatchers.any())).thenReturn(
                SyncJobResp.builder().id(402L).status("PENDING").build());

        scheduler.refreshCapitalFlowIntraday();

        verify(dataSyncJobService).startSystemTask(org.mockito.ArgumentMatchers.argThat(request ->
                "CAPITAL_FLOW".equals(request.getTaskType()) && "stock".equals(request.getMode())));
    }

    @Test
    void capitalFlowScheduleSkipsDisabledOrNonTradingDay() {
        IConfigService configService = mock(IConfigService.class);
        IDataSyncJobService dataSyncJobService = mock(IDataSyncJobService.class);
        DataSyncScheduler scheduler = new DataSyncScheduler();
        ReflectionTestUtils.setField(scheduler, "configService", configService);
        ReflectionTestUtils.setField(scheduler, "dataSyncJobService", dataSyncJobService);
        when(configService.getString("auto_sync_enabled", "false")).thenReturn("false", "true");

        scheduler.refreshCapitalFlow(LocalDate.of(2026, 8, 20), "all");
        scheduler.refreshCapitalFlow(LocalDate.of(2026, 8, 22), "all");

        verify(dataSyncJobService, never()).startSystemTask(org.mockito.ArgumentMatchers.any());
    }
}
