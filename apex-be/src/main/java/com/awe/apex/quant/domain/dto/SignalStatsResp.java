package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 信号统计
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalStatsResp {

    /**
     * 近 N 日
     */
    private Integer days;

    /**
     * 总数
     */
    private Integer total;

    /**
     * BUY 数
     */
    private Integer buyCount;

    /**
     * SELL 数
     */
    private Integer sellCount;

    /**
     * 按策略计数
     */
    private Map<String, Integer> byStrategy;

    /**
     * TOP 代码（按出现次数）
     */
    private List<CodeCountItem> topCodes;
}
