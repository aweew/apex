package com.awe.apex.quant.market;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 市场简报纯计算单测
 */
class MarketBriefingMathTest {

    @Test
    void fenbuAggregatesLimitBucketsIntoUpDown() {
        Map<Integer, Integer> levels = new LinkedHashMap<>();
        levels.put(1, 810);
        levels.put(11, 75);
        levels.put(0, 51);
        levels.put(-1, 448);
        levels.put(-11, 8);
        int[] b = MarketBriefingMath.aggregateFenbu(levels);
        assertEquals(885, b[0]);
        assertEquals(456, b[1]);
        assertEquals(51, b[2]);
    }

    @Test
    void sumIndexBreadthAddsShanghaiAndShenzhen() {
        int[] b = MarketBriefingMath.sumIndexBreadth(1657, 665, 27, 2255, 634, 37);
        assertEquals(3912, b[0]);
        assertEquals(1299, b[1]);
        assertEquals(64, b[2]);
    }

    @Test
    void divergentDayDoesNotBoostBullishScore() {
        MarketBriefingMath.BreadthSignal s = MarketBriefingMath.resolveBreadthSignal(
                3912, 1299, new BigDecimal("-0.80"));
        assertEquals("分化", s.signal);
        assertEquals(0, s.scoreDelta);
        assertTrue(s.divergent);
        assertTrue(s.tip.contains("权重拖累"));
    }

    @Test
    void strongBreadthWithFlatIndexStillBullish() {
        MarketBriefingMath.BreadthSignal s = MarketBriefingMath.resolveBreadthSignal(
                4000, 1000, new BigDecimal("0.20"));
        assertEquals("偏多", s.signal);
        assertEquals(6, s.scoreDelta);
        assertFalse(s.divergent);
    }

    @Test
    void weakBreadthWithIndexUpIsDivergent() {
        MarketBriefingMath.BreadthSignal s = MarketBriefingMath.resolveBreadthSignal(
                1000, 3000, new BigDecimal("0.50"));
        assertEquals("分化", s.signal);
        assertEquals(0, s.scoreDelta);
        assertTrue(s.divergent);
    }

    @Test
    void pickIndexAmountPrefersF6OverZeroF48() {
        assertEquals(new BigDecimal("100"),
                MarketBriefingMath.pickIndexAmount(new BigDecimal("100"), BigDecimal.ZERO));
        assertNull(MarketBriefingMath.pickIndexAmount(BigDecimal.ZERO, BigDecimal.ZERO));
        assertEquals(new BigDecimal("50"),
                MarketBriefingMath.pickIndexAmount(null, new BigDecimal("50")));
    }

    @Test
    void volumeLabelUsesTrendWhenMaPresent() {
        assertEquals("缩量 -10.00%", MarketBriefingMath.volumeLabel(new BigDecimal("-10"), true));
        assertEquals("放量 +5.50%", MarketBriefingMath.volumeLabel(new BigDecimal("5.5"), false));
        assertEquals("实时", MarketBriefingMath.volumeLabel(null, true));
        assertEquals("今日", MarketBriefingMath.volumeLabel(null, false));
    }

    @Test
    void upSharePctRoundsDown() {
        assertEquals(74, MarketBriefingMath.upSharePct(3912, 1299, 64));
        assertNull(MarketBriefingMath.upSharePct(0, 0, 0));
    }
}
