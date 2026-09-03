package com.awe.apex.quant.signal;

import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.signal.event.MarketBehaviorDetector;
import com.awe.apex.quant.signal.event.SignalDetectionResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 市场行为检测器测试。
 */
class MarketBehaviorDetectorTest {

    private final MarketBehaviorDetector detector = new MarketBehaviorDetector();

    /**
     * 验证放量突破证据及截止日期隔离。
     */
    @Test
    void detectsVolumeBreakoutWithoutReadingFutureBars() {
        List<BarDaily> bars = normalBars(65);
        LocalDate breakoutDate = bars.get(64).getTradeDate();
        bars.set(64, bar(breakoutDate, 10.20, 11.30, 10.10, 11.20, 2600, 28_000_000));

        List<SignalDetectionResult> beforeFuture = detector.detect("000001", bars, breakoutDate);
        bars.add(bar(breakoutDate.plusDays(1), 11.20, 11.30, 9.50, 9.70, 5000, 55_000_000));
        List<SignalDetectionResult> afterFuture = detector.detect("000001", bars, breakoutDate);

        SignalDetectionResult breakout = beforeFuture.stream()
                .filter(item -> "S001".equals(item.getSignalCode()))
                .findFirst()
                .orElseThrow();
        assertEquals(beforeFuture, afterFuture);
        assertEquals("BULLISH", breakout.getDirection());
        assertEquals("TRIGGERED", breakout.getLifecycleState());
        assertTrue(breakout.getEvidence().getVolumeRatio().doubleValue() >= 1.5);
        assertTrue(breakout.getEvidence().getClosePosition().doubleValue() >= 0.75);
    }

    /**
     * 验证关键行情缺失时只发布数据风险。
     */
    @Test
    void returnsDataRiskAndSuppressesDirectionalSignalsWhenVolumeIsMissing() {
        List<BarDaily> bars = normalBars(65);
        BarDaily latestBar = bars.get(64);
        latestBar.setVolume(null);

        List<SignalDetectionResult> results = detector.detect("000001", bars, latestBar.getTradeDate());

        assertTrue(results.stream().anyMatch(item -> "R007".equals(item.getSignalCode())));
        assertFalse(results.stream().anyMatch(item -> "BULLISH".equals(item.getDirection())
                || "BEARISH".equals(item.getDirection())));
    }

    private List<BarDaily> normalBars(int count) {
        List<BarDaily> bars = new ArrayList<>();
        LocalDate startDate = LocalDate.of(2026, 1, 1);
        for (int index = 0; index < count; index++) {
            double close = 9.50 + index * 0.01;
            bars.add(bar(startDate.plusDays(index), close - 0.05, close + 0.10,
                    close - 0.10, close, 1000, 10_000_000));
        }
        return bars;
    }

    private BarDaily bar(LocalDate tradeDate, double open, double high, double low, double close,
                         double volume, double amount) {
        return BarDaily.builder()
                .code("000001")
                .tradeDate(tradeDate)
                .openPrice(BigDecimal.valueOf(open))
                .highPrice(BigDecimal.valueOf(high))
                .lowPrice(BigDecimal.valueOf(low))
                .closePrice(BigDecimal.valueOf(close))
                .volume(BigDecimal.valueOf(volume))
                .amount(BigDecimal.valueOf(amount))
                .turnoverRate(BigDecimal.ONE)
                .build();
    }
}
