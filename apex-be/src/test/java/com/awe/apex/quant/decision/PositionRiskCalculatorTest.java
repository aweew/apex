package com.awe.apex.quant.decision;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 风险预算仓位计算测试
 */
class PositionRiskCalculatorTest {

    private final PositionRiskCalculator calculator = new PositionRiskCalculator();

    @Test
    void shouldClampStopDistanceToThreePercent() {
        PositionRiskResult result = calculator.calculate(riskInput()
                .marketRegime(MarketRegimeEnum.BALANCE)
                .stopDistance(new BigDecimal("0.02"))
                .singleLimit(new BigDecimal("0.30"))
                .build());

        assertTrue(result.getCanOpenPosition());
        assertDecimalEquals("500.00", result.getMaxRiskAmount());
        assertDecimalEquals("0.165", result.getTargetWeight());
        assertDecimalEquals("0.165", result.getIncrementalWeight());
        assertEquals("风险预算通过", result.getReason());
    }

    @Test
    void shouldUseDefenseRiskRate() {
        PositionRiskResult result = calculator.calculate(riskInput()
                .marketRegime(MarketRegimeEnum.DEFENSE)
                .stopDistance(new BigDecimal("0.05"))
                .build());

        assertTrue(result.getCanOpenPosition());
        assertDecimalEquals("250.0000", result.getMaxRiskAmount());
        assertDecimalEquals("0.05", result.getTargetWeight());
    }

    @Test
    void shouldApplyFivePercentDrawdownMultiplier() {
        PositionRiskResult result = calculator.calculate(riskInput()
                .marketRegime(MarketRegimeEnum.BALANCE)
                .currentDrawdown(new BigDecimal("0.05"))
                .stopDistance(new BigDecimal("0.05"))
                .build());

        assertDecimalEquals("375.00000", result.getMaxRiskAmount());
        assertDecimalEquals("0.075", result.getTargetWeight());
        assertDecimalEquals("0.375", result.getTotalExposureLimit());
    }

    @Test
    void shouldApplyEightPercentDrawdownMultiplier() {
        PositionRiskResult result = calculator.calculate(riskInput()
                .marketRegime(MarketRegimeEnum.ATTACK)
                .currentDrawdown(new BigDecimal("0.08"))
                .stopDistance(new BigDecimal("0.05"))
                .build());

        assertDecimalEquals("375.0000", result.getMaxRiskAmount());
        assertDecimalEquals("0.075", result.getTargetWeight());
        assertDecimalEquals("0.35", result.getTotalExposureLimit());
    }

    @Test
    void shouldRejectNewPositionAtTwelvePercentDrawdown() {
        PositionRiskResult result = calculator.calculate(riskInput()
                .marketRegime(MarketRegimeEnum.ATTACK)
                .currentDrawdown(new BigDecimal("0.12"))
                .build());

        assertFalse(result.getCanOpenPosition());
        assertDecimalEquals("0", result.getIncrementalWeight());
        assertDecimalEquals("0", result.getMaxRiskAmount());
        assertDecimalEquals("0.20", result.getTotalExposureLimit());
        assertEquals("当前回撤达到12%，停止新开仓", result.getReason());
    }

    @Test
    void shouldCapExposureAtTenPercentAtFifteenPercentDrawdown() {
        PositionRiskResult result = calculator.calculate(riskInput()
                .marketRegime(MarketRegimeEnum.ATTACK)
                .currentDrawdown(new BigDecimal("0.15"))
                .build());

        assertFalse(result.getCanOpenPosition());
        assertDecimalEquals("0.10", result.getTotalExposureLimit());
        assertEquals("当前回撤达到15%，仅允许减仓或卖出", result.getReason());
    }

    @Test
    void shouldRejectUnknownRegimeAndOversizedStopDistance() {
        PositionRiskResult unknownResult = calculator.calculate(riskInput()
                .marketRegime(MarketRegimeEnum.UNKNOWN)
                .build());
        PositionRiskResult oversizedStopResult = calculator.calculate(riskInput()
                .marketRegime(MarketRegimeEnum.ATTACK)
                .stopDistance(new BigDecimal("0.1001"))
                .build());

        assertFalse(unknownResult.getCanOpenPosition());
        assertEquals("市场数据不完整，禁止新开仓", unknownResult.getReason());
        assertFalse(oversizedStopResult.getCanOpenPosition());
        assertEquals("止损距离超过10%，禁止新开仓", oversizedStopResult.getReason());
    }

    @Test
    void shouldLimitIncrementByTotalExposure() {
        PositionRiskResult result = calculator.calculate(riskInput()
                .marketRegime(MarketRegimeEnum.BALANCE)
                .currentExposure(new BigDecimal("0.46"))
                .stopDistance(new BigDecimal("0.03"))
                .build());

        assertDecimalEquals("0.04", result.getIncrementalWeight());
        assertDecimalEquals("0.04", result.getTargetWeight());
    }

    @Test
    void shouldUseLowerConfiguredTotalExposureLimit() {
        PositionRiskResult result = calculator.calculate(riskInput()
                .marketRegime(MarketRegimeEnum.ATTACK)
                .totalExposureLimit(new BigDecimal("0.35"))
                .currentExposure(new BigDecimal("0.32"))
                .stopDistance(new BigDecimal("0.03"))
                .build());

        assertDecimalEquals("0.35", result.getTotalExposureLimit());
        assertDecimalEquals("0.03", result.getIncrementalWeight());
        assertDecimalEquals("0.03", result.getTargetWeight());
    }

    @Test
    void shouldLimitAdditionalPositionBySingleStockRoom() {
        PositionRiskResult result = calculator.calculate(riskInput()
                .marketRegime(MarketRegimeEnum.ATTACK)
                .currentWeight(new BigDecimal("0.10"))
                .singleLimit(new BigDecimal("0.15"))
                .stopDistance(new BigDecimal("0.03"))
                .build());

        assertDecimalEquals("0.05", result.getIncrementalWeight());
        assertDecimalEquals("0.15", result.getTargetWeight());
    }

    @Test
    void shouldLimitIncrementByIndustryAndCorrelationClusterRoom() {
        PositionRiskResult industryResult = calculator.calculate(riskInput()
                .marketRegime(MarketRegimeEnum.ATTACK)
                .industryWeight(new BigDecimal("0.28"))
                .industryLimit(new BigDecimal("0.30"))
                .build());
        PositionRiskResult clusterResult = calculator.calculate(riskInput()
                .marketRegime(MarketRegimeEnum.ATTACK)
                .correlationClusterWeight(new BigDecimal("0.19"))
                .build());

        assertDecimalEquals("0.02", industryResult.getIncrementalWeight());
        assertDecimalEquals("0.01", clusterResult.getIncrementalWeight());
    }

    @Test
    void shouldLimitIncrementByCashAndRoundDownToWholeLot() {
        PositionRiskResult cashResult = calculator.calculate(riskInput()
                .marketRegime(MarketRegimeEnum.ATTACK)
                .cash(new BigDecimal("3500"))
                .lotValue(new BigDecimal("1000"))
                .build());
        PositionRiskResult roundedResult = calculator.calculate(riskInput()
                .marketRegime(MarketRegimeEnum.ATTACK)
                .cash(new BigDecimal("100000"))
                .singleLimit(new BigDecimal("0.137"))
                .lotValue(new BigDecimal("3000"))
                .build());

        assertDecimalEquals("0.03", cashResult.getIncrementalWeight());
        assertDecimalEquals("0.03", cashResult.getTargetWeight());
        assertDecimalEquals("0.12", roundedResult.getIncrementalWeight());
        assertDecimalEquals("0.12", roundedResult.getTargetWeight());
    }

    @Test
    void shouldRejectWhenAvailableBudgetCannotBuyOneLot() {
        PositionRiskResult result = calculator.calculate(riskInput()
                .marketRegime(MarketRegimeEnum.ATTACK)
                .cash(new BigDecimal("999"))
                .lotValue(new BigDecimal("1000"))
                .build());

        assertFalse(result.getCanOpenPosition());
        assertDecimalEquals("0", result.getIncrementalWeight());
        assertEquals("可用风险预算不足一手", result.getReason());
    }

    @Test
    void shouldRejectAdditionalPositionWhenCurrentWeightReachedRiskTarget() {
        PositionRiskResult result = calculator.calculate(riskInput()
                .marketRegime(MarketRegimeEnum.BALANCE)
                .currentWeight(new BigDecimal("0.11"))
                .stopDistance(new BigDecimal("0.05"))
                .build());

        assertFalse(result.getCanOpenPosition());
        assertDecimalEquals("0.11", result.getTargetWeight());
        assertEquals("当前持仓已达到风险预算目标", result.getReason());
    }

    @Test
    void shouldFailClosedWhenRiskRatioIsOutOfRange() {
        PositionRiskResult result = calculator.calculate(riskInput()
                .marketRegime(MarketRegimeEnum.ATTACK)
                .currentExposure(new BigDecimal("1.01"))
                .build());

        assertFalse(result.getCanOpenPosition());
        assertEquals("风险预算入参不完整或数值越界", result.getReason());
    }

    private PositionRiskInput.PositionRiskInputBuilder riskInput() {
        return PositionRiskInput.builder()
                .equity(new BigDecimal("100000"))
                .cash(new BigDecimal("100000"))
                .currentExposure(BigDecimal.ZERO)
                .currentDrawdown(BigDecimal.ZERO)
                .totalExposureLimit(new BigDecimal("0.70"))
                .singleLimit(new BigDecimal("0.30"))
                .industryLimit(new BigDecimal("0.30"))
                .currentWeight(BigDecimal.ZERO)
                .industryWeight(BigDecimal.ZERO)
                .stopDistance(new BigDecimal("0.05"))
                .correlationClusterWeight(BigDecimal.ZERO)
                .lotValue(new BigDecimal("500"));
    }

    private void assertDecimalEquals(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
