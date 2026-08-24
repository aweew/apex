package com.awe.apex.quant.factor;

import com.awe.apex.quant.domain.dto.ResearchScoreComponentResp;
import com.awe.apex.quant.domain.dto.ResearchScoreResp;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ResearchScoreCalculatorTest {

    private final ResearchScoreCalculator calculator = new ResearchScoreCalculator();

    @Test
    void shouldRankHigherValuesAtHigherPercentileAndKeepTiesTogether() {
        List<BigDecimal> peerValues = List.of(new BigDecimal("10"), new BigDecimal("20"),
                new BigDecimal("20"), new BigDecimal("40"));

        assertEquals(new BigDecimal("25.00"), calculator.calculatePercentile(new BigDecimal("10"), peerValues));
        assertEquals(new BigDecimal("75.00"), calculator.calculatePercentile(new BigDecimal("20"), peerValues));
        assertEquals(new BigDecimal("100.00"), calculator.calculatePercentile(new BigDecimal("40"), peerValues));
    }

    @Test
    void shouldRequireCoreFactorsAndEightyPercentCoverageForResearchScore() {
        ResearchScoreResp missingCore = calculator.summarize(List.of(
                component("QUALITY", 25, null),
                component("MOMENTUM", 25, "80"),
                component("GROWTH", 20, "70"),
                component("VALUATION", 15, "60"),
                component("CAPITAL", 15, "50")
        ));
        ResearchScoreResp incompleteCoverage = calculator.summarize(List.of(
                component("QUALITY", 25, "80"),
                component("MOMENTUM", 25, "80"),
                component("GROWTH", 20, "70"),
                component("VALUATION", 15, null),
                component("CAPITAL", 15, null)
        ));

        assertEquals("INSUFFICIENT", missingCore.getStatus());
        assertNull(missingCore.getScore());
        assertEquals("INSUFFICIENT", incompleteCoverage.getStatus());
        assertEquals(new BigDecimal("70.00"), incompleteCoverage.getCoverage());
    }

    @Test
    void shouldBuildWeightedScoreAndSignedContributionFromNeutralPercentile() {
        ResearchScoreResp result = calculator.summarize(List.of(
                component("QUALITY", 25, "80"),
                component("MOMENTUM", 25, "60"),
                component("GROWTH", 20, "50"),
                component("VALUATION", 15, "40"),
                component("CAPITAL", 15, "70")
        ));

        assertEquals("READY", result.getStatus());
        assertEquals(new BigDecimal("61.50"), result.getScore());
        assertEquals(new BigDecimal("7.50"), result.getComponents().get(0).getContribution());
        assertEquals(new BigDecimal("-1.50"), result.getComponents().get(3).getContribution());
    }

    private ResearchScoreComponentResp component(String key, int weight, String percentile) {
        return ResearchScoreComponentResp.builder()
                .key(key)
                .weight(BigDecimal.valueOf(weight))
                .percentile(percentile == null ? null : new BigDecimal(percentile))
                .available(percentile != null)
                .build();
    }
}
