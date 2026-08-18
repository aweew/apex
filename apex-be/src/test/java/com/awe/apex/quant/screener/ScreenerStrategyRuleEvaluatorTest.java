package com.awe.apex.quant.screener;

import com.awe.apex.quant.domain.bo.ScreenerCandidateBO;
import com.awe.apex.quant.domain.bo.ScreenerRuleEvaluationBO;
import com.awe.apex.quant.domain.dto.ScreenerStrategyRuleResp;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.enums.ScreenerRuleTypeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenerStrategyRuleEvaluatorTest {

    private ScreenerStrategyRuleEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new ScreenerStrategyRuleEvaluator();
        ReflectionTestUtils.setField(evaluator, "metricCalculator", new ScreenerMetricCalculator());
    }

    @Test
    void shouldEvaluateNewHistoricalRulesAndStoreEvidenceValues() {
        ScreenerCandidateBO candidate = ScreenerCandidateBO.builder()
                .bars(buildComebackBars())
                .build();

        ScreenerRuleEvaluationBO days = evaluator.evaluateHistorical(candidate,
                integerRule(ScreenerRuleTypeEnum.DAYS_SINCE_LIMIT_UP, "LTE", 10, 10), null);
        ScreenerRuleEvaluationBO volume = evaluator.evaluateHistorical(candidate,
                numberRule(ScreenerRuleTypeEnum.VOLUME_MA_RATIO, "GTE", "120", 5), null);
        ScreenerRuleEvaluationBO breakout = evaluator.evaluateHistorical(candidate,
                booleanRule(ScreenerRuleTypeEnum.BREAKOUT_PREVIOUS_HIGH, true, 3), null);

        assertTrue(days.getPassed());
        assertEquals("4", days.getActualValue());
        assertEquals(4, candidate.getDaysSinceLimitUp());
        assertTrue(volume.getPassed());
        assertEquals("200", volume.getActualValue());
        assertEquals(new BigDecimal("200.00"), candidate.getVolumeMaRatio());
        assertTrue(breakout.getPassed());
        assertEquals(Boolean.TRUE, candidate.getBreakoutPreviousHigh());
    }

    @Test
    void shouldReportMissingWhenHistoricalWindowIsInsufficient() {
        ScreenerCandidateBO candidate = ScreenerCandidateBO.builder()
                .bars(buildComebackBars().subList(0, 4))
                .build();

        ScreenerRuleEvaluationBO result = evaluator.evaluateHistorical(candidate,
                booleanRule(ScreenerRuleTypeEnum.MA_BULLISH_ALIGNMENT, true, null), null);

        assertFalse(result.getPassed());
        assertTrue(result.getMissing());
        assertTrue(result.getMissingMessage().contains("日线不足"));
    }

    private ScreenerStrategyRuleResp integerRule(ScreenerRuleTypeEnum type, String operator,
                                                   Integer value, Integer lookback) {
        return ScreenerStrategyRuleResp.builder()
                .ruleType(type.getCode())
                .ruleName(type.getDesc())
                .operatorCode(operator)
                .intValue(value)
                .lookbackDays(lookback)
                .build();
    }

    private ScreenerStrategyRuleResp numberRule(ScreenerRuleTypeEnum type, String operator,
                                                  String value, Integer lookback) {
        return ScreenerStrategyRuleResp.builder()
                .ruleType(type.getCode())
                .ruleName(type.getDesc())
                .operatorCode(operator)
                .minValue(new BigDecimal(value))
                .lookbackDays(lookback)
                .build();
    }

    private ScreenerStrategyRuleResp booleanRule(ScreenerRuleTypeEnum type, boolean value,
                                                   Integer lookback) {
        return ScreenerStrategyRuleResp.builder()
                .ruleType(type.getCode())
                .ruleName(type.getDesc())
                .operatorCode("EQ")
                .boolValue(value)
                .lookbackDays(lookback)
                .build();
    }

    private List<BarDaily> buildComebackBars() {
        List<BarDaily> bars = new ArrayList<>();
        for (int index = 0; index < 21; index++) {
            BigDecimal close = new BigDecimal("10").add(BigDecimal.valueOf(index).movePointLeft(1));
            bars.add(BarDaily.builder()
                    .tradeDate(LocalDate.of(2026, 7, 1).plusDays(index))
                    .highPrice(close.add(new BigDecimal("0.05")))
                    .lowPrice(close.subtract(new BigDecimal("0.05")))
                    .closePrice(close)
                    .volume(new BigDecimal("100"))
                    .pctChg(BigDecimal.ONE)
                    .build());
        }
        bars.get(16).setPctChg(new BigDecimal("9.80"));
        bars.get(20).setClosePrice(new BigDecimal("13"));
        bars.get(20).setVolume(new BigDecimal("200"));
        return bars;
    }
}
