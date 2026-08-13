package com.awe.apex.quant.decision;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 市场状态评估入参
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketRegimeInput {

    /**
     * 指数是否位于 MA60 上方
     */
    private Boolean indexAboveMa60;

    /**
     * MA20 是否位于 MA60 上方
     */
    private Boolean ma20AboveMa60;

    /**
     * MA20 五日斜率是否向上
     */
    private Boolean ma20SlopeUp;

    /**
     * 全市场位于 MA20 上方的股票比例
     */
    private BigDecimal breadthPct;

    /**
     * 指数二十日回撤比例
     */
    private BigDecimal drawdown20;

    /**
     * 二百五十二日波动率分位
     */
    private BigDecimal volatilityPercentile;

    /**
     * 核心市场数据是否完整
     */
    private Boolean dataComplete;
}
