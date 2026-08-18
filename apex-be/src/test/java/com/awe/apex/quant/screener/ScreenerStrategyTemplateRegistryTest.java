package com.awe.apex.quant.screener;

import com.awe.apex.quant.domain.dto.ScreenerStrategyResp;
import com.awe.apex.quant.domain.enums.ScreenerRuleTypeEnum;
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
        assertEquals("公开首板分散模型", firstBoard.getName());
        assertTrue(firstBoard.getTemplate());
        assertFalse(firstBoard.getEditable());
        assertTrue(firstBoard.getDisclaimer().contains("收益未经验证"));
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
}
