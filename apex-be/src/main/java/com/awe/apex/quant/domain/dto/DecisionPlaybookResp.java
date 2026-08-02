package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 决策中心：策略战法 + 交易规则总览
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionPlaybookResp {

    /**
     * 流水线步骤（人话）
     */
    private List<String> pipelineSteps;

    /**
     * 策略战法
     */
    private List<StrategyPlaybookItem> strategies;

    /**
     * 评分规则
     */
    private List<String> scoreRules;

    /**
     * 仓位规则
     */
    private List<String> positionRules;

    /**
     * 基本面门禁
     */
    private List<String> fundRules;

    /**
     * 卖出优先级
     */
    private List<String> sellRules;

    /**
     * 说明
     */
    private String message;
}
