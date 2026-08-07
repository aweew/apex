package com.awe.apex.quant.decision;

import java.math.BigDecimal;
import java.util.List;

public record DecisionFeature(
        String code,
        String action,
        String strategyId,
        BigDecimal signalScore,
        BigDecimal finalScore,
        BigDecimal suggestedWeight,
        Integer confluenceCount,
        Integer hotSourceCount,
        Boolean mainlineMatch,
        String valuationLevel,
        BigDecimal valuationScore,
        String marketStance,
        String dataQuality,
        Boolean executableHint,
        boolean fundExclude,
        boolean fundWeak,
        boolean offMainline,
        Integer valuationScoreDelta,
        BigDecimal buyWeightFactor,
        BigDecimal singleLimit,
        boolean observeOnly,
        List<String> riskFlags,
        String featureHash) {
}
