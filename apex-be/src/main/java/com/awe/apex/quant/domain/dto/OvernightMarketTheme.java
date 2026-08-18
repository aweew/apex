package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 隔夜美股主题情绪。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OvernightMarketTheme {

    /**
     * 主题编码。
     */
    private String code;

    /**
     * 主题名称。
     */
    private String name;

    /**
     * 有效报价数量。
     */
    private Integer quoteCount;

    /**
     * 上涨数量。
     */
    private Integer upCount;

    /**
     * 下跌数量。
     */
    private Integer downCount;

    /**
     * 平盘数量。
     */
    private Integer flatCount;

    /**
     * 涨跌幅中位数。
     */
    private BigDecimal medianPctChg;

    /**
     * 领涨股报价。
     */
    private OvernightMarketQuote leaderQuote;

    /**
     * 领跌股报价。
     */
    private OvernightMarketQuote laggardQuote;
}
