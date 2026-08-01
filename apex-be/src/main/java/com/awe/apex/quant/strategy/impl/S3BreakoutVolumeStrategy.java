package com.awe.apex.quant.strategy.impl;

import com.awe.apex.quant.indicator.IndicatorUtils;
import com.awe.apex.quant.strategy.BarSeries;
import com.awe.apex.quant.strategy.SignalSide;
import com.awe.apex.quant.strategy.Strategy;
import com.awe.apex.quant.strategy.StrategySignalResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * S3 突破放量：收盘创 20 日高 + 量比 &gt;1.5；跌破突破日低点离场
 */
@Component
public class S3BreakoutVolumeStrategy implements Strategy {

    public static final String ID = "S3";

    @Override
    public String strategyId() {
        return ID;
    }

    @Override
    public String strategyName() {
        return "突破放量";
    }

    @Override
    public StrategySignalResult evaluate(String code, BarSeries series) {
        int index = series.size() - 1;
        if (index < 20) {
            return null;
        }
        if (shouldEnter(series, index)) {
            Map<String, Object> reason = new HashMap<>();
            reason.put("rule", "20日新高且量比>1.5");
            reason.put("breakLow", series.getLows().get(index));
            return StrategySignalResult.builder()
                    .strategyId(ID)
                    .code(code)
                    .signalDate(series.getDates().get(index))
                    .side(SignalSide.BUY)
                    .score(new BigDecimal("78"))
                    .reason(reason)
                    .build();
        }
        return null;
    }

    @Override
    public boolean shouldEnter(BarSeries series, int index) {
        if (index < 20) {
            return false;
        }
        BigDecimal close = series.getCloses().get(index);
        BigDecimal high20Prev = IndicatorUtils.highest(series.getHighs(), 20, index - 1);
        BigDecimal volMa20 = IndicatorUtils.ma(series.getVolumes(), 20, index);
        BigDecimal volume = series.getVolumes().get(index);
        if (Objects.isNull(close) || Objects.isNull(high20Prev) || Objects.isNull(volMa20) || Objects.isNull(volume)) {
            return false;
        }
        if (volMa20.signum() <= 0) {
            return false;
        }
        boolean newHigh = close.compareTo(high20Prev) > 0;
        BigDecimal volumeRatio = volume.divide(volMa20, 4, RoundingMode.HALF_UP);
        return newHigh && volumeRatio.compareTo(new BigDecimal("1.5")) > 0;
    }

    @Override
    public boolean shouldExit(BarSeries series, int index, int entryIndex, BigDecimal entryBreakLow) {
        if (Objects.isNull(entryBreakLow) || index <= entryIndex) {
            return false;
        }
        BigDecimal close = series.getCloses().get(index);
        if (Objects.isNull(close)) {
            return false;
        }
        return close.compareTo(entryBreakLow) < 0;
    }
}
