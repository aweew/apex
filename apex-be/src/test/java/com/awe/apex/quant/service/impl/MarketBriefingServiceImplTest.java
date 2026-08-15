package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.cache.RedisCacheService;
import com.awe.apex.quant.domain.dto.MarketBriefingResp;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class MarketBriefingServiceImplTest {

    @Test
    void shouldReturnMemoryBriefingWithoutLoadingExternalQuotes() {
        MarketBriefingServiceImpl service = new MarketBriefingServiceImpl();
        RedisCacheService redisCacheService = mock(RedisCacheService.class);
        MarketBriefingResp briefing = MarketBriefingResp.builder()
                .asOf(LocalDate.of(2026, 8, 15))
                .build();
        ReflectionTestUtils.setField(service, "redisCacheService", redisCacheService);
        ReflectionTestUtils.setField(service, "cachedBriefing", briefing);
        ReflectionTestUtils.setField(service, "cachedAtMs", System.currentTimeMillis());

        MarketBriefingResp actual = service.loadCachedBriefing();

        assertSame(briefing, actual);
        verifyNoInteractions(redisCacheService);
    }
}
