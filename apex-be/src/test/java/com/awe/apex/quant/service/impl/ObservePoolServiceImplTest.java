package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.domain.entity.ObservePool;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ObservePoolServiceImplTest {

    @Test
    void shouldArchiveAutomaticItemWhenTargetReached() {
        ObservePool row = automaticRow("买入", LocalDateTime.of(2026, 8, 12, 16, 0));

        String reason = ObservePoolServiceImpl.autoArchiveReason(row, "HIT_TARGET", LocalDate.of(2026, 8, 13));

        assertEquals("自动归档：触及目标", reason);
    }

    @Test
    void shouldArchiveAutomaticBuyAfterFiveTradingDaysWithoutDecisionUpdate() {
        ObservePool row = automaticRow("买入", LocalDateTime.of(2026, 8, 6, 16, 0));

        String reason = ObservePoolServiceImpl.autoArchiveReason(row, "WATCHING", LocalDate.of(2026, 8, 13));

        assertEquals("自动归档：自动买入观察超过 5 个交易日未更新", reason);
    }

    @Test
    void shouldArchiveAutomaticMoodAfterTwoTradingDaysWithoutDecisionUpdate() {
        ObservePool row = automaticRow("情绪", LocalDateTime.of(2026, 8, 11, 16, 0));

        String reason = ObservePoolServiceImpl.autoArchiveReason(row, "WATCHING", LocalDate.of(2026, 8, 13));

        assertEquals("自动归档：自动情绪观察超过 2 个交易日未更新", reason);
    }

    @Test
    void shouldNotArchiveManualItem() {
        ObservePool row = automaticRow("买入", LocalDateTime.of(2026, 7, 1, 16, 0));
        row.setTags("手动,买入");

        String reason = ObservePoolServiceImpl.autoArchiveReason(row, "STOPPED", LocalDate.of(2026, 8, 13));

        assertNull(reason);
    }

    private ObservePool automaticRow(String type, LocalDateTime decisionUpdatedAt) {
        ObservePool row = new ObservePool();
        row.setTags("决策,自动," + type);
        row.setDecisionUpdatedAt(decisionUpdatedAt);
        return row;
    }
}
