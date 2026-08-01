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
 * S1 均线趋势：MA20 上穿 MA60 + 成交量 > 20 日均量；跌破 MA20 离场
 */
@Component
public class S1MaTrendStrategy implements Strategy {

    public static final String ID = "S1";

    @Override
    public String strategyId() {
        return ID;
    }

    @Override
    public String strategyName() {
        return "均线趋势";
    }

    @Override
    public StrategySignalResult evaluate(String code, BarSeries series) {
        int index = series.size() - 1;
        if (index < 60) {
            return null;
        }
        if (shouldEnter(series, index)) {
            Map<String, Object> reason = new HashMap<>();
            reason.put("rule", "MA20上穿MA60且放量");
            reason.put("ma20", IndicatorUtils.ma(series.getCloses(), 20, index));
            reason.put("ma60", IndicatorUtils.ma(series.getCloses(), 60, index));
            return StrategySignalResult.builder()
                    .strategyId(ID)
                    .code(code)
                    .signalDate(series.getDates().get(index))
                    .side(SignalSide.BUY)
                    .score(new BigDecimal("80"))
                    .reason(reason)
                    .build();
        }
        if (shouldExit(series, index, -1, null)) {
            Map<String, Object> reason = new HashMap<>();
            reason.put("rule", "跌破MA20");
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
        BigDecimal ma20 = IndicatorUtils.ma(series.getCloses(), 20, index);
        BigDecimal ma60 = IndicatorUtils.ma(series.getCloses(), 60, index);
        BigDecimal prevMa20 = IndicatorUtils.ma(series.getCloses(), 20, index - 1);
        BigDecimal prevMa60 = IndicatorUtils.ma(series.getCloses(), 60, index - 1);
        BigDecimal volMa20 = IndicatorUtils.ma(series.getVolumes(), 20, index);
        BigDecimal volume = series.getVolumes().get(index);
        if (Objects.isNull(ma20) || Objects.isNull(ma60) || Objects.isNull(prevMa20)
                || Objects.isNull(prevMa60) || Objects.isNull(volMa20) || Objects.isNull(volume)) {
            return false;
        }
        boolean crossUp = prevMa20.compareTo(prevMa60) <= 0 && ma20.compareTo(ma60) > 0;
        boolean volOk = volume.compareTo(volMa20) > 0;
        return crossUp && volOk;
    }

    @Override
    public boolean shouldExit(BarSeries series, int index, int entryIndex, BigDecimal entryBreakLow) {
        if (index < 20) {
            return false;
        }
        BigDecimal ma20 = IndicatorUtils.ma(series.getCloses(), 20, index);
        BigDecimal close = series.getCloses().get(index);
        if (Objects.isNull(ma20) || Objects.isNull(close)) {
            return false;
        }
        return close.compareTo(ma20) < 0;
    }
}
