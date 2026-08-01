package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * ATR 止损建议
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AtrStopResp {

    /**
     * 止损倍数
     */
    private BigDecimal stopMult;

    /**
     * 止盈倍数
     */
    private BigDecimal takeMult;

    /**
     * 说明
     */
    private String message;

    /**
     * 明细
     */
    private List<AtrStopItem> items;
}
