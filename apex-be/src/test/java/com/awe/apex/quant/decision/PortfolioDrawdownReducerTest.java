package com.awe.apex.quant.decision;

import com.awe.apex.quant.domain.dto.DecisionItemResp;
import com.awe.apex.quant.domain.dto.DecisionPortfolioHolding;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PortfolioDrawdownReducerTest {

    private final PortfolioDrawdownReducer reducer = new PortfolioDrawdownReducer();

    @Test
    void createsProportionalReductionActionsWhenExposureExceedsDrawdownLimit() {
        List<DecisionPortfolioHolding> holdings = List.of(
                holding("000001", "平安银行", "40000", 4000, "10"),
                holding("600000", "浦发银行", "20000", 2000, "10"));

        List<DecisionItemResp> actions = reducer.buildReductionActions(
                LocalDate.of(2026, 8, 7), holdings, new BigDecimal("100000"),
                new BigDecimal("0.60"), new BigDecimal("0.60"), new BigDecimal("0.08"));

        assertEquals(2, actions.size());
        assertEquals("REDUCE", actions.get(0).getAction());
        assertEquals(new BigDecimal("0.2000"), actions.get(0).getSuggestedWeight());
        assertEquals(new BigDecimal("0.1000"), actions.get(1).getSuggestedWeight());
        assertEquals(Boolean.TRUE, actions.get(0).getExecutableHint());
    }

    @Test
    void avoidsExtraReductionWhenExistingExitActionsAlreadyReachTheLimit() {
        List<DecisionPortfolioHolding> holdings = List.of(
                holding("000001", "平安银行", "40000", 4000, "10"),
                holding("600000", "浦发银行", "20000", 2000, "10"));

        List<DecisionItemResp> actions = reducer.buildReductionActions(
                LocalDate.of(2026, 8, 7), holdings, new BigDecimal("100000"),
                new BigDecimal("0.60"), new BigDecimal("0.60"), new BigDecimal("0.08"),
                java.util.Set.of("000001"));

        assertEquals(0, actions.size());
    }

    private DecisionPortfolioHolding holding(String code, String name, String marketValue,
                                              Integer quantity, String marketPrice) {
        return DecisionPortfolioHolding.builder()
                .code(code)
                .name(name)
                .marketValue(new BigDecimal(marketValue))
                .quantity(quantity)
                .marketPrice(new BigDecimal(marketPrice))
                .build();
    }
}
