package com.awe.apex.quant.strategy;

import com.awe.apex.quant.domain.entity.BarDaily;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BarSeriesTest {

    @Test
    void shouldExposeImmutablePrefixWithoutFutureBars() {
        BarSeries series = BarSeries.from(List.of(
                bar("600519", LocalDate.of(2026, 1, 5), "10.00"),
                bar("600519", LocalDate.of(2026, 1, 6), "11.00"),
                bar("600519", LocalDate.of(2026, 1, 7), "99.00")));

        BarSeries prefixSeries = series.prefix(2);

        assertEquals(2, prefixSeries.size());
        assertEquals(new BigDecimal("11.00"), prefixSeries.getCloses().get(1));
        assertThrows(UnsupportedOperationException.class,
                () -> prefixSeries.getCloses().add(new BigDecimal("100.00")));
    }

    @Test
    void shouldResolvePriceLimitRateByBoardAndReformDate() {
        BarSeries series = BarSeries.from(List.of(
                bar("600519", LocalDate.of(2026, 1, 5), "10.00"),
                bar("300750", LocalDate.of(2020, 8, 21), "10.00"),
                bar("300750", LocalDate.of(2020, 8, 24), "10.00"),
                bar("688981", LocalDate.of(2026, 1, 5), "10.00"),
                bar("920001", LocalDate.of(2026, 1, 5), "10.00")));
        BarSeries stSeries = BarSeries.from(
                List.of(bar("600519", LocalDate.of(2026, 1, 5), "10.00")), true);

        assertEquals(List.of(
                new BigDecimal("0.10"),
                new BigDecimal("0.10"),
                new BigDecimal("0.20"),
                new BigDecimal("0.20"),
                new BigDecimal("0.30")), series.getPriceLimitRates());
        assertEquals(new BigDecimal("0.05"), stSeries.getPriceLimitRates().get(0));
    }

    private BarDaily bar(String code, LocalDate tradeDate, String closePrice) {
        BigDecimal close = new BigDecimal(closePrice);
        return BarDaily.builder()
                .code(code)
                .tradeDate(tradeDate)
                .openPrice(close)
                .highPrice(close)
                .lowPrice(close)
                .closePrice(close)
                .volume(new BigDecimal("1000000"))
                .build();
    }
}
