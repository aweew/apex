package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.cache.RedisCacheService;
import com.awe.apex.quant.domain.dto.MarketBriefingResp;
import com.awe.apex.quant.domain.dto.MarketHotThemeItem;
import com.awe.apex.quant.domain.dto.MarketTipItem;
import com.awe.apex.quant.domain.dto.SectorBoardItem;
import com.awe.apex.quant.domain.entity.IndexBar;
import com.awe.apex.quant.service.ISectorBoardService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
    void shouldDeduplicateTipsLoadedFromMemoryCache() {
        MarketBriefingServiceImpl service = new MarketBriefingServiceImpl();
        RedisCacheService redisCacheService = mock(RedisCacheService.class);
        MarketTipItem stanceTip = MarketTipItem.builder()
                .level("info")
                .text("市场中性偏均衡：有信号再做，仓位中等、纪律优先。")
                .build();
        MarketBriefingResp briefing = MarketBriefingResp.builder()
                .tips(List.of(stanceTip, stanceTip, stanceTip))
                .build();
        ReflectionTestUtils.setField(service, "redisCacheService", redisCacheService);
        ReflectionTestUtils.setField(service, "cachedBriefing", briefing);
        ReflectionTestUtils.setField(service, "cachedAtMs", System.currentTimeMillis());

        MarketBriefingResp actual = service.loadCachedBriefing();

        assertSame(briefing, actual);
        assertEquals(1, actual.getTips().size());
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

    @Test
    void shouldKeepSectorCodeInHotThemeItems() {
        MarketBriefingServiceImpl service = new MarketBriefingServiceImpl();
        ISectorBoardService sectorBoardService = mock(ISectorBoardService.class);
        LocalDate tradeDate = LocalDate.of(2026, 8, 18);
        LocalDateTime syncedAt = tradeDate.atTime(10, 15);
        when(sectorBoardService.mainline(null, 30)).thenReturn(List.of(SectorBoardItem.builder()
                .code("BK1156")
                .name("机器人执行器")
                .boardType("CONCEPT")
                .pctChg(new BigDecimal("1.23"))
                .tradeDate(tradeDate)
                .syncedAt(syncedAt)
                .build()));
        ReflectionTestUtils.setField(service, "sectorBoardService", sectorBoardService);

        List<MarketHotThemeItem> themes = ReflectionTestUtils.invokeMethod(service, "loadHotThemeItems");

        assertEquals(1, themes.size());
        assertEquals("BK1156", themes.get(0).getCode());
        assertEquals("CONCEPT", themes.get(0).getBoardType());
        assertEquals(tradeDate, themes.get(0).getTradeDate());
        assertEquals(syncedAt, themes.get(0).getSyncedAt());
    }

    @Test
    void shouldFindNearestConfirmedShanghaiResistanceAboveCurrentPrice() {
        MarketBriefingServiceImpl service = new MarketBriefingServiceImpl();
        List<IndexBar> shanghaiBars = List.of(
                indexBar(1, "3100", "3090"),
                indexBar(2, "3120", "3110"),
                indexBar(3, "3150", "3140"),
                indexBar(4, "3300", "3260"),
                indexBar(5, "3200", "3180"),
                indexBar(6, "3180", "3170"),
                indexBar(7, "3190", "3185"),
                indexBar(8, "3250", "3230"),
                indexBar(9, "3220", "3200")
        );

        BigDecimal resistance = ReflectionTestUtils.invokeMethod(service,
                "resolveShanghaiKeyResistance", shanghaiBars, new BigDecimal("3200"));

        assertEquals(new BigDecimal("3300.00"), resistance);
    }

    @Test
    void shouldKeepSingleStanceTipAfterRepeatedLiveRefresh() {
        MarketBriefingServiceImpl service = new MarketBriefingServiceImpl();
        MarketBriefingResp briefing = MarketBriefingResp.builder()
                .dataLevel("GREEN")
                .factors(List.of())
                .tips(List.of())
                .build();

        ReflectionTestUtils.invokeMethod(service, "applyStanceFromLiveFactors", briefing,
                BigDecimal.ZERO, true, BigDecimal.ZERO);
        ReflectionTestUtils.invokeMethod(service, "applyStanceFromLiveFactors", briefing,
                BigDecimal.ZERO, true, BigDecimal.ZERO);

        int stanceTipCount = 0;
        for (MarketTipItem tipItem : briefing.getTips()) {
            if ("市场中性偏均衡：有信号再做，仓位中等、纪律优先。".equals(tipItem.getText())) {
                stanceTipCount++;
            }
        }
        assertEquals(1, stanceTipCount);
    }

    private IndexBar indexBar(int day, String highPrice, String closePrice) {
        return IndexBar.builder()
                .code("CN_SH")
                .tradeDate(LocalDate.of(2026, 7, day))
                .highPrice(new BigDecimal(highPrice))
                .closePrice(new BigDecimal(closePrice))
                .build();
    }
}
