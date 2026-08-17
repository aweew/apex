package com.awe.apex.quant.decision;

import com.awe.apex.quant.mapper.DecisionOutcomeMapper;
import com.awe.apex.quant.domain.dto.DecisionStrategyPerformance;
import com.awe.apex.quant.context.ApexUserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DecisionPerformanceCalibratorTest {

    private DecisionPerformanceCalibrator calibrator;
    private DecisionOutcomeMapper outcomeMapper;

    @BeforeEach
    void setUp() {
        calibrator = new DecisionPerformanceCalibrator();
        outcomeMapper = mock(DecisionOutcomeMapper.class);
        ApexUserContext userContext = mock(ApexUserContext.class);
        ReflectionTestUtils.setField(calibrator, "decisionOutcomeMapper", outcomeMapper);
        ReflectionTestUtils.setField(calibrator, "userContext", userContext);
        when(userContext.currentUserId()).thenReturn(7L);
    }

    @Test
    void ignoresStrategiesBeforeMinimumSampleCount() {
        when(outcomeMapper.selectStrategyPerformance(7L)).thenReturn(List.of(
                DecisionStrategyPerformance.builder()
                        .strategyId("S1")
                        .sampleCount(19)
                        .avgExcess5d(new BigDecimal("0.10"))
                        .winRate5d(new BigDecimal("0.90"))
                        .build()));

        assertFalse(calibrator.loadAdjustments().containsKey("S1"));
    }

    @Test
    void combinesExcessAndWinRateWithFivePointCap() {
        when(outcomeMapper.selectStrategyPerformance(7L)).thenReturn(List.of(
                DecisionStrategyPerformance.builder()
                        .strategyId("S1").sampleCount(30)
                        .avgExcess5d(new BigDecimal("0.020"))
                        .winRate5d(new BigDecimal("0.60")).build(),
                DecisionStrategyPerformance.builder()
                        .strategyId("S2").sampleCount(40)
                        .avgExcess5d(new BigDecimal("-0.10"))
                        .winRate5d(new BigDecimal("0.20")).build()
        ));

        Map<String, BigDecimal> adjustments = calibrator.loadAdjustments();

        assertEquals(new BigDecimal("3.00"), adjustments.get("S1"));
        assertEquals(new BigDecimal("-5.00"), adjustments.get("S2"));
    }
}
