package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Apex AI 归因证据项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApexAiContributor {

    /**
     * 排名
     */
    private Integer rank;

    /**
     * 归因项名称
     */
    private String name;

    /**
     * 证据说明
     */
    private String detail;

    /**
     * 影响金额或收益指标
     */
    private BigDecimal value;

    /**
     * 对组合收益率的贡献百分点
     */
    private BigDecimal contributionPct;

    /**
     * 涉及证券或样本数量
     */
    private Integer sampleCount;

    /**
     * 影响方向，支持 POSITIVE、NEGATIVE、NEUTRAL
     */
    private String direction;
}
