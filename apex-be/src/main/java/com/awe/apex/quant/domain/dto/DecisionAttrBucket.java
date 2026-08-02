package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 决策复盘归因桶
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionAttrBucket {

    /**
     * 分桶键（策略ID / 共振 / 主线 / 立场）
     */
    private String key;

    /**
     * 显示名
     */
    private String label;

    /**
     * 样本数（买入建议条数）
     */
    private Integer sampleCount;

    /**
     * 有次日收益的样本数
     */
    private Integer measuredCount;

    /**
     * 次日平均涨跌幅%
     */
    private BigDecimal avgNextPct;

    /**
     * 胜率（次日涨>0）
     */
    private BigDecimal winRate;
}
