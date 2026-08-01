package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 权益日收益 Bootstrap 蒙特卡洛
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonteCarloResp {

    /**
     * 历史日收益样本数
     */
    private Integer sampleDays;

    /**
     * 路径数
     */
    private Integer paths;

    /**
     * 每路径长度（交易日）
     */
    private Integer horizonDays;

    /**
     * 终值收益 P5
     */
    private BigDecimal terminalReturnP5;

    /**
     * 终值收益中位
     */
    private BigDecimal terminalReturnP50;

    /**
     * 终值收益 P95
     */
    private BigDecimal terminalReturnP95;

    /**
     * 路径最大回撤均值
     */
    private BigDecimal avgMaxDrawdown;

    /**
     * 路径最大回撤 P95（更差一侧）
     */
    private BigDecimal maxDrawdownP95;

    /**
     * 说明
     */
    private String message;
}
