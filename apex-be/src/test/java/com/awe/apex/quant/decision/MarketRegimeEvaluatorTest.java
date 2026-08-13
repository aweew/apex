package com.awe.apex.quant.decision;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 市场状态评估测试
 */
class MarketRegimeEvaluatorTest {

    private final MarketRegimeEvaluator evaluator = new MarketRegimeEvaluator();

    @Test
    void shouldReturnUnknownWhenMarketDataIsIncomplete() {
        MarketRegimeResult result = evaluator.evaluate(marketInput(true, true, true,
                "0.60", "0.02", "0.30", false));

        assertEquals(MarketRegimeEnum.UNKNOWN, result.getMarketRegime());
        assertDecimalEquals("0.20", result.getTotalExposureLimit());
    }

    @Test
    void shouldReturnAttackAtBreadthBoundary() {
        MarketRegimeResult result = evaluator.evaluate(marketInput(true, true, true,
                "0.55", "0.02", "0.30", true));

        assertEquals(MarketRegimeEnum.ATTACK, result.getMarketRegime());
        assertDecimalEquals("0.70", result.getTotalExposureLimit());
    }

    @Test
    void shouldPrioritizeDefenseWhenDrawdownReachesEightPercent() {
        MarketRegimeResult result = evaluator.evaluate(marketInput(true, true, true,
                "0.60", "0.08", "0.30", true));

        assertEquals(MarketRegimeEnum.DEFENSE, result.getMarketRegime());
        assertDecimalEquals("0.20", result.getTotalExposureLimit());
    }

    @Test
    void shouldReturnDefenseWhenIndexAndBreadthAreWeak() {
        MarketRegimeResult result = evaluator.evaluate(marketInput(false, false, false,
                "0.4499", "0.02", "0.30", true));

        assertEquals(MarketRegimeEnum.DEFENSE, result.getMarketRegime());
    }

    @Test
    void shouldReturnDefenseWhenHighVolatilityMeetsWeakBreadth() {
        MarketRegimeResult result = evaluator.evaluate(marketInput(true, false, false,
                "0.44", "0.02", "0.80", true));

        assertEquals(MarketRegimeEnum.DEFENSE, result.getMarketRegime());
    }

    @Test
    void shouldReturnBalanceWhenNeitherAttackNorDefenseMatches() {
        MarketRegimeResult result = evaluator.evaluate(marketInput(true, true, true,
                "0.5499", "0.02", "0.80", true));

        assertEquals(MarketRegimeEnum.BALANCE, result.getMarketRegime());
        assertDecimalEquals("0.50", result.getTotalExposureLimit());
    }

    @Test
    void shouldFailClosedWhenRequiredValueIsMissing() {
        MarketRegimeInput marketRegimeInput = marketInput(true, true, true,
                "0.60", "0.02", "0.30", true);
        marketRegimeInput.setBreadthPct(null);

        MarketRegimeResult result = evaluator.evaluate(marketRegimeInput);

        assertEquals(MarketRegimeEnum.UNKNOWN, result.getMarketRegime());
    }

    @Test
    void shouldFailClosedWhenMarketRatioIsOutOfRange() {
        MarketRegimeResult result = evaluator.evaluate(marketInput(true, true, true,
                "1.01", "0.02", "0.30", true));

        assertEquals(MarketRegimeEnum.UNKNOWN, result.getMarketRegime());
        assertDecimalEquals("0.20", result.getTotalExposureLimit());
    }

    private MarketRegimeInput marketInput(boolean indexAboveMa60,
                                          boolean ma20AboveMa60,
                                          boolean ma20SlopeUp,
                                          String breadthPct,
                                          String drawdown20,
                                          String volatilityPercentile,
                                          boolean dataComplete) {
        return MarketRegimeInput.builder()
                .indexAboveMa60(indexAboveMa60)
                .ma20AboveMa60(ma20AboveMa60)
                .ma20SlopeUp(ma20SlopeUp)
                .breadthPct(new BigDecimal(breadthPct))
                .drawdown20(new BigDecimal(drawdown20))
                .volatilityPercentile(new BigDecimal(volatilityPercentile))
                .dataComplete(dataComplete)
                .build();
    }

    private void assertDecimalEquals(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
