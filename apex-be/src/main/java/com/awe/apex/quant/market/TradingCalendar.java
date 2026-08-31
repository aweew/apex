package com.awe.apex.quant.market;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A 股交易日历（工作日剔除法定节假日；不含临时停市）
 */
public final class TradingCalendar {

    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalTime MARKET_CLOSE_TIME = LocalTime.of(15, 0);
    private static final Set<LocalDate> HOLIDAYS = new HashSet<>();

    static {
        // 2024
        addRange("2024-01-01", "2024-01-01");
        addRange("2024-02-09", "2024-02-17");
        addRange("2024-04-04", "2024-04-06");
        addRange("2024-05-01", "2024-05-05");
        addRange("2024-06-10", "2024-06-10");
        addRange("2024-09-15", "2024-09-17");
        addRange("2024-10-01", "2024-10-07");
        // 2025
        addRange("2025-01-01", "2025-01-01");
        addRange("2025-01-28", "2025-02-04");
        addRange("2025-04-04", "2025-04-06");
        addRange("2025-05-01", "2025-05-05");
        addRange("2025-05-31", "2025-06-02");
        addRange("2025-10-01", "2025-10-08");
        // 2026
        addRange("2026-01-01", "2026-01-03");
        addRange("2026-02-15", "2026-02-23");
        addRange("2026-04-04", "2026-04-06");
        addRange("2026-05-01", "2026-05-05");
        addRange("2026-06-19", "2026-06-21");
        addRange("2026-09-25", "2026-09-27");
        addRange("2026-10-01", "2026-10-07");
    }

    private TradingCalendar() {
    }

    /**
     * 是否交易日
     *
     * @param date 日期
     * @return true=交易日
     */
    public static boolean isTradingDay(LocalDate date) {
        if (date == null) {
            return false;
        }
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        return !HOLIDAYS.contains(date);
    }

    /**
     * 上一交易日（不含当天）
     *
     * @param date 基准日
     * @return 上一交易日
     */
    public static LocalDate prevTradingDay(LocalDate date) {
        LocalDate cursor = date.minusDays(1);
        while (!isTradingDay(cursor)) {
            cursor = cursor.minusDays(1);
        }
        return cursor;
    }

    /**
     * 下一交易日（不含当天）
     *
     * @param date 基准日
     * @return 下一交易日
     */
    public static LocalDate nextTradingDay(LocalDate date) {
        LocalDate cursor = date.plusDays(1);
        while (!isTradingDay(cursor)) {
            cursor = cursor.plusDays(1);
        }
        return cursor;
    }

    /**
     * 最近交易日（若当天是交易日则返回当天）
     *
     * @param date 基准日
     * @return 最近交易日
     */
    public static LocalDate latestTradingDayOnOrBefore(LocalDate date) {
        LocalDate cursor = date;
        while (!isTradingDay(cursor)) {
            cursor = cursor.minusDays(1);
        }
        return cursor;
    }

    /**
     * 最近一个已经收盘的交易日
     *
     * @return 已收盘交易日
     */
    public static LocalDate latestCompletedTradingDay() {
        return latestCompletedTradingDay(LocalDateTime.now(SHANGHAI_ZONE));
    }

    /**
     * 根据指定时间查询最近一个已经收盘的交易日
     *
     * @param dateTime 上海市场时间
     * @return 已收盘交易日
     */
    public static LocalDate latestCompletedTradingDay(LocalDateTime dateTime) {
        LocalDate currentDate = dateTime.toLocalDate();
        if (isTradingDay(currentDate) && dateTime.toLocalTime().isBefore(MARKET_CLOSE_TIME)) {
            return prevTradingDay(currentDate);
        }
        return latestTradingDayOnOrBefore(currentDate);
    }

    /**
     * 回溯若干个交易日
     *
     * @param end   结束日（含）
     * @param count 交易日数量
     * @return 升序列表
     */
    public static List<LocalDate> recentTradingDays(LocalDate end, int count) {
        int size = Math.max(1, Math.min(count, 60));
        List<LocalDate> desc = new ArrayList<>();
        LocalDate cursor = latestTradingDayOnOrBefore(end);
        while (desc.size() < size) {
            if (isTradingDay(cursor)) {
                desc.add(cursor);
            }
            cursor = cursor.minusDays(1);
        }
        List<LocalDate> asc = new ArrayList<>();
        for (int i = desc.size() - 1; i >= 0; i--) {
            asc.add(desc.get(i));
        }
        return asc;
    }

    private static void addRange(String begin, String end) {
        LocalDate from = LocalDate.parse(begin);
        LocalDate to = LocalDate.parse(end);
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            HOLIDAYS.add(cursor);
            cursor = cursor.plusDays(1);
        }
    }
}
