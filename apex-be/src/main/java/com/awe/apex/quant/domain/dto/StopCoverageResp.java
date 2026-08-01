package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 止损止盈覆盖率
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StopCoverageResp {

    /**
     * 持仓数
     */
    private Integer positionCount;

    /**
     * 已设止损
     */
    private Integer withStopLoss;

    /**
     * 已设止盈
     */
    private Integer withTakeProfit;

    /**
     * 止损覆盖率
     */
    private BigDecimal stopCoverage;

    /**
     * 止盈覆盖率
     */
    private BigDecimal takeCoverage;

    /**
     * 止损距现价平均%（正数=距止损空间）
     */
    private BigDecimal avgStopDistancePct;

    /**
     * 说明
     */
    private String message;
}
