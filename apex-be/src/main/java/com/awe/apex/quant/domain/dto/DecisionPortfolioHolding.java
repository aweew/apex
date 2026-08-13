package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 决策时点的组合持仓明细
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionPortfolioHolding {

    /**
     * 证券代码
     */
    private String code;

    /**
     * 证券简称
     */
    private String name;

    /**
     * 持仓数量
     */
    private Integer quantity;

    /**
     * 持仓成本价
     */
    private BigDecimal costPrice;

    /**
     * 决策时点价格
     */
    private BigDecimal marketPrice;

    /**
     * 决策时点市值
     */
    private BigDecimal marketValue;

    /**
     * 止损价
     */
    private BigDecimal stopLoss;

    /**
     * 止盈价
     */
    private BigDecimal takeProfit;

    /**
     * 所属行业
     */
    private String industry;
}
