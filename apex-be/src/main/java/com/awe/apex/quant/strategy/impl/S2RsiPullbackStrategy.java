package com.awe.apex.quant.strategy.impl;

import com.awe.apex.quant.indicator.IndicatorUtils;
import com.awe.apex.quant.strategy.BarSeries;
import com.awe.apex.quant.strategy.SignalSide;
import com.awe.apex.quant.strategy.Strategy;
import com.awe.apex.quant.strategy.StrategyParams;
import com.awe.apex.quant.strategy.StrategySignalResult;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * S2 RSI 回调：价格在均线上方，RSI 从超卖回升；RSI 超买或跌破均线离场
 */
@Component
public class S2RsiPullbackStrategy implements Strategy {

    public static final String ID = "S2";

    @Resource
    private StrategyParams strategyParams;

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
        int maN = strategyParams.s2Ma();
        if (index < maN) {
            return null;
        }
        if (shouldEnter(series, index)) {
            Map<String, Object> reason = new HashMap<>();
            reason.put("rule", "MA" + maN + "上方RSI超卖回升");
            reason.put("rsi", IndicatorUtils.rsi(series.getCloses(), strategyParams.s2RsiPeriod(), index));
            reason.put("rsiOversold", strategyParams.s2RsiOversold());
            reason.put("rsiRebound", strategyParams.s2RsiRebound());
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
            reason.put("rule", "RSI超买或跌破MA" + maN);
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
        int maN = strategyParams.s2Ma();
        int rsiPeriod = strategyParams.s2RsiPeriod();
        BigDecimal oversold = strategyParams.s2RsiOversold();
        BigDecimal rebound = strategyParams.s2RsiRebound();
        if (index < maN) {
            return false;
        }
        BigDecimal ma = IndicatorUtils.ma(series.getCloses(), maN, index);
        BigDecimal close = series.getCloses().get(index);
        BigDecimal rsi = IndicatorUtils.rsi(series.getCloses(), rsiPeriod, index);
        BigDecimal prevRsi = IndicatorUtils.rsi(series.getCloses(), rsiPeriod, index - 1);
        if (Objects.isNull(ma) || Objects.isNull(close) || Objects.isNull(rsi) || Objects.isNull(prevRsi)) {
            return false;
        }
        boolean aboveMa = close.compareTo(ma) > 0;
        boolean reboundOk = prevRsi.compareTo(oversold) < 0 && rsi.compareTo(rebound) > 0;
        return aboveMa && reboundOk;
    }

    @Override
    public boolean shouldExit(BarSeries series, int index, int entryIndex, BigDecimal entryBreakLow) {
        int maN = strategyParams.s2Ma();
        int rsiPeriod = strategyParams.s2RsiPeriod();
        BigDecimal overbought = strategyParams.s2RsiOverbought();
        if (index < maN) {
            return false;
        }
        BigDecimal ma = IndicatorUtils.ma(series.getCloses(), maN, index);
        BigDecimal close = series.getCloses().get(index);
        BigDecimal rsi = IndicatorUtils.rsi(series.getCloses(), rsiPeriod, index);
        if (Objects.isNull(ma) || Objects.isNull(close) || Objects.isNull(rsi)) {
            return false;
        }
        return rsi.compareTo(overbought) > 0 || close.compareTo(ma) < 0;
    }
}
