package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 策略对比单项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyCompareItemResp {

    /**
     * 策略
     */
    private String strategyId;

    /**
     * 回测任务ID
     */
    private Long jobId;

    /**
     * 累计收益
     */
    private BigDecimal totalReturn;

    /**
     * 年化
     */
    private BigDecimal annualReturn;

    /**
     * 最大回撤
     */
    private BigDecimal maxDrawdown;

    /**
     * 夏普
     */
    private BigDecimal sharpe;

    /**
     * 胜率
     */
    private BigDecimal winRate;

    /**
     * 成交次数
     */
    private Integer tradeCount;

    /**
     * 错误
     */
    private String error;
}
