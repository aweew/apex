package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 信号评分分桶前瞻
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalScoreBucketItem {

    /**
     * 分桶
     */
    private String bucket;

    /**
     * 样本数
     */
    private Integer sampleCount;

    /**
     * 胜率
     */
    private BigDecimal hitRate;

    /**
     * 平均前瞻收益
     */
    private BigDecimal avgForwardReturn;
}
