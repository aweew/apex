package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 盘前市场总结。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreMarketSummaryResp {

    /**
     * 基于 A 股市场立场形成的盘前结论。
     */
    private String headline;

    /**
     * 今日市场预测。
     */
    private MarketForecastResp forecast;

    /**
     * 机会方向，最多两项。
     */
    private List<CommandDirectionItemResp> opportunityItems;

    /**
     * 风险方向，最多两项。
     */
    private List<CommandDirectionItemResp> riskItems;

    /**
     * 结论依据，最多四项。
     */
    private List<CommandEvidenceItemResp> evidenceItems;

    /**
     * 观察和失效条件，最多两项。
     */
    private List<CommandWatchConditionResp> watchConditions;
}
