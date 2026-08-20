package com.awe.apex.quant.factor;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.quant.domain.dto.AlphaComponentResp;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.indicator.IndicatorUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 因子指标与标准分计算器
 */
@Component
public class FactorCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /**
     * 计算指定交易日跨度的收益率。
     *
     * @param bars   升序日线
     * @param period 交易日跨度
     * @return 收益率百分比
     */
    public BigDecimal calculateReturn(List<BarDaily> bars, int period) {
        if (CollUtil.isEmpty(bars) || period <= 0 || bars.size() <= period) {
            return null;
        }
        BigDecimal latestClose = bars.get(bars.size() - 1).getClosePrice();
        BigDecimal baseClose = bars.get(bars.size() - 1 - period).getClosePrice();
        if (Objects.isNull(latestClose) || Objects.isNull(baseClose) || baseClose.signum() <= 0) {
            return null;
        }
        return latestClose.divide(baseClose, 8, RoundingMode.HALF_UP)
                .subtract(BigDecimal.ONE)
                .multiply(HUNDRED)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 计算最新成交额相对前期均额倍数。
     *
     * @param bars   升序日线
     * @param period 均值周期
     * @return 成交额倍数
     */
    public BigDecimal calculateAmountRatio(List<BarDaily> bars, int period) {
        if (CollUtil.isEmpty(bars) || period <= 0 || bars.size() <= period) {
            return null;
        }
        int latestIndex = bars.size() - 1;
        BigDecimal latestAmount = bars.get(latestIndex).getAmount();
        BigDecimal amountSum = BigDecimal.ZERO;
        for (int index = latestIndex - period; index < latestIndex; index++) {
            BigDecimal amount = bars.get(index).getAmount();
            if (Objects.isNull(amount)) {
                return null;
            }
            amountSum = amountSum.add(amount);
        }
        if (Objects.isNull(latestAmount) || amountSum.signum() <= 0) {
            return null;
        }
        BigDecimal averageAmount = amountSum.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);
        return latestAmount.divide(averageAmount, 2, RoundingMode.HALF_UP);
    }

    /**
     * 计算日收益率的年化波动率。
     *
     * @param bars   升序日线
     * @param period 日收益周期
     * @return 年化波动率百分比
     */
    public BigDecimal calculateAnnualizedVolatility(List<BarDaily> bars, int period) {
        if (CollUtil.isEmpty(bars) || period <= 1 || bars.size() <= period) {
            return null;
        }
        List<Double> returns = new ArrayList<>();
        int start = bars.size() - period;
        for (int index = start; index < bars.size(); index++) {
            BigDecimal previousClose = bars.get(index - 1).getClosePrice();
            BigDecimal currentClose = bars.get(index).getClosePrice();
            if (Objects.isNull(previousClose) || Objects.isNull(currentClose) || previousClose.signum() <= 0) {
                return null;
            }
            returns.add(currentClose.divide(previousClose, 10, RoundingMode.HALF_UP)
                    .subtract(BigDecimal.ONE).doubleValue());
        }
        double average = 0D;
        for (Double dailyReturn : returns) {
            average += dailyReturn;
        }
        average /= returns.size();
        double variance = 0D;
        for (Double dailyReturn : returns) {
            double deviation = dailyReturn - average;
            variance += deviation * deviation;
        }
        variance /= returns.size();
        return BigDecimal.valueOf(Math.sqrt(variance) * Math.sqrt(252D) * 100D)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 计算最新 MACD 柱。
     *
     * @param bars 升序日线
     * @return MACD 柱值
     */
    public BigDecimal calculateMacdHist(List<BarDaily> bars) {
        List<BigDecimal> closes = closePrices(bars);
        if (closes.size() < 35) {
            return null;
        }
        List<BigDecimal> difSeries = new ArrayList<>();
        for (int index = 0; index < closes.size(); index++) {
            difSeries.add(IndicatorUtils.macdDif(closes, index));
        }
        int latestIndex = closes.size() - 1;
        return IndicatorUtils.macdHist(difSeries.get(latestIndex),
                IndicatorUtils.macdDea(difSeries, latestIndex));
    }

    /**
     * 计算 ATR14 占最新收盘价比例。
     *
     * @param bars 升序日线
     * @return ATR 百分比
     */
    public BigDecimal calculateAtrPct(List<BarDaily> bars) {
        if (CollUtil.isEmpty(bars) || bars.size() < 15) {
            return null;
        }
        BigDecimal trueRangeSum = BigDecimal.ZERO;
        int latestIndex = bars.size() - 1;
        for (int index = latestIndex - 13; index <= latestIndex; index++) {
            BarDaily currentBar = bars.get(index);
            BigDecimal previousClose = bars.get(index - 1).getClosePrice();
            if (Objects.isNull(currentBar.getHighPrice()) || Objects.isNull(currentBar.getLowPrice())
                    || Objects.isNull(previousClose)) {
                return null;
            }
            BigDecimal range = currentBar.getHighPrice().subtract(currentBar.getLowPrice());
            BigDecimal highGap = currentBar.getHighPrice().subtract(previousClose).abs();
            BigDecimal lowGap = currentBar.getLowPrice().subtract(previousClose).abs();
            trueRangeSum = trueRangeSum.add(range.max(highGap).max(lowGap));
        }
        BigDecimal latestClose = bars.get(latestIndex).getClosePrice();
        if (Objects.isNull(latestClose) || latestClose.signum() <= 0) {
            return null;
        }
        return trueRangeSum.divide(new BigDecimal("14"), 8, RoundingMode.HALF_UP)
                .divide(latestClose, 8, RoundingMode.HALF_UP)
                .multiply(HUNDRED)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 计算 Alpha 综合分。
     *
     * @param components 评分组成
     * @return 按可用权重归一化后的综合分
     */
    public BigDecimal calculateAlphaScore(List<AlphaComponentResp> components) {
        if (CollUtil.isEmpty(components)) {
            return null;
        }
        BigDecimal weightedScore = BigDecimal.ZERO;
        BigDecimal availableWeight = BigDecimal.ZERO;
        for (AlphaComponentResp component : components) {
            if (!Boolean.TRUE.equals(component.getAvailable()) || Objects.isNull(component.getScore())
                    || Objects.isNull(component.getWeight())) {
                continue;
            }
            weightedScore = weightedScore.add(component.getScore().multiply(component.getWeight()));
            availableWeight = availableWeight.add(component.getWeight());
        }
        if (availableWeight.signum() <= 0) {
            return null;
        }
        return weightedScore.divide(availableWeight, 2, RoundingMode.HALF_UP);
    }

    /**
     * 计算 Alpha 可用权重覆盖率。
     *
     * @param components 评分组成
     * @return 覆盖率百分比
     */
    public BigDecimal calculateCoverage(List<AlphaComponentResp> components) {
        if (CollUtil.isEmpty(components)) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal availableWeight = BigDecimal.ZERO;
        for (AlphaComponentResp component : components) {
            if (Boolean.TRUE.equals(component.getAvailable()) && Objects.nonNull(component.getScore())
                    && Objects.nonNull(component.getWeight())) {
                availableWeight = availableWeight.add(component.getWeight());
            }
        }
        return availableWeight.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 将动量收益映射为标准分。
     *
     * @param momentumPct 动量百分比
     * @return 0-100 标准分
     */
    public BigDecimal scoreMomentum(BigDecimal momentumPct) {
        return score(momentumPct, new BigDecimal("1.5"), new BigDecimal("50"));
    }

    /**
     * 将 ROE 映射为标准分。
     *
     * @param roe ROE 百分比
     * @return 0-100 标准分
     */
    public BigDecimal scoreRoe(BigDecimal roe) {
        if (Objects.isNull(roe)) {
            return null;
        }
        return clamp(roe.add(new BigDecimal("5")).divide(new BigDecimal("30"), 6, RoundingMode.HALF_UP)
                .multiply(HUNDRED));
    }

    /**
     * 将盈利增速映射为标准分。
     *
     * @param growthPct 盈利同比百分比
     * @return 0-100 标准分
     */
    public BigDecimal scoreGrowth(BigDecimal growthPct) {
        return score(growthPct, BigDecimal.ONE, new BigDecimal("50"));
    }

    /**
     * 将成交额倍数映射为标准分。
     *
     * @param amountRatio 成交额倍数
     * @return 0-100 标准分
     */
    public BigDecimal scoreVolume(BigDecimal amountRatio) {
        if (Objects.isNull(amountRatio)) {
            return null;
        }
        return clamp(amountRatio.multiply(new BigDecimal("50")));
    }

    /**
     * 将市场强度限制在标准分范围。
     *
     * @param marketStrength 市场强度
     * @return 0-100 标准分
     */
    public BigDecimal scoreMarket(BigDecimal marketStrength) {
        return clamp(marketStrength);
    }

    private BigDecimal score(BigDecimal rawValue, BigDecimal multiplier, BigDecimal base) {
        return Objects.isNull(rawValue) ? null : clamp(base.add(rawValue.multiply(multiplier)));
    }

    private BigDecimal clamp(BigDecimal score) {
        if (Objects.isNull(score)) {
            return null;
        }
        return score.max(BigDecimal.ZERO).min(HUNDRED).setScale(2, RoundingMode.HALF_UP);
    }

    private List<BigDecimal> closePrices(List<BarDaily> bars) {
        List<BigDecimal> closes = new ArrayList<>();
        if (CollUtil.isEmpty(bars)) {
            return closes;
        }
        for (BarDaily bar : bars) {
            closes.add(bar.getClosePrice());
        }
        return closes;
    }
}
