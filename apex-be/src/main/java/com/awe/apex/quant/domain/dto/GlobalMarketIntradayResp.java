package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 全球市场日内 K 线响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlobalMarketIntradayResp {

    /**
     * 上一交易日收盘价。
     */
    private BigDecimal previousClose;

    /**
     * 适合缩略图展示的日内 K 线。
     */
    private List<IntradayKlineBar> bars;
}
