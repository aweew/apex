package com.awe.apex.quant.indicator;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * 技术指标纯函数工具
 */
public final class IndicatorUtils {

    private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);

    private IndicatorUtils() {
    }

    /**
     * 简单移动平均
     *
     * @param values 价格序列
     * @param period 周期
     * @param index  结束下标（含）
     * @return 均线值，数据不足返回 null
     */
    public static BigDecimal ma(List<BigDecimal> values, int period, int index) {
        if (Objects.isNull(values) || period <= 0 || index < period - 1 || index >= values.size()) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = index - period + 1; i <= index; i++) {
            BigDecimal value = values.get(i);
            if (Objects.isNull(value)) {
                return null;
            }
            sum = sum.add(value);
        }
        return sum.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
    }

    /**
     * RSI（Wilder 平滑的简化版：均值增益/均值损失）
     *
     * @param closes 收盘价
     * @param period 周期，常用 14
     * @param index  结束下标（含）
     * @return RSI，数据不足返回 null
     */
    public static BigDecimal rsi(List<BigDecimal> closes, int period, int index) {
        if (Objects.isNull(closes) || period <= 0 || index < period || index >= closes.size()) {
            return null;
        }
        BigDecimal gainSum = BigDecimal.ZERO;
        BigDecimal lossSum = BigDecimal.ZERO;
        for (int i = index - period + 1; i <= index; i++) {
            BigDecimal prev = closes.get(i - 1);
            BigDecimal curr = closes.get(i);
            if (Objects.isNull(prev) || Objects.isNull(curr)) {
                return null;
            }
            BigDecimal change = curr.subtract(prev);
            if (change.signum() >= 0) {
                gainSum = gainSum.add(change);
            } else {
                lossSum = lossSum.add(change.abs());
            }
        }
        BigDecimal avgGain = gainSum.divide(BigDecimal.valueOf(period), MC);
        BigDecimal avgLoss = lossSum.divide(BigDecimal.valueOf(period), MC);
        if (avgLoss.signum() == 0) {
            return new BigDecimal("100.0000");
        }
        BigDecimal rs = avgGain.divide(avgLoss, MC);
        BigDecimal rsi = BigDecimal.valueOf(100).subtract(
                BigDecimal.valueOf(100).divide(BigDecimal.ONE.add(rs), MC)
        );
        return rsi.setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * 最近 period 根最高价（含 index）
     *
     * @param highs  最高价序列
     * @param period 周期
     * @param index  结束下标
     * @return 最高价
     */
    public static BigDecimal highest(List<BigDecimal> highs, int period, int index) {
        if (Objects.isNull(highs) || period <= 0 || index < period - 1 || index >= highs.size()) {
            return null;
        }
        BigDecimal max = highs.get(index - period + 1);
        for (int i = index - period + 2; i <= index; i++) {
            BigDecimal value = highs.get(i);
            if (Objects.isNull(value) || Objects.isNull(max)) {
                return null;
            }
            if (value.compareTo(max) > 0) {
                max = value;
            }
        }
        return max;
    }

    /**
     * EMA
     *
     * @param values 序列
     * @param period 周期
     * @param index  结束下标
     * @return EMA
     */
    public static BigDecimal ema(List<BigDecimal> values, int period, int index) {
        if (Objects.isNull(values) || period <= 0 || index < period - 1 || index >= values.size()) {
            return null;
        }
        BigDecimal k = BigDecimal.valueOf(2).divide(BigDecimal.valueOf(period + 1L), MC);
        BigDecimal ema = ma(values, period, period - 1);
        if (Objects.isNull(ema)) {
            return null;
        }
        for (int i = period; i <= index; i++) {
            BigDecimal value = values.get(i);
            if (Objects.isNull(value)) {
                return null;
            }
            ema = value.multiply(k).add(ema.multiply(BigDecimal.ONE.subtract(k)));
        }
        return ema.setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * MACD DIF = EMA12 - EMA26
     *
     * @param closes 收盘价
     * @param index  结束下标
     * @return DIF
     */
    public static BigDecimal macdDif(List<BigDecimal> closes, int index) {
        BigDecimal ema12 = ema(closes, 12, index);
        BigDecimal ema26 = ema(closes, 26, index);
        if (Objects.isNull(ema12) || Objects.isNull(ema26)) {
            return null;
        }
        return ema12.subtract(ema26).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * MACD DEA：DIF 的 9 日 EMA（按已算好的 DIF 序列）
     *
     * @param difSeries DIF 序列（与 closes 等长，前段可为 null）
     * @param index     结束下标
     * @return DEA
     */
    public static BigDecimal macdDea(List<BigDecimal> difSeries, int index) {
        return emaSparse(difSeries, 9, index);
    }

    /**
     * MACD 柱 = 2 * (DIF - DEA)
     *
     * @param dif DIF
     * @param dea DEA
     * @return 柱值
     */
    public static BigDecimal macdHist(BigDecimal dif, BigDecimal dea) {
        if (Objects.isNull(dif) || Objects.isNull(dea)) {
            return null;
        }
        return dif.subtract(dea).multiply(BigDecimal.valueOf(2)).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * 是否上穿：prevA&lt;=prevB 且 a&gt;b
     *
     * @param prevA 前值 A
     * @param prevB 前值 B
     * @param a     当前 A
     * @param b     当前 B
     * @return 是否金叉/上穿
     */
    public static boolean crossedUp(BigDecimal prevA, BigDecimal prevB, BigDecimal a, BigDecimal b) {
        if (Objects.isNull(prevA) || Objects.isNull(prevB) || Objects.isNull(a) || Objects.isNull(b)) {
            return false;
        }
        return prevA.compareTo(prevB) <= 0 && a.compareTo(b) > 0;
    }

    /**
     * 是否下穿
     *
     * @param prevA 前值 A
     * @param prevB 前值 B
     * @param a     当前 A
     * @param b     当前 B
     * @return 是否死叉/下穿
     */
    public static boolean crossedDown(BigDecimal prevA, BigDecimal prevB, BigDecimal a, BigDecimal b) {
        if (Objects.isNull(prevA) || Objects.isNull(prevB) || Objects.isNull(a) || Objects.isNull(b)) {
            return false;
        }
        return prevA.compareTo(prevB) >= 0 && a.compareTo(b) < 0;
    }

    /**
     * 允许前段为 null 的 EMA（从首个非空窗口起步）
     */
    private static BigDecimal emaSparse(List<BigDecimal> values, int period, int index) {
        if (Objects.isNull(values) || period <= 0 || index < 0 || index >= values.size()) {
            return null;
        }
        int start = -1;
        for (int i = 0; i <= index; i++) {
            if (Objects.nonNull(values.get(i))) {
                start = i;
                break;
            }
        }
        if (start < 0 || index - start + 1 < period) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        int endSeed = start + period - 1;
        for (int i = start; i <= endSeed; i++) {
            BigDecimal v = values.get(i);
            if (Objects.isNull(v)) {
                return null;
            }
            sum = sum.add(v);
        }
        BigDecimal ema = sum.divide(BigDecimal.valueOf(period), MC);
        BigDecimal k = BigDecimal.valueOf(2).divide(BigDecimal.valueOf(period + 1L), MC);
        for (int i = endSeed + 1; i <= index; i++) {
            BigDecimal v = values.get(i);
            if (Objects.isNull(v)) {
                return null;
            }
            ema = v.multiply(k).add(ema.multiply(BigDecimal.ONE.subtract(k)));
        }
        return ema.setScale(4, RoundingMode.HALF_UP);
    }
}
