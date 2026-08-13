package com.awe.apex.quant.decision;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 市场状态评估器
 */
@Component
public class MarketRegimeEvaluator {

    private static final BigDecimal ATTACK_BREADTH = new BigDecimal("0.55");
    private static final BigDecimal DEFENSE_BREADTH = new BigDecimal("0.45");
    private static final BigDecimal DEFENSE_DRAWDOWN = new BigDecimal("0.08");
    private static final BigDecimal HIGH_VOLATILITY = new BigDecimal("0.80");

    /**
     * 根据预先计算的市场指标评估市场状态
     *
     * @param marketRegimeInput 市场状态评估入参
     * @return 市场状态评估结果
     */
    public MarketRegimeResult evaluate(MarketRegimeInput marketRegimeInput) {
        // 1. 核心数据缺失或比例越界时失败关闭
        if (Objects.isNull(marketRegimeInput)
                || !Boolean.TRUE.equals(marketRegimeInput.getDataComplete())
                || Objects.isNull(marketRegimeInput.getIndexAboveMa60())
                || Objects.isNull(marketRegimeInput.getMa20AboveMa60())
                || Objects.isNull(marketRegimeInput.getMa20SlopeUp())
                || Objects.isNull(marketRegimeInput.getBreadthPct())
                || Objects.isNull(marketRegimeInput.getDrawdown20())
                || Objects.isNull(marketRegimeInput.getVolatilityPercentile())
                || marketRegimeInput.getBreadthPct().compareTo(BigDecimal.ZERO) < 0
                || marketRegimeInput.getBreadthPct().compareTo(BigDecimal.ONE) > 0
                || marketRegimeInput.getDrawdown20().compareTo(BigDecimal.ZERO) < 0
                || marketRegimeInput.getDrawdown20().compareTo(BigDecimal.ONE) > 0
                || marketRegimeInput.getVolatilityPercentile().compareTo(BigDecimal.ZERO) < 0
                || marketRegimeInput.getVolatilityPercentile().compareTo(BigDecimal.ONE) > 0) {
            return MarketRegimeResult.builder()
                    .marketRegime(MarketRegimeEnum.UNKNOWN)
                    .totalExposureLimit(MarketRegimeEnum.UNKNOWN.getTotalExposureLimit())
                    .reason("市场核心数据不完整")
                    .build();
        }

        // 2. 防守门禁优先于进攻条件，避免大回撤或高波动期间误开进攻仓位
        boolean weakIndexAndBreadth = !Boolean.TRUE.equals(marketRegimeInput.getIndexAboveMa60())
                && marketRegimeInput.getBreadthPct().compareTo(DEFENSE_BREADTH) < 0;
        boolean drawdownDefense = marketRegimeInput.getDrawdown20().compareTo(DEFENSE_DRAWDOWN) >= 0;
        boolean volatilityDefense = marketRegimeInput.getVolatilityPercentile().compareTo(HIGH_VOLATILITY) >= 0
                && marketRegimeInput.getBreadthPct().compareTo(DEFENSE_BREADTH) < 0;
        if (weakIndexAndBreadth || drawdownDefense || volatilityDefense) {
            return MarketRegimeResult.builder()
                    .marketRegime(MarketRegimeEnum.DEFENSE)
                    .totalExposureLimit(MarketRegimeEnum.DEFENSE.getTotalExposureLimit())
                    .reason("市场风险条件触发防守状态")
                    .build();
        }

        // 3. 趋势和市场宽度同时转强时进入进攻状态，其余保持均衡
        boolean attack = Boolean.TRUE.equals(marketRegimeInput.getIndexAboveMa60())
                && Boolean.TRUE.equals(marketRegimeInput.getMa20AboveMa60())
                && Boolean.TRUE.equals(marketRegimeInput.getMa20SlopeUp())
                && marketRegimeInput.getBreadthPct().compareTo(ATTACK_BREADTH) >= 0;
        MarketRegimeEnum marketRegime = attack ? MarketRegimeEnum.ATTACK : MarketRegimeEnum.BALANCE;
        return MarketRegimeResult.builder()
                .marketRegime(marketRegime)
                .totalExposureLimit(marketRegime.getTotalExposureLimit())
                .reason(attack ? "趋势与市场宽度同步转强" : "市场未触发进攻或防守条件")
                .build();
    }
}
