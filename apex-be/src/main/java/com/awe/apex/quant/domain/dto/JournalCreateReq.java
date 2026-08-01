package com.awe.apex.quant.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 人工成交录入
 */
@Data
public class JournalCreateReq {

    /**
     * 成交日
     */
    @NotBlank
    private String tradeDate;

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
     * 成交价
     */
    @NotNull
    private BigDecimal price;

    /**
     * 数量
     */
    @NotNull
    private Integer quantity;

    /**
     * 关联清单ID
     */
    private Long relatedActionId;

    /**
     * 备注
     */
    private String note;
}
