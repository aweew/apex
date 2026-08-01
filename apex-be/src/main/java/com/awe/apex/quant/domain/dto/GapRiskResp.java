package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 持仓隔夜缺口风险
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GapRiskResp {

    /**
     * 样本数
     */
    private Integer sampleCount;

    /**
     * 平均 |隔夜缺口|（%）
     */
    private BigDecimal avgAbsGapPct;

    /**
     * 最大 |隔夜缺口|（%）
     */
    private BigDecimal maxAbsGapPct;

    /**
     * 说明
     */
    private String message;

    /**
     * 单票明细
     */
    private List<GapRiskItem> items;
}
