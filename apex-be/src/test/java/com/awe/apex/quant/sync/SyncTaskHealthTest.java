package com.awe.apex.quant.sync;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 同步任务健康色
 */
class SyncTaskHealthTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 8, 2, 12, 0);

    @Test
    void runningIsRunning() {
        assertEquals("RUNNING", SyncTaskHealth.resolve(true, now.minusHours(1), false, now));
    }

    @Test
    void failedIsRed() {
        assertEquals("RED", SyncTaskHealth.resolve(false, now.minusDays(1), true, now));
    }

    @Test
    void neverSuccessIsRed() {
        assertEquals("RED", SyncTaskHealth.resolve(false, null, false, now));
    }

    @Test
    void within3DaysIsGreen() {
        assertEquals("GREEN", SyncTaskHealth.resolve(false, now.minusDays(2), false, now));
    }

    @Test
    void between3And7IsYellow() {
        assertEquals("YELLOW", SyncTaskHealth.resolve(false, now.minusDays(4), false, now));
    }

    @Test
    void over7DaysIsRed() {
        assertEquals("RED", SyncTaskHealth.resolve(false, now.minusDays(8), false, now));
    }
}
