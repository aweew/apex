package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 今日市场预测的关注或回避方向。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketForecastDirectionResp {

    /**
     * 板块、题材或风险方向名称。
     */
    private String name;

    /**
     * 基于已同步行情形成的判断依据。
     */
    private String reason;

    /**
     * 该方向可关联的当日可执行候选股名称。
     */
    private List<String> watchStocks;

    /**
     * 开盘后的关注方式。
     */
    private String action;
}
