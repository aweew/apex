package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 决策复盘归因
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionAttributionResp {

    /**
     * 回溯天数
     */
    private Integer days;

    /**
     * 按策略
     */
    private List<DecisionAttrBucket> byStrategy;

    /**
     * 按是否共振
     */
    private List<DecisionAttrBucket> byConfluence;

    /**
     * 按是否主线（优先落库 mainline_match，否则理由近似）
     */
    private List<DecisionAttrBucket> byMainline;

    /**
     * 按市场立场（快照）
     */
    private List<DecisionAttrBucket> byStance;

    /**
     * 按卖出策略（卖出建议次日表现，用于检验离场质量）
     */
    private List<DecisionAttrBucket> bySellStrategy;

    /**
     * 说明
     */
    private String message;
}
