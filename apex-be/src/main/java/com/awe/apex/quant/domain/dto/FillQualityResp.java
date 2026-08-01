package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 成交质量评估（相对当日收盘）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FillQualityResp {

    /**
     * 样本订单数
     */
    private Integer sampleCount;

    /**
     * 平均买入滑点（正=买贵）
     */
    private BigDecimal avgBuySlippage;

    /**
     * 平均卖出滑点（正=卖便宜）
     */
    private BigDecimal avgSellSlippage;

    /**
     * 综合质量分 0-100
     */
    private BigDecimal qualityScore;

    /**
     * 说明
     */
    private String message;

    /**
     * 明细（最多 50）
     */
    private List<FillQualityItem> items;
}
