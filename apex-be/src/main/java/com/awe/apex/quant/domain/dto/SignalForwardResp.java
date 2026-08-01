package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 信号前瞻收益统计
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalForwardResp {

    /**
     * 前瞻交易日数
     */
    private Integer horizonDays;

    /**
     * 样本信号数（可评估）
     */
    private Integer sampleCount;

    /**
     * 胜率（方向正确占比）
     */
    private BigDecimal hitRate;

    /**
     * 平均前瞻收益（BUY 为正收益方向，SELL 取相反）
     */
    private BigDecimal avgForwardReturn;

    /**
     * 中位数前瞻收益
     */
    private BigDecimal medianForwardReturn;

    /**
     * BUY 样本数
     */
    private Integer buyCount;

    /**
     * SELL 样本数
     */
    private Integer sellCount;

    /**
     * 按评分分桶的前瞻期望
     */
    private List<SignalScoreBucketItem> scoreBuckets;

    /**
     * 说明
     */
    private String message;
}
