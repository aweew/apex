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
        assertNotNull(first.getFeatureHash());
        assertEquals(64, first.getFeatureHash().length());
    }

    @Test
    void keepsMissingValuesExplicit() {
        DecisionFeature feature = builder.build(
                DecisionItemResp.builder().code("600000").action("HOLD").build(),
                0,
                MarketBriefingResp.builder().dataLevel("YELLOW").build());

        assertEquals("600000", feature.getCode());
        assertEquals("HOLD", feature.getAction());
        assertEquals("YELLOW", feature.getDataQuality());
        assertEquals(List.of(), feature.getRiskFlags());
    }

    @Test
    void selectionStatusChangesFeatureHash() {
        DecisionItemResp item = DecisionItemResp.builder()
                .code("000001")
                .action("BUY")
                .strategyId("S2")
                .score(new BigDecimal("80"))
                .build();
        MarketBriefingResp briefing = MarketBriefingResp.builder()
                .stance("均衡")
                .dataLevel("GREEN")
                .build();

        DecisionFeature selected = builder.build(item, DecisionFeatureSource.builder()
                .scoringInput(DecisionFeatureInput.builder().signalScore(new BigDecimal("70")).build())
                .hotSourceCount(0)
                .briefing(briefing)
                .selectionStatus("SELECTED")
                .rankNo(1)
                .build());
        DecisionFeature rejected = builder.build(item, DecisionFeatureSource.builder()
                .scoringInput(DecisionFeatureInput.builder().signalScore(new BigDecimal("70")).build())
                .hotSourceCount(0)
                .briefing(briefing)
                .selectionStatus("REJECTED")
                .rejectReason("基本面未过门槛")
                .rankNo(1)
                .build());

        assertEquals("SELECTED", selected.getSelectionStatus());
        assertEquals("REJECTED", rejected.getSelectionStatus());
        assertNotNull(rejected.getRejectReason());
        assertEquals(1, selected.getRankNo());
        org.junit.jupiter.api.Assertions.assertNotEquals(selected.getFeatureHash(), rejected.getFeatureHash());
    }

    @Test
    void entryGateStatusChangesFeatureHash() {
        DecisionItemResp item = DecisionItemResp.builder()
                .code("000001")
                .action("BUY")
                .strategyId("S1")
                .score(new BigDecimal("90"))
                .build();
        MarketBriefingResp briefing = MarketBriefingResp.builder().dataLevel("GREEN").build();

        DecisionFeature passed = builder.build(item, DecisionFeatureSource.builder()
                .scoringInput(DecisionFeatureInput.builder().entryGatePassed(true).build())
                .briefing(briefing)
                .selectionStatus("SELECTED")
                .build());
        DecisionFeature blocked = builder.build(item, DecisionFeatureSource.builder()
                .scoringInput(DecisionFeatureInput.builder().entryGatePassed(false).build())
                .briefing(briefing)
                .selectionStatus("WATCH")
                .build());

        assertEquals(Boolean.TRUE, passed.getEntryGatePassed());
        assertEquals(Boolean.FALSE, blocked.getEntryGatePassed());
        org.junit.jupiter.api.Assertions.assertNotEquals(passed.getFeatureHash(), blocked.getFeatureHash());
    }
}
