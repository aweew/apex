package com.awe.apex.quant.decision;

import com.awe.apex.quant.domain.dto.DecisionItemResp;
import com.awe.apex.quant.domain.dto.MarketBriefingResp;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DecisionFeatureBuilderTest {

    private final DecisionFeatureBuilder builder = new DecisionFeatureBuilder();

    @Test
    void buildsStableFeatureHash() {
        DecisionItemResp item = DecisionItemResp.builder()
                .code("000001")
                .action("BUY")
                .strategyId("S2")
                .score(new BigDecimal("91.50"))
                .suggestedWeight(new BigDecimal("0.1200"))
                .confluenceCount(2)
                .mainlineMatch(true)
                .valuationLevel("UNDERVALUED")
                .valuationScore(new BigDecimal("82.00"))
                .riskFlags(List.of("逆主线", "估值偏高"))
                .executableHint(true)
                .build();
        MarketBriefingResp briefing = MarketBriefingResp.builder()
                .stance("均衡")
                .dataLevel("GREEN")
                .build();

        DecisionFeature first = builder.build(item, 3, briefing);
        item.setRiskFlags(List.of("估值偏高", "逆主线"));
        DecisionFeature second = builder.build(item, 3, briefing);

        assertEquals(first, second);
        assertNotNull(first.featureHash());
        assertEquals(64, first.featureHash().length());
    }

    @Test
    void keepsMissingValuesExplicit() {
        DecisionFeature feature = builder.build(
                DecisionItemResp.builder().code("600000").action("HOLD").build(),
                0,
                MarketBriefingResp.builder().dataLevel("YELLOW").build());

        assertEquals("600000", feature.code());
        assertEquals("HOLD", feature.action());
        assertEquals("YELLOW", feature.dataQuality());
        assertEquals(List.of(), feature.riskFlags());
    }
}
