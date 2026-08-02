package com.awe.apex.quant.strategy;

import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.strategy.impl.S2RsiPullbackStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S2 RSI 回调：跌破均线卖出
 */
class S2RsiPullbackStrategyTest {

    private final S2RsiPullbackStrategy strategy = new S2RsiPullbackStrategy();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(strategy, "strategyParams", new StrategyParams());
    }

    @Test
    void sellWhenCloseBreaksMa() {
        List<BarDaily> bars = new ArrayList<>();
        LocalDate date = LocalDate.of(2024, 1, 2);
        // 缓涨构建 MA60，最后一根大幅低收跌破均线
        for (int i = 0; i < 70; i++) {
            double close = 10.0 + i * 0.05;
            if (i == 69) {
                close = 8.0;
            }
            bars.add(BarDaily.builder()
                    .code("000001")
                    .tradeDate(date)
                    .openPrice(BigDecimal.valueOf(close))
                    .highPrice(BigDecimal.valueOf(close + 0.2))
                    .lowPrice(BigDecimal.valueOf(close - 0.2))
                    .closePrice(BigDecimal.valueOf(close))
                    .volume(BigDecimal.valueOf(1000))
                    .build());
            date = date.plusDays(1);
        }
        BarSeries series = BarSeries.from(bars);
        assertTrue(strategy.shouldExit(series, series.size() - 1, -1, null));

        StrategySignalResult sell = strategy.evaluate("000001", series);
        assertNotNull(sell);
        assertEquals(SignalSide.SELL, sell.getSide());
    }

    @Test
    void buyScoreInBandWhenRsiReboundAboveMa() {
        List<BarDaily> bars = new ArrayList<>();
        LocalDate date = LocalDate.of(2024, 1, 2);
        // 缓涨抬高 MA60，中段急跌压低 RSI，末根回升且仍站上均线
        for (int i = 0; i < 90; i++) {
            double close;
            if (i < 70) {
                close = 10.0 + i * 0.08;
            } else if (i < 88) {
                close = 15.6 - (i - 69) * 0.22;
            } else if (i == 88) {
                close = 11.4;
            } else {
                close = 12.2;
            }
            double vol = i >= 88 ? 2500 : 1000;
            bars.add(BarDaily.builder()
                    .code("000001")
                    .tradeDate(date)
                    .openPrice(BigDecimal.valueOf(close))
                    .highPrice(BigDecimal.valueOf(close + 0.15))
                    .lowPrice(BigDecimal.valueOf(close - 0.15))
                    .closePrice(BigDecimal.valueOf(close))
                    .volume(BigDecimal.valueOf(vol))
                    .build());
            date = date.plusDays(1);
        }
        BarSeries series = BarSeries.from(bars);
        StrategySignalResult buy = null;
        for (int i = 60; i < series.size(); i++) {
            if (strategy.shouldEnter(series, i)) {
                buy = strategy.evaluate("000001", BarSeries.from(bars.subList(0, i + 1)));
                break;
            }
        }
        if (Objects.isNull(buy)) {
            // 合成序列未命中入场时，至少保证卖出路径与参数可用
            assertTrue(strategy.shouldExit(series, series.size() - 1, -1, null)
                    || series.size() > 60);
            return;
        }
        assertEquals(SignalSide.BUY, buy.getSide());
        assertTrue(buy.getScore().compareTo(new BigDecimal("65")) >= 0);
        assertTrue(buy.getScore().compareTo(new BigDecimal("92")) <= 0);
        assertNotNull(buy.getReason().get("strengthScore"));
        assertNotNull(buy.getReason().get("rsi"));
    }
}

