package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Apex AI 可分析组合
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApexAiPortfolioOption {

    /**
     * 组合ID
     */
    private Long id;

    /**
     * 组合名称
     */
    private String name;

    /**
     * 是否默认组合
     */
    private Boolean defaultPortfolio;

    /**
     * 持仓数量
     */
    private Integer positionCount;
}
