package com.awe.apex.quant.factor;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.quant.domain.dto.ResearchScoreComponentResp;
import com.awe.apex.quant.domain.dto.ResearchScoreResp;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * 横截面研究评分计算器。
 */
@Component
public class ResearchScoreCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal NEUTRAL_PERCENTILE = new BigDecimal("50");
    private static final BigDecimal MINIMUM_COVERAGE = new BigDecimal("80");

    /**
     * 计算数值在可比组中的升序分位数。
     *
     * @param value 当前数值
     * @param peerValues 可比组数值
     * @return 分位数
     */
    public BigDecimal calculatePercentile(BigDecimal value, List<BigDecimal> peerValues) {
        if (Objects.isNull(value) || CollUtil.isEmpty(peerValues)) {
            return null;
        }
        int validCount = 0;
        int lessOrEqualCount = 0;
        for (BigDecimal peerValue : peerValues) {
            if (Objects.isNull(peerValue)) {
                continue;
            }
            validCount++;
            if (peerValue.compareTo(value) <= 0) {
                lessOrEqualCount++;
            }
        }
        if (validCount == 0) {
            return null;
        }
        return BigDecimal.valueOf(lessOrEqualCount)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(validCount), 2, RoundingMode.HALF_UP);
    }

    /**
     * 汇总各维度的研究评分。
     *
     * @param components 研究维度
     * @return 研究评分与覆盖说明
     */
    public ResearchScoreResp summarize(List<ResearchScoreComponentResp> components) {
        if (CollUtil.isEmpty(components)) {
            return insufficient(BigDecimal.ZERO, components, "尚未发布横截面研究快照");
        }
        BigDecimal availableWeight = BigDecimal.ZERO;
        BigDecimal weightedScore = BigDecimal.ZERO;
        boolean qualityAvailable = false;
        boolean momentumAvailable = false;
        for (ResearchScoreComponentResp component : components) {
            if (!Boolean.TRUE.equals(component.getAvailable()) || Objects.isNull(component.getPercentile())
                    || Objects.isNull(component.getWeight())) {
                continue;
            }
            BigDecimal contribution = component.getPercentile().subtract(NEUTRAL_PERCENTILE)
                    .multiply(component.getWeight())
                    .divide(HUNDRED, 2, RoundingMode.HALF_UP);
            component.setContribution(contribution);
            availableWeight = availableWeight.add(component.getWeight());
            weightedScore = weightedScore.add(component.getPercentile().multiply(component.getWeight()));
            qualityAvailable = qualityAvailable || "QUALITY".equals(component.getKey());
            momentumAvailable = momentumAvailable || "MOMENTUM".equals(component.getKey());
        }
        BigDecimal coverage = availableWeight.setScale(2, RoundingMode.HALF_UP);
        if (!qualityAvailable || !momentumAvailable || coverage.compareTo(MINIMUM_COVERAGE) < 0) {
            return insufficient(coverage, components, "质量和动量为核心维度，且可用权重需达到80%");
        }
        BigDecimal score = weightedScore.divide(availableWeight, 2, RoundingMode.HALF_UP);
        return ResearchScoreResp.builder()
                .status("READY")
                .score(score)
                .label(resolveLabel(score))
                .model("RESEARCH_V2")
                .confidence(coverage.compareTo(HUNDRED) >= 0 ? "HIGH" : "MEDIUM")
                .coverage(coverage)
                .components(components)
                .reason("横截面分位按同日可比组计算；财务披露可用日期尚未入库，本评分不用于历史回测")
                .build();
    }

    private ResearchScoreResp insufficient(BigDecimal coverage, List<ResearchScoreComponentResp> components,
                                            String reason) {
        return ResearchScoreResp.builder()
                .status("INSUFFICIENT")
                .model("RESEARCH_V2")
                .confidence("LOW")
                .coverage(coverage)
                .components(components)
                .reason(reason)
                .build();
    }

    private String resolveLabel(BigDecimal score) {
        if (score.compareTo(new BigDecimal("75")) >= 0) {
            return "优先研究";
        }
        if (score.compareTo(new BigDecimal("60")) >= 0) {
            return "积极跟踪";
        }
        if (score.compareTo(new BigDecimal("40")) >= 0) {
            return "中性观察";
        }
        return "谨慎观察";
    }
}
