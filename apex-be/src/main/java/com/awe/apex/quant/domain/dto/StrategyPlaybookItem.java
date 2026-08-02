package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 策略战法条目
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyPlaybookItem {

    /**
     * 策略 ID
     */
    private String strategyId;

    /**
     * 名称
     */
    private String name;

    /**
     * 战法类型：趋势 / 回调 / 突破
     */
    private String style;

    /**
     * 买入条件
     */
    private String buyRule;

    /**
     * 卖出/离场条件
     */
    private String exitRule;

    /**
     * 适用市况
     */
    private String marketFit;

    /**
     * 默认信号分
     */
    private Integer defaultScore;

    /**
     * 是否参与日决策流水线
     */
    private Boolean inDecisionPipeline;

    /**
     * 可调参数键提示
     */
    private String paramsHint;
}
