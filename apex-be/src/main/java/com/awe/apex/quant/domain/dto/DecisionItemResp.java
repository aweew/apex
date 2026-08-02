package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 智能决策条目
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionItemResp {

    /**
     * 清单行 ID
     */
    private Long id;

    /**
     * 决策日
     */
    private LocalDate actionDate;

    /**
     * 证券代码
     */
    private String code;

    /**
     * 简称
     */
    private String name;

    /**
     * BUY/SELL/HOLD
     */
    private String action;

    /**
     * 主策略 ID
     */
    private String strategyId;

    /**
     * 人话理由
     */
    private String reason;

    /**
     * 综合评分
     */
    private BigDecimal score;

    /**
     * 建议仓位（0~1）
     */
    private BigDecimal suggestedWeight;

    /**
     * 离场规则
     */
    private String exitRule;

    /**
     * 共振策略数
     */
    private Integer confluenceCount;

    /**
     * 是否共振（≥2）
     */
    private Boolean confluence;

    /**
     * 参与共振的策略
     */
    private List<String> strategies;

    /**
     * 基本面要点
     */
    private String fundNote;

    /**
     * 关联信号 ID（便于一键模拟下单）
     */
    private Long signalId;

    /**
     * 是否匹配当日主线
     */
    private Boolean mainlineMatch;

    /**
     * 匹配的主线名称
     */
    private String mainlineName;

    /**
     * 评分/仓位可解释明细
     */
    private String scoreExplain;
}
