package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 日内 K 线。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntradayKlineBar {

    /**
     * K 线起始时间。
     */
    private String datetime;

    /**
     * 开盘价。
     */
    private BigDecimal openPrice;

    /**
     * 收盘价。
     */
    private BigDecimal closePrice;

    /**
     * 最高价。
     */
    private BigDecimal highPrice;

    /**
     * 最低价。
     */
    private BigDecimal lowPrice;
}
