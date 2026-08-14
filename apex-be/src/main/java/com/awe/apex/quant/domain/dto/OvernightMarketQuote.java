package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 隔夜市场报价。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OvernightMarketQuote {

    /**
     * 行情源证券标识。
     */
    private String symbol;

    /**
     * 证券名称。
     */
    private String name;

    /**
     * 最新价。
     */
    private BigDecimal latestPrice;

    /**
     * 涨跌幅百分比。
     */
    private BigDecimal pctChg;

    /**
     * 行情时间。
     */
    private LocalDateTime quoteTime;
}
