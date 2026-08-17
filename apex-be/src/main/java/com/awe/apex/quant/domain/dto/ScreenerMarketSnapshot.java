package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 全市场实时选股截面
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenerMarketSnapshot {

    /** 证券代码 */
    private String code;

    /** 证券简称 */
    private String name;

    /** 所属市场 */
    private String market;

    /** 最新价 */
    private BigDecimal latestPrice;

    /** 涨跌幅 */
    private BigDecimal pctChg;

    /** 换手率 */
    private BigDecimal turnoverRate;

    /** 量比 */
    private BigDecimal volumeRatio;

    /** 滚动市盈率 */
    private BigDecimal peTtm;

    /** 市净率 */
    private BigDecimal pb;

    /** 总市值 */
    private BigDecimal totalMv;

    /** 流通市值 */
    private BigDecimal circMv;

    /** 成交额 */
    private BigDecimal amount;

    /** 行业 */
    private String industry;

    /** 行情时间 */
    private LocalDateTime quoteTime;
}
