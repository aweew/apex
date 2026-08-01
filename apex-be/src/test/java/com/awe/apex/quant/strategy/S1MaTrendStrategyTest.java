package com.awe.apex.quant.strategy;

import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.strategy.impl.S1MaTrendStrategy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S1 策略触发用例
 */
class S1MaTrendStrategyTest {

    private final S1MaTrendStrategy strategy = new S1MaTrendStrategy();

    @Test
    void shouldEnterOnMaCrossWithVolume() {
        List<BarDaily> bars = buildBars(true);
        BarSeries series = BarSeries.from(bars);
        boolean entered = false;
        for (int i = 60; i < series.size(); i++) {
            if (strategy.shouldEnter(series, i)) {
                entered = true;
                break;
            }
        }
        assertTrue(entered);
    }

    @Test
    void shouldNotEnterWithoutVolume() {
        List<BarDaily> bars = buildBars(false);
        BarSeries series = BarSeries.from(bars);
        boolean entered = false;
        for (int i = 60; i < series.size(); i++) {
            if (strategy.shouldEnter(series, i)) {
                entered = true;
                break;
            }
        }
        assertFalse(entered);
    }

    private List<BarDaily> buildBars(boolean strongVolumeOnCrossWindow) {
        List<BarDaily> bars = new ArrayList<>();
        LocalDate date = LocalDate.of(2024, 1, 2);
        // 前 80 根横盘，后 40 根快速拉升，制造 MA20 上穿 MA60
        for (int i = 0; i < 120; i++) {
            double close = i < 80 ? 10.0 : 10.0 + (i - 79) * 0.8;
            // 交叉窗口附近放量，其余缩量
            double volume = 1000;
            if (strongVolumeOnCrossWindow && i >= 80) {
                volume = 5000;
            }
            bars.add(BarDaily.builder()
                    .code("000001")
                    .tradeDate(date)
                    .openPrice(BigDecimal.valueOf(close))
                    .highPrice(BigDecimal.valueOf(close + 0.1))
                    .lowPrice(BigDecimal.valueOf(close - 0.1))
                    .closePrice(BigDecimal.valueOf(close))
                    .volume(BigDecimal.valueOf(volume))
                    .build());
            date = date.plusDays(1);
        }
        return bars;
    }
}
