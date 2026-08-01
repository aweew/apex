package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Kelly 仓位建议
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KellySuggestResp {

    /**
     * 胜率
     */
    private BigDecimal winRate;

    /**
     * 盈亏比
     */
    private BigDecimal payoffRatio;

    /**
     * 全 Kelly 比例
     */
    private BigDecimal fullKelly;

    /**
     * 半 Kelly（常用保守）
     */
    private BigDecimal halfKelly;

    /**
     * 建议单票权重上限（与风控取小）
     */
    private BigDecimal suggestedWeight;

    /**
     * 当前单票风控上限
     */
    private BigDecimal singleStockLimit;

    /**
     * 说明
     */
    private String message;
}
