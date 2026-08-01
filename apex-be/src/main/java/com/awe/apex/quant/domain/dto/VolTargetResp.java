package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 波动目标仓位缩放
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VolTargetResp {

    /**
     * 目标年化波动
     */
    private BigDecimal targetAnnVol;

    /**
     * 当前实现年化波动
     */
    private BigDecimal realizedAnnVol;

    /**
     * 缩放系数（0~1+，上限 1.5）
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
