package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 财务现金质量指标
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialQualityResp {

    /**
     * 指标对应报告期
     */
    private LocalDate reportDate;

    /**
     * 净利润
     */
    private BigDecimal netProfit;

    /**
     * 经营活动现金流净额
     */
    private BigDecimal operatingCashFlow;

    /**
     * 应收账款
     */
    private BigDecimal accountsReceivable;

    /**
     * 现金含量：经营活动现金流净额除以净利润
     */
    private BigDecimal cashConversionRatio;

    /**
     * 资本开支：购建固定资产、无形资产和其他长期资产支付的现金
     */
    private BigDecimal capitalExpenditure;

    /**
     * 自由现金流：经营活动现金流净额减资本开支
     */
    private BigDecimal freeCashFlow;

    /**
     * 市现率：总市值除以自由现金流，仅自由现金流为正时计算
     */
    private BigDecimal priceToFreeCashFlow;
}
