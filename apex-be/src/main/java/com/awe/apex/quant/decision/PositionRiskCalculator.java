package com.awe.apex.quant.decision;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 风险预算仓位计算器
 */
@Component
public class PositionRiskCalculator {

    private static final BigDecimal MIN_STOP_DISTANCE = new BigDecimal("0.03");
    private static final BigDecimal MAX_STOP_DISTANCE = new BigDecimal("0.10");
    private static final BigDecimal CORRELATION_CLUSTER_LIMIT = new BigDecimal("0.20");
    private static final BigDecimal FIVE_PERCENT_DRAWDOWN = new BigDecimal("0.05");
    private static final BigDecimal EIGHT_PERCENT_DRAWDOWN = new BigDecimal("0.08");
    private static final BigDecimal TWELVE_PERCENT_DRAWDOWN = new BigDecimal("0.12");
    private static final BigDecimal FIFTEEN_PERCENT_DRAWDOWN = new BigDecimal("0.15");

    /**
     * 根据市场状态、回撤门禁和组合约束计算允许新增的仓位
     *
     * @param positionRiskInput 风险预算仓位计算入参
     * @return 风险预算仓位计算结果
     */
    public PositionRiskResult calculate(PositionRiskInput positionRiskInput) {
        // 1. 入参不完整或数值越界时失败关闭
        if (Objects.isNull(positionRiskInput)
                || Objects.isNull(positionRiskInput.getEquity())
                || Objects.isNull(positionRiskInput.getCash())
                || Objects.isNull(positionRiskInput.getCurrentExposure())
                || Objects.isNull(positionRiskInput.getCurrentDrawdown())
                || Objects.isNull(positionRiskInput.getTotalExposureLimit())
                || Objects.isNull(positionRiskInput.getMarketRegime())
                || Objects.isNull(positionRiskInput.getSingleLimit())
                || Objects.isNull(positionRiskInput.getIndustryLimit())
                || Objects.isNull(positionRiskInput.getCurrentWeight())
                || Objects.isNull(positionRiskInput.getIndustryWeight())
                || Objects.isNull(positionRiskInput.getStopDistance())
                || Objects.isNull(positionRiskInput.getCorrelationClusterWeight())
                || Objects.isNull(positionRiskInput.getLotValue())
                || positionRiskInput.getEquity().compareTo(BigDecimal.ZERO) <= 0
                || positionRiskInput.getCash().compareTo(BigDecimal.ZERO) < 0
                || positionRiskInput.getCurrentExposure().compareTo(BigDecimal.ZERO) < 0
                || positionRiskInput.getCurrentExposure().compareTo(BigDecimal.ONE) > 0
                || positionRiskInput.getCurrentDrawdown().compareTo(BigDecimal.ZERO) < 0
                || positionRiskInput.getCurrentDrawdown().compareTo(BigDecimal.ONE) > 0
                || positionRiskInput.getTotalExposureLimit().compareTo(BigDecimal.ZERO) <= 0
                || positionRiskInput.getTotalExposureLimit().compareTo(BigDecimal.ONE) > 0
                || positionRiskInput.getSingleLimit().compareTo(BigDecimal.ZERO) <= 0
                || positionRiskInput.getSingleLimit().compareTo(BigDecimal.ONE) > 0
                || positionRiskInput.getIndustryLimit().compareTo(BigDecimal.ZERO) <= 0
                || positionRiskInput.getIndustryLimit().compareTo(BigDecimal.ONE) > 0
                || positionRiskInput.getCurrentWeight().compareTo(BigDecimal.ZERO) < 0
                || positionRiskInput.getCurrentWeight().compareTo(BigDecimal.ONE) > 0
                || positionRiskInput.getIndustryWeight().compareTo(BigDecimal.ZERO) < 0
                || positionRiskInput.getIndustryWeight().compareTo(BigDecimal.ONE) > 0
                || positionRiskInput.getStopDistance().compareTo(BigDecimal.ZERO) <= 0
                || positionRiskInput.getCorrelationClusterWeight().compareTo(BigDecimal.ZERO) < 0
                || positionRiskInput.getCorrelationClusterWeight().compareTo(BigDecimal.ONE) > 0
                || positionRiskInput.getLotValue().compareTo(BigDecimal.ZERO) <= 0) {
            return buildRejectedResult(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, "风险预算入参不完整或数值越界");
        }

        BigDecimal baseExposureLimit = positionRiskInput.getMarketRegime().getTotalExposureLimit()
                .min(positionRiskInput.getTotalExposureLimit());
        BigDecimal totalExposureLimit = resolveExposureLimit(
                baseExposureLimit, positionRiskInput.getCurrentDrawdown());
        if (positionRiskInput.getCurrentDrawdown().compareTo(FIFTEEN_PERCENT_DRAWDOWN) >= 0) {
            return buildRejectedResult(positionRiskInput.getCurrentWeight(), BigDecimal.ZERO, BigDecimal.ZERO,
                    totalExposureLimit, "当前回撤达到15%，仅允许减仓或卖出");
        }
        if (positionRiskInput.getCurrentDrawdown().compareTo(TWELVE_PERCENT_DRAWDOWN) >= 0) {
            return buildRejectedResult(positionRiskInput.getCurrentWeight(), BigDecimal.ZERO, BigDecimal.ZERO,
                    totalExposureLimit, "当前回撤达到12%，停止新开仓");
        }
        if (MarketRegimeEnum.UNKNOWN.equals(positionRiskInput.getMarketRegime())) {
            return buildRejectedResult(positionRiskInput.getCurrentWeight(), BigDecimal.ZERO, BigDecimal.ZERO,
                    totalExposureLimit, "市场数据不完整，禁止新开仓");
        }
        if (positionRiskInput.getStopDistance().compareTo(MAX_STOP_DISTANCE) > 0) {
            return buildRejectedResult(positionRiskInput.getCurrentWeight(), BigDecimal.ZERO, BigDecimal.ZERO,
                    totalExposureLimit, "止损距离超过10%，禁止新开仓");
        }

        // 2. 市场风险预算随当前组合回撤收缩
        BigDecimal drawdownMultiplier = BigDecimal.ONE;
        if (positionRiskInput.getCurrentDrawdown().compareTo(EIGHT_PERCENT_DRAWDOWN) >= 0) {
            drawdownMultiplier = new BigDecimal("0.50");
        } else if (positionRiskInput.getCurrentDrawdown().compareTo(FIVE_PERCENT_DRAWDOWN) >= 0) {
            drawdownMultiplier = new BigDecimal("0.75");
        }
        BigDecimal maxRiskAmount = positionRiskInput.getEquity()
                .multiply(positionRiskInput.getMarketRegime().getSingleRiskRate())
                .multiply(drawdownMultiplier);
        BigDecimal effectiveStopDistance = positionRiskInput.getStopDistance().max(MIN_STOP_DISTANCE);
        BigDecimal riskTargetWeight = maxRiskAmount.divide(positionRiskInput.getEquity(), 12, RoundingMode.DOWN)
                .divide(effectiveStopDistance, 12, RoundingMode.DOWN);
        BigDecimal unconstrainedTargetWeight = riskTargetWeight.min(positionRiskInput.getSingleLimit());
        BigDecimal desiredIncrementalWeight = unconstrainedTargetWeight.subtract(positionRiskInput.getCurrentWeight());
        if (desiredIncrementalWeight.compareTo(BigDecimal.ZERO) <= 0) {
            return buildRejectedResult(positionRiskInput.getCurrentWeight(), BigDecimal.ZERO, maxRiskAmount,
                    totalExposureLimit, "当前持仓已达到风险预算目标");
        }

        // 3. 依次收紧总仓、单票、行业、相关簇与现金约束
        List<BigDecimal> incrementalLimits = new ArrayList<>();
        incrementalLimits.add(desiredIncrementalWeight);
        incrementalLimits.add(totalExposureLimit.subtract(positionRiskInput.getCurrentExposure()).max(BigDecimal.ZERO));
        incrementalLimits.add(positionRiskInput.getSingleLimit().subtract(positionRiskInput.getCurrentWeight())
                .max(BigDecimal.ZERO));
        incrementalLimits.add(positionRiskInput.getIndustryLimit().subtract(positionRiskInput.getIndustryWeight())
                .max(BigDecimal.ZERO));
        incrementalLimits.add(CORRELATION_CLUSTER_LIMIT.subtract(positionRiskInput.getCorrelationClusterWeight())
                .max(BigDecimal.ZERO));
        incrementalLimits.add(positionRiskInput.getCash().divide(positionRiskInput.getEquity(), 12, RoundingMode.DOWN));

        BigDecimal allowedIncrementalWeight = desiredIncrementalWeight;
        for (BigDecimal incrementalLimit : incrementalLimits) {
            allowedIncrementalWeight = allowedIncrementalWeight.min(incrementalLimit);
        }

        // 4. 按一百股对应金额向下取整，避免仓位建议不可成交
        BigDecimal allowedAmount = allowedIncrementalWeight.multiply(positionRiskInput.getEquity());
        BigDecimal lotCount = allowedAmount.divide(positionRiskInput.getLotValue(), 0, RoundingMode.DOWN);
        BigDecimal incrementalAmount = lotCount.multiply(positionRiskInput.getLotValue());
        BigDecimal incrementalWeight = incrementalAmount.divide(positionRiskInput.getEquity(), 12, RoundingMode.DOWN)
                .stripTrailingZeros();
        if (incrementalWeight.compareTo(BigDecimal.ZERO) <= 0) {
            return buildRejectedResult(positionRiskInput.getCurrentWeight(), BigDecimal.ZERO, maxRiskAmount,
                    totalExposureLimit, "可用风险预算不足一手");
        }

        return PositionRiskResult.builder()
                .canOpenPosition(true)
                .targetWeight(positionRiskInput.getCurrentWeight().add(incrementalWeight).stripTrailingZeros())
                .incrementalWeight(incrementalWeight)
                .maxRiskAmount(maxRiskAmount)
                .totalExposureLimit(totalExposureLimit)
                .reason("风险预算通过")
                .build();
    }

    private PositionRiskResult buildRejectedResult(BigDecimal targetWeight,
                                                   BigDecimal incrementalWeight,
                                                   BigDecimal maxRiskAmount,
                                                   BigDecimal totalExposureLimit,
                                                   String reason) {
        return PositionRiskResult.builder()
                .canOpenPosition(false)
                .targetWeight(targetWeight)
                .incrementalWeight(incrementalWeight)
                .maxRiskAmount(maxRiskAmount)
                .totalExposureLimit(totalExposureLimit)
                .reason(reason)
                .build();
    }

    static BigDecimal resolveExposureLimit(BigDecimal baseExposureLimit, BigDecimal drawdown) {
        if (drawdown.compareTo(FIFTEEN_PERCENT_DRAWDOWN) >= 0) {
            return baseExposureLimit.min(new BigDecimal("0.10"));
        }
        if (drawdown.compareTo(TWELVE_PERCENT_DRAWDOWN) >= 0) {
            return baseExposureLimit.min(new BigDecimal("0.20"));
        }
        if (drawdown.compareTo(EIGHT_PERCENT_DRAWDOWN) >= 0) {
            return baseExposureLimit.multiply(new BigDecimal("0.50"));
        }
        if (drawdown.compareTo(FIVE_PERCENT_DRAWDOWN) >= 0) {
            return baseExposureLimit.multiply(new BigDecimal("0.75"));
        }
        return baseExposureLimit;
    }
}
