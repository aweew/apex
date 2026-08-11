package com.awe.apex.quant.decision;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 主线板块排除与类型偏好
 */
class MainlineBoardRulesTest {

    @Test
    void excludeYesterdayLianbanBoards() {
        assertTrue(MainlineBoardRules.isOutcomeBoard("昨日连板_含一字"));
        assertTrue(MainlineBoardRules.isOutcomeBoard("昨日连板"));
        assertTrue(MainlineBoardRules.isOutcomeBoard("昨日涨停_含一字"));
        assertTrue(MainlineBoardRules.isOutcomeBoard("昨日高振幅"));
        assertTrue(MainlineBoardRules.isOutcomeBoard("最近多板"));
        assertTrue(MainlineBoardRules.isOutcomeBoard("近期连板"));
        assertTrue(MainlineBoardRules.isOutcomeBoard("今日涨停"));
        assertTrue(MainlineBoardRules.isOutcomeBoard("炸板股"));
        assertTrue(MainlineBoardRules.isOutcomeBoard("曾涨停"));
    }

    @Test
    void keepRealThemes() {
        assertFalse(MainlineBoardRules.isOutcomeBoard("钨"));
        assertFalse(MainlineBoardRules.isOutcomeBoard("半导体"));
        assertFalse(MainlineBoardRules.isOutcomeBoard("商业航天"));
        assertFalse(MainlineBoardRules.isOutcomeBoard("人工智能"));
        assertFalse(MainlineBoardRules.isOutcomeBoard("光伏设备"));
    }

    @Test
    void keepOnlyPureConceptBoards() {
        assertTrue(MainlineBoardRules.isConceptBoard("CONCEPT", "机器人执行器"));
        assertTrue(MainlineBoardRules.isConceptBoard("CONCEPT", "创新药"));
        assertTrue(MainlineBoardRules.isConceptBoard("CONCEPT", "商业航天"));
        assertFalse(MainlineBoardRules.isConceptBoard("CONCEPT", "昨日打二板以上表现"));
        assertFalse(MainlineBoardRules.isConceptBoard("CONCEPT", "昨日连板_含一字"));
        assertFalse(MainlineBoardRules.isConceptBoard("CONCEPT", "基金重仓"));
        assertFalse(MainlineBoardRules.isConceptBoard("CONCEPT", "医药医疗风格"));
        assertFalse(MainlineBoardRules.isConceptBoard("INDUSTRY", "创新药"));
        assertFalse(MainlineBoardRules.isConceptBoard("THEME", "机器人执行器"));
    }

    @Test
    void blankNameExcluded() {
        assertTrue(MainlineBoardRules.isOutcomeBoard(null));
        assertTrue(MainlineBoardRules.isOutcomeBoard("  "));
    }

    @Test
    void industryPreferredOverTheme() {
        assertTrue(MainlineBoardRules.typeBonus("INDUSTRY") > MainlineBoardRules.typeBonus("CONCEPT"));
        assertTrue(MainlineBoardRules.typeBonus("CONCEPT") > MainlineBoardRules.typeBonus("THEME"));
        assertEquals(0, MainlineBoardRules.typeRank("INDUSTRY"));
        assertTrue(MainlineBoardRules.typeRank("THEME") > MainlineBoardRules.typeRank("INDUSTRY"));
    }
}
