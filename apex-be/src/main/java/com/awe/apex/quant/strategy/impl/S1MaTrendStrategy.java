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
 * S1 均线趋势：快线上穿慢线 + 放量；跌破快线离场
 */
@Component
public class S1MaTrendStrategy implements Strategy {

    public static final String ID = "S1";

    /**
     * 策略逻辑版本，交易规则变化时递增
     */
    public static final String LOGIC_VERSION = "S1_V1";

    private static final BigDecimal SCORE_MIN = new BigDecimal("65");
    private static final BigDecimal SCORE_MAX = new BigDecimal("92");
    private static final BigDecimal BUY_BASE = new BigDecimal("68");

    @Resource
    private StrategyParams strategyParams;

    private Integer fixedFastMa;

    private Integer fixedSlowMa;

    private Integer fixedVolumeMa;

    /**
     * 创建使用系统参数的S1策略
     */
    public S1MaTrendStrategy() {
    }

    /**
     * 创建使用实验快照参数的S1策略
     *
     * @param fastMa   快均线周期
     * @param slowMa   慢均线周期
     * @param volumeMa 成交量均线周期
     */
    public S1MaTrendStrategy(int fastMa, int slowMa, int volumeMa) {
        this.fixedFastMa = fastMa;
        this.fixedSlowMa = slowMa;
        this.fixedVolumeMa = volumeMa;
    }

    @Override
    public String strategyId() {
        return ID;
    }

    @Override
    public String strategyName() {
        return "均线趋势";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String logicVersion() {
        return LOGIC_VERSION;
    }

    @Override
    public StrategySignalResult evaluate(String code, BarSeries series) {
        int index = series.size() - 1;
        int slow = slowMa();
        if (index < slow) {
            return null;
        }
        if (shouldEnter(series, index)) {
            int fast = fastMa();
            int volMaN = volumeMa();
            BigDecimal maFast = IndicatorUtils.ma(series.getCloses(), fast, index);
            BigDecimal maSlow = IndicatorUtils.ma(series.getCloses(), slow, index);
            BigDecimal close = series.getCloses().get(index);
            BigDecimal volMa = IndicatorUtils.ma(series.getVolumes(), volMaN, index);
            BigDecimal volume = series.getVolumes().get(index);
            BigDecimal crossPct = pctAbove(maFast, maSlow);
            BigDecimal volRatio = safeDivide(volume, volMa);
            BigDecimal closeVsMa = pctAbove(close, maFast);
            BigDecimal score = strengthScore(crossPct, volRatio, closeVsMa);

            Map<String, Object> reason = new HashMap<>();
            reason.put("rule", "MA" + fast + "上穿MA" + slow + "且放量");
            reason.put("maFast", maFast);
            reason.put("maSlow", maSlow);
            reason.put("crossPct", crossPct);
            reason.put("volRatio", volRatio);
            reason.put("closeVsMaFast", closeVsMa);
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
            reason.put("rule", "跌破MA" + fastMa());
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
        int fast = fastMa();
        int slow = slowMa();
        int volMaN = volumeMa();
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
        int fast = fastMa();
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

    private int fastMa() {
        return Objects.nonNull(fixedFastMa) ? fixedFastMa : strategyParams.s1FastMa();
    }

    private int slowMa() {
        return Objects.nonNull(fixedSlowMa) ? fixedSlowMa : strategyParams.s1SlowMa();
    }

    private int volumeMa() {
        return Objects.nonNull(fixedVolumeMa) ? fixedVolumeMa : strategyParams.s1VolMa();
    }

    /**
     * 连续强度分：金叉幅度 + 量比 + 收盘相对快线位置，落在 [65,92]
     */
    private BigDecimal strengthScore(BigDecimal crossPct, BigDecimal volRatio, BigDecimal closeVsMa) {
        BigDecimal score = BUY_BASE;
        // 金叉幅度：0~2% → +0~10
        if (Objects.nonNull(crossPct) && crossPct.signum() > 0) {
            BigDecimal boost = crossPct.multiply(new BigDecimal("5")).min(new BigDecimal("10"));
            score = score.add(boost);
        }
        // 量比：1~3 → +0~8
        if (Objects.nonNull(volRatio) && volRatio.compareTo(BigDecimal.ONE) > 0) {
            BigDecimal boost = volRatio.subtract(BigDecimal.ONE).multiply(new BigDecimal("4"))
                    .min(new BigDecimal("8"));
            score = score.add(boost);
        }
        // 收盘略高于快线加分，过高略减（追涨）
        if (Objects.nonNull(closeVsMa)) {
            if (closeVsMa.compareTo(new BigDecimal("0.5")) >= 0
                    && closeVsMa.compareTo(new BigDecimal("3")) <= 0) {
                score = score.add(new BigDecimal("6"));
            } else if (closeVsMa.compareTo(new BigDecimal("3")) > 0
                    && closeVsMa.compareTo(new BigDecimal("6")) <= 0) {
                score = score.add(new BigDecimal("3"));
            } else if (closeVsMa.compareTo(new BigDecimal("6")) > 0) {
                score = score.add(new BigDecimal("1"));
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
}
