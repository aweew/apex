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
    void schedulesPreMarketAndIntradayDecisionSessions() throws Exception {
        Method preMarketSession = DecisionScheduler.class.getMethod("runPreMarketSession");
        Method mainSessions = DecisionScheduler.class.getMethod("runMainSessions");
        Method outcomeCalculation = DecisionScheduler.class.getMethod("calculateDecisionOutcomes");

        Scheduled preMarketSchedule = preMarketSession.getAnnotation(Scheduled.class);
        Scheduled mainSchedule = mainSessions.getAnnotation(Scheduled.class);
        Scheduled outcomeSchedule = outcomeCalculation.getAnnotation(Scheduled.class);
        assertEquals("0 50 6 * * MON-FRI", preMarketSchedule.cron());
        assertEquals("0 40 11,15 * * MON-FRI", mainSchedule.cron());
        assertEquals("0 30 18 * * MON-FRI", outcomeSchedule.cron());
        assertEquals("Asia/Shanghai", preMarketSchedule.zone());
        assertEquals("Asia/Shanghai", mainSchedule.zone());
        assertEquals("Asia/Shanghai", outcomeSchedule.zone());
    }

    @Test
    void submitsOneSharedDecisionOnTradingDay() {
        ReflectionTestUtils.setField(scheduler, "dataSyncJobService", dataSyncJobService);

        scheduler.runScheduledDecision(LocalDate.of(2026, 8, 12));

        verify(dataSyncJobService).startSystemTask(argThat(this::isDecisionTask));
        verify(dataSyncJobService, never()).startForUser(argThat(this::isDecisionTask),
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void skipsDecisionOnNonTradingDay() {
        ReflectionTestUtils.setField(scheduler, "dataSyncJobService", dataSyncJobService);

        scheduler.runScheduledDecision(LocalDate.of(2026, 8, 15));

        verify(dataSyncJobService, never()).startSystemTask(argThat(this::isDecisionTask));
    }

    @Test
    void sharedDecisionSubmissionFailureDoesNotEscapeScheduler() {
        ReflectionTestUtils.setField(scheduler, "dataSyncJobService", dataSyncJobService);
        org.mockito.Mockito.doThrow(new IllegalStateException("busy")).when(dataSyncJobService)
                .startSystemTask(argThat(this::isDecisionTask));

        scheduler.runScheduledDecision(LocalDate.of(2026, 8, 12));

        verify(dataSyncJobService).startSystemTask(argThat(this::isDecisionTask));
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
