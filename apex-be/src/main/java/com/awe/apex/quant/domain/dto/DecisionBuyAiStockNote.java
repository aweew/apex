package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 建议买入 · 单票 AI 要点
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionBuyAiStockNote {

    /**
     * 证券代码
     */
    private String code;

    /**
     * 简称
     */
    private String name;

    /**
     * 一句话点评
     */
    private String note;

    /**
     * 优先度：高 / 中 / 低 / 观望
     */
    private String priority;
}
