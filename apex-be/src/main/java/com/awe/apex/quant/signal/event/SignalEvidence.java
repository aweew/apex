package com.awe.apex.quant.signal.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 市场行为信号量化证据。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalEvidence {

    /** 前序阻力价 */
    private BigDecimal resistancePrice;

    /** 本次有效突破价位。 */
    private BigDecimal breakoutPrice;

    /** 本次回踩最低价。 */
    private BigDecimal pullbackPrice;

    /** 当前价格相对突破价位的距离百分比。 */
    private BigDecimal distancePct;

    /** 前序支撑价 */
    private BigDecimal supportPrice;

    /** ATR14 */
    private BigDecimal atr14;

    /** 20日量比 */
    private BigDecimal volumeRatio;

    /** 收盘在当日振幅内的位置 */
    private BigDecimal closePosition;

    /** 20日均价 */
    private BigDecimal ma20;

    /** 60日均价 */
    private BigDecimal ma60;

    /** 收盘价 */
    private BigDecimal closePrice;

    /** 60日价格区间位置 */
    private BigDecimal rangePosition60;

    /** 计算说明 */
    private String reason;

    /** 触发条件。 */
    private String triggerCondition;

    /** 失效条件。 */
    private String invalidCondition;
}
