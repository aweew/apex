package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 最终决策中的单票行动
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionAdviceActionResp {

    /**
     * 执行优先级
     */
    private Integer priority;

    /**
     * 证券代码
     */
    private String code;

    /**
     * 证券简称
     */
    private String name;

    /**
     * BUY/ADD/REDUCE/SELL/HOLD/WATCH
     */
    private String action;

    /**
     * 当前仓位比例
     */
    private BigDecimal currentWeight;

    /**
     * 目标仓位比例
     */
    private BigDecimal targetWeight;

    /**
     * 建议交易股数，A股按整手取整
     */
    private Integer quantity;

    /**
     * 决策参考价
     */
    private BigDecimal referencePrice;

    /**
     * 止损价
     */
    private BigDecimal stopLossPrice;

    /**
     * 止盈价
     */
    private BigDecimal takeProfitPrice;

    /**
     * 预期持有天数下限
     */
    private Integer minHoldingDays;

    /**
     * 预期持有天数上限
     */
    private Integer maxHoldingDays;

    /**
     * 行动理由
     */
    private String reason;

    /**
     * 风险提示
     */
    private String riskNote;

    /**
     * 是否可执行
     */
    private Boolean executable;
}
