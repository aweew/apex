package com.awe.apex.quant.job;

import cn.dev33.satoken.stp.StpUtil;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.service.ApexUserAuthService;
import com.awe.apex.quant.service.IObservePoolService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataSyncSchedulerTest {

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
}
