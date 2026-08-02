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

    private static final BigDecimal SCORE_MIN = new BigDecimal("65");
    private static final BigDecimal SCORE_MAX = new BigDecimal("92");
    private static final BigDecimal BUY_BASE = new BigDecimal("68");

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
            BigDecimal close = series.getCloses().get(index);
            BigDecimal highPrev = IndicatorUtils.highest(series.getHighs(), lookback, index - 1);
            BigDecimal volMa = IndicatorUtils.ma(series.getVolumes(), lookback, index);
            BigDecimal volume = series.getVolumes().get(index);
            BigDecimal breakoutPct = pctAbove(close, highPrev);
            BigDecimal volumeRatio = safeDivide(volume, volMa);
            BigDecimal score = strengthScore(breakoutPct, volumeRatio, strategyParams.s3VolumeRatio());

            Map<String, Object> reason = new HashMap<>();
            reason.put("rule", lookback + "日新高且量比>" + strategyParams.s3VolumeRatio());
            reason.put("breakLow", series.getLows().get(index));
            reason.put("lookback", lookback);
            reason.put("volumeRatioMin", strategyParams.s3VolumeRatio());
            reason.put("breakoutPct", breakoutPct);
            reason.put("volumeRatio", volumeRatio);
            reason.put("strengthScore", score);
            return StrategySignalResult.builder()
                    .strategyId(ID)
                    .code(code)
                    .signalDate(series.getDates().get(index))
                    .side(SignalSide.BUY)
                    .score(score)
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
     * 连续强度分：突破幅度 + 量比超阈值程度，落在 [65,92]
     */
    private BigDecimal strengthScore(BigDecimal breakoutPct, BigDecimal volumeRatio, BigDecimal volRatioMin) {
        BigDecimal score = BUY_BASE;
        if (Objects.nonNull(breakoutPct) && breakoutPct.signum() > 0) {
            // 0~3% 突破加分，过大视为一日游行情略降
            if (breakoutPct.compareTo(new BigDecimal("3")) <= 0) {
                score = score.add(breakoutPct.multiply(new BigDecimal("3")).min(new BigDecimal("10")));
            } else if (breakoutPct.compareTo(new BigDecimal("6")) <= 0) {
                score = score.add(new BigDecimal("7"));
            } else {
                score = score.add(new BigDecimal("3"));
            }
        }
        if (Objects.nonNull(volumeRatio) && Objects.nonNull(volRatioMin)) {
            BigDecimal excess = volumeRatio.subtract(volRatioMin);
            if (excess.signum() > 0) {
                score = score.add(excess.multiply(new BigDecimal("4")).min(new BigDecimal("10")));
            }
        }
        return clampScore(score);
    }

    private static BigDecimal clampScore(BigDecimal score) {
        if (score.compareTo(SCORE_MIN) < 0) {
            return SCORE_MIN;
        }
        if (score.compareTo(SCORE_MAX) > 0) {
            return SCORE_MAX;
        }
        return score.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal pctAbove(BigDecimal upper, BigDecimal lower) {
        if (Objects.isNull(upper) || Objects.isNull(lower) || lower.signum() <= 0) {
            return null;
        }
        return upper.subtract(lower)
                .divide(lower, 6, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal safeDivide(BigDecimal a, BigDecimal b) {
        if (Objects.isNull(a) || Objects.isNull(b) || b.signum() <= 0) {
            return null;
        }
        return a.divide(b, 4, RoundingMode.HALF_UP);
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
