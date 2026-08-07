package com.awe.apex.quant.decision;

import com.awe.apex.quant.domain.dto.MarketBriefingResp;

public record DecisionFeatureSource(
        DecisionFeatureInput scoringInput,
        int hotSourceCount,
        MarketBriefingResp briefing) {
}
