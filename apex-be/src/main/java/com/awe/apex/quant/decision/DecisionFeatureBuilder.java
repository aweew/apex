package com.awe.apex.quant.decision;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.exception.BusinessException;
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
import java.util.Objects;

@Component
public class DecisionFeatureBuilder {

    /**
     * 使用默认入选状态构建特征
     *
     * @param item           决策条目
     * @param hotSourceCount 热点来源数
     * @param briefing       市场简报
     * @return 决策特征
     */
    public DecisionFeature build(DecisionItemResp item, int hotSourceCount, MarketBriefingResp briefing) {
        DecisionFeatureInput input = DecisionFeatureInput.builder()
                .signalScore(item.getScore())
                .buyWeightFactor(briefing.getBuyWeightFactor())
                .build();
        return build(item, DecisionFeatureSource.builder()
                .scoringInput(input)
                .hotSourceCount(hotSourceCount)
                .briefing(briefing)
                .selectionStatus("SELECTED")
                .build());
    }

    /**
     * 构建可追溯的候选特征
     *
     * @param item   决策候选
     * @param source 特征来源
     * @return 决策特征
     */
    public DecisionFeature build(DecisionItemResp item, DecisionFeatureSource source) {
        DecisionFeatureInput input = source.getScoringInput();
        MarketBriefingResp briefing = source.getBriefing();
        int hotSourceCount = source.getHotSourceCount();
        List<String> riskFlags = sortedRiskFlags(item.getRiskFlags());
        String canonical = String.join("|",
                text(item.getCode()),
                text(item.getAction()),
                text(item.getStrategyId()),
                number(input.getSignalScore()),
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
                String.valueOf(input.isFundExclude()),
                String.valueOf(input.isFundWeak()),
                String.valueOf(input.isOffMainline()),
                text(input.getValuationScoreDelta()),
                number(input.getBuyWeightFactor()),
                number(input.getSingleLimit()),
                String.valueOf(input.isObserveOnly()),
                text(input.getEntryGatePassed()),
                text(source.getSelectionStatus()),
                text(source.getRejectReason()),
                text(source.getRankNo()),
                String.join(",", riskFlags));
        return DecisionFeature.builder()
                .code(item.getCode())
                .action(item.getAction())
                .strategyId(item.getStrategyId())
                .signalScore(input.getSignalScore())
                .finalScore(item.getScore())
                .suggestedWeight(item.getSuggestedWeight())
                .confluenceCount(item.getConfluenceCount())
                .hotSourceCount(hotSourceCount)
                .mainlineMatch(item.getMainlineMatch())
                .valuationLevel(item.getValuationLevel())
                .valuationScore(item.getValuationScore())
                .marketStance(briefing.getStance())
                .dataQuality(briefing.getDataLevel())
                .executableHint(item.getExecutableHint())
                .fundExclude(input.isFundExclude())
                .fundWeak(input.isFundWeak())
                .offMainline(input.isOffMainline())
                .valuationScoreDelta(input.getValuationScoreDelta())
                .buyWeightFactor(input.getBuyWeightFactor())
                .singleLimit(input.getSingleLimit())
                .observeOnly(input.isObserveOnly())
                .entryGatePassed(input.getEntryGatePassed())
                .selectionStatus(source.getSelectionStatus())
                .rejectReason(source.getRejectReason())
                .rankNo(source.getRankNo())
                .riskFlags(riskFlags)
                .featureHash(sha256(canonical))
                .build();
    }

    private List<String> sortedRiskFlags(List<String> riskFlags) {
        if (CollUtil.isEmpty(riskFlags)) {
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
            throw new BusinessException("决策特征哈希计算失败", ex);
        }
    }

    private String number(BigDecimal value) {
        return Objects.isNull(value) ? "<null>" : value.toPlainString();
    }

    private String text(Object value) {
        return Objects.isNull(value) ? "<null>" : String.valueOf(value);
    }
}
