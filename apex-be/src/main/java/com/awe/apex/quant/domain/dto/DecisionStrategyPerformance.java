package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 决策策略历史表现
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionStrategyPerformance {

    /**
     * 策略ID
     */
    private String strategyId;

    /**
     * 完整样本数
     */
    private Integer sampleCount;

    /**
     * 五日平均超额收益率
     */
    private BigDecimal avgExcess5d;

    /**
     * 五日超额胜率
     */
    private BigDecimal winRate5d;
}
