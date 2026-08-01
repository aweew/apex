package com.awe.apex.quant.backtest;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 回测权益点
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestEquityPoint {

    /**
     * 交易日
     */
    private LocalDate tradeDate;

    /**
     * 权益
     */
    private BigDecimal equity;
}
