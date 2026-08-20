package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Apex AI 工作台上下文
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApexAiContextResp {

    /**
     * 大模型是否已配置
     */
    private Boolean aiConfigured;

    /**
     * 当前用户可分析组合
     */
    @Builder.Default
    private List<ApexAiPortfolioOption> portfolios = new ArrayList<>();

    /**
     * 当前用户已有归因策略
     */
    @Builder.Default
    private List<ApexAiStrategyOption> strategies = new ArrayList<>();

    /**
     * 推荐问题
     */
    @Builder.Default
    private List<String> recommendedQuestions = new ArrayList<>();
}
