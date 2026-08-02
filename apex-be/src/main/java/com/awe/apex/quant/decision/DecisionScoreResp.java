package com.awe.apex.quant.decision;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 决策评分结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionScoreResp {

    /**
     * 综合评分
     */
    private BigDecimal finalScore;

    /**
     * 建议仓位（观察-only 为 0）
     */
    private BigDecimal suggestedWeight;

    /**
     * 短中文评分拆解
     */
    private String scoreExplain;

    /**
     * 风险旗标
     */
    @Builder.Default
    private List<String> riskFlags = new ArrayList<>();

    /**
     * 是否允许高分直接可执行（观察池 TRIGGERED）
     */
    private boolean executableHint;

    /**
     * 估值×策略联动提示
     */
    private String linkHint;
}
