package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 综合分析 · 技术面维度
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAnalysisTechResp {

    /**
     * 评估方向 BUY/SELL
     */
    private String side;

    /**
     * 技术雷达项
     */
    private List<ObserveTechSignal> signals;

    /**
     * 命中数
     */
    private Integer hitCount;

    /**
     * 总项数
     */
    private Integer total;

    /**
     * 命中率 0~100
     */
    private BigDecimal hitRate;

    /**
     * 技术面一句话
     */
    private String summary;

    /**
     * 结构状态码：TREND_HOLD / PULLBACK_WATCH / BREAKDOWN_CUT / REPAIR / NEUTRAL / INSUFFICIENT
     */
    private String regime;

    /**
     * 结构状态中文标签
     */
    private String regimeLabel;

    /**
     * 强弱档：STRONG / NEUTRAL / WEAK
     */
    private String grade;

    /**
     * RSI14
     */
    private BigDecimal rsi14;

    /**
     * ATR14
     */
    private BigDecimal atr14;

    /**
     * ATR%
     */
    private BigDecimal atrPct;

    /**
     * MA5
     */
    private BigDecimal ma5;

    /**
     * MA20
     */
    private BigDecimal ma20;

    /**
     * 量比
     */
    private BigDecimal volumeRatio;

    /**
     * RS20 vs 沪深300（百分点）
     */
    private BigDecimal rs20VsHs300;

    /**
     * RS60 vs 沪深300（百分点）
     */
    private BigDecimal rs60VsHs300;
}
