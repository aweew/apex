package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 选股策略通俗指南响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenerStrategyGuideResp {

    /** 策略分类 */
    private String category;

    /** 通俗解释 */
    private String plainExplanation;

    /** 核心逻辑 */
    private String coreIdea;

    /** 适用市场环境 */
    private String suitableMarket;

    /** 操作步骤 */
    private List<String> executionSteps;

    /** 风险纪律 */
    private List<String> riskNotes;
}
