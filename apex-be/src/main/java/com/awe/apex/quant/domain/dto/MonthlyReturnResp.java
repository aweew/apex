package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 月度收益
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyReturnResp {

    /**
     * 年月 yyyy-MM
     */
    private String month;

    /**
     * 当月收益
     */
    private BigDecimal monthReturn;

    /**
     * 月末权益
     */
    private BigDecimal endEquity;
}
