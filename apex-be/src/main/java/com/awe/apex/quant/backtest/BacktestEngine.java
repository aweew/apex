package com.awe.apex.quant.backtest;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.exception.BusinessException;
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
    private static final BigDecimal PRICE_LIMIT_TOLERANCE = new BigDecimal("0.002");
    private static final BigDecimal MAX_COST_RATE = new BigDecimal("0.05");
    private static final BigDecimal MAX_LOTS = BigDecimal.valueOf(Integer.MAX_VALUE / 100);
    public static final String EXECUTION_MODEL_VERSION = "NEXT_OPEN_V4";
    public static final String PRICE_ADJUSTMENT = "QFQ";
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
        return run(strategy, series, initCash, cost, 0, false);
    }

    /**
     * 使用历史序列预热指标，并从指定位置独立起算回测资金
     *
     * @param strategy             策略
     * @param series               含预热区间的日线
     * @param initCash             初始资金
     * @param cost                 成本
     * @param evaluationStartIndex 评估起始索引，之前的数据只用于指标预热
     * @return 结果
     */
    public BacktestResult run(Strategy strategy, BarSeries series, BigDecimal initCash,
                              BacktestCostConfig cost, int evaluationStartIndex) {
        return run(strategy, series, initCash, cost, evaluationStartIndex, false);
    }

    /**
     * 使用历史序列预热指标，并按窗口清算规则运行回测
     *
     * @param strategy             策略
     * @param series               含预热区间的日线
     * @param initCash             初始资金
     * @param cost                 成本
     * @param evaluationStartIndex 评估起始索引，之前的数据只用于指标预热
     * @param liquidateAtEnd       是否在评估窗口末日按收盘价强平
     * @return 结果
     */
    public BacktestResult run(Strategy strategy, BarSeries series, BigDecimal initCash,
                              BacktestCostConfig cost, int evaluationStartIndex, boolean liquidateAtEnd) {
        if (Objects.isNull(initCash) || initCash.signum() <= 0) {
            throw new BusinessException("初始资金必须大于0");
        }
        if (Objects.isNull(cost)) {
            throw new BusinessException("回测成本配置不能为空");
        }
        BigDecimal[] costRates = new BigDecimal[]{cost.getCommissionRate(), cost.getStampTaxRate(),
                cost.getBuySlippage(), cost.getSellSlippage()};
        for (BigDecimal costRate : costRates) {
            if (Objects.isNull(costRate) || costRate.signum() < 0 || costRate.compareTo(MAX_COST_RATE) > 0) {
                throw new BusinessException("成本和滑点必须在0到5%之间");
            }
        }
        if (series.size() == 0 && evaluationStartIndex == 0) {
            return BacktestResult.builder()
                    .initCash(initCash)
                    .finalCash(initCash)
                    .totalReturn(BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP))
                    .annualReturn(BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP))
                    .maxDrawdown(BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP))
                    .sharpe(BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP))
                    .sortino(BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP))
                    .winRate(BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP))
                    .profitFactor(BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP))
                    .avgHoldDays(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP))
                    .tradeCount(0)
                    .endingPositionQuantity(0)
                    .endingPositionMarketValue(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                    .trades(new ArrayList<>())
                    .equities(new ArrayList<>())
                    .disclaimer(DISCLAIMER)
                    .build();
        }
        if (evaluationStartIndex < 0 || evaluationStartIndex >= series.size()) {
            throw new BusinessException("评估起始索引超出日线范围");
        }
        BigDecimal cash = initCash;
        int position = 0;
        int entryIndex = -1;
        BigDecimal entryBreakLow = null;
        BigDecimal entryCost = null;
        LocalDate entryDate = null;
        String pendingSide = null;
        BigDecimal pendingEntryBreakLow = null;

        // 样本内最后一个收盘信号可在首个样本外交易日开盘执行，但不继承样本内仓位。
        BarSeries initialVisibleSeries = evaluationStartIndex > 0 ? series.prefix(evaluationStartIndex) : null;
        if (Objects.nonNull(initialVisibleSeries)
                && strategy.shouldEnter(initialVisibleSeries, evaluationStartIndex - 1)) {
            pendingSide = "BUY";
            if (S3BreakoutVolumeStrategy.ID.equals(strategy.strategyId())) {
                pendingEntryBreakLow = series.getLows().get(evaluationStartIndex - 1);
            }
        }

        List<BacktestTradePoint> trades = new ArrayList<>();
        List<BacktestEquityPoint> equities = new ArrayList<>();
        List<BigDecimal> dailyReturns = new ArrayList<>();
        BigDecimal prevEquity = initCash;

        int winCount = 0;
        BigDecimal winSum = BigDecimal.ZERO;
        BigDecimal lossSum = BigDecimal.ZERO;
        int holdDaysSum = 0;
        int roundTrips = 0;

        for (int i = evaluationStartIndex; i < series.size(); i++) {
            BigDecimal open = series.getOpens().get(i);
            BigDecimal close = series.getCloses().get(i);
            BigDecimal volume = series.getVolumes().get(i);
            LocalDate date = series.getDates().get(i);
            if (Objects.isNull(close)) {
                continue;
            }
            boolean tradingSuspended = Objects.nonNull(volume) && volume.signum() == 0;
            if (Objects.nonNull(pendingSide) && !tradingSuspended && Objects.isNull(open)) {
                throw new BusinessException("开盘价缺失，无法按次日开盘价成交: " + date);
            }

            // 一字板只阻断排队方向：涨停不能买，跌停不能卖；反向订单仍可成交。
            boolean limitLocked = !tradingSuspended && Objects.nonNull(pendingSide)
                    && isOnePriceLimitLocked(series, i, pendingSide, open);
            boolean executionBlocked = tradingSuspended || limitLocked;

            // 先执行上一交易日收盘后形成的信号，避免使用当日收盘信息在同一价格成交。
            if ("SELL".equals(pendingSide) && position > 0 && !executionBlocked) {
                BigDecimal sellPrice = open.multiply(BigDecimal.ONE.subtract(cost.getSellSlippage()));
                BigDecimal amount = sellPrice.multiply(BigDecimal.valueOf(position));
                BigDecimal fee = amount.multiply(cost.getCommissionRate())
                        .add(amount.multiply(cost.getStampTaxRate()));
                cash = cash.add(amount).subtract(fee);
                trades.add(BacktestTradePoint.builder()
                        .tradeDate(date)
                        .side("SELL")
                        .price(sellPrice.setScale(4, RoundingMode.HALF_UP))
                        .quantity(position)
                        .amount(amount.setScale(2, RoundingMode.HALF_UP))
                        .fee(fee.setScale(4, RoundingMode.HALF_UP))
                        .reason("策略信号次日开盘离场")
                        .build());

                BigDecimal pnl = amount.subtract(fee).subtract(entryCost);
                if (pnl.signum() >= 0) {
                    winCount++;
                    winSum = winSum.add(pnl);
                } else {
                    lossSum = lossSum.add(pnl.abs());
                }
                holdDaysSum += (int) (date.toEpochDay() - entryDate.toEpochDay());
                roundTrips++;
                position = 0;
                entryIndex = -1;
                entryBreakLow = null;
                entryCost = null;
                entryDate = null;
                pendingSide = null;
            } else if ("BUY".equals(pendingSide) && position == 0 && !executionBlocked) {
                BigDecimal buyPrice = open.multiply(BigDecimal.ONE.add(cost.getBuySlippage()));
                BigDecimal lotCost = buyPrice.multiply(LOT)
                        .multiply(BigDecimal.ONE.add(cost.getCommissionRate()));
                BigDecimal affordableLots = cash.divide(lotCost, 0, RoundingMode.DOWN);
                if (affordableLots.compareTo(MAX_LOTS) > 0) {
                    throw new BusinessException("初始资金相对股价过大，超出可模拟持仓范围");
                }
                int lots = affordableLots.intValueExact();
                if (lots > 0) {
                    int qty = lots * 100;
                    BigDecimal amount = buyPrice.multiply(BigDecimal.valueOf(qty));
                    BigDecimal fee = amount.multiply(cost.getCommissionRate());
                    entryCost = amount.add(fee);
                    cash = cash.subtract(entryCost);
                    position = qty;
                    entryIndex = i;
                    entryBreakLow = pendingEntryBreakLow;
                    entryDate = date;
                    trades.add(BacktestTradePoint.builder()
                            .tradeDate(date)
                            .side("BUY")
                            .price(buyPrice.setScale(4, RoundingMode.HALF_UP))
                            .quantity(qty)
                            .amount(amount.setScale(2, RoundingMode.HALF_UP))
                            .fee(fee.setScale(4, RoundingMode.HALF_UP))
                            .reason("策略信号次日开盘开仓")
                            .build());
                }
                pendingSide = null;
                pendingEntryBreakLow = null;
            }

            if (liquidateAtEnd && !tradingSuspended && i == series.size() - 1 && position > 0 && entryIndex < i
                    && !isOnePriceLimitLocked(series, i, "SELL", close)) {
                BigDecimal sellPrice = close.multiply(BigDecimal.ONE.subtract(cost.getSellSlippage()));
                BigDecimal amount = sellPrice.multiply(BigDecimal.valueOf(position));
                BigDecimal fee = amount.multiply(cost.getCommissionRate())
                        .add(amount.multiply(cost.getStampTaxRate()));
                cash = cash.add(amount).subtract(fee);
                trades.add(BacktestTradePoint.builder()
                        .tradeDate(date)
                        .side("SELL")
                        .price(sellPrice.setScale(4, RoundingMode.HALF_UP))
                        .quantity(position)
                        .amount(amount.setScale(2, RoundingMode.HALF_UP))
                        .fee(fee.setScale(4, RoundingMode.HALF_UP))
                        .reason("窗口结束强平")
                        .build());
                BigDecimal pnl = amount.subtract(fee).subtract(entryCost);
                if (pnl.signum() >= 0) {
                    winCount++;
                    winSum = winSum.add(pnl);
                } else {
                    lossSum = lossSum.add(pnl.abs());
                }
                holdDaysSum += (int) (date.toEpochDay() - entryDate.toEpochDay());
                roundTrips++;
                position = 0;
                entryIndex = -1;
                entryBreakLow = null;
                entryCost = null;
                entryDate = null;
                pendingSide = null;
            }

            BigDecimal equity = cash.add(close.multiply(BigDecimal.valueOf(position)));
            equities.add(BacktestEquityPoint.builder().tradeDate(date).equity(equity.setScale(2, RoundingMode.HALF_UP)).build());
            if (prevEquity.signum() > 0) {
                dailyReturns.add(equity.subtract(prevEquity).divide(prevEquity, MC));
            }
            prevEquity = equity;

            // 收盘后只生成下一交易日订单，最后一个交易日不再产生无法执行的信号。
            if (i < series.size() - 1 && !tradingSuspended && Objects.isNull(pendingSide)) {
                BarSeries visibleSeries = series.prefix(i + 1);
                if (position > 0 && strategy.shouldExit(visibleSeries, i, entryIndex, entryBreakLow)) {
                    pendingSide = "SELL";
                } else if (position == 0 && strategy.shouldEnter(visibleSeries, i)) {
                    pendingSide = "BUY";
                    if (S3BreakoutVolumeStrategy.ID.equals(strategy.strategyId())) {
                        pendingEntryBreakLow = series.getLows().get(i);
                    }
                }
            }
        }

        BigDecimal finalEquity = CollUtil.isEmpty(equities) ? initCash : equities.get(equities.size() - 1).getEquity();
        BigDecimal endingPositionMarketValue = position > 0
                ? finalEquity.subtract(cash).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalReturn = finalEquity.subtract(initCash).divide(initCash, MC);
        BigDecimal maxDrawdown = calcMaxDrawdown(equities, initCash);
        BigDecimal sharpe = calcSharpe(dailyReturns);
        BigDecimal sortino = calcSortino(dailyReturns);
        int tradeDays = Math.max(series.size() - evaluationStartIndex, 1);
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
                .endingPositionQuantity(position)
                .endingPositionMarketValue(endingPositionMarketValue)
                .trades(trades)
                .equities(equities)
                .disclaimer(DISCLAIMER)
                .build();
    }

    private boolean isOnePriceLimitLocked(BarSeries series, int index, String side, BigDecimal executionPrice) {
        if (index <= 0 || Objects.isNull(executionPrice)
                || Objects.isNull(series.getCloses().get(index - 1))
                || series.getCloses().get(index - 1).signum() <= 0
                || Objects.isNull(series.getHighs().get(index))
                || Objects.isNull(series.getLows().get(index))
                || series.getHighs().get(index).compareTo(series.getLows().get(index)) != 0) {
            return false;
        }
        BigDecimal previousClose = series.getCloses().get(index - 1);
        BigDecimal priceChange = executionPrice.subtract(previousClose).divide(previousClose, MC);
        BigDecimal limitThreshold = series.getPriceLimitRates().get(index).subtract(PRICE_LIMIT_TOLERANCE);
        boolean limitUp = priceChange.compareTo(limitThreshold) >= 0;
        boolean limitDown = priceChange.compareTo(limitThreshold.negate()) <= 0;
        return ("BUY".equals(side) && limitUp) || ("SELL".equals(side) && limitDown);
    }

    private BigDecimal calcMaxDrawdown(List<BacktestEquityPoint> equities, BigDecimal initCash) {
        BigDecimal peak = initCash;
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
