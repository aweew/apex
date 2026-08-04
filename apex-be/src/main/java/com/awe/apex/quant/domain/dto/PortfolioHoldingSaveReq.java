package com.awe.apex.quant.domain.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 保存组合持仓
 */
@Data
public class PortfolioHoldingSaveReq {

    /**
     * 主键，更新时可选
     */
    private Long id;

    /**
     * 证券代码
     */
    private String code;

    /**
     * 简称
     */
    private String name;

    /**
     * 数量
     */
    private Integer quantity;

    /**
     * 成本价
     */
    private BigDecimal costPrice;

    /**
     * 止损
     */
    private BigDecimal stopLoss;

    /**
     * 止盈
     */
    private BigDecimal takeProfit;

    /**
     * 备注
     */
    private String note;
}
