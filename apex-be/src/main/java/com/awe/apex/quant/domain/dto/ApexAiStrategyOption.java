package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Apex AI 可诊断策略
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApexAiStrategyOption {

    /**
     * 策略ID
     */
    private String strategyId;

    /**
     * 策略显示名称
     */
    private String strategyName;

    /**
     * 已计量样本数量
     */
    private Integer measuredCount;

    /**
     * 次日平均涨跌幅百分比
     */
    private BigDecimal avgNextPct;

    /**
     * 次日胜率百分比
     */
    private BigDecimal winRate;
}
