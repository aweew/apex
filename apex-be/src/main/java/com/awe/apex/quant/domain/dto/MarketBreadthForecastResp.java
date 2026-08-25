package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 看板展示的盘前涨跌比预测与回测信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketBreadthForecastResp {

    /** 预测是否可用。 */
    private Boolean available;

    /** 预测对应交易日。 */
    private LocalDate tradeDate;

    /** 预测生成时间。 */
    private LocalDateTime generatedAt;

    /** 预测上涨占比，单位百分比。 */
    private BigDecimal predictedUpRatio;

    /** 预测下跌占比，单位百分比。 */
    private BigDecimal predictedDownRatio;

    /** 预测置信度，高/中/低。 */
    private String confidence;

    /** 本次预测采用的盘前依据。 */
    private List<String> reasons;

    /** 已结算历史样本带来的校准值，单位百分点。 */
    private BigDecimal calibrationAdjustment;

    /** 模型版本。 */
    private String modelVersion;

    /** 是否完成收盘回测。 */
    private Boolean settled;

    /** 实际上涨占比，单位百分比。 */
    private BigDecimal actualUpRatio;

    /** 实际下跌占比，单位百分比。 */
    private BigDecimal actualDownRatio;

    /** 预测与实际上涨占比的绝对误差，单位百分点。 */
    private BigDecimal absoluteError;

    /** 涨跌方向是否命中。 */
    private Boolean directionHit;

    /** 本次收盘回测结论。 */
    private String analysisSummary;

    /** 最近已结算样本的滚动回测摘要。 */
    private String rollingBacktestSummary;

    /** 当前不可用或待回测的原因。 */
    private String message;
}
