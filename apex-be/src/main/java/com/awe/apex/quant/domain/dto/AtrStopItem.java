package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * ATR 止损建议单票
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AtrStopItem {

    /**
     * 代码
     */
    private String code;

    /**
     * 名称
     */
    private String name;

    /**
     * 成本价
     */
    private BigDecimal costPrice;

    /**
     * 现价
     */
    private BigDecimal marketPrice;

    /**
     * ATR14
     */
    private BigDecimal atr14;

    /**
     * 建议止损
     */
    private BigDecimal suggestedStopLoss;

    /**
     * 建议止盈
     */
    private BigDecimal suggestedTakeProfit;

    /**
     * 当前止损
     */
    private BigDecimal currentStopLoss;

    /**
     * 当前止盈
     */
    private BigDecimal currentTakeProfit;
}
