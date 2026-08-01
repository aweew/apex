package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 参数扫描单项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParamSweepItemResp {

    /**
     * 策略ID
     */
    private String strategyId;

    /**
     * 快线
     */
    private Integer fast;

    /**
     * 慢线
     */
    private Integer slow;

    /**
     * 累计收益
     */
    private BigDecimal totalReturn;

    /**
     * 最大回撤
     */
    private BigDecimal maxDrawdown;

    /**
     * 夏普
     */
    private BigDecimal sharpe;

    /**
     * 成交次数
     */
    private Integer tradeCount;

    /**
     * 错误信息
     */
    private String error;
}
