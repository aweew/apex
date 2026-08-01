package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 成交日历单日
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeCalendarDay {

    /**
     * 交易日
     */
    private LocalDate tradeDate;

    /**
     * 买入笔数
     */
    private Integer buyCount;

    /**
     * 卖出笔数
     */
    private Integer sellCount;

    /**
     * 成交额
     */
    private BigDecimal turnover;

    /**
     * 费用
     */
    private BigDecimal fee;

    /**
     * 当日净买入额（买−卖）
     */
    private BigDecimal netBuyAmount;
}
