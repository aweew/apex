package com.awe.apex.quant.decision;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.DecisionItemResp;
import com.awe.apex.quant.domain.dto.DecisionPortfolioHolding;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 组合回撤降仓动作生成器
 */
@Component
public class PortfolioDrawdownReducer {

    /**
     * 按组合回撤后的总仓上限生成等比例减仓动作
     *
     * @param actionDate        决策日
     * @param holdings          冻结持仓快照
     * @param totalEquity       冻结总权益
     * @param currentExposure   当前总仓位
     * @param baseExposureLimit 市场状态与配置共同约束的基础上限
     * @param drawdown          当前组合回撤
     * @return 减仓动作
     */
    public List<DecisionItemResp> buildReductionActions(LocalDate actionDate,
                                                        List<DecisionPortfolioHolding> holdings,
                                                        BigDecimal totalEquity,
                                                        BigDecimal currentExposure,
                                                        BigDecimal baseExposureLimit,
                                                        BigDecimal drawdown) {
        return buildReductionActions(actionDate, holdings, totalEquity, currentExposure,
                baseExposureLimit, drawdown, Set.of());
    }

    /**
     * 按组合回撤后的总仓上限生成等比例减仓动作，忽略已有全卖动作的证券
     *
     * @param actionDate        决策日
     * @param holdings          冻结持仓快照
     * @param totalEquity       冻结总权益
     * @param currentExposure   当前总仓位
     * @param baseExposureLimit 市场状态与配置共同约束的基础上限
     * @param drawdown          当前组合回撤
     * @param excludedCodes     已有全卖动作的证券代码
     * @return 减仓动作
     */
    public List<DecisionItemResp> buildReductionActions(LocalDate actionDate,
                                                        List<DecisionPortfolioHolding> holdings,
                                                        BigDecimal totalEquity,
                                                        BigDecimal currentExposure,
                                                        BigDecimal baseExposureLimit,
                                                        BigDecimal drawdown,
                                                        Set<String> excludedCodes) {
        List<DecisionItemResp> reductionActions = new ArrayList<>();
        if (CollUtil.isEmpty(holdings) || Objects.isNull(totalEquity) || totalEquity.signum() <= 0
                || Objects.isNull(currentExposure) || currentExposure.signum() <= 0
                || Objects.isNull(baseExposureLimit) || Objects.isNull(drawdown)) {
            return reductionActions;
        }
        BigDecimal targetExposureLimit = PositionRiskCalculator.resolveExposureLimit(
                baseExposureLimit, drawdown);
        if (currentExposure.compareTo(targetExposureLimit) <= 0) {
            return reductionActions;
        }

        BigDecimal excludedExposure = BigDecimal.ZERO;
        if (CollUtil.isNotEmpty(excludedCodes)) {
            for (DecisionPortfolioHolding holding : holdings) {
                if (Objects.nonNull(holding) && excludedCodes.contains(holding.getCode())
                        && Objects.nonNull(holding.getMarketValue())) {
                    excludedExposure = excludedExposure.add(holding.getMarketValue()
                            .divide(totalEquity, 12, RoundingMode.HALF_UP));
                }
            }
        }
        BigDecimal remainingExposure = currentExposure.subtract(excludedExposure).max(BigDecimal.ZERO);
        if (remainingExposure.compareTo(targetExposureLimit) <= 0) {
            return reductionActions;
        }
        BigDecimal retentionRatio = targetExposureLimit.divide(
                remainingExposure, 12, RoundingMode.DOWN);
        String reason = "组合回撤" + percentText(drawdown)
                + "，总仓需降至" + percentText(targetExposureLimit);

        for (DecisionPortfolioHolding holding : holdings) {
            if (Objects.isNull(holding) || StringUtils.isBlank(holding.getCode())
                    || excludedCodes.contains(holding.getCode())
                    || Objects.isNull(holding.getMarketValue()) || holding.getMarketValue().signum() <= 0) {
                continue;
            }
            BigDecimal currentWeight = holding.getMarketValue()
                    .divide(totalEquity, 12, RoundingMode.HALF_UP);
            if (Objects.isNull(holding.getQuantity()) || holding.getQuantity() <= 0
                    || Objects.isNull(holding.getMarketPrice()) || holding.getMarketPrice().signum() <= 0) {
                continue;
            }
            BigDecimal targetQuantity = BigDecimal.valueOf(holding.getQuantity())
                    .multiply(retentionRatio)
                    .divide(new BigDecimal("100"), 0, RoundingMode.DOWN)
                    .multiply(new BigDecimal("100"));
            BigDecimal targetWeight = targetQuantity.multiply(holding.getMarketPrice())
                    .divide(totalEquity, 4, RoundingMode.DOWN);
            if (targetWeight.compareTo(currentWeight) >= 0) {
                continue;
            }
            reductionActions.add(DecisionItemResp.builder()
                    .actionDate(actionDate)
                    .code(holding.getCode())
                    .name(holding.getName())
                    .action("REDUCE")
                    .strategyId("RISK")
                    .reason(reason)
                    .score(new BigDecimal("85"))
                    .suggestedWeight(targetWeight)
                    .referencePrice(holding.getMarketPrice())
                    .stopLossPrice(holding.getStopLoss())
                    .takeProfitPrice(holding.getTakeProfit())
                    .exitRule("按目标仓位减仓；保留仓位继续执行原止损止盈")
                    .confluenceCount(0)
                    .confluence(false)
                    .strategies(List.of("RISK"))
                    .scoreExplain(reason + "，按持仓权重等比例收缩")
                    .riskFlags(List.of("组合回撤降仓"))
                    .executableHint(true)
                    .build());
        }
        return reductionActions;
    }

    private String percentText(BigDecimal value) {
        return value.multiply(new BigDecimal("100")).setScale(1, RoundingMode.HALF_UP) + "%";
    }
}
