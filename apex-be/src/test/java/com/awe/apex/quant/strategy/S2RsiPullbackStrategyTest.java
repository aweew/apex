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
}
