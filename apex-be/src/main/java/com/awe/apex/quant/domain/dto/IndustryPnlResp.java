package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 行业盈亏归因
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndustryPnlResp {

    /**
     * 行业
     */
    private String industry;

    /**
     * 持仓市值
     */
    private BigDecimal marketValue;

    /**
     * 浮盈亏
     */
    private BigDecimal pnl;

    /**
     * 仓位占比
     */
    private BigDecimal weight;

    /**
     * 浮盈亏贡献占比（相对绝对浮盈合计）
     */
    private BigDecimal pnlContribution;
}
