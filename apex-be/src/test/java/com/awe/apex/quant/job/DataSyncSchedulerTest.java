package com.awe.apex.quant.job;

import com.awe.apex.quant.service.IObservePoolService;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DataSyncSchedulerTest {

    @Test
    void schedulesObservePoolRefreshDuringTradingSessions() throws Exception {
        Method method = DataSyncScheduler.class.getMethod("refreshObservePoolIntraday");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertEquals("0 5 10,11,14,15 * * MON-FRI", scheduled.cron());
        assertEquals("Asia/Shanghai", scheduled.zone());
    }

    @Test
    void refreshesObservePoolOnTradingDay() {
        IObservePoolService observePoolService = mock(IObservePoolService.class);
        DataSyncScheduler scheduler = new DataSyncScheduler();
        ReflectionTestUtils.setField(scheduler, "observePoolService", observePoolService);

        scheduler.refreshObservePool(LocalDate.of(2026, 8, 13));

        verify(observePoolService).refresh();
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
