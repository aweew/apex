package com.awe.apex.quant.decision;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 主线匹配
 */
class MainlineMatcherTest {

    @Test
    void exactMatch() {
        MainlineMatcher.Hit hit = MainlineMatcher.match("半导体", List.of("半导体", "白酒"));
        assertTrue(hit.match);
        assertEquals("半导体", hit.name);
    }

    @Test
    void industryContainsTheme() {
        MainlineMatcher.Hit hit = MainlineMatcher.match("半导体材料", List.of("半导体"));
        assertTrue(hit.match);
    }

    @Test
    void shortThemeNotSubstringTrap() {
        // 「银」过短，不应命中
        MainlineMatcher.Hit hit = MainlineMatcher.match("银行", List.of("银"));
        assertFalse(hit.match);
    }

    @Test
    void themeContainsIndustryRequiresLongIndustry() {
        // industry「银行」仅 2 字，不走 n.contains(industry)
        MainlineMatcher.Hit hit = MainlineMatcher.match("银行", List.of("江苏银行概念"));
        assertFalse(hit.match);
    }

    @Test
    void blankIndustry() {
        assertFalse(MainlineMatcher.match(null, List.of("半导体")).match);
        assertFalse(MainlineMatcher.match("半导体", List.of()).match);
    }
}
