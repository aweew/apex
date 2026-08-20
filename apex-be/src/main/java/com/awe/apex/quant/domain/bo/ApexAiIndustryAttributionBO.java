package com.awe.apex.quant.domain.bo;

import com.awe.apex.quant.domain.entity.PortfolioHolding;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 组合行业收益归因中间结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApexAiIndustryAttributionBO {

    /**
     * 行业名称
     */
    private String industry;

    /**
     * 行业当日盈亏
     */
    @Builder.Default
    private BigDecimal todayPnl = BigDecimal.ZERO;

    /**
     * 行业持仓列表
     */
    @Builder.Default
    private List<PortfolioHolding> holdings = new ArrayList<>();
}
