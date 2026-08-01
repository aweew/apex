package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 股票搜索结果项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockSearchItem {

    /**
     * 证券代码
     */
    private String code;

    /**
     * 证券简称
     */
    private String name;

    /**
     * 市场
     */
    private String market;

    /**
     * 最新价
     */
    private BigDecimal latestPrice;

    /**
     * 来源：watchlist / stock_basic
     */
    private String source;
}
