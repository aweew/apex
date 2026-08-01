package com.awe.apex.quant.domain.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 保存我的持仓
 */
@Data
public class MyHoldingSaveReq {

    /**
     * 主键，更新时必填
     */
    private Long id;

    /**
     * 证券代码
     */
    private String code;

    /**
     * 简称（可空，自动补全）
     */
    private String name;

    /**
     * 持仓数量（股）
     */
    private Integer quantity;

    /**
     * 成本价
     */
    private BigDecimal costPrice;

    /**
     * 止损价
     */
    private BigDecimal stopLoss;

    /**
     * 止盈价
     */
    private BigDecimal takeProfit;

    /**
     * 备注
     */
    private String note;
}
