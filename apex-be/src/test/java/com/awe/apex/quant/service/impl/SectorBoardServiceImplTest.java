package com.awe.apex.quant.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SectorBoardServiceImplTest {

    @Test
    void shouldUseLatestAvailableSnapshotWhenRequestedDateHasNotBeenSynced() {
        SectorBoardServiceImpl service = new SectorBoardServiceImpl();
        LocalDate actualTradeDate = LocalDate.of(2026, 8, 10);
        LocalDate resolvedDate = ReflectionTestUtils.invokeMethod(
                service,
                "resolveTradeDate",
                "2026-08-11",
                List.of(actualTradeDate, LocalDate.of(2026, 8, 7)),
                actualTradeDate
        );

        assertEquals(actualTradeDate, resolvedDate);
    }
}
