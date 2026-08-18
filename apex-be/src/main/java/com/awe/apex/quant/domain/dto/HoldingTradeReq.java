package com.awe.apex.quant.domain.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 持仓成交请求。
 */
@Data
public class HoldingTradeReq {

    /**
     * 持仓ID，已有持仓买卖时必填。
     */
    private Long holdingId;

    /**
     * 证券代码，新建持仓买入时必填。
     */
    private String code;

    /**
     * 证券简称，新建持仓买入时可选。
     */
    private String name;

    /**
     * 成交方向，BUY 或 SELL。
     */
    private String side;

    /**
     * 本次成交数量。
     */
    private Integer quantity;

    /**
     * 实际成交价。
     */
    private BigDecimal tradePrice;

    /**
     * 实际成交时间，为空时使用当前时间。
     */
    private LocalDateTime tradeTime;
}
