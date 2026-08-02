package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * 今日智能决策汇总
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionTodayResp {

    /**
     * 决策日
     */
    private LocalDate actionDate;

    /**
     * 自选分组
     */
    private String groupName;

    /**
     * 股票池数量
     */
    private Integer universeCount;

    /**
     * 买入建议
     */
    private List<DecisionItemResp> buys;

    /**
     * 卖出建议
     */
    private List<DecisionItemResp> sells;

    /**
     * 继续持有
     */
    private List<DecisionItemResp> holds;

    /**
     * 全部条目（按动作优先级）
     */
    private List<DecisionItemResp> items;

    /**
     * 风控摘要
     */
    private String riskNote;

    /**
     * 每日市场简报（大盘/风格/量能/情绪提示）
     */
    private MarketBriefingResp marketBriefing;

    /**
     * 说明
     */
    private String message;
}
