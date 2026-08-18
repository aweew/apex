package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.cache.RedisCacheService;
import com.awe.apex.quant.domain.dto.MarketBriefingResp;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Test
    void shouldExplainStanceWithCurrentMarketFactsInsteadOfScoringLabels() {
        MarketBriefingServiceImpl service = new MarketBriefingServiceImpl();
        MarketBriefingResp briefing = MarketBriefingResp.builder()
                .breadthUp(2070)
                .breadthDown(3149)
                .limitUpCount(81)
                .limitDownCount(6)
                .volumeLabel("放量 +130.46%")
                .build();

        String stanceReason = ReflectionTestUtils.invokeMethod(service, "buildStanceReason", briefing,
                new BigDecimal("0.82"), new BigDecimal("0.19"));

        assertTrue(stanceReason.contains("全A均价+0.82%"));
        assertTrue(stanceReason.contains("上涨2070家、下跌3149家，广度偏弱"));
        assertTrue(stanceReason.contains("涨停81家、跌停6家，短线情绪活跃"));
        assertTrue(stanceReason.contains("量能放量 +130.46%"));
        assertTrue(!stanceReason.contains("综合大盘、趋势、量能、风格、广度与涨停情绪"));
    }
}
