package com.awe.apex.quant.screener;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.quant.domain.dto.ScreenerStrategyResp;
import com.awe.apex.quant.domain.enums.ScreenerRuleTypeEnum;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenerStrategyTemplateRegistryTest {

    @Test
    void shouldExposeEditableCopySourcesAndPublicFirstBoardTemplate() {
        ScreenerStrategyTemplateRegistry registry = new ScreenerStrategyTemplateRegistry();

        List<ScreenerStrategyResp> templates = registry.listTemplates();

        assertTrue(templates.size() >= 6);
        ScreenerStrategyResp firstBoard = registry.getTemplate("PUBLIC_FIRST_BOARD_DISPERSION");
        assertNotNull(firstBoard);
        assertEquals("北京炒家首板（公开规则版）", firstBoard.getName());
        assertTrue(firstBoard.getTemplate());
        assertFalse(firstBoard.getEditable());
        assertTrue(firstBoard.getDisclaimer().contains("收益未经验证"));
        assertNotNull(firstBoard.getGuide());
        assertTrue(firstBoard.getGuide().getCoreIdea().contains("分仓"));
        assertTrue(CollUtil.isNotEmpty(firstBoard.getGuide().getExecutionSteps()));
        assertTrue(CollUtil.isNotEmpty(firstBoard.getGuide().getRiskNotes()));
        assertTrue(firstBoard.getRules().stream()
                .anyMatch(rule -> ScreenerRuleTypeEnum.LIMIT_UP_LEVEL.getCode().equals(rule.getRuleType())));
        assertTrue(firstBoard.getRules().stream()
                .anyMatch(rule -> ScreenerRuleTypeEnum.THEME_LINKAGE_COUNT.getCode().equals(rule.getRuleType())));
    }

    @Test
    void shouldExposeMainBoardAcceptanceTemplateWithRealtimeAndIntradayRules() {
        ScreenerStrategyTemplateRegistry registry = new ScreenerStrategyTemplateRegistry();

        ScreenerStrategyResp strategy = registry.getTemplate("MAIN_BOARD_STRONG_ACCEPTANCE");

        assertNotNull(strategy);
        assertTrue(strategy.getRules().stream()
                .anyMatch(rule -> ScreenerRuleTypeEnum.TURNOVER_RATE.getCode().equals(rule.getRuleType())));
        assertTrue(strategy.getRules().stream()
                .anyMatch(rule -> ScreenerRuleTypeEnum.LIMIT_UP_COUNT.getCode().equals(rule.getRuleType())
                        && Integer.valueOf(20).equals(rule.getLookbackDays())));
        assertTrue(strategy.getRules().stream()
                .anyMatch(rule -> ScreenerRuleTypeEnum.INTRADAY_ABOVE_AVG_RATIO.getCode()
                        .equals(rule.getRuleType())));
    }

    @Test
    void shouldExposeFourCommonShortTermTemplates() {
        ScreenerStrategyTemplateRegistry registry = new ScreenerStrategyTemplateRegistry();

        assertEquals(12, registry.listTemplates().size());

        ScreenerStrategyResp resonance = registry.getTemplate("SECTOR_RESONANCE_FIRST_BOARD");
        assertNotNull(resonance);
        assertEquals("板块共振首板", resonance.getName());
        assertEquals("REALTIME", resonance.getRunMode());
        assertTrue(hasRule(resonance, ScreenerRuleTypeEnum.LIMIT_UP_LEVEL));
        assertTrue(hasRule(resonance, ScreenerRuleTypeEnum.THEME_LINKAGE_COUNT));

        ScreenerStrategyResp lowPosition = registry.getTemplate("LOW_POSITION_FIRST_BOARD");
        assertNotNull(lowPosition);
        assertEquals("低位首板", lowPosition.getName());
        assertTrue(hasRule(lowPosition, ScreenerRuleTypeEnum.PRICE_POSITION));
        assertTrue(hasRule(lowPosition, ScreenerRuleTypeEnum.LIMIT_UP_LEVEL));

        ScreenerStrategyResp comeback = registry.getTemplate("LIMIT_UP_COMEBACK");
        assertNotNull(comeback);
        assertEquals("涨停回马枪", comeback.getName());
        assertEquals("CLOSE", comeback.getRunMode());
        assertTrue(hasRule(comeback, ScreenerRuleTypeEnum.MARKET_BOARD));
        assertTrue(hasRule(comeback, ScreenerRuleTypeEnum.DAYS_SINCE_LIMIT_UP));
        assertTrue(hasRule(comeback, ScreenerRuleTypeEnum.VOLUME_MA_RATIO));
        assertTrue(hasRule(comeback, ScreenerRuleTypeEnum.BREAKOUT_PREVIOUS_HIGH));

        ScreenerStrategyResp pullback = registry.getTemplate("VOLUME_CONTRACTION_PULLBACK");
        assertNotNull(pullback);
        assertEquals("缩量回踩", pullback.getName());
        assertEquals("CLOSE", pullback.getRunMode());
        assertTrue(hasRule(pullback, ScreenerRuleTypeEnum.MA_BULLISH_ALIGNMENT));
        assertTrue(hasRule(pullback, ScreenerRuleTypeEnum.CLOSE_MA_DISTANCE_PCT));
        assertTrue(hasRule(pullback, ScreenerRuleTypeEnum.VOLUME_MA_RATIO));
    }

    @Test
    void shouldExplainMainBoardAcceptanceSwingAndGridStrategies() {
        ScreenerStrategyTemplateRegistry registry = new ScreenerStrategyTemplateRegistry();

        ScreenerStrategyResp acceptance = registry.getTemplate("MAIN_BOARD_STRONG_ACCEPTANCE");
        assertNotNull(acceptance.getGuide());
        assertEquals("盘中承接", acceptance.getGuide().getCategory());
        assertTrue(acceptance.getGuide().getPlainExplanation().contains("分时均价线"));

        ScreenerStrategyResp swing = registry.getTemplate("SWING_TREND_PULLBACK");
        assertNotNull(swing);
        assertEquals("趋势波段", swing.getGuide().getCategory());
        assertEquals("CLOSE", swing.getRunMode());
        assertTrue(hasRule(swing, ScreenerRuleTypeEnum.MA_BULLISH_ALIGNMENT));
        assertTrue(hasRule(swing, ScreenerRuleTypeEnum.CLOSE_MA_DISTANCE_PCT));

        ScreenerStrategyResp grid = registry.getTemplate("RANGE_GRID_CANDIDATE");
        assertNotNull(grid);
        assertEquals("震荡网格", grid.getGuide().getCategory());
        assertEquals("CLOSE", grid.getRunMode());
        assertTrue(grid.getDescription().contains("候选"));
        assertTrue(hasRule(grid, ScreenerRuleTypeEnum.ATR_PCT));
        assertTrue(hasRule(grid, ScreenerRuleTypeEnum.RANGE_RETURN));
    }

    @Test
    void shouldProvidePracticalGuideForEverySystemTemplate() {
        ScreenerStrategyTemplateRegistry registry = new ScreenerStrategyTemplateRegistry();

        for (ScreenerStrategyResp strategy : registry.listTemplates()) {
            assertNotNull(strategy.getGuide(), strategy.getName());
            assertTrue(StringUtils.isNotBlank(strategy.getGuide().getCategory()), strategy.getName());
            assertTrue(StringUtils.isNotBlank(strategy.getGuide().getPlainExplanation()), strategy.getName());
            assertTrue(StringUtils.isNotBlank(strategy.getGuide().getCoreIdea()), strategy.getName());
            assertTrue(StringUtils.isNotBlank(strategy.getGuide().getSuitableMarket()), strategy.getName());
            assertTrue(CollUtil.isNotEmpty(strategy.getGuide().getExecutionSteps()), strategy.getName());
            assertTrue(CollUtil.isNotEmpty(strategy.getGuide().getRiskNotes()), strategy.getName());
        }
    }

    private boolean hasRule(ScreenerStrategyResp strategy, ScreenerRuleTypeEnum ruleType) {
        return strategy.getRules().stream().anyMatch(rule -> ruleType.getCode().equals(rule.getRuleType()));
    }
}
