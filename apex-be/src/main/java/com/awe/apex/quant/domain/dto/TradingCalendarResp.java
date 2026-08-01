package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * 交易日历响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradingCalendarResp {

    /**
     * 查询日
     */
    private LocalDate date;

    /**
     * 是否交易日
     */
    private Boolean tradingDay;

    /**
     * 最近交易日（含当天若开市）
     */
    private LocalDate latestTradingDay;

    /**
     * 上一交易日
     */
    private LocalDate prevTradingDay;

    /**
     * 下一交易日
     */
    private LocalDate nextTradingDay;

    /**
     * 近 N 个交易日
     */
    private List<LocalDate> recentDays;

    /**
     * 说明
     */
    private String note;
}
