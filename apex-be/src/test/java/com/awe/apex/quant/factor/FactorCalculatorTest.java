package com.awe.apex.quant.factor;

import com.awe.apex.quant.domain.dto.AlphaComponentResp;
import com.awe.apex.quant.domain.entity.BarDaily;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FactorCalculatorTest {

    private final FactorCalculator calculator = new FactorCalculator();

    @Test
    void shouldCalculateTradingDayReturn() {
        List<BarDaily> bars = buildBars(21, new BigDecimal("10"));
        bars.get(20).setClosePrice(new BigDecimal("12"));

        assertEquals(new BigDecimal("20.00"), calculator.calculateReturn(bars, 20));
        assertNull(calculator.calculateReturn(bars, 60));
    }

    @Test
    void shouldCompareLatestAmountWithPreviousAverage() {
        List<BarDaily> bars = buildBars(21, BigDecimal.TEN);
        for (int index = 0; index < 20; index++) {
            bars.get(index).setAmount(new BigDecimal("100"));
        }
        bars.get(20).setAmount(new BigDecimal("150"));

        assertEquals(new BigDecimal("1.50"), calculator.calculateAmountRatio(bars, 20));
    }

    @Test
    void shouldCalculateAnnualizedVolatility() {
        List<BarDaily> bars = new ArrayList<>();
        BigDecimal price = new BigDecimal("100");
        for (int index = 0; index < 21; index++) {
            bars.add(BarDaily.builder()
                    .tradeDate(LocalDate.of(2026, 7, 1).plusDays(index))
                    .closePrice(price)
                    .build());
            price = price.multiply(index % 2 == 0 ? new BigDecimal("1.01") : new BigDecimal("0.99"));
        }

        assertEquals(new BigDecimal("15.87"), calculator.calculateAnnualizedVolatility(bars, 20));
    }

    @Test
    void shouldNormalizeAlphaScoreByAvailableWeight() {
        List<AlphaComponentResp> components = List.of(
                component("MOMENTUM", 30, "80"),
                component("ROE", 20, "60"),
                component("EARNINGS_GROWTH", 20, null),
                component("VOLUME", 15, "40"),
                component("MARKET_STRENGTH", 15, "70")
        );

        assertEquals(new BigDecimal("65.63"), calculator.calculateAlphaScore(components));
        assertEquals(new BigDecimal("80.00"), calculator.calculateCoverage(components));
    }

    private AlphaComponentResp component(String key, int weight, String score) {
        return AlphaComponentResp.builder()
                .key(key)
                .weight(new BigDecimal(weight))
                .score(score == null ? null : new BigDecimal(score))
                .available(score != null)
                .build();
    }

    private List<BarDaily> buildBars(int count, BigDecimal closePrice) {
        List<BarDaily> bars = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            bars.add(BarDaily.builder()
                    .tradeDate(LocalDate.of(2026, 7, 1).plusDays(index))
                    .closePrice(closePrice)
                    .amount(new BigDecimal("100"))
                    .build());
        }
        return bars;
    }
}
