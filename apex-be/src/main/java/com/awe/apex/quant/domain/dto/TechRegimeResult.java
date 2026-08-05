package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 技术结构状态机评估结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TechRegimeResult {

    /**
     * 结构状态：TREND_HOLD / PULLBACK_WATCH / BREAKDOWN_CUT / REPAIR / NEUTRAL / INSUFFICIENT
     */
    private String regime;

    /**
     * 状态中文标签
     */
    private String regimeLabel;

    /**
     * 强弱档：STRONG / NEUTRAL / WEAK
     */
    private String grade;

    /**
     * RS 语气：BULLISH / NEUTRAL / BEARISH
     */
    private String rsTone;

    /**
     * 一句话摘要
     */
    private String summary;

    /**
     * 雷达明细（展示用，不参与主分级）
     */
    private List<ObserveTechSignal> radarSignals;

    /**
     * 雷达命中数
     */
    private Integer hitCount;

    /**
     * 雷达总项数
     */
    private Integer total;

    /**
     * RS20 vs 沪深300（百分点）
     */
    private BigDecimal rs20VsHs300;

    /**
     * RS60 vs 沪深300（百分点）
     */
    private BigDecimal rs60VsHs300;
}
