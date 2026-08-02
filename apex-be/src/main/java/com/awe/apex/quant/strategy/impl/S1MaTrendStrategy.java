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
 * S1 均线趋势：快线上穿慢线 + 放量；跌破快线离场
 */
@Component
public class S1MaTrendStrategy implements Strategy {

    public static final String ID = "S1";

    @Resource
    private StrategyParams strategyParams;

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
        int slow = strategyParams.s1SlowMa();
        if (index < slow) {
            return null;
        }
        if (shouldEnter(series, index)) {
            int fast = strategyParams.s1FastMa();
            Map<String, Object> reason = new HashMap<>();
            reason.put("rule", "MA" + fast + "上穿MA" + slow + "且放量");
            reason.put("maFast", IndicatorUtils.ma(series.getCloses(), fast, index));
            reason.put("maSlow", IndicatorUtils.ma(series.getCloses(), slow, index));
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
            reason.put("rule", "跌破MA" + strategyParams.s1FastMa());
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
        int fast = strategyParams.s1FastMa();
        int slow = strategyParams.s1SlowMa();
        int volMaN = strategyParams.s1VolMa();
        if (index < slow) {
            return false;
        }
        BigDecimal maFast = IndicatorUtils.ma(series.getCloses(), fast, index);
        BigDecimal maSlow = IndicatorUtils.ma(series.getCloses(), slow, index);
        BigDecimal prevMaFast = IndicatorUtils.ma(series.getCloses(), fast, index - 1);
        BigDecimal prevMaSlow = IndicatorUtils.ma(series.getCloses(), slow, index - 1);
        BigDecimal volMa = IndicatorUtils.ma(series.getVolumes(), volMaN, index);
        BigDecimal volume = series.getVolumes().get(index);
        if (Objects.isNull(maFast) || Objects.isNull(maSlow) || Objects.isNull(prevMaFast)
                || Objects.isNull(prevMaSlow) || Objects.isNull(volMa) || Objects.isNull(volume)) {
            return false;
        }
        boolean crossUp = prevMaFast.compareTo(prevMaSlow) <= 0 && maFast.compareTo(maSlow) > 0;
        boolean volOk = volume.compareTo(volMa) > 0;
        return crossUp && volOk;
    }

    @Override
    public boolean shouldExit(BarSeries series, int index, int entryIndex, BigDecimal entryBreakLow) {
        int fast = strategyParams.s1FastMa();
        if (index < fast) {
            return false;
        }
        BigDecimal maFast = IndicatorUtils.ma(series.getCloses(), fast, index);
        BigDecimal close = series.getCloses().get(index);
        if (Objects.isNull(maFast) || Objects.isNull(close)) {
            return false;
        }
        return close.compareTo(maFast) < 0;
    }
}
