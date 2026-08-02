package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 分时点
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntradayPoint {

    /**
     * 时间 如 09:30
     */
    private String time;

    /**
     * 完整时间 如 2026-07-31 09:30
     */
    private String datetime;

    /**
     * 现价
     */
    private BigDecimal price;

    /**
     * 均价
     */
    private BigDecimal avgPrice;

    /**
     * 成交量（手）
     */
    private BigDecimal volume;

    /**
     * 成交额
     */
    private BigDecimal amount;
}
