package com.awe.apex.quant.decision;

import com.awe.apex.quant.domain.dto.DecisionItemResp;
import com.awe.apex.quant.domain.dto.MarketBriefingResp;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Component
public class DecisionFeatureBuilder {

    public DecisionFeature build(DecisionItemResp item, int hotSourceCount, MarketBriefingResp briefing) {
        DecisionFeatureInput input = new DecisionFeatureInput(
                item.getScore(), false, false, false, null,
                briefing.getBuyWeightFactor(), null, false);
        return build(item, new DecisionFeatureSource(input, hotSourceCount, briefing));
    }

    public DecisionFeature build(DecisionItemResp item, DecisionFeatureSource source) {
        DecisionFeatureInput input = source.scoringInput();
        MarketBriefingResp briefing = source.briefing();
        int hotSourceCount = source.hotSourceCount();
        List<String> riskFlags = sortedRiskFlags(item.getRiskFlags());
        String canonical = String.join("|",
                text(item.getCode()),
                text(item.getAction()),
                text(item.getStrategyId()),
                number(input.signalScore()),
                number(item.getScore()),
                number(item.getSuggestedWeight()),
                text(item.getConfluenceCount()),
                String.valueOf(hotSourceCount),
                text(item.getMainlineMatch()),
                text(item.getValuationLevel()),
                number(item.getValuationScore()),
                text(briefing.getStance()),
                text(briefing.getDataLevel()),
                text(item.getExecutableHint()),
                String.valueOf(input.fundExclude()),
                String.valueOf(input.fundWeak()),
                String.valueOf(input.offMainline()),
                text(input.valuationScoreDelta()),
                number(input.buyWeightFactor()),
                number(input.singleLimit()),
                String.valueOf(input.observeOnly()),
                String.join(",", riskFlags));
        return new DecisionFeature(
                item.getCode(), item.getAction(), item.getStrategyId(), input.signalScore(), item.getScore(),
                item.getSuggestedWeight(), item.getConfluenceCount(), hotSourceCount,
                item.getMainlineMatch(), item.getValuationLevel(), item.getValuationScore(),
                briefing.getStance(), briefing.getDataLevel(), item.getExecutableHint(),
                input.fundExclude(), input.fundWeak(), input.offMainline(), input.valuationScoreDelta(),
                input.buyWeightFactor(), input.singleLimit(), input.observeOnly(),
                riskFlags, sha256(canonical));
    }

    private List<String> sortedRiskFlags(List<String> riskFlags) {
        if (riskFlags == null || riskFlags.isEmpty()) {
            return List.of();
        }
        List<String> sorted = new ArrayList<>(riskFlags);
        sorted.sort(String::compareTo);
        return List.copyOf(sorted);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private String number(BigDecimal value) {
        return value == null ? "<null>" : value.toPlainString();
    }

    private String text(Object value) {
        return value == null ? "<null>" : String.valueOf(value);
    }
}
