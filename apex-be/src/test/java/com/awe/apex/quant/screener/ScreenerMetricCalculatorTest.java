package com.awe.apex.quant.screener;

import com.awe.apex.quant.domain.dto.IntradayAcceptanceMetric;
import com.awe.apex.quant.domain.dto.IntradayPoint;
import com.awe.apex.quant.domain.entity.BarDaily;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenerMetricCalculatorTest {

    private final ScreenerMetricCalculator calculator = new ScreenerMetricCalculator();

    @Test
    void shouldCountMainBoardLimitUpsInsideLookback() {
        List<BarDaily> bars = new ArrayList<>();
        for (int index = 0; index < 25; index++) {
            bars.add(BarDaily.builder()
                    .tradeDate(LocalDate.of(2026, 7, 1).plusDays(index))
                    .closePrice(BigDecimal.TEN)
                    .pctChg(index == 10 || index == 22 ? new BigDecimal("9.80") : BigDecimal.ONE)
                    .build());
        }

        assertEquals(2, calculator.countLimitUps(bars, 20));
        assertEquals(1, calculator.countLimitUps(bars, 10));
    }

    @Test
    void shouldAcceptNinetyFivePercentAboveAverageWithAtMostThreeMinuteBreak() {
        List<IntradayPoint> points = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            boolean below = index == 8;
            points.add(point("09:" + String.format("%02d", 35 + index), below ? "9.99" : "10.01", "10.00"));
        }

        IntradayAcceptanceMetric metric = calculator.calculateIntradayAcceptance(points, "09:35");

        assertEquals(new BigDecimal("95.00"), metric.getAboveAvgRatio());
        assertTrue(metric.getCurrentAboveAvg());
        assertEquals(1, metric.getMaxConsecutiveBelowMinutes());
        assertEquals(20, metric.getPointCount());
    }

    @Test
    void shouldRejectWhenCurrentPriceIsBelowAverageOrBreakExceedsTolerance() {
        List<IntradayPoint> points = List.of(
                point("09:35", "10.01", "10.00"),
                point("09:36", "9.99", "10.00"),
                point("09:37", "9.98", "10.00"),
                point("09:38", "9.97", "10.00"),
                point("09:39", "9.96", "10.00")
        );

        IntradayAcceptanceMetric metric = calculator.calculateIntradayAcceptance(points, "09:35");

        assertFalse(metric.getCurrentAboveAvg());
        assertEquals(4, metric.getMaxConsecutiveBelowMinutes());
    }

    @Test
    void shouldCalculateDaysSinceLatestLimitUpWithinLookback() {
        List<BarDaily> bars = dailyBars(12);
        bars.get(7).setPctChg(new BigDecimal("9.80"));

        assertEquals(4, calculator.calculateDaysSinceLimitUp(bars, 10));
        assertEquals(4, calculator.calculateDaysSinceLimitUp(bars, 3));
        assertNull(calculator.calculateDaysSinceLimitUp(bars.subList(9, 12), 3));
    }

    @Test
    void shouldCalculateLatestVolumeAgainstPreviousAverage() {
        List<BarDaily> bars = dailyBars(6);
        for (int index = 0; index < 5; index++) {
            bars.get(index).setVolume(new BigDecimal("100"));
        }
        bars.get(5).setVolume(new BigDecimal("120"));

        assertEquals(new BigDecimal("120.00"), calculator.calculateVolumeMaRatioPct(bars, 5));
        assertNull(calculator.calculateVolumeMaRatioPct(bars.subList(0, 5), 5));
    }

    @Test
    void shouldCalculateCloseDistanceFromMovingAverage() {
        List<BarDaily> bars = dailyBars(5);
        for (int index = 0; index < bars.size(); index++) {
            bars.get(index).setClosePrice(BigDecimal.valueOf(8 + index));
        }

        assertEquals(new BigDecimal("20.00"), calculator.calculateCloseMaDistancePct(bars, 5));
        assertNull(calculator.calculateCloseMaDistancePct(bars, 6));
    }

    @Test
    void shouldRequireCloseToStrictlyBreakPreviousHigh() {
        List<BarDaily> bars = dailyBars(4);
        bars.get(0).setHighPrice(new BigDecimal("10"));
        bars.get(1).setHighPrice(new BigDecimal("11"));
        bars.get(2).setHighPrice(new BigDecimal("12"));
        bars.get(3).setClosePrice(new BigDecimal("12.01"));

        assertTrue(calculator.isBreakoutPreviousHigh(bars, 3));

        bars.get(3).setClosePrice(new BigDecimal("12"));
        assertFalse(calculator.isBreakoutPreviousHigh(bars, 3));
    }

    @Test
    void shouldRecognizeFiveTenTwentyDayBullishAlignment() {
        List<BarDaily> risingBars = dailyBars(20);
        for (int index = 0; index < risingBars.size(); index++) {
            risingBars.get(index).setClosePrice(BigDecimal.valueOf(index + 1L));
        }
        List<BarDaily> fallingBars = dailyBars(20);
        for (int index = 0; index < fallingBars.size(); index++) {
            fallingBars.get(index).setClosePrice(BigDecimal.valueOf(20L - index));
        }

        assertTrue(calculator.isMaBullishAlignment(risingBars));
        assertFalse(calculator.isMaBullishAlignment(fallingBars));
        assertNull(calculator.isMaBullishAlignment(risingBars.subList(0, 19)));
    }

    private List<BarDaily> dailyBars(int count) {
        List<BarDaily> bars = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            BigDecimal price = BigDecimal.valueOf(10L + index);
            bars.add(BarDaily.builder()
                    .tradeDate(LocalDate.of(2026, 7, 1).plusDays(index))
                    .highPrice(price)
                    .lowPrice(price)
                    .closePrice(price)
                    .volume(BigDecimal.valueOf(100))
                    .pctChg(BigDecimal.ONE)
                    .build());
        }
        return bars;
    }

    private IntradayPoint point(String time, String price, String avgPrice) {
        return IntradayPoint.builder()
                .time(time)
                .price(new BigDecimal(price))
                .avgPrice(new BigDecimal(avgPrice))
                .build();
    }
}
