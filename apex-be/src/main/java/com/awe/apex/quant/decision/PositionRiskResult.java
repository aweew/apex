package com.awe.apex.quant.decision;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 风险预算仓位计算结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PositionRiskResult {

    /**
     * 是否允许新开仓或加仓
     */
    private Boolean canOpenPosition;

    /**
     * 计算后的单票目标权重
     */
    private BigDecimal targetWeight;

    /**
     * 本次允许增加的权重
     */
    private BigDecimal incrementalWeight;

    /**
     * 本次最大风险金额
     */
    private BigDecimal maxRiskAmount;

    /**
     * 回撤门禁调整后的总仓上限
     */
    private BigDecimal totalExposureLimit;

    /**
     * 决策原因
     */
    private String reason;
}
