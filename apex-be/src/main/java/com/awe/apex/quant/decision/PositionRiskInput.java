package com.awe.apex.quant.decision;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 风险预算仓位计算入参
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PositionRiskInput {

    /**
     * 组合总权益
     */
    private BigDecimal equity;

    /**
     * 可用现金
     */
    private BigDecimal cash;

    /**
     * 当前总仓位
     */
    private BigDecimal currentExposure;

    /**
     * 当前组合回撤
     */
    private BigDecimal currentDrawdown;

    /**
     * 风控配置总仓上限
     */
    private BigDecimal totalExposureLimit;

    /**
     * 市场状态
     */
    private MarketRegimeEnum marketRegime;

    /**
     * 单票仓位上限
     */
    private BigDecimal singleLimit;

    /**
     * 行业仓位上限
     */
    private BigDecimal industryLimit;

    /**
     * 当前单票仓位
     */
    private BigDecimal currentWeight;

    /**
     * 当前行业仓位
     */
    private BigDecimal industryWeight;

    /**
     * 入场价到止损价的距离比例
     */
    private BigDecimal stopDistance;

    /**
     * 当前相关性簇仓位
     */
    private BigDecimal correlationClusterWeight;

    /**
     * 一百股对应的交易金额
     */
    private BigDecimal lotValue;
}
