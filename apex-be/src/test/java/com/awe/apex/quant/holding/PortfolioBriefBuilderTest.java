package com.awe.apex.quant.holding;

import com.awe.apex.quant.domain.dto.PortfolioBriefResp;
import com.awe.apex.quant.domain.dto.PortfolioTipItem;
import com.awe.apex.quant.domain.entity.PortfolioHolding;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 组合简报：止损不左右重复，风险文案含名称或组合级汇总
 */
class PortfolioBriefBuilderTest {

    @Test
    void stopHitsAreGroupedNotDuplicatedAcrossActionsAndRisks() {
        List<PortfolioHolding> holdings = List.of(
                holding("000063", "中兴通讯", "止损卖出", "34.44", "5"),
                holding("002466", "天齐锂业", "止损卖出", "54.49", "5"),
                holding("603986", "兆易创新", "止损卖出", "120.00", "4")
        );

        PortfolioBriefResp brief = PortfolioBriefBuilder.build(holdings);
        assertNotNull(brief);
        assertEquals(3, brief.getStopHitCount());

        List<PortfolioTipItem> actions = brief.getActions();
        List<PortfolioTipItem> risks = brief.getRisks();
        assertEquals(1, actions.stream().filter(t -> "critical".equals(t.getLevel())).count());
        assertTrue(actions.get(0).getText().contains("中兴通讯"));
        assertTrue(actions.get(0).getText().contains("天齐锂业"));
        assertTrue(actions.get(0).getText().contains("兆易创新"));

        long stopRisks = risks.stream()
                .filter(t -> "critical".equals(t.getLevel()) && t.getText().contains("止损纪律"))
                .count();
        assertEquals(1, stopRisks);
        assertTrue(risks.get(0).getText().contains("已破线 3 只"));
        // 不再逐票复读「000063 已破止损线」
        assertFalse(risks.stream().anyMatch(t -> t.getText().matches(".*止损纪律风险：\\d{5,6} 已破.*")));
    }

    @Test
    void concentrationRiskIncludesStockName() {
        PortfolioHolding heavy = holding("002281", "光迅科技", "继续持有", "50.00", "30");
        heavy.setStopLoss(new BigDecimal("40.00"));
        heavy.setMarketPrice(new BigDecimal("50.00"));
        PortfolioBriefResp brief = PortfolioBriefBuilder.build(List.of(heavy));
        assertNotNull(brief);
        assertTrue(brief.getRisks().stream().anyMatch(t ->
                t.getText().contains("光迅科技") && t.getText().contains("集中度")));
    }

    private static PortfolioHolding holding(String code, String name, String verdict,
                                            String stop, String weight) {
        return PortfolioHolding.builder()
                .code(code)
                .name(name)
                .verdict(verdict)
                .stopLoss(new BigDecimal(stop))
                .marketPrice(new BigDecimal(stop).subtract(new BigDecimal("0.5")))
                .weightPct(new BigDecimal(weight))
                .marketValue(new BigDecimal(weight).multiply(new BigDecimal("1000")))
                .quantity(100)
                .build();
    }
}
