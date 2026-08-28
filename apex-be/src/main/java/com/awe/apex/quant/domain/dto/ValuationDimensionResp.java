package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 估值单维评分
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValuationDimensionResp {

    /**
     * 维度键 peRelative / pbRelative / peg / dcf / quality / growth
     */
    private String key;

    /**
     * 维度名
     */
    private String name;

    /**
     * 0~100，越高越便宜/越好（质量维为质量分）
     */
    private BigDecimal score;

    /**
     * 权重 0~1
     */
    private BigDecimal weight;

    /**
     * 结论短评
     */
    private String verdict;

    /**
     * 指标参考区间及适用限制
     */
    private String reference;

    /**
     * 明细说明
     */
    private String detail;

    /**
     * 原始指标值（可选）
     */
    private BigDecimal rawValue;
}
