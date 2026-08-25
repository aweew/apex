package com.awe.apex.quant.market;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketBreadthForecastEvaluatorTest {

    private final MarketBreadthForecastEvaluator evaluator = new MarketBreadthForecastEvaluator();

    @Test
    void shouldBuildBullishForecastWithBoundedCalibration() {
        MarketBreadthForecastEvalResp result = evaluator.evaluate(MarketBreadthForecastEvalReq.builder()
                .previousBreadthUpRatio(new BigDecimal("58"))
                .overnightIndexPctChg(new BigDecimal("0.80"))
                .ftseA50PctChg(new BigDecimal("0.50"))
                .asiaIndexPctChg(new BigDecimal("0.20"))
                .newsBullCount(4)
                .newsBearCount(1)
                .calibrationAdjustment(new BigDecimal("1.20"))
                .build());

        assertEquals(new BigDecimal("62.00"), result.getPredictedUpRatio());
        assertEquals(new BigDecimal("38.00"), result.getPredictedDownRatio());
        assertEquals(new BigDecimal("100.00"), result.getPredictedUpRatio().add(result.getPredictedDownRatio()));
        assertEquals("中", result.getConfidence());
        assertTrue(result.getReasons().contains("隔夜指数偏多"));
        assertTrue(result.getReasons().contains("消息面偏多"));
    }

    @Test
    void shouldKeepForecastNeutralWhenOnlyOneSignalIsAvailable() {
        MarketBreadthForecastEvalResp result = evaluator.evaluate(MarketBreadthForecastEvalReq.builder()
                .previousBreadthUpRatio(new BigDecimal("55"))
                .build());

        assertEquals(new BigDecimal("51.00"), result.getPredictedUpRatio());
        assertEquals(new BigDecimal("49.00"), result.getPredictedDownRatio());
        assertEquals("低", result.getConfidence());
    }

    @Test
    void shouldLimitCalibrationToAvoidOverfittingSingleDayError() {
        MarketBreadthForecastEvalResp result = evaluator.evaluate(MarketBreadthForecastEvalReq.builder()
                .calibrationAdjustment(new BigDecimal("20"))
                .build());

        assertEquals(new BigDecimal("56.00"), result.getPredictedUpRatio());
        assertEquals(new BigDecimal("44.00"), result.getPredictedDownRatio());
        assertEquals("低", result.getConfidence());
    }
}
