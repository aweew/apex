package com.awe.apex.quant.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 模拟下单请求
 */
@Data
public class PaperOrderReq {

    /**
     * 账户ID
     */
    @NotNull
    private Long accountId;

    /**
     * 证券代码
     */
    @NotBlank
    private String code;

    /**
     * BUY/SELL
     */
    @NotBlank
    private String side;

    /**
     * 数量（股）；与 targetWeight 二选一
     */
    private Integer quantity;

    /**
     * 目标仓位比例（0~1），买入时按总资产换算整百股
     */
    private BigDecimal targetWeight;

    /**
     * 可选指定价格，空则按收盘价撮合
     */
    private String tradeDate;
}
