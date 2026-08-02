package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 决策历史快照（含事后收益）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionHistoryItem {

    /**
     * 决策日
     */
    private LocalDate actionDate;

    /**
     * 买入条数
     */
    private Integer buyCount;

    /**
     * 卖出条数
     */
    private Integer sellCount;

    /**
     * 持有条数
     */
    private Integer holdCount;

    /**
     * 可执行提示条数
     */
    private Integer executableCount;

    /**
     * 低估/偏低条数
     */
    private Integer valuationCheapCount;

    /**
     * 合理估值条数
     */
    private Integer valuationFairCount;

    /**
     * 高估/偏高条数
     */
    private Integer valuationRichCount;

    /**
     * 主线匹配条数
     */
    private Integer mainlineMatchCount;

    /**
     * 买入建议次日平均涨跌幅%（有日线时）
     */
    private BigDecimal nextDayAvgPct;

    /**
     * 当日市场立场（快照）
     */
    private String stance;

    /**
     * 当日数据等级（快照）
     */
    private String dataLevel;

    /**
     * 说明
     */
    private String note;
}
