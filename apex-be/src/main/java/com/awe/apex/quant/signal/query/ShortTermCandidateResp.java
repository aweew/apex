package com.awe.apex.quant.signal.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 放量回踩不破短线候选。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortTermCandidateResp {

    /** 证券代码。 */
    private String symbol;

    /** 证券名称。 */
    private String name;

    /** 证券所属市场。 */
    private String market;

    /** 信号编码。 */
    private String signalCode;

    /** CONFIRMED或OBSERVING。 */
    private String state;

    /** 数据日期。 */
    private LocalDate dataAsOf;

    /** 突破价位。 */
    private BigDecimal breakoutPrice;

    /** 本次回踩价位。 */
    private BigDecimal pullbackPrice;

    /** 最新收盘价。 */
    private BigDecimal currentPrice;

    /** 当前价格相对突破价位的距离百分比。 */
    private BigDecimal distancePct;

    /** ATR14。 */
    private BigDecimal atr14;

    /** 最新量比。 */
    private BigDecimal volumeRatio;

    /** 收盘位置。 */
    private BigDecimal closePosition;

    /** 触发条件。 */
    private String triggerCondition;

    /** 失效条件。 */
    private String invalidCondition;
}
