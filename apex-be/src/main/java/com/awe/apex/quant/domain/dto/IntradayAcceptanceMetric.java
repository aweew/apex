package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 分时均价承接指标
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntradayAcceptanceMetric {

    /** 均价线上方分钟占比 */
    private BigDecimal aboveAvgRatio;

    /** 当前价是否不低于均价 */
    private Boolean currentAboveAvg;

    /** 最大连续跌破均价分钟数 */
    private Integer maxConsecutiveBelowMinutes;

    /** 参与计算的分钟点数 */
    private Integer pointCount;

    /** 最后一个分钟点 */
    private String latestTime;
}
