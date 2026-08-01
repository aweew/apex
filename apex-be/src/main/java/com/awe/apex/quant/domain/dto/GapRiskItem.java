package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 单票隔夜缺口
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GapRiskItem {

    /**
     * 代码
     */
    private String code;

    /**
     * 名称
     */
    private String name;

    /**
     * 近20日平均 |缺口|%
     */
    private BigDecimal avgAbsGapPct;

    /**
     * 近20日最大 |缺口|%
     */
    private BigDecimal maxAbsGapPct;

    /**
     * 最近一日缺口%
     */
    private BigDecimal lastGapPct;
}
