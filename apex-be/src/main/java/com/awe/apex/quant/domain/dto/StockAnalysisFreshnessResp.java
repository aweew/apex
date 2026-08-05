package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 综合研判 · 数据新鲜度
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAnalysisFreshnessResp {

    /**
     * 本地日线最后交易日
     */
    private LocalDate lastBarDate;

    /**
     * 期望交易日（最近一个交易日）
     */
    private LocalDate expectedTradeDate;

    /**
     * 本地日线条数
     */
    private Integer barCount;

    /**
     * 本次是否补过日线
     */
    private Boolean barsSynced;

    /**
     * 本次是否刷新现价
     */
    private Boolean quoteRefreshed;

    /**
     * 日线是否仍滞后
     */
    private Boolean barsStale;

    /**
     * 一句话说明
     */
    private String note;
}
