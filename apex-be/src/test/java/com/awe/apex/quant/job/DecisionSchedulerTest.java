package com.awe.apex.quant.job;

import com.awe.apex.quant.domain.dto.SyncStartReq;
import com.awe.apex.quant.service.IDataSyncJobService;
import com.awe.apex.quant.service.IDecisionOutcomeService;
import com.awe.apex.quant.service.ApexUserAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DecisionSchedulerTest {

    private final IDataSyncJobService dataSyncJobService = mock(IDataSyncJobService.class);
    private final IDecisionOutcomeService decisionOutcomeService = mock(IDecisionOutcomeService.class);
    private final ApexUserAuthService userAuthService = mock(ApexUserAuthService.class);
    private final DecisionScheduler scheduler = new DecisionScheduler();

    @Test
    void schedulesThreeExpectedTradingSessionTimes() throws Exception {
        Method preMarketSession = DecisionScheduler.class.getMethod("runPreMarketSession");
        Method mainSessions = DecisionScheduler.class.getMethod("runMainSessions");
        Method closingSession = DecisionScheduler.class.getMethod("runClosingSession");
        Method outcomeCalculation = DecisionScheduler.class.getMethod("calculateDecisionOutcomes");

        Scheduled preMarketSchedule = preMarketSession.getAnnotation(Scheduled.class);
        Scheduled mainSchedule = mainSessions.getAnnotation(Scheduled.class);
        Scheduled closingSchedule = closingSession.getAnnotation(Scheduled.class);
        Scheduled outcomeSchedule = outcomeCalculation.getAnnotation(Scheduled.class);
        assertEquals("0 50 6 * * MON-FRI", preMarketSchedule.cron());
        assertEquals("0 40 11,15 * * MON-FRI", mainSchedule.cron());
        assertEquals("0 10 16 * * MON-FRI", closingSchedule.cron());
        assertEquals("0 30 18 * * MON-FRI", outcomeSchedule.cron());
        assertEquals("Asia/Shanghai", preMarketSchedule.zone());
        assertEquals("Asia/Shanghai", mainSchedule.zone());
        assertEquals("Asia/Shanghai", closingSchedule.zone());
        assertEquals("Asia/Shanghai", outcomeSchedule.zone());
    }

    @Test
    void submitsDecisionOnTradingDay() {
        ReflectionTestUtils.setField(scheduler, "dataSyncJobService", dataSyncJobService);
        ReflectionTestUtils.setField(scheduler, "userAuthService", userAuthService);
        when(userAuthService.listEnabledUserIds()).thenReturn(java.util.List.of(7L, 8L));

        scheduler.runScheduledDecision(LocalDate.of(2026, 8, 12));

        verify(dataSyncJobService).startForUser(argThat(this::isDecisionTask), org.mockito.ArgumentMatchers.eq(7L));
        verify(dataSyncJobService).startForUser(argThat(this::isDecisionTask), org.mockito.ArgumentMatchers.eq(8L));
    }

    @Test
    void skipsDecisionOnNonTradingDay() {
        ReflectionTestUtils.setField(scheduler, "dataSyncJobService", dataSyncJobService);
        ReflectionTestUtils.setField(scheduler, "userAuthService", userAuthService);

        scheduler.runScheduledDecision(LocalDate.of(2026, 8, 15));

        verify(dataSyncJobService, never()).startForUser(argThat(this::isDecisionTask),
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void oneUsersFailureDoesNotSkipRemainingUsers() {
        ReflectionTestUtils.setField(scheduler, "dataSyncJobService", dataSyncJobService);
        ReflectionTestUtils.setField(scheduler, "userAuthService", userAuthService);
        when(userAuthService.listEnabledUserIds()).thenReturn(java.util.List.of(7L, 8L));
        doThrow(new IllegalStateException("busy")).when(dataSyncJobService)
                .startForUser(argThat(this::isDecisionTask), org.mockito.ArgumentMatchers.eq(7L));

        scheduler.runScheduledDecision(LocalDate.of(2026, 8, 12));

        verify(dataSyncJobService).startForUser(argThat(this::isDecisionTask), org.mockito.ArgumentMatchers.eq(8L));
    }

    @Test
    void calculatesPendingDecisionOutcomes() {
        ReflectionTestUtils.setField(scheduler, "decisionOutcomeService", decisionOutcomeService);

        scheduler.calculateDecisionOutcomes();

        verify(decisionOutcomeService).calculatePendingOutcomes();
    }

    private boolean isDecisionTask(SyncStartReq request) {
        return "DECISION".equals(request.getTaskType());
    }
}
