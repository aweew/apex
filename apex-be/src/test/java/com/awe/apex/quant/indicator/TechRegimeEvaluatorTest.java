package com.awe.apex.quant.indicator;

import com.awe.apex.quant.domain.dto.TechRegimeResult;
import com.awe.apex.quant.domain.entity.BarDaily;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 技术结构状态机金样例
 */
class TechRegimeEvaluatorTest {

    private TechRegimeEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new TechRegimeEvaluator();
        ReflectionTestUtils.setField(evaluator, "techSignalEvaluator", new TechSignalEvaluator());
    }

    @Test
    void insufficientBarsWhenSeriesTooShort() {
        TechRegimeResult result = evaluator.evaluate(barsLinear(10, 10, 0.1), null, null);
        assertEquals(TechRegimeEvaluator.REGIME_INSUFFICIENT, result.getRegime());
        assertEquals("日线不足", result.getRegimeLabel());
    }

    @Test
    void trendHoldOnSteadyUptrend() {
        // 单调上行：收盘站上 MA20，短均线多头
        TechRegimeResult result = evaluator.evaluate(barsLinear(60, 20, 0.4), null, null);
        assertEquals(TechRegimeEvaluator.REGIME_TREND_HOLD, result.getRegime());
        assertEquals(TechRegimeEvaluator.GRADE_STRONG, result.getGrade());
        assertTrue(result.getSummary().contains("上升持有"));
    }

    @Test
    void pullbackWatchWhenDipUnderMa5StillAboveMa20() {
        // 平台抬升后再回撤：收盘>MA20，收盘<MA5，且 MA5 已落到 MA20 下方
        List<BarDaily> bars = new ArrayList<>();
        LocalDate day = LocalDate.of(2025, 1, 2);
        for (int i = 0; i < 30; i++) {
            bars.add(barAt(day, new BigDecimal("30.00")));
            day = nextTradeDay(day);
        }
        double[] lift = {30.5, 31.0, 31.5, 32.0, 32.4};
        for (double px : lift) {
            bars.add(barAt(day, BigDecimal.valueOf(px)));
            day = nextTradeDay(day);
        }
        double[] pull = {32.0, 31.6, 31.2, 30.9, 30.7};
        for (double px : pull) {
            bars.add(barAt(day, BigDecimal.valueOf(px)));
            day = nextTradeDay(day);
        }

        TechRegimeResult result = evaluator.evaluate(bars, new BigDecimal("1.0"), new BigDecimal("0.5"));
        assertEquals(TechRegimeEvaluator.REGIME_PULLBACK_WATCH, result.getRegime(),
                "实际=" + result.getRegime() + " summary=" + result.getSummary());
        assertEquals(TechRegimeEvaluator.GRADE_NEUTRAL, result.getGrade());
        assertTrue(result.getSummary().contains("回调观察"));
    }

    @Test
    void breakdownCutOnSustainedDowntrend() {
        // 单调下行：破 MA20 且均线走弱
        TechRegimeResult result = evaluator.evaluate(barsLinear(60, 50, -0.5), null, null);
        assertEquals(TechRegimeEvaluator.REGIME_BREAKDOWN_CUT, result.getRegime());
        assertEquals(TechRegimeEvaluator.GRADE_WEAK, result.getGrade());
        assertTrue(result.getSummary().contains("破位减仓"));
    }

    @Test
    void repairWhenRecoveringFromWeakStructure() {
        // 先下跌再抬升末段，形成修复（站上 MA5、柱改善，但仍可能低于 MA20）
        List<BarDaily> bars = barsLinear(45, 40, -0.4);
        BigDecimal bottom = bars.get(bars.size() - 1).getClosePrice();
        for (int i = 0; i < 6; i++) {
            int idx = bars.size() - 6 + i;
            BigDecimal px = bottom.multiply(BigDecimal.ONE.add(new BigDecimal("0.012").multiply(BigDecimal.valueOf(i + 1))));
            BarDaily bar = bars.get(idx);
            bar.setClosePrice(px);
            bar.setOpenPrice(px.multiply(new BigDecimal("0.995")));
            bar.setHighPrice(px.multiply(new BigDecimal("1.01")));
            bar.setLowPrice(px.multiply(new BigDecimal("0.99")));
            bar.setVolume(new BigDecimal("2000000"));
        }
        TechRegimeResult result = evaluator.evaluate(bars, null, null);
        assertTrue(
                TechRegimeEvaluator.REGIME_REPAIR.equals(result.getRegime())
                        || TechRegimeEvaluator.REGIME_NEUTRAL.equals(result.getRegime())
                        || TechRegimeEvaluator.REGIME_TREND_HOLD.equals(result.getRegime()),
                "修复序列应落在修复/中性/上升，实际=" + result.getRegime());
    }

    @Test
    void trendHoldDowngradesGradeWhenRsBearish() {
        TechRegimeResult result = evaluator.evaluate(
                barsLinear(60, 20, 0.4), new BigDecimal("-4"), new BigDecimal("-1"));
        assertEquals(TechRegimeEvaluator.REGIME_TREND_HOLD, result.getRegime());
        assertEquals(TechRegimeEvaluator.GRADE_NEUTRAL, result.getGrade());
        assertEquals(TechRegimeEvaluator.RS_BEARISH, result.getRsTone());
        assertTrue(result.getSummary().contains("相对大盘偏弱"));
    }

    @Test
    void rsNullDoesNotBreakEvaluation() {
        TechRegimeResult result = evaluator.evaluate(barsLinear(60, 20, 0.4), null, null);
        assertEquals(TechRegimeEvaluator.RS_NEUTRAL, result.getRsTone());
        assertEquals(TechRegimeEvaluator.REGIME_TREND_HOLD, result.getRegime());
    }

    private List<BarDaily> barsLinear(int n, double start, double step) {
        List<BarDaily> list = new ArrayList<>();
        LocalDate day = LocalDate.of(2025, 1, 2);
        for (int i = 0; i < n; i++) {
            double close = start + step * i;
            list.add(barAt(day, BigDecimal.valueOf(close).setScale(4, java.math.RoundingMode.HALF_UP)));
            day = nextTradeDay(day);
        }
        return list;
    }

    private BarDaily barAt(LocalDate day, BigDecimal close) {
        BigDecimal open = close.multiply(new BigDecimal("0.998"));
        BigDecimal high = close.multiply(new BigDecimal("1.01"));
        BigDecimal low = close.multiply(new BigDecimal("0.99"));
        return BarDaily.builder()
                .code("TEST")
                .tradeDate(day)
                .openPrice(open)
                .highPrice(high)
                .lowPrice(low)
                .closePrice(close)
                .volume(new BigDecimal("1000000"))
                .amount(new BigDecimal("10000000"))
                .build();
    }

    private LocalDate nextTradeDay(LocalDate day) {
        LocalDate next = day.plusDays(1);
        while (next.getDayOfWeek().getValue() >= 6) {
            next = next.plusDays(1);
        }
        return next;
    }
}
