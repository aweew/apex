package com.awe.apex.quant.strategy;

import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.strategy.impl.S3BreakoutVolumeStrategy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S3 突破与跌破低点卖出
 */
class S3BreakoutVolumeStrategyTest {

    private final S3BreakoutVolumeStrategy strategy =
            new S3BreakoutVolumeStrategy(20, new BigDecimal("1.5"));

    @Test
    void buyThenSellWhenBreakLow() {
        List<BarDaily> bars = new ArrayList<>();
        LocalDate date = LocalDate.of(2024, 1, 2);
        // 20 根横盘后放量突破，再跌破突破日低点
        for (int i = 0; i < 25; i++) {
            double close = 10.0;
            double high = 10.2;
            double low = 9.8;
            double vol = 1000;
            if (i == 20) {
                close = 11.0;
                high = 11.2;
                low = 10.5;
                vol = 5000;
            } else if (i > 20) {
                close = 10.0;
                high = 10.3;
                low = 9.7;
                vol = 1200;
            }
            bars.add(BarDaily.builder()
                    .code("000001")
                    .tradeDate(date)
                    .openPrice(BigDecimal.valueOf(close))
                    .highPrice(BigDecimal.valueOf(high))
                    .lowPrice(BigDecimal.valueOf(low))
                    .closePrice(BigDecimal.valueOf(close))
                    .volume(BigDecimal.valueOf(vol))
                    .build());
            date = date.plusDays(1);
        }
        BarSeries series = BarSeries.from(bars);
        assertTrue(strategy.shouldEnter(series, 20));

        StrategySignalResult sell = strategy.evaluate("000001", series);
        assertNotNull(sell);
        assertEquals(SignalSide.SELL, sell.getSide());
    }

    @Test
    void buyScoreReflectsBreakoutStrength() {
        StrategySignalResult buy = strategy.evaluate("000001", BarSeries.from(buildBreakoutBars(5000)));
        assertNotNull(buy);
        assertEquals(SignalSide.BUY, buy.getSide());
        assertTrue(buy.getScore().compareTo(new BigDecimal("65")) >= 0);
        assertTrue(buy.getScore().compareTo(new BigDecimal("92")) <= 0);
        assertNotNull(buy.getReason().get("breakoutPct"));
        assertNotNull(buy.getReason().get("volumeRatio"));
    }

    @Test
    void strongerVolumeScoresHigherThanWeakBreakout() {
        StrategySignalResult weak = strategy.evaluate("000001", BarSeries.from(buildBreakoutBars(1600)));
        StrategySignalResult strong = strategy.evaluate("000001", BarSeries.from(buildBreakoutBars(6000)));
        assertNotNull(weak);
        assertNotNull(strong);
        assertEquals(SignalSide.BUY, weak.getSide());
        assertEquals(SignalSide.BUY, strong.getSide());
        assertTrue(strong.getScore().compareTo(weak.getScore()) > 0,
                "强放量突破分应高于弱放量: strong=" + strong.getScore() + " weak=" + weak.getScore());
    }

    private List<BarDaily> buildBreakoutBars(double breakoutVolume) {
        List<BarDaily> bars = new ArrayList<>();
        LocalDate date = LocalDate.of(2024, 1, 2);
        for (int i = 0; i < 21; i++) {
            double close = 10.0;
            double high = 10.2;
            double low = 9.8;
            double vol = 1000;
            if (i == 20) {
                close = 10.5;
                high = 10.6;
                low = 10.2;
                vol = breakoutVolume;
            }
            bars.add(BarDaily.builder()
                    .code("000001")
                    .tradeDate(date)
                    .openPrice(BigDecimal.valueOf(close))
                    .highPrice(BigDecimal.valueOf(high))
                    .lowPrice(BigDecimal.valueOf(low))
                    .closePrice(BigDecimal.valueOf(close))
                    .volume(BigDecimal.valueOf(vol))
                    .build());
            date = date.plusDays(1);
        }
        return bars;
    }
}
