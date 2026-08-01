package com.awe.apex.quant.indicator;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 指标工具金样例
 */
class IndicatorUtilsTest {

    @Test
    void maShouldAverageLastPeriod() {
        List<BigDecimal> closes = decimals(10, 11, 12, 13, 14);
        assertNull(IndicatorUtils.ma(closes, 3, 1));
        assertEquals(0, new BigDecimal("11.0000").compareTo(IndicatorUtils.ma(closes, 3, 2)));
        assertEquals(0, new BigDecimal("13.0000").compareTo(IndicatorUtils.ma(closes, 3, 4)));
    }

    @Test
    void rsi14ShouldMatchKnownSeries() {
        // 构造单调上涨序列，RSI 应接近 100
        List<BigDecimal> up = decimals(
                100, 101, 102, 103, 104, 105, 106, 107, 108, 109,
                110, 111, 112, 113, 114, 115
        );
        BigDecimal rsi = IndicatorUtils.rsi(up, 14, up.size() - 1);
        assertEquals(1, rsi.compareTo(new BigDecimal("90")));

        // 构造下跌序列，RSI 应接近 0
        List<BigDecimal> down = decimals(
                115, 114, 113, 112, 111, 110, 109, 108, 107, 106,
                105, 104, 103, 102, 101, 100
        );
        BigDecimal rsiDown = IndicatorUtils.rsi(down, 14, down.size() - 1);
        assertEquals(-1, rsiDown.compareTo(new BigDecimal("10")));
    }

    private List<BigDecimal> decimals(double... values) {
        return Arrays.stream(values).mapToObj(BigDecimal::valueOf).toList();
    }
}
