package com.awe.apex.quant.strategy.impl;

import com.awe.apex.quant.indicator.IndicatorUtils;
import com.awe.apex.quant.strategy.BarSeries;
import com.awe.apex.quant.strategy.SignalSide;
import com.awe.apex.quant.strategy.Strategy;
import com.awe.apex.quant.strategy.StrategySignalResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * S2 RSI 回调：价格在 MA60 上方，RSI14 从 &lt;30 回升至 &gt;35；RSI&gt;70 或跌破 MA60 离场
 */
@Component
public class S2RsiPullbackStrategy implements Strategy {

    public static final String ID = "S2";

    @Override
    public String strategyId() {
        return ID;
    }

    @Override
    public String strategyName() {
        return "RSI回调";
    }

    @Override
    public StrategySignalResult evaluate(String code, BarSeries series) {
        int index = series.size() - 1;
        if (index < 60) {
            return null;
        }
        if (shouldEnter(series, index)) {
            Map<String, Object> reason = new HashMap<>();
            reason.put("rule", "MA60上方RSI超卖回升");
            reason.put("rsi", IndicatorUtils.rsi(series.getCloses(), 14, index));
            return StrategySignalResult.builder()
                    .strategyId(ID)
                    .code(code)
                    .signalDate(series.getDates().get(index))
                    .side(SignalSide.BUY)
                    .score(new BigDecimal("75"))
                    .reason(reason)
                    .build();
        }
        if (shouldExit(series, index, -1, null)) {
            Map<String, Object> reason = new HashMap<>();
            reason.put("rule", "RSI超买或跌破MA60");
            return StrategySignalResult.builder()
                    .strategyId(ID)
                    .code(code)
                    .signalDate(series.getDates().get(index))
                    .side(SignalSide.SELL)
                    .score(new BigDecimal("70"))
                    .reason(reason)
                    .build();
        }
        return null;
    }

    @Override
    public boolean shouldEnter(BarSeries series, int index) {
        if (index < 60) {
            return false;
        }
        BigDecimal ma60 = IndicatorUtils.ma(series.getCloses(), 60, index);
        BigDecimal close = series.getCloses().get(index);
        BigDecimal rsi = IndicatorUtils.rsi(series.getCloses(), 14, index);
        BigDecimal prevRsi = IndicatorUtils.rsi(series.getCloses(), 14, index - 1);
        if (Objects.isNull(ma60) || Objects.isNull(close) || Objects.isNull(rsi) || Objects.isNull(prevRsi)) {
            return false;
        }
        boolean aboveMa = close.compareTo(ma60) > 0;
        boolean rebound = prevRsi.compareTo(new BigDecimal("30")) < 0
                && rsi.compareTo(new BigDecimal("35")) > 0;
        return aboveMa && rebound;
    }

    @Override
    public boolean shouldExit(BarSeries series, int index, int entryIndex, BigDecimal entryBreakLow) {
        if (index < 60) {
            return false;
        }
        BigDecimal ma60 = IndicatorUtils.ma(series.getCloses(), 60, index);
        BigDecimal close = series.getCloses().get(index);
        BigDecimal rsi = IndicatorUtils.rsi(series.getCloses(), 14, index);
        if (Objects.isNull(ma60) || Objects.isNull(close) || Objects.isNull(rsi)) {
            return false;
        }
        return rsi.compareTo(new BigDecimal("70")) > 0 || close.compareTo(ma60) < 0;
    }
}
