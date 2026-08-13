package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 面向默认组合的最终决策提示
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionAdviceResp {

    /**
     * 决策运行号
     */
    private String runNo;

    /**
     * 决策日
     */
    private LocalDate actionDate;

    /**
     * 建议执行日
     */
    private LocalDate executionDate;

    /**
     * 执行时点
     */
    private String executionTiming;

    /**
     * 市场状态
     */
    private String marketRegime;

    /**
     * 市场状态说明
     */
    private String regimeReason;

    /**
     * 当前总仓位
     */
    private BigDecimal currentExposure;

    /**
     * 本轮决策后目标总仓位
     */
    private BigDecimal targetExposure;

    /**
     * 可用现金
     */
    private BigDecimal cash;

    /**
     * 组合总权益
     */
    private BigDecimal totalEquity;

    /**
     * 当前回撤
     */
    private BigDecimal drawdown;

    /**
     * 决策总览
     */
    private String summary;

    /**
     * AI是否成功增强本地说明
     */
    private Boolean aiEnhanced;

    /**
     * 决策行动列表
     */
    private List<DecisionAdviceActionResp> actions;

    /**
     * 复核节奏
     */
    private List<String> reviewSchedule;

    /**
     * 生成时间
     */
    private LocalDateTime generatedAt;
}
