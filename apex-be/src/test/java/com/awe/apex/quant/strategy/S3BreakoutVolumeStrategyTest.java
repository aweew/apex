package com.awe.apex.quant.strategy;

import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.strategy.impl.S3BreakoutVolumeStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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

    private final S3BreakoutVolumeStrategy strategy = new S3BreakoutVolumeStrategy();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(strategy, "strategyParams", new StrategyParams());
    }

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
}
