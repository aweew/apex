package com.awe.apex.quant.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 开户/入金请求
 */
@Data
public class PaperOpenReq {

    /**
     * 账户名
     */
    @NotBlank
    private String accountName;

    /**
     * 初始资金/入金金额
     */
    @NotNull
    private BigDecimal cash;
}
