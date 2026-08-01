package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 批量回测单项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchBacktestItemResp {

    /**
     * 代码
     */
    private String code;

    /**
     * 任务ID
     */
    private Long jobId;

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
     * Sortino
     */
    private BigDecimal sortino;

    /**
     * 成交次数
     */
    private Integer tradeCount;

    /**
     * 错误信息
     */
    private String error;
}
