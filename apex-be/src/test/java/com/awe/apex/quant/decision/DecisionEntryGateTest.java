package com.awe.apex.quant.decision;

import com.awe.apex.quant.strategy.StrategyParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 决策开仓门禁测试
 */
class DecisionEntryGateTest {

    private final DecisionEntryGate gate = new DecisionEntryGate();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(gate, "strategyParams", new StrategyParams());
    }

    @Test
    void shouldPassWhenMarketBreadthAndMainlineConfirm() {
        DecisionEntryGateResp result = gate.evaluate(DecisionEntryGateReq.builder()
                .dataSufficient(true)
                .breadthUp(2500)
                .mainlineMatch(true)
                .offMainline(false)
                .hotSourceCount(0)
                .build());

        assertTrue(result.isPassed());
        assertTrue(result.getBlockReasons().isEmpty());
    }

    @Test
    void shouldBlockWhenMarketBreadthIsInsufficient() {
        DecisionEntryGateResp result = gate.evaluate(DecisionEntryGateReq.builder()
                .dataSufficient(true)
                .breadthUp(1999)
                .mainlineMatch(true)
                .offMainline(false)
                .build());

        assertFalse(result.isPassed());
        assertTrue(result.getBlockReasons().contains("市场广度不足，禁止新开仓"));
    }

    @Test
    void shouldBlockWhenNeitherMainlineNorHotConfirmationExists() {
        DecisionEntryGateResp result = gate.evaluate(DecisionEntryGateReq.builder()
                .dataSufficient(true)
                .breadthUp(2500)
                .mainlineMatch(false)
                .offMainline(false)
                .hotSourceCount(1)
                .build());

        assertFalse(result.isPassed());
        assertTrue(result.getBlockReasons().contains("缺少主线或多源热点确认，仅观察"));
    }

    @Test
    void shouldBlockWhenIndustryRunsAgainstMainline() {
        DecisionEntryGateResp result = gate.evaluate(DecisionEntryGateReq.builder()
                .dataSufficient(true)
                .breadthUp(2500)
                .mainlineMatch(false)
                .offMainline(true)
                .hotSourceCount(3)
                .build());

        assertFalse(result.isPassed());
        assertTrue(result.getBlockReasons().contains("行业逆主线，禁止新开仓"));
    }

    @Test
    void shouldPassQualifiedGrowthLaneEvenWhenIndustryIsOffMainline() {
        DecisionEntryGateResp result = gate.evaluate(DecisionEntryGateReq.builder()
                .dataSufficient(true)
                .breadthUp(2500)
                .mainlineMatch(false)
                .offMainline(true)
                .growthLane(true)
                .hotSourceCount(2)
                .build());

        assertTrue(result.isPassed());
    }

    @Test
    void shouldFailClosedWhenMarketDataIsUnavailable() {
        DecisionEntryGateResp result = gate.evaluate(DecisionEntryGateReq.builder()
                .dataSufficient(false)
                .breadthUp(2500)
                .mainlineMatch(true)
                .offMainline(false)
                .build());

        assertFalse(result.isPassed());
        assertTrue(result.getBlockReasons().containsAll(List.of("市场数据不足，禁止新开仓")));
    }
}
