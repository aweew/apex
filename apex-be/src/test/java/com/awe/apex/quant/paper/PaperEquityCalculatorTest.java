package com.awe.apex.quant.paper;

import com.awe.apex.quant.domain.dto.EquityPointResp;
import com.awe.apex.quant.domain.entity.PaperOrder;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 权益市值回放单测
 */
class PaperEquityCalculatorTest {

    @Test
    void replayMarksToMarket() {
        BigDecimal init = new BigDecimal("100000");
        List<PaperOrder> orders = List.of(
                PaperOrder.builder().id(1L).code("600519").side("BUY")
                        .quantity(100).price(new BigDecimal("100"))
                        .amount(new BigDecimal("10000")).fee(new BigDecimal("5"))
                        .tradeDate(LocalDate.of(2026, 1, 2)).build(),
                PaperOrder.builder().id(2L).code("600519").side("SELL")
                        .quantity(100).price(new BigDecimal("110"))
                        .amount(new BigDecimal("11000")).fee(new BigDecimal("6"))
                        .tradeDate(LocalDate.of(2026, 1, 6)).build()
        );
        Map<String, Map<LocalDate, BigDecimal>> closes = new HashMap<>();
        Map<LocalDate, BigDecimal> px = new HashMap<>();
        px.put(LocalDate.of(2026, 1, 2), new BigDecimal("100"));
        px.put(LocalDate.of(2026, 1, 3), new BigDecimal("105"));
        px.put(LocalDate.of(2026, 1, 6), new BigDecimal("110"));
        closes.put("600519", px);

        PaperEquityCalculator.ReplayResult result = PaperEquityCalculator.replay(init, orders, closes, null);
        List<EquityPointResp> points = result.getPoints();
        assertEquals(3, points.size());
        // 1/2: cash 89995 + mv 10000 = 99995
        assertEquals(0, new BigDecimal("99995.00").compareTo(points.get(0).getEquity()));
        // 1/3: cash 89995 + mv 10500 = 100495
        assertEquals(0, new BigDecimal("100495.00").compareTo(points.get(1).getEquity()));
        // 1/6 sell: cash 89995+11000-6=100989
        assertEquals(0, new BigDecimal("100989.00").compareTo(points.get(2).getEquity()));
        assertTrue(result.getMaxDrawdown().signum() >= 0);
        assertTrue(result.getDailyVar95().signum() >= 0);
        assertTrue(result.getDailyCvar95().signum() >= 0);
    }
}
