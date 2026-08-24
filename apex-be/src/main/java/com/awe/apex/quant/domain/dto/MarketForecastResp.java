package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 基于隔夜、亚太与上一交易日 A 股结构形成的今日市场预测。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketForecastResp {

    /**
     * 对开盘节奏和日内结构的判断。
     */
    private String marketOutlook;

    /**
     * 优先关注的方向。
     */
    private List<MarketForecastDirectionResp> focusItems;

    /**
     * 需要回避或降低开盘节奏的方向。
     */
    private List<MarketForecastDirectionResp> riskItems;

    /**
     * 会改变预测判断的盘中观察条件。
     */
    private List<CommandWatchConditionResp> watchConditions;
}
