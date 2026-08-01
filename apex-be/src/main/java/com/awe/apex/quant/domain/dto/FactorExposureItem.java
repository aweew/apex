package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 单票因子
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FactorExposureItem {

    /**
     * 代码
     */
    private String code;

    /**
     * 权重
     */
    private BigDecimal weight;

    /**
     * 动量20
     */
    private BigDecimal momentum20;

    /**
     * 波动20
     */
    private BigDecimal volatility20;

    /**
     * RS20
     */
    private BigDecimal rs20VsHs300;
}
