package com.awe.apex.quant.backtest;

import com.awe.apex.quant.strategy.BarSeries;
import com.awe.apex.quant.strategy.Strategy;
import com.awe.apex.quant.strategy.impl.S3BreakoutVolumeStrategy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 单标的日线回测引擎
 */
@Component
public class BacktestEngine {

    private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);
    private static final BigDecimal LOT = BigDecimal.valueOf(100);
    public static final String DISCLAIMER = "过去表现不代表未来收益";

    /**
     * 运行回测
     *
     * @param strategy 策略
     * @param series   日线
     * @param initCash 初始资金
     * @param cost     成本
     * @return 结果
     */
    public BacktestResult run(Strategy strategy, BarSeries series, BigDecimal initCash, BacktestCostConfig cost) {
        BigDecimal cash = initCash;
        int position = 0;
        int entryIndex = -1;
        BigDecimal entryBreakLow = null;
        BigDecimal entryPrice = null;
        LocalDate entryDate = null;

        List<BacktestTradePoint> trades = new ArrayList<>();
        List<BacktestEquityPoint> equities = new ArrayList<>();
        List<BigDecimal> dailyReturns = new ArrayList<>();
        BigDecimal prevEquity = initCash;

        int winCount = 0;
        int lossCount = 0;
        BigDecimal winSum = BigDecimal.ZERO;
        BigDecimal lossSum = BigDecimal.ZERO;
        int holdDaysSum = 0;
        int roundTrips = 0;

        for (int i = 0; i < series.size(); i++) {
            BigDecimal close = series.getCloses().get(i);
            LocalDate date = series.getDates().get(i);
            if (Objects.isNull(close)) {
                continue;
            }

            // 涨跌停简化：涨跌幅绝对值 >= 9.8% 视为不可成交
            boolean limitLocked = false;
            if (i > 0 && Objects.nonNull(series.getCloses().get(i - 1))
                    && series.getCloses().get(i - 1).signum() > 0) {
                BigDecimal pct = close.subtract(series.getCloses().get(i - 1))
                        .divide(series.getCloses().get(i - 1), MC).abs();
                limitLocked = pct.compareTo(new BigDecimal("0.098")) >= 0;
            }

            if (position > 0 && strategy.shouldExit(series, i, entryIndex, entryBreakLow) && !limitLocked) {
                BigDecimal sellPrice = close.multiply(BigDecimal.ONE.subtract(cost.getSellSlippage()), MC);
                BigDecimal amount = sellPrice.multiply(BigDecimal.valueOf(position), MC);
                BigDecimal fee = amount.multiply(cost.getCommissionRate(), MC)
                        .add(amount.multiply(cost.getStampTaxRate(), MC));
                cash = cash.add(amount).subtract(fee);
                trades.add(BacktestTradePoint.builder()
                        .tradeDate(date)
                        .side("SELL")
                        .price(sellPrice.setScale(4, RoundingMode.HALF_UP))
                        .quantity(position)
                        .amount(amount.setScale(2, RoundingMode.HALF_UP))
                        .fee(fee.setScale(4, RoundingMode.HALF_UP))
                        .reason("策略离场")
                        .build());

                BigDecimal pnl = sellPrice.subtract(entryPrice).multiply(BigDecimal.valueOf(position), MC).subtract(fee);
                if (pnl.signum() >= 0) {
                    winCount++;
                    winSum = winSum.add(pnl);
                } else {
                    lossCount++;
                    lossSum = lossSum.add(pnl.abs());
                }
                holdDaysSum += (int) (date.toEpochDay() - entryDate.toEpochDay());
                roundTrips++;
                position = 0;
                entryIndex = -1;
                entryBreakLow = null;
                entryPrice = null;
                entryDate = null;
            } else if (position == 0 && strategy.shouldEnter(series, i) && !limitLocked) {
                BigDecimal buyPrice = close.multiply(BigDecimal.ONE.add(cost.getBuySlippage()), MC);
                int lots = cash.divide(buyPrice.multiply(LOT), 0, RoundingMode.DOWN).intValue();
                if (lots > 0) {
                    int qty = lots * 100;
                    BigDecimal amount = buyPrice.multiply(BigDecimal.valueOf(qty), MC);
                    BigDecimal fee = amount.multiply(cost.getCommissionRate(), MC);
                    if (cash.compareTo(amount.add(fee)) >= 0) {
                        cash = cash.subtract(amount).subtract(fee);
                        position = qty;
                        entryIndex = i;
                        entryPrice = buyPrice;
                        entryDate = date;
                        if (S3BreakoutVolumeStrategy.ID.equals(strategy.strategyId())) {
                            entryBreakLow = series.getLows().get(i);
                        }
                        trades.add(BacktestTradePoint.builder()
                                .tradeDate(date)
                                .side("BUY")
                                .price(buyPrice.setScale(4, RoundingMode.HALF_UP))
                                .quantity(qty)
                                .amount(amount.setScale(2, RoundingMode.HALF_UP))
                                .fee(fee.setScale(4, RoundingMode.HALF_UP))
                                .reason("策略开仓")
                                .build());
                    }
                }
            }

            BigDecimal equity = cash.add(close.multiply(BigDecimal.valueOf(position), MC));
            equities.add(BacktestEquityPoint.builder().tradeDate(date).equity(equity.setScale(2, RoundingMode.HALF_UP)).build());
            if (prevEquity.signum() > 0) {
                dailyReturns.add(equity.subtract(prevEquity).divide(prevEquity, MC));
            }
            prevEquity = equity;
        }

        BigDecimal finalEquity = equities.isEmpty() ? initCash : equities.get(equities.size() - 1).getEquity();
        BigDecimal totalReturn = finalEquity.subtract(initCash).divide(initCash, MC);
        BigDecimal maxDrawdown = calcMaxDrawdown(equities);
        BigDecimal sharpe = calcSharpe(dailyReturns);
        BigDecimal sortino = calcSortino(dailyReturns);
        int tradeDays = Math.max(series.size(), 1);
        BigDecimal annualReturn = BigDecimal.valueOf(Math.pow(1 + totalReturn.doubleValue(), 252.0 / tradeDays) - 1)
                .setScale(6, RoundingMode.HALF_UP);
        BigDecimal winRate = roundTrips == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(winCount).divide(BigDecimal.valueOf(roundTrips), 6, RoundingMode.HALF_UP);
        BigDecimal profitFactor = lossSum.signum() == 0
                ? (winSum.signum() > 0 ? new BigDecimal("999") : BigDecimal.ZERO)
                : winSum.divide(lossSum, 6, RoundingMode.HALF_UP);
        BigDecimal avgHoldDays = roundTrips == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(holdDaysSum).divide(BigDecimal.valueOf(roundTrips), 4, RoundingMode.HALF_UP);

        return BacktestResult.builder()
                .initCash(initCash)
                .finalCash(finalEquity)
                .totalReturn(totalReturn.setScale(6, RoundingMode.HALF_UP))
                .annualReturn(annualReturn)
                .maxDrawdown(maxDrawdown)
                .sharpe(sharpe)
                .sortino(sortino)
                .winRate(winRate)
                .profitFactor(profitFactor)
                .avgHoldDays(avgHoldDays)
                .tradeCount(trades.size())
                .trades(trades)
                .equities(equities)
                .disclaimer(DISCLAIMER)
                .build();
    }

    private BigDecimal calcMaxDrawdown(List<BacktestEquityPoint> equities) {
        BigDecimal peak = BigDecimal.ZERO;
        BigDecimal maxDd = BigDecimal.ZERO;
        for (BacktestEquityPoint point : equities) {
            if (point.getEquity().compareTo(peak) > 0) {
                peak = point.getEquity();
            }
            if (peak.signum() > 0) {
                BigDecimal dd = peak.subtract(point.getEquity()).divide(peak, MC);
                if (dd.compareTo(maxDd) > 0) {
                    maxDd = dd;
                }
            }
        }
        return maxDd.setScale(6, RoundingMode.HALF_UP);
    }

    private BigDecimal calcSharpe(List<BigDecimal> dailyReturns) {
        if (dailyReturns.size() < 2) {
            return BigDecimal.ZERO;
        }
        BigDecimal mean = dailyReturns.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(dailyReturns.size()), MC);
        BigDecimal variance = BigDecimal.ZERO;
        for (BigDecimal r : dailyReturns) {
            BigDecimal diff = r.subtract(mean);
            variance = variance.add(diff.multiply(diff, MC));
        }
        variance = variance.divide(BigDecimal.valueOf(dailyReturns.size() - 1), MC);
        if (variance.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        double std = Math.sqrt(variance.doubleValue());
        if (std == 0) {
            return BigDecimal.ZERO;
        }
        double sharpe = mean.doubleValue() / std * Math.sqrt(252);
        return BigDecimal.valueOf(sharpe).setScale(6, RoundingMode.HALF_UP);
    }

    private BigDecimal calcSortino(List<BigDecimal> dailyReturns) {
        if (dailyReturns.size() < 5) {
            return BigDecimal.ZERO;
        }
        BigDecimal mean = dailyReturns.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(dailyReturns.size()), MC);
        BigDecimal downside = BigDecimal.ZERO;
        int n = 0;
        for (BigDecimal r : dailyReturns) {
            if (r.signum() < 0) {
                downside = downside.add(r.multiply(r, MC));
                n++;
            }
        }
        if (n == 0) {
            return mean.signum() > 0 ? new BigDecimal("99") : BigDecimal.ZERO;
        }
        double dd = Math.sqrt(downside.divide(BigDecimal.valueOf(n), MC).doubleValue());
        if (dd == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(mean.doubleValue() / dd * Math.sqrt(252)).setScale(6, RoundingMode.HALF_UP);
    }
}
