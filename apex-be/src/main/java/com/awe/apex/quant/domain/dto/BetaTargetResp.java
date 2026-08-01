package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 组合 Beta 目标
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BetaTargetResp {

    /**
     * 目标 Beta
     */
    private BigDecimal targetBeta;

    /**
     * 当前 Beta
     */
    private BigDecimal currentBeta;

    /**
     * 建议仓位缩放（使 Beta→目标）
     */
    private BigDecimal scale;

    /**
     * 当前仓位
     */
    private BigDecimal currentPositionRatio;

    /**
     * 建议仓位
     */
    private BigDecimal suggestedPositionRatio;

    /**
     * 说明
     */
    private String message;
}
