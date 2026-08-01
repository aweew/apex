package com.awe.apex.quant.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 更新止损止盈
 */
@Data
public class PositionStopsReq {

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
     * 止损价
     */
    private BigDecimal stopLoss;

    /**
     * 止盈价
     */
    private BigDecimal takeProfit;
}
