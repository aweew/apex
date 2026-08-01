package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 组合简易因子暴露
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FactorExposureResp {

    /**
     * 加权动量20日
     */
    private BigDecimal momentum20;

    /**
     * 加权波动20日（年化近似）
     */
    private BigDecimal volatility20;

    /**
     * 加权相对沪深300的RS20
     */
    private BigDecimal rs20VsHs300;

    /**
     * 现金权重
     */
    private BigDecimal cashWeight;

    /**
     * 股票权重
     */
    private BigDecimal stockWeight;

    /**
     * 说明
     */
    private String message;

    /**
     * 单票明细
     */
    private List<FactorExposureItem> items;
}
