package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 主营收入构成项（按产品/行业）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MainRevenueItemResp {

    /**
     * 业务名称
     */
    private String name;

    /**
     * 营收占比（百分数，如 46.56）
     */
    private BigDecimal revenueRatio;

    /**
     * 利润占比（百分数，如 35.73）
     */
    private BigDecimal profitRatio;

    /**
     * 主营收入（元）
     */
    private BigDecimal income;
}
