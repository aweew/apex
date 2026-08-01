package com.awe.apex.quant.backtest;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 回测成交点
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestTradePoint {

    /**
     * 成交日
     */
    private LocalDate tradeDate;

    /**
     * BUY/SELL
     */
    private String side;

    /**
     * 成交价
     */
    private BigDecimal price;

    /**
     * 数量
     */
    private Integer quantity;

    /**
     * 成交额
     */
    private BigDecimal amount;

    /**
     * 费用
     */
    private BigDecimal fee;

    /**
     * 原因
     */
    private String reason;
}
