package com.awe.apex.quant.decision;

import com.awe.apex.quant.domain.dto.MarketBriefingResp;
import com.awe.apex.quant.domain.dto.MarketFactorItem;
import com.awe.apex.quant.domain.dto.ValuationBriefResp;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 科技成长线资格测试
 */
class DecisionGrowthLaneEvaluatorTest {

    private final DecisionGrowthLaneEvaluator evaluator = new DecisionGrowthLaneEvaluator();

    @Test
    void shouldEnableGrowthLaneForQualifiedTechnologyBreakout() {
        DecisionGrowthLaneEvalResp result = evaluator.evaluate(DecisionGrowthLaneEvalReq.builder()
                .briefing(growthBriefing())
                .industry("半导体")
                .strategyId("S3")
                .confluenceCount(1)
                .hotSourceCount(2)
                .valuation(ValuationBriefResp.builder().growthQualityVerified(true).build())
                .build());

        assertTrue(result.isQualified());
        assertTrue(result.getRejectReasons().isEmpty());
    }

    @Test
    void shouldRejectWhenGrowthQualityIsNotVerified() {
        DecisionGrowthLaneEvalResp result = evaluator.evaluate(DecisionGrowthLaneEvalReq.builder()
                .briefing(growthBriefing())
                .industry("软件开发")
                .strategyId("S3")
                .hotSourceCount(2)
                .valuation(ValuationBriefResp.builder().growthQualityVerified(false).build())
                .build());

        assertFalse(result.isQualified());
        assertTrue(result.getRejectReasons().contains("增长质量未验证"));
    }

    @Test
    void shouldRejectWhenGrowthStyleIsNotConfirmed() {
        DecisionGrowthLaneEvalResp result = evaluator.evaluate(DecisionGrowthLaneEvalReq.builder()
                .briefing(MarketBriefingResp.builder()
                        .factors(List.of(MarketFactorItem.builder().name("最近风格").value("权重/防御占优").signal("提示").build()))
                        .build())
                .industry("通信设备")
                .strategyId("S3")
                .hotSourceCount(2)
                .valuation(ValuationBriefResp.builder().growthQualityVerified(true).build())
                .build());

        assertFalse(result.isQualified());
        assertTrue(result.getRejectReasons().contains("成长风格未确认"));
    }

    private MarketBriefingResp growthBriefing() {
        return MarketBriefingResp.builder()
                .factors(List.of(MarketFactorItem.builder()
                        .name("最近风格")
                        .value("成长/小盘占优")
                        .signal("偏多")
                        .build()))
                .build();
    }
}
