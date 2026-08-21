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
     * BUY/REDUCE/SELL/HOLD
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
     * 决策时参考价
     */
    private BigDecimal referencePrice;

    /**
     * 决策止损价
     */
    private BigDecimal stopLossPrice;

    /**
     * 决策止盈价
     */
    private BigDecimal takeProfitPrice;

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

    /**
     * 估值档位 UNDERVALUED / FAIR / OVERVALUED ...
     */
    private String valuationLevel;

    /**
     * 估值中文结论
     */
    private String valuationLabel;

    /**
     * 估值综合分
     */
    private BigDecimal valuationScore;

    /**
     * 估值一句话
     */
    private String valuationSummary;

    /**
     * 风险旗标（决策评分产出）
     */
    private List<String> riskFlags;

    /**
     * 是否允许高分直接可执行（观察池）
     */
    private Boolean executableHint;

    /**
     * 是否通过市场与板块开仓门禁
     */
    private Boolean entryGatePassed;

    /**
     * 估值×策略联动提示
     */
    private String linkHint;

    /**
     * 规则和基本面提炼的个股亮点
     */
    private List<String> highlights;

    /**
     * 近七日直接相关消息的事实性摘要
     */
    private String newsSummary;

    /**
     * 近七日直接相关消息
     */
    private List<DecisionStockNewsResp> recentNews;
}
