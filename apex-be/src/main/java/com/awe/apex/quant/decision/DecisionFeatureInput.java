package com.awe.apex.quant.decision;

import java.math.BigDecimal;

public record DecisionFeatureInput(
        BigDecimal signalScore,
        boolean fundExclude,
        boolean fundWeak,
        boolean offMainline,
        Integer valuationScoreDelta,
        BigDecimal buyWeightFactor,
        BigDecimal singleLimit,
        boolean observeOnly) {
}
