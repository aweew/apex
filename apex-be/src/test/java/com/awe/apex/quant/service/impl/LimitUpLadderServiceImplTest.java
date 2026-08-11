package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.domain.dto.MarketBriefingResp;
import com.awe.apex.quant.service.IMarketBriefingService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LimitUpLadderServiceImplTest {

    @Test
    void shouldUseLatestAvailableSnapshotWhenRequestedDateHasNotBeenSynced() {
        LimitUpLadderServiceImpl service = new LimitUpLadderServiceImpl();
        LocalDate actualTradeDate = LocalDate.of(2026, 8, 10);
        LocalDate resolvedDate = ReflectionTestUtils.invokeMethod(
                service,
                "resolveTradeDate",
                "2026-08-11",
                List.of(actualTradeDate, LocalDate.of(2026, 8, 7))
        );

        assertEquals(actualTradeDate, resolvedDate);
    }

    @Test
    void shouldUseMarketLimitUpCountForTheSameTradeDate() {
        LimitUpLadderServiceImpl service = new LimitUpLadderServiceImpl();
        IMarketBriefingService marketBriefingService = mock(IMarketBriefingService.class);
        LocalDate tradeDate = LocalDate.of(2026, 8, 11);
        when(marketBriefingService.briefing(false)).thenReturn(MarketBriefingResp.builder()
                .asOf(tradeDate)
                .limitUpCount(60)
                .build());
        ReflectionTestUtils.setField(service, "marketBriefingService", marketBriefingService);

        Integer totalCount = ReflectionTestUtils.invokeMethod(service, "resolveTotalCount", tradeDate, 58);

        assertEquals(60, totalCount);
    }

    @Test
    void shouldKeepPoolCountWhenMarketBriefingUsesAnotherTradeDate() {
        LimitUpLadderServiceImpl service = new LimitUpLadderServiceImpl();
        IMarketBriefingService marketBriefingService = mock(IMarketBriefingService.class);
        when(marketBriefingService.briefing(false)).thenReturn(MarketBriefingResp.builder()
                .asOf(LocalDate.of(2026, 8, 11))
                .limitUpCount(60)
                .build());
        ReflectionTestUtils.setField(service, "marketBriefingService", marketBriefingService);

        Integer totalCount = ReflectionTestUtils.invokeMethod(
                service,
                "resolveTotalCount",
                LocalDate.of(2026, 8, 8),
                42
        );

        assertEquals(42, totalCount);
    }
}
