package com.awe.apex.quant.job;

import com.awe.apex.quant.domain.dto.SyncStartReq;
import com.awe.apex.quant.service.IDataSyncJobService;
import com.awe.apex.quant.service.IDecisionOutcomeService;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DecisionSchedulerTest {

    private final IDataSyncJobService dataSyncJobService = mock(IDataSyncJobService.class);
    private final IDecisionOutcomeService decisionOutcomeService = mock(IDecisionOutcomeService.class);
    private final DecisionScheduler scheduler = new DecisionScheduler();

    @Test
    void schedulesThreeExpectedTradingSessionTimes() throws Exception {
        Method mainSessions = DecisionScheduler.class.getMethod("runMainSessions");
        Method closingSession = DecisionScheduler.class.getMethod("runClosingSession");
        Method outcomeCalculation = DecisionScheduler.class.getMethod("calculateDecisionOutcomes");

        Scheduled mainSchedule = mainSessions.getAnnotation(Scheduled.class);
        Scheduled closingSchedule = closingSession.getAnnotation(Scheduled.class);
        Scheduled outcomeSchedule = outcomeCalculation.getAnnotation(Scheduled.class);
        assertEquals("0 40 11,15 * * MON-FRI", mainSchedule.cron());
        assertEquals("0 10 16 * * MON-FRI", closingSchedule.cron());
        assertEquals("0 30 18 * * MON-FRI", outcomeSchedule.cron());
        assertEquals("Asia/Shanghai", mainSchedule.zone());
        assertEquals("Asia/Shanghai", closingSchedule.zone());
        assertEquals("Asia/Shanghai", outcomeSchedule.zone());
    }

    @Test
    void submitsDecisionOnTradingDay() {
        ReflectionTestUtils.setField(scheduler, "dataSyncJobService", dataSyncJobService);

        scheduler.runScheduledDecision(LocalDate.of(2026, 8, 12));

        verify(dataSyncJobService).start(argThat(this::isDecisionTask));
    }

    @Test
    void skipsDecisionOnNonTradingDay() {
        ReflectionTestUtils.setField(scheduler, "dataSyncJobService", dataSyncJobService);

        scheduler.runScheduledDecision(LocalDate.of(2026, 8, 15));

        verify(dataSyncJobService, never()).start(argThat(this::isDecisionTask));
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
