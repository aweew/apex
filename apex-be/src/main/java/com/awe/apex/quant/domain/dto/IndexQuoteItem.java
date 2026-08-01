package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 指数最新报价 + 量能
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexQuoteItem {

    /**
     * 内部代码
     */
    private String code;

    /**
     * 名称
     */
    private String name;

    /**
     * 市场区域
     */
    private String region;

    /**
     * 交易日
     */
    private LocalDate tradeDate;

    /**
     * 最新价/收盘
     */
    private BigDecimal closePrice;

    /**
     * 涨跌幅%
     */
    private BigDecimal pctChg;

    /**
     * 成交量
     */
    private BigDecimal volume;

    /**
     * 昨成交量
     */
    private BigDecimal prevVolume;

    /**
     * 成交量较昨日变化%（正=放量）
     */
    private BigDecimal volumeChgPct;

    /**
     * 量能趋势：放量/缩量/平量/无数据
     */
    private String volumeTrend;

    /**
     * 近 N 日收盘（迷你走势）
     */
    private List<BigDecimal> sparkCloses;

    /**
     * 近 N 日成交量
     */
    private List<BigDecimal> sparkVolumes;
}
