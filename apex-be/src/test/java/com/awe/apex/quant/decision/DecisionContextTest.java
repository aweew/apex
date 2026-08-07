package com.awe.apex.quant.decision;

import com.awe.apex.quant.domain.dto.DecisionRunReq;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DecisionContextTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-07T07:30:00Z"), ZONE);

    @Test
    void emptyRequestDefaultsToLiveAtCurrentTime() {
        DecisionContext context = DecisionContext.from(null, CLOCK);

        assertEquals(DecisionMode.LIVE, context.getMode());
        assertEquals(DecisionDataPolicy.LATEST_AVAILABLE, context.getDataPolicy());
        assertEquals(LocalDate.of(2026, 8, 7), context.getActionDate());
        assertEquals(LocalDateTime.of(2026, 8, 7, 15, 30), context.getAsOfTime());
    }

    @Test
    void legacyPastDateDefaultsToReplayWithEndOfDayCutoff() {
        DecisionRunReq request = new DecisionRunReq();
        request.setDate(LocalDate.of(2026, 7, 31));

        DecisionContext context = DecisionContext.from(request, CLOCK);

        assertEquals(DecisionMode.REPLAY, context.getMode());
        assertEquals(DecisionDataPolicy.POINT_IN_TIME, context.getDataPolicy());
        assertEquals(LocalDate.of(2026, 7, 31), context.getActionDate());
        assertEquals(LocalDateTime.of(2026, 7, 31, 23, 59, 59), context.getAsOfTime());
    }

    @Test
    void replayAcceptsExplicitEndOfDayCutoff() {
        DecisionRunReq request = new DecisionRunReq();
        request.setMode(DecisionMode.REPLAY);
        request.setDate(LocalDate.of(2026, 7, 31));
        request.setAsOfTime(LocalDateTime.of(2026, 7, 31, 23, 59, 59));

        DecisionContext context = DecisionContext.from(request, CLOCK);

        assertEquals(request.getAsOfTime(), context.getAsOfTime());
    }

    @Test
    void replayRejectsIntradayCutoffUntilIntradayDataIsSupported() {
        DecisionRunReq request = new DecisionRunReq();
        request.setMode(DecisionMode.REPLAY);
        request.setDate(LocalDate.of(2026, 7, 31));
        request.setAsOfTime(LocalDateTime.of(2026, 7, 31, 10, 0));

        assertThrows(IllegalArgumentException.class, () -> DecisionContext.from(request, CLOCK));
    }

    @Test
    void replayRejectsCutoffOutsideActionDate() {
        DecisionRunReq request = new DecisionRunReq();
        request.setMode(DecisionMode.REPLAY);
        request.setDate(LocalDate.of(2026, 7, 31));
        request.setAsOfTime(LocalDateTime.of(2026, 8, 1, 9, 0));

        assertThrows(IllegalArgumentException.class, () -> DecisionContext.from(request, CLOCK));
    }

    @Test
    void replayRejectsFutureActionDate() {
        DecisionRunReq request = new DecisionRunReq();
        request.setMode(DecisionMode.REPLAY);
        request.setDate(LocalDate.of(2026, 8, 8));

        assertThrows(IllegalArgumentException.class, () -> DecisionContext.from(request, CLOCK));
    }

    @Test
    void explicitReplayRequiresActionDate() {
        DecisionRunReq request = new DecisionRunReq();
        request.setMode(DecisionMode.REPLAY);

        assertThrows(IllegalArgumentException.class, () -> DecisionContext.from(request, CLOCK));
    }

    @Test
    void explicitLiveRejectsHistoricalActionDate() {
        DecisionRunReq request = new DecisionRunReq();
        request.setMode(DecisionMode.LIVE);
        request.setDate(LocalDate.of(2026, 7, 31));

        assertThrows(IllegalArgumentException.class, () -> DecisionContext.from(request, CLOCK));
    }

    @Test
    void liveRejectsFutureCutoff() {
        DecisionRunReq request = new DecisionRunReq();
        request.setAsOfTime(LocalDateTime.of(2026, 8, 7, 16, 0));

        assertThrows(IllegalArgumentException.class, () -> DecisionContext.from(request, CLOCK));
    }
}
