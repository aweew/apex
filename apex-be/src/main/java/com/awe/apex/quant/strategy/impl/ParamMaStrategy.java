package com.awe.apex.quant.strategy.impl;

import com.awe.apex.quant.indicator.IndicatorUtils;
import com.awe.apex.quant.strategy.BarSeries;
import com.awe.apex.quant.strategy.SignalSide;
import com.awe.apex.quant.strategy.Strategy;
import com.awe.apex.quant.strategy.StrategySignalResult;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 参数化均线交叉（用于参数扫描，非 Spring Bean）
 */
public class ParamMaStrategy implements Strategy {

    private final int fast;
    private final int slow;

    public ParamMaStrategy(int fast, int slow) {
        this.fast = fast;
        this.slow = slow;
    }

    @Override
    public String strategyId() {
        return "MA_" + fast + "_" + slow;
    }

    @Override
    public String strategyName() {
        return "均线交叉 MA" + fast + "/" + slow;
    }

    @Override
    public StrategySignalResult evaluate(String code, BarSeries series) {
        int index = series.size() - 1;
        if (shouldEnter(series, index)) {
            Map<String, Object> reason = new HashMap<>();
            reason.put("rule", "MA" + fast + "上穿MA" + slow);
            return StrategySignalResult.builder()
                    .strategyId(strategyId())
                    .code(code)
                    .signalDate(series.getDates().get(index))
                    .side(SignalSide.BUY)
                    .score(new BigDecimal("75"))
                    .reason(reason)
                    .build();
        }
        if (shouldExit(series, index, -1, null)) {
            Map<String, Object> reason = new HashMap<>();
            reason.put("rule", "跌破MA" + fast);
            return StrategySignalResult.builder()
                    .strategyId(strategyId())
                    .code(code)
                    .signalDate(series.getDates().get(index))
                    .side(SignalSide.SELL)
                    .score(new BigDecimal("65"))
                    .reason(reason)
                    .build();
        }
        return null;
    }

    @Override
    public boolean shouldEnter(BarSeries series, int index) {
        if (index < slow) {
            return false;
        }
        BigDecimal maFast = IndicatorUtils.ma(series.getCloses(), fast, index);
        BigDecimal maSlow = IndicatorUtils.ma(series.getCloses(), slow, index);
        BigDecimal prevFast = IndicatorUtils.ma(series.getCloses(), fast, index - 1);
        BigDecimal prevSlow = IndicatorUtils.ma(series.getCloses(), slow, index - 1);
        if (Objects.isNull(maFast) || Objects.isNull(maSlow) || Objects.isNull(prevFast) || Objects.isNull(prevSlow)) {
            return false;
        }
        return prevFast.compareTo(prevSlow) <= 0 && maFast.compareTo(maSlow) > 0;
    }

    @Override
    public boolean shouldExit(BarSeries series, int index, int entryIndex, BigDecimal entryBreakLow) {
        if (index < fast) {
            return false;
        }
        BigDecimal close = series.getCloses().get(index);
        BigDecimal maFast = IndicatorUtils.ma(series.getCloses(), fast, index);
        if (Objects.isNull(close) || Objects.isNull(maFast)) {
            return false;
        }
        return close.compareTo(maFast) < 0;
    }
}
