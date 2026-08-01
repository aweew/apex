package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 建议仓位（按风控上限）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PositionSuggestResp {

    /**
     * 代码
     */
    private String code;

    /**
     * 参考价
     */
    private BigDecimal price;

    /**
     * 总资产
     */
    private BigDecimal totalAsset;

    /**
     * 可用现金
     */
    private BigDecimal cash;

    /**
     * 单票仓位上限
     */
    private BigDecimal singleLimit;

    /**
     * 建议目标仓位
     */
    private BigDecimal targetWeight;

    /**
     * 建议股数（整百）
     */
    private Integer suggestedQuantity;

    /**
     * 预计金额
     */
    private BigDecimal estimatedAmount;

    /**
     * 近20日均成交额
     */
    private BigDecimal avgDailyAmount;

    /**
     * 预计成交占均额比例
     */
    private BigDecimal participationRate;

    /**
     * 流动性是否可接受（默认参与率≤5%）
     */
    private Boolean liquidityOk;

    /**
     * ATR14
     */
    private BigDecimal atr14;

    /**
     * 风险预算股数（风险金额/ATR止损距，整百）
     */
    private Integer riskBudgetQuantity;

    /**
     * 单笔风险金额（总资产×risk_per_trade）
     */
    private BigDecimal riskBudgetAmount;

    /**
     * 说明
     */
    private String message;
}
