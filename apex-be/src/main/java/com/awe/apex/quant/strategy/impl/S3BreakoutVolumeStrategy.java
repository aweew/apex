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
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * S3 突破放量：收盘创 N 日高 + 量比超阈值；跌破最近突破日低点离场
 */
@Component
public class S3BreakoutVolumeStrategy implements Strategy {

    public static final String ID = "S3";

    @Resource
    private StrategyParams strategyParams;

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
        int lookback = strategyParams.s3Lookback();
        if (index < lookback) {
            return null;
        }
        if (shouldEnter(series, index)) {
            Map<String, Object> reason = new HashMap<>();
            reason.put("rule", lookback + "日新高且量比>" + strategyParams.s3VolumeRatio());
            reason.put("breakLow", series.getLows().get(index));
            reason.put("lookback", lookback);
            reason.put("volumeRatioMin", strategyParams.s3VolumeRatio());
            return StrategySignalResult.builder()
                    .strategyId(ID)
                    .code(code)
                    .signalDate(series.getDates().get(index))
                    .side(SignalSide.BUY)
                    .score(new BigDecimal("78"))
                    .reason(reason)
                    .build();
        }
        // 日频卖出：相对最近一次突破日低点失效
        int entryIndex = findLatestEntryIndex(series, index);
        if (entryIndex >= 0) {
            BigDecimal breakLow = series.getLows().get(entryIndex);
            if (shouldExit(series, index, entryIndex, breakLow)) {
                Map<String, Object> reason = new HashMap<>();
                reason.put("rule", "跌破突破日低点");
                reason.put("entryDate", series.getDates().get(entryIndex));
                reason.put("breakLow", breakLow);
                return StrategySignalResult.builder()
                        .strategyId(ID)
                        .code(code)
                        .signalDate(series.getDates().get(index))
                        .side(SignalSide.SELL)
                        .score(new BigDecimal("72"))
                        .reason(reason)
                        .build();
            }
        }
        return null;
    }

    @Override
    public boolean shouldEnter(BarSeries series, int index) {
        int lookback = strategyParams.s3Lookback();
        BigDecimal volRatioMin = strategyParams.s3VolumeRatio();
        if (index < lookback) {
            return false;
        }
        BigDecimal close = series.getCloses().get(index);
        BigDecimal highPrev = IndicatorUtils.highest(series.getHighs(), lookback, index - 1);
        BigDecimal volMa = IndicatorUtils.ma(series.getVolumes(), lookback, index);
        BigDecimal volume = series.getVolumes().get(index);
        if (Objects.isNull(close) || Objects.isNull(highPrev) || Objects.isNull(volMa) || Objects.isNull(volume)) {
            return false;
        }
        if (volMa.signum() <= 0) {
            return false;
        }
        boolean newHigh = close.compareTo(highPrev) > 0;
        BigDecimal volumeRatio = volume.divide(volMa, 4, RoundingMode.HALF_UP);
        return newHigh && volumeRatio.compareTo(volRatioMin) > 0;
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

    /**
     * 回溯最近一次突破买入日（不含当日）
     *
     * @param series 序列
     * @param index  当前下标
     * @return 下标，无则 -1
     */
    private int findLatestEntryIndex(BarSeries series, int index) {
        int lookback = strategyParams.s3Lookback();
        int from = Math.max(lookback, index - 60);
        for (int i = index - 1; i >= from; i--) {
            if (shouldEnter(series, i)) {
                return i;
            }
        }
        return -1;
    }
}
