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
 * S2 RSI 回调：价格在均线上方，RSI 从超卖回升；RSI 超买或跌破均线离场
 */
@Component
public class S2RsiPullbackStrategy implements Strategy {

    public static final String ID = "S2";

    private static final BigDecimal SCORE_MIN = new BigDecimal("65");
    private static final BigDecimal SCORE_MAX = new BigDecimal("92");
    private static final BigDecimal BUY_BASE = new BigDecimal("65");

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
            int rsiPeriod = strategyParams.s2RsiPeriod();
            BigDecimal ma = IndicatorUtils.ma(series.getCloses(), maN, index);
            BigDecimal close = series.getCloses().get(index);
            BigDecimal rsi = IndicatorUtils.rsi(series.getCloses(), rsiPeriod, index);
            BigDecimal prevRsi = IndicatorUtils.rsi(series.getCloses(), rsiPeriod, index - 1);
            BigDecimal volMa = IndicatorUtils.ma(series.getVolumes(), 20, index);
            BigDecimal volume = series.getVolumes().get(index);
            BigDecimal rsiLift = Objects.nonNull(rsi) && Objects.nonNull(prevRsi)
                    ? rsi.subtract(prevRsi).setScale(4, RoundingMode.HALF_UP) : null;
            BigDecimal distMaPct = pctAbove(close, ma);
            BigDecimal volRatio = safeDivide(volume, volMa);
            BigDecimal score = strengthScore(rsi, strategyParams.s2RsiRebound(), rsiLift, distMaPct, volRatio);

            Map<String, Object> reason = new HashMap<>();
            reason.put("rule", "MA" + maN + "上方RSI超卖回升");
            reason.put("rsi", rsi);
            reason.put("rsiOversold", strategyParams.s2RsiOversold());
            reason.put("rsiRebound", strategyParams.s2RsiRebound());
            reason.put("rsiLift", rsiLift);
            reason.put("distMaPct", distMaPct);
            reason.put("volRatio", volRatio);
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

    /**
     * 连续强度分：RSI 回升幅度 + 距均线距离 + 量能确认，落在 [65,92]
     */
    private BigDecimal strengthScore(BigDecimal rsi, BigDecimal rebound, BigDecimal rsiLift,
                                     BigDecimal distMaPct, BigDecimal volRatio) {
        BigDecimal score = BUY_BASE;
        if (Objects.nonNull(rsi) && Objects.nonNull(rebound)) {
            BigDecimal above = rsi.subtract(rebound);
            if (above.signum() > 0) {
                score = score.add(above.multiply(new BigDecimal("0.8")).min(new BigDecimal("10")));
            }
        }
        if (Objects.nonNull(rsiLift) && rsiLift.signum() > 0) {
            score = score.add(rsiLift.multiply(new BigDecimal("0.5")).min(new BigDecimal("6")));
        }
        // 贴近均线的回调更优：0~5% 加分，拉太远减益
        if (Objects.nonNull(distMaPct)) {
            if (distMaPct.compareTo(new BigDecimal("5")) <= 0) {
                score = score.add(new BigDecimal("8"));
            } else if (distMaPct.compareTo(new BigDecimal("10")) <= 0) {
                score = score.add(new BigDecimal("4"));
            } else {
                score = score.add(new BigDecimal("1"));
            }
        }
        if (Objects.nonNull(volRatio) && volRatio.compareTo(new BigDecimal("1.1")) >= 0) {
            score = score.add(new BigDecimal("4"));
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
}
