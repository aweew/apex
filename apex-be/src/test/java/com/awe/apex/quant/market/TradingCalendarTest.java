package com.awe.apex.quant.market;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 交易日历单测
 */
class TradingCalendarTest {

    @Test
    void weekendIsNotTradingDay() {
        assertFalse(TradingCalendar.isTradingDay(LocalDate.of(2026, 8, 1)));
        assertFalse(TradingCalendar.isTradingDay(LocalDate.of(2026, 8, 2)));
    }

    @Test
    void weekdayIsTradingDay() {
        assertTrue(TradingCalendar.isTradingDay(LocalDate.of(2026, 7, 31)));
    }

    @Test
    void holidayIsNotTradingDay() {
        assertFalse(TradingCalendar.isTradingDay(LocalDate.of(2026, 10, 1)));
        assertFalse(TradingCalendar.isTradingDay(LocalDate.of(2025, 1, 29)));
    }

    @Test
    void latestAndNeighbors() {
        LocalDate sat = LocalDate.of(2026, 8, 1);
        assertEquals(LocalDate.of(2026, 7, 31), TradingCalendar.latestTradingDayOnOrBefore(sat));
        assertEquals(LocalDate.of(2026, 7, 31), TradingCalendar.prevTradingDay(sat));
        assertEquals(LocalDate.of(2026, 8, 3), TradingCalendar.nextTradingDay(sat));
    }

    @Test
    void latestCompletedTradingDayUsesPreviousSessionBeforeClose() {
        LocalDateTime mondayMorning = LocalDateTime.of(2026, 8, 31, 8, 0);
        LocalDateTime mondayTrading = LocalDateTime.of(2026, 8, 31, 14, 59);

        assertEquals(LocalDate.of(2026, 8, 28), TradingCalendar.latestCompletedTradingDay(mondayMorning));
        assertEquals(LocalDate.of(2026, 8, 28), TradingCalendar.latestCompletedTradingDay(mondayTrading));
    }

    @Test
    void latestCompletedTradingDayIncludesCurrentSessionAfterClose() {
        LocalDateTime mondayClose = LocalDateTime.of(2026, 8, 31, 15, 0);
        LocalDateTime saturdayMorning = LocalDateTime.of(2026, 9, 5, 8, 0);

        assertEquals(LocalDate.of(2026, 8, 31), TradingCalendar.latestCompletedTradingDay(mondayClose));
        assertEquals(LocalDate.of(2026, 9, 4), TradingCalendar.latestCompletedTradingDay(saturdayMorning));
    }

    @Test
    void recentTradingDaysAsc() {
        List<LocalDate> days = TradingCalendar.recentTradingDays(LocalDate.of(2026, 8, 1), 5);
        assertEquals(5, days.size());
        assertTrue(days.get(0).isBefore(days.get(4)));
        for (LocalDate day : days) {
            assertTrue(TradingCalendar.isTradingDay(day));
        }
    }
}
