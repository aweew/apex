package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.TradingCalendarResp;
import com.awe.apex.quant.market.TradingCalendar;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 交易日历接口
 */
@RestController
@RequestMapping("/api/market/calendar")
public class MarketCalendarController {

    /**
     * 查询交易日信息
     *
     * @param date 日期 yyyy-MM-dd，默认今天
     * @param recent 近 N 个交易日
     * @return 日历信息
     */
    @GetMapping
    public Result<TradingCalendarResp> calendar(@RequestParam(required = false) String date,
                                                @RequestParam(defaultValue = "10") Integer recent) {
        LocalDate day = StringUtils.isNotBlank(date) ? LocalDate.parse(date.trim()) : LocalDate.now();
        int n = recent == null ? 10 : Math.max(1, Math.min(recent, 60));
        List<LocalDate> recentDays = TradingCalendar.recentTradingDays(day, n);
        return Result.success(TradingCalendarResp.builder()
                .date(day)
                .tradingDay(TradingCalendar.isTradingDay(day))
                .latestTradingDay(TradingCalendar.latestTradingDayOnOrBefore(day))
                .prevTradingDay(TradingCalendar.prevTradingDay(day))
                .nextTradingDay(TradingCalendar.nextTradingDay(day))
                .recentDays(recentDays)
                .note("基于工作日剔除法定节假日，不含临时停市")
                .build());
    }
}
