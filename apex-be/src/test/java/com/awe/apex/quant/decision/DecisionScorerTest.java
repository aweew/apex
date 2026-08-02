package com.awe.apex.quant.decision;

import com.awe.apex.quant.domain.dto.ValuationBriefResp;
import com.awe.apex.quant.strategy.StrategyParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DecisionScorer：估值×策略联动与可执行提示
 */
class DecisionScorerTest {

    private final DecisionScorer scorer = new DecisionScorer();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scorer, "strategyParams", new StrategyParams());
    }

    @Test
    void undervaluedS2GetsLinkBoostAndCanBeExecutable() {
        DecisionScoreResp resp = scorer.scoreBuy(DecisionScoreReq.builder()
                .signalScore(new BigDecimal("80"))
                .strategyId("S2")
                .confluenceCount(2)
                .hotSourceCount(0)
                .fundExclude(false)
                .fundWeak(false)
                .mainlineMatch(true)
                .offMainline(false)
                .valuation(ValuationBriefResp.builder()
                        .level("UNDERVALUED")
                        .levelLabel("低估")
                        .scoreDelta(10)
                        .build())
                .marketStance("均衡")
                .buyWeightFactor(BigDecimal.ONE)
                .singleLimit(new BigDecimal("0.15"))
                .observeOnly(false)
                .build());
        assertTrue(resp.getFinalScore().compareTo(new BigDecimal("100")) >= 0);
        assertTrue(resp.getScoreExplain().contains("低估回调"));
        assertTrue("低估回调优先".equals(resp.getLinkHint()));
        assertTrue(resp.isExecutableHint());
    }

    @Test
    void overvaluedS3GetsPenaltyAndNotExecutable() {
        DecisionScoreResp resp = scorer.scoreBuy(DecisionScoreReq.builder()
                .signalScore(new BigDecimal("90"))
                .strategyId("S3")
                .confluenceCount(2)
                .hotSourceCount(3)
                .fundExclude(false)
                .fundWeak(false)
                .mainlineMatch(true)
                .offMainline(false)
                .valuation(ValuationBriefResp.builder()
                        .level("OVERVALUED")
                        .levelLabel("高估")
                        .scoreDelta(-12)
                        .build())
                .marketStance("进攻")
                .buyWeightFactor(BigDecimal.ONE)
                .singleLimit(new BigDecimal("0.15"))
                .observeOnly(false)
                .build());
        assertTrue(resp.getScoreExplain().contains("高估突破"));
        assertTrue(resp.getRiskFlags().stream().anyMatch(f -> f.contains("高估突破")));
        assertFalse(resp.isExecutableHint());
        assertTrue("高估突破降权".equals(resp.getLinkHint()));
    }

    @Test
    void fairS1ConfluenceKeepsWeightNearCap() {
        DecisionScoreResp resp = scorer.scoreBuy(DecisionScoreReq.builder()
                .signalScore(new BigDecimal("80"))
                .strategyId("S1")
                .confluenceCount(2)
                .hotSourceCount(0)
                .fundExclude(false)
                .fundWeak(false)
                .mainlineMatch(false)
                .offMainline(false)
                .valuation(ValuationBriefResp.builder()
                        .level("FAIR")
                        .levelLabel("合理")
                        .scoreDelta(0)
                        .build())
                .marketStance("均衡")
                .buyWeightFactor(BigDecimal.ONE)
                .singleLimit(new BigDecimal("0.15"))
                .observeOnly(false)
                .build());
        assertTrue(resp.getSuggestedWeight().compareTo(new BigDecimal("0.12")) >= 0);
        assertTrue(resp.getSuggestedWeight().compareTo(new BigDecimal("0.15")) <= 0);
        assertTrue(resp.isExecutableHint() || resp.getFinalScore().compareTo(new BigDecimal("88")) < 0);
    }

    @Test
    void fundExcludeForcesObserveOnly() {
        DecisionScoreResp resp = scorer.scoreBuy(DecisionScoreReq.builder()
                .signalScore(new BigDecimal("85"))
                .strategyId("S1")
                .confluenceCount(1)
                .fundExclude(true)
                .fundWeak(true)
                .observeOnly(true)
                .buyWeightFactor(BigDecimal.ONE)
                .singleLimit(new BigDecimal("0.15"))
                .build());
        assertFalse(resp.isExecutableHint());
        assertTrue(resp.getSuggestedWeight().compareTo(BigDecimal.ZERO) == 0);
        assertTrue(resp.getRiskFlags().stream().anyMatch(f -> f.contains("基本面")));
    }
}
