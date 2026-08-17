package com.awe.apex.quant.backtest;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.strategy.BarSeries;
import com.awe.apex.quant.strategy.Strategy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BacktestEngineExecutionTest {

    @Test
    void shouldExecuteCloseSignalAtNextTradingDayOpen() {
        Strategy strategy = mock(Strategy.class);
        when(strategy.shouldEnter(any(), anyInt())).thenAnswer(invocation -> invocation.getArgument(1, Integer.class) == 0);
        when(strategy.shouldExit(any(), anyInt(), anyInt(), any())).thenReturn(false);
        when(strategy.strategyId()).thenReturn("TEST");
        BarSeries series = BarSeries.from(List.of(
                bar(LocalDate.of(2026, 1, 5), "9.50", "10.00"),
                bar(LocalDate.of(2026, 1, 6), "20.00", "21.00"),
                bar(LocalDate.of(2026, 1, 7), "22.00", "23.00")));

        BacktestResult result = new BacktestEngine().run(
                strategy,
                series,
                new BigDecimal("100000"),
                BacktestCostConfig.of(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        assertEquals(1, result.getTrades().size());
        assertEquals(LocalDate.of(2026, 1, 6), result.getTrades().get(0).getTradeDate());
        assertEquals(new BigDecimal("20.0000"), result.getTrades().get(0).getPrice());
    }

    @Test
    void shouldRejectPendingOrderWhenNextOpenIsMissing() {
        Strategy strategy = mock(Strategy.class);
        when(strategy.shouldEnter(any(), anyInt())).thenAnswer(invocation -> invocation.getArgument(1, Integer.class) == 0);
        when(strategy.shouldExit(any(), anyInt(), anyInt(), any())).thenReturn(false);
        when(strategy.strategyId()).thenReturn("TEST");
        BarDaily missingOpenBar = bar(LocalDate.of(2026, 1, 6), "20.00", "21.00");
        missingOpenBar.setOpenPrice(null);
        BarSeries series = BarSeries.from(List.of(
                bar(LocalDate.of(2026, 1, 5), "9.50", "10.00"),
                missingOpenBar,
                bar(LocalDate.of(2026, 1, 7), "22.00", "23.00")));

        BusinessException exception = assertThrows(BusinessException.class, () -> new BacktestEngine().run(
                strategy,
                series,
                new BigDecimal("100000"),
                BacktestCostConfig.of(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)));

        assertEquals("开盘价缺失，无法按次日开盘价成交: 2026-01-06", exception.getMessage());
    }

    @Test
    void shouldLiquidateOpenPositionAtEvaluationWindowEnd() {
        Strategy strategy = mock(Strategy.class);
        when(strategy.shouldEnter(any(), anyInt())).thenAnswer(invocation -> invocation.getArgument(1, Integer.class) == 0);
        when(strategy.shouldExit(any(), anyInt(), anyInt(), any())).thenReturn(false);
        when(strategy.strategyId()).thenReturn("TEST");
        BarSeries series = BarSeries.from(List.of(
                bar(LocalDate.of(2026, 1, 5), "10.00", "10.00"),
                bar(LocalDate.of(2026, 1, 6), "10.50", "10.50"),
                bar(LocalDate.of(2026, 1, 7), "11.00", "11.00")));

        BacktestResult result = new BacktestEngine().run(
                strategy,
                series,
                new BigDecimal("100000"),
                BacktestCostConfig.of(new BigDecimal("0.001"), new BigDecimal("0.0005"),
                        BigDecimal.ZERO, BigDecimal.ZERO),
                0,
                true);

        assertEquals(2, result.getTrades().size());
        assertEquals("BUY", result.getTrades().get(0).getSide());
        assertEquals("SELL", result.getTrades().get(1).getSide());
        assertEquals("窗口结束强平", result.getTrades().get(1).getReason());
        assertEquals(LocalDate.of(2026, 1, 7), result.getTrades().get(1).getTradeDate());
        assertEquals(0, result.getEndingPositionQuantity());
        assertEquals(new BigDecimal("0.00"), result.getEndingPositionMarketValue());
    }

    @Test
    void shouldKeepPositionWhenBoughtOnEvaluationWindowEnd() {
        Strategy strategy = mock(Strategy.class);
        when(strategy.shouldEnter(any(), anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(1, Integer.class) == 1);
        when(strategy.shouldExit(any(), anyInt(), anyInt(), any())).thenReturn(false);
        when(strategy.strategyId()).thenReturn("TEST");
        BarSeries series = BarSeries.from(List.of(
                bar(LocalDate.of(2026, 1, 5), "10.00", "10.00"),
                bar(LocalDate.of(2026, 1, 6), "10.00", "10.00"),
                bar(LocalDate.of(2026, 1, 7), "10.00", "11.00")));

        BacktestResult result = new BacktestEngine().run(
                strategy, series, new BigDecimal("100000"), zeroCost(), 0, true);

        assertEquals(1, result.getTrades().size());
        assertEquals("BUY", result.getTrades().get(0).getSide());
        assertEquals(10000, result.getEndingPositionQuantity());
        assertEquals(new BigDecimal("110000.00"), result.getEndingPositionMarketValue());
    }

    @Test
    void shouldKeepPositionMarkedToMarketWhenEndLiquidationIsLimitLocked() {
        Strategy strategy = enterOnFirstBar();
        BarSeries series = BarSeries.from(List.of(
                bar(LocalDate.of(2026, 1, 5), "10.00", "10.00"),
                bar(LocalDate.of(2026, 1, 6), "10.00", "10.00"),
                bar(LocalDate.of(2026, 1, 7), "9.00", "9.00")));

        BacktestResult result = new BacktestEngine().run(
                strategy, series, new BigDecimal("100000"), zeroCost(), 0, true);

        assertEquals(1, result.getTrades().size());
        assertEquals("BUY", result.getTrades().get(0).getSide());
        assertEquals(10000, result.getEndingPositionQuantity());
        assertEquals(new BigDecimal("90000.00"), result.getEndingPositionMarketValue());
        assertEquals(new BigDecimal("90000.00"), result.getFinalCash());
        assertEquals(new BigDecimal("-0.100000"), result.getTotalReturn());
    }

    @Test
    void shouldKeepOrdersAndPositionsPendingAcrossZeroVolumeBars() {
        BarDaily suspendedEntryBar = bar(LocalDate.of(2026, 1, 6), "10.00", "10.00");
        suspendedEntryBar.setVolume(BigDecimal.ZERO);
        BacktestResult delayedEntryResult = new BacktestEngine().run(
                enterOnFirstBar(),
                BarSeries.from(List.of(
                        bar(LocalDate.of(2026, 1, 5), "10.00", "10.00"),
                        suspendedEntryBar,
                        bar(LocalDate.of(2026, 1, 7), "10.50", "10.50"))),
                new BigDecimal("100000"), zeroCost());

        Strategy suspendedSignalStrategy = mock(Strategy.class);
        when(suspendedSignalStrategy.shouldEnter(any(), anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(1, Integer.class) == 1);
        when(suspendedSignalStrategy.shouldExit(any(), anyInt(), anyInt(), any())).thenReturn(false);
        when(suspendedSignalStrategy.strategyId()).thenReturn("TEST");
        BacktestResult suspendedSignalResult = new BacktestEngine().run(
                suspendedSignalStrategy,
                BarSeries.from(List.of(
                        bar(LocalDate.of(2026, 1, 5), "10.00", "10.00"),
                        suspendedEntryBar,
                        bar(LocalDate.of(2026, 1, 7), "10.50", "10.50"))),
                new BigDecimal("100000"), zeroCost());

        BarDaily suspendedExitBar = bar(LocalDate.of(2026, 1, 7), "9.00", "9.00");
        suspendedExitBar.setVolume(BigDecimal.ZERO);
        BacktestResult heldPositionResult = new BacktestEngine().run(
                enterOnFirstBar(),
                BarSeries.from(List.of(
                        bar(LocalDate.of(2026, 1, 5), "10.00", "10.00"),
                        bar(LocalDate.of(2026, 1, 6), "10.00", "10.00"),
                        suspendedExitBar)),
                new BigDecimal("100000"), zeroCost(), 0, true);

        assertEquals(1, delayedEntryResult.getTrades().size());
        assertEquals(LocalDate.of(2026, 1, 7), delayedEntryResult.getTrades().get(0).getTradeDate());
        assertTrue(suspendedSignalResult.getTrades().isEmpty());
        assertEquals(1, heldPositionResult.getTrades().size());
        assertEquals(10000, heldPositionResult.getEndingPositionQuantity());
        assertEquals(new BigDecimal("90000.00"), heldPositionResult.getEndingPositionMarketValue());
    }

    @Test
    void shouldMeasureFirstEvaluationDayLossFromInitialEquity() {
        Strategy strategy = enterOnFirstBar();
        BarSeries series = BarSeries.from(List.of(
                bar(LocalDate.of(2026, 1, 5), "10.00", "10.00"),
                bar(LocalDate.of(2026, 1, 6), "10.00", "8.00"),
                bar(LocalDate.of(2026, 1, 7), "8.00", "8.00")));

        BacktestResult result = new BacktestEngine().run(
                strategy, series, new BigDecimal("100000"), zeroCost(), 1);

        assertEquals(new BigDecimal("0.200000"), result.getMaxDrawdown());
    }

    @Test
    void shouldHideFutureBarsFromStrategyEvaluation() {
        Strategy strategy = mock(Strategy.class);
        when(strategy.shouldEnter(any(), anyInt())).thenAnswer(invocation -> {
            BarSeries visibleSeries = invocation.getArgument(0, BarSeries.class);
            int signalIndex = invocation.getArgument(1, Integer.class);
            return visibleSeries.size() > signalIndex + 1;
        });
        when(strategy.shouldExit(any(), anyInt(), anyInt(), any())).thenReturn(false);
        when(strategy.strategyId()).thenReturn("FUTURE_READER");
        BarSeries series = BarSeries.from(List.of(
                bar(LocalDate.of(2026, 1, 5), "10.00", "10.00"),
                bar(LocalDate.of(2026, 1, 6), "11.00", "11.00"),
                bar(LocalDate.of(2026, 1, 7), "12.00", "12.00")));

        BacktestResult result = new BacktestEngine().run(
                strategy,
                series,
                new BigDecimal("100000"),
                BacktestCostConfig.of(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        assertEquals(0, result.getTrades().size());
    }

    @Test
    void shouldKeepEmptySeriesCompatibleForStandardBacktest() {
        Strategy strategy = mock(Strategy.class);
        when(strategy.strategyId()).thenReturn("EMPTY");

        BacktestResult result = new BacktestEngine().run(
                strategy,
                BarSeries.from(List.of()),
                new BigDecimal("100000"),
                BacktestCostConfig.defaults());

        assertEquals(new BigDecimal("0.000000"), result.getTotalReturn());
        assertEquals(new BigDecimal("100000"), result.getFinalCash());
        assertEquals(0, result.getTrades().size());
    }

    @Test
    void shouldRejectNonPositiveInitialCash() {
        Strategy strategy = mock(Strategy.class);
        when(strategy.strategyId()).thenReturn("TEST");

        BusinessException exception = assertThrows(BusinessException.class, () -> new BacktestEngine().run(
                strategy,
                BarSeries.from(List.of(bar(LocalDate.of(2026, 1, 5), "10.00", "10.00"))),
                BigDecimal.ZERO,
                BacktestCostConfig.defaults()));

        assertEquals("初始资金必须大于0", exception.getMessage());
    }

    @Test
    void shouldRejectPositionQuantityOverflow() {
        Strategy strategy = enterOnFirstBar();
        BarSeries series = BarSeries.from(List.of(
                bar(LocalDate.of(2026, 1, 5), "0.01", "0.01"),
                bar(LocalDate.of(2026, 1, 6), "0.01", "0.01")));

        BusinessException exception = assertThrows(BusinessException.class, () -> new BacktestEngine().run(
                strategy, series, new BigDecimal("1000000000000"), zeroCost()));

        assertEquals("初始资金相对股价过大，超出可模拟持仓范围", exception.getMessage());
    }

    @Test
    void shouldNeverSpendMoreThanAvailableCashWhenSizingBuyOrder() {
        Strategy strategy = enterOnFirstBar();
        BarSeries series = BarSeries.from(List.of(
                bar(LocalDate.of(2026, 1, 5), "497.0314", "497.0314"),
                bar(LocalDate.of(2026, 1, 6), "497.0314", "497.0314")));
        BigDecimal initCash = new BigDecimal("669094127040.7017032");
        BigDecimal commissionRate = new BigDecimal("0.005333");

        BacktestResult result = new BacktestEngine().run(
                strategy,
                series,
                initCash,
                BacktestCostConfig.of(commissionRate, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        BacktestTradePoint buyTrade = result.getTrades().get(0);
        assertEquals(1339039600, buyTrade.getQuantity());
        assertTrue(buyTrade.getAmount().add(buyTrade.getFee()).compareTo(initCash) <= 0);
    }

    @Test
    void shouldKeepLargeSellAmountAndFeeExactBeforeOutputRounding() {
        Strategy strategy = mock(Strategy.class);
        when(strategy.shouldEnter(any(), anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(1, Integer.class) == 0);
        when(strategy.shouldExit(any(), anyInt(), anyInt(), any()))
                .thenAnswer(invocation -> invocation.getArgument(1, Integer.class) == 1);
        when(strategy.strategyId()).thenReturn("TEST");
        BigDecimal initCash = new BigDecimal("999999999999.99");
        BigDecimal buyPrice = new BigDecimal("499.9999");
        BigDecimal sellPrice = new BigDecimal("500.12345678");
        BigDecimal commissionRate = new BigDecimal("0.00050049");
        BigDecimal stampTaxRate = new BigDecimal("0.00050049");
        BarSeries series = BarSeries.from(List.of(
                bar(LocalDate.of(2026, 1, 5), "499.9999", "499.9999"),
                bar(LocalDate.of(2026, 1, 6), "499.9999", "499.9999"),
                bar(LocalDate.of(2026, 1, 7), "500.12345678", "500.12345678")));

        BacktestResult result = new BacktestEngine().run(
                strategy,
                series,
                initCash,
                BacktestCostConfig.of(commissionRate, stampTaxRate, BigDecimal.ZERO, BigDecimal.ZERO));

        BigDecimal lotCost = buyPrice.multiply(BigDecimal.valueOf(100))
                .multiply(BigDecimal.ONE.add(commissionRate));
        int quantity = initCash.divide(lotCost, 0, RoundingMode.DOWN)
                .intValueExact() * 100;
        BigDecimal buyAmount = buyPrice.multiply(BigDecimal.valueOf(quantity));
        BigDecimal buyFee = buyAmount.multiply(commissionRate);
        BigDecimal sellAmount = sellPrice.multiply(BigDecimal.valueOf(quantity));
        BigDecimal sellFee = sellAmount.multiply(commissionRate).add(sellAmount.multiply(stampTaxRate));
        BigDecimal expectedFinalCash = initCash.subtract(buyAmount).subtract(buyFee)
                .add(sellAmount).subtract(sellFee).setScale(2, RoundingMode.HALF_UP);
        BacktestTradePoint sellTrade = result.getTrades().get(1);
        assertEquals(sellAmount.setScale(2, RoundingMode.HALF_UP), sellTrade.getAmount());
        assertEquals(sellFee.setScale(4, RoundingMode.HALF_UP), sellTrade.getFee());
        assertEquals(expectedFinalCash, result.getFinalCash());
    }

    @Test
    void shouldKeepLargeOpenPositionMarketValueExactBeforeOutputRounding() {
        BigDecimal initCash = new BigDecimal("999999999999.99");
        BigDecimal buyPrice = new BigDecimal("499.9999");
        BigDecimal closingPrice = new BigDecimal("500.12345678");
        BarSeries series = BarSeries.from(List.of(
                bar(LocalDate.of(2026, 1, 5), "499.9999", "499.9999"),
                bar(LocalDate.of(2026, 1, 6), "499.9999", "499.9999"),
                bar(LocalDate.of(2026, 1, 7), "500.12345678", "500.12345678")));

        BacktestResult result = new BacktestEngine().run(
                enterOnFirstBar(), series, initCash, zeroCost());

        int quantity = initCash.divide(buyPrice.multiply(BigDecimal.valueOf(100)),
                        0, RoundingMode.DOWN)
                .intValueExact() * 100;
        BigDecimal remainingCash = initCash.subtract(buyPrice.multiply(BigDecimal.valueOf(quantity)));
        BigDecimal expectedMarketValue = closingPrice.multiply(BigDecimal.valueOf(quantity))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal expectedFinalCash = remainingCash.add(closingPrice.multiply(BigDecimal.valueOf(quantity)))
                .setScale(2, RoundingMode.HALF_UP);
        assertEquals(expectedMarketValue, result.getEndingPositionMarketValue());
        assertEquals(expectedFinalCash, result.getFinalCash());
    }

    @Test
    void shouldRejectInvalidCostAssumptionsBeforeSimulation() {
        Strategy strategy = enterOnFirstBar();
        BarSeries series = BarSeries.from(List.of(
                bar(LocalDate.of(2026, 1, 5), "10.00", "10.00"),
                bar(LocalDate.of(2026, 1, 6), "10.00", "10.00")));
        BacktestCostConfig negativeCommission = BacktestCostConfig.of(
                new BigDecimal("-0.001"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        BusinessException missingCostException = assertThrows(BusinessException.class,
                () -> new BacktestEngine().run(strategy, series, new BigDecimal("100000"), null));
        BusinessException invalidRateException = assertThrows(BusinessException.class,
                () -> new BacktestEngine().run(strategy, series, new BigDecimal("100000"), negativeCommission));

        assertEquals("回测成本配置不能为空", missingCostException.getMessage());
        assertEquals("成本和滑点必须在0到5%之间", invalidRateException.getMessage());
    }

    @Test
    void shouldAllowBuyOrderAtOnePriceLimitDown() {
        Strategy strategy = mock(Strategy.class);
        when(strategy.shouldEnter(any(), anyInt())).thenAnswer(invocation -> invocation.getArgument(1, Integer.class) == 0);
        when(strategy.shouldExit(any(), anyInt(), anyInt(), any())).thenReturn(false);
        when(strategy.strategyId()).thenReturn("TEST");
        BarSeries series = BarSeries.from(List.of(
                bar(LocalDate.of(2026, 1, 5), "10.00", "10.00"),
                bar(LocalDate.of(2026, 1, 6), "9.00", "9.00"),
                bar(LocalDate.of(2026, 1, 7), "9.50", "9.50")));

        BacktestResult result = new BacktestEngine().run(
                strategy,
                series,
                new BigDecimal("100000"),
                BacktestCostConfig.of(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        assertEquals(1, result.getTrades().size());
        assertEquals(LocalDate.of(2026, 1, 6), result.getTrades().get(0).getTradeDate());
    }

    @Test
    void shouldAllowSellOrderAtOnePriceLimitUp() {
        Strategy strategy = mock(Strategy.class);
        when(strategy.shouldEnter(any(), anyInt())).thenAnswer(invocation -> invocation.getArgument(1, Integer.class) == 0);
        when(strategy.shouldExit(any(), anyInt(), anyInt(), any()))
                .thenAnswer(invocation -> invocation.getArgument(1, Integer.class) == 1);
        when(strategy.strategyId()).thenReturn("TEST");
        BarSeries series = BarSeries.from(List.of(
                bar(LocalDate.of(2026, 1, 5), "10.00", "10.00"),
                bar(LocalDate.of(2026, 1, 6), "10.00", "10.50"),
                bar(LocalDate.of(2026, 1, 7), "11.55", "11.55")));

        BacktestResult result = new BacktestEngine().run(
                strategy,
                series,
                new BigDecimal("100000"),
                BacktestCostConfig.of(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        assertEquals(2, result.getTrades().size());
        assertEquals("SELL", result.getTrades().get(1).getSide());
        assertEquals(LocalDate.of(2026, 1, 7), result.getTrades().get(1).getTradeDate());
    }

    @Test
    void shouldDeferBuyOrderAtOnePriceLimitUp() {
        Strategy strategy = mock(Strategy.class);
        when(strategy.shouldEnter(any(), anyInt())).thenAnswer(invocation -> invocation.getArgument(1, Integer.class) == 0);
        when(strategy.shouldExit(any(), anyInt(), anyInt(), any())).thenReturn(false);
        when(strategy.strategyId()).thenReturn("TEST");
        BarSeries series = BarSeries.from(List.of(
                bar(LocalDate.of(2026, 1, 5), "10.00", "10.00"),
                bar(LocalDate.of(2026, 1, 6), "11.00", "11.00"),
                bar(LocalDate.of(2026, 1, 7), "10.80", "10.80")));

        BacktestResult result = new BacktestEngine().run(
                strategy,
                series,
                new BigDecimal("100000"),
                BacktestCostConfig.of(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        assertEquals(1, result.getTrades().size());
        assertEquals(LocalDate.of(2026, 1, 7), result.getTrades().get(0).getTradeDate());
    }

    @Test
    void shouldDeferSellOrderAtOnePriceLimitDown() {
        Strategy strategy = mock(Strategy.class);
        when(strategy.shouldEnter(any(), anyInt())).thenAnswer(invocation -> invocation.getArgument(1, Integer.class) == 0);
        when(strategy.shouldExit(any(), anyInt(), anyInt(), any()))
                .thenAnswer(invocation -> invocation.getArgument(1, Integer.class) == 1);
        when(strategy.strategyId()).thenReturn("TEST");
        BarSeries series = BarSeries.from(List.of(
                bar(LocalDate.of(2026, 1, 5), "10.00", "10.00"),
                bar(LocalDate.of(2026, 1, 6), "10.00", "10.50"),
                bar(LocalDate.of(2026, 1, 7), "9.45", "9.45"),
                bar(LocalDate.of(2026, 1, 8), "9.60", "9.60")));

        BacktestResult result = new BacktestEngine().run(
                strategy,
                series,
                new BigDecimal("100000"),
                BacktestCostConfig.of(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        assertEquals(2, result.getTrades().size());
        assertEquals("SELL", result.getTrades().get(1).getSide());
        assertEquals(LocalDate.of(2026, 1, 8), result.getTrades().get(1).getTradeDate());
    }

    @Test
    void shouldAllowTenPercentOnePriceRiseOnChiNext() {
        Strategy strategy = enterOnFirstBar();
        BarSeries series = BarSeries.from(List.of(
                bar("300750", LocalDate.of(2026, 1, 5), "10.00", "10.00"),
                bar("300750", LocalDate.of(2026, 1, 6), "11.00", "11.00"),
                bar("300750", LocalDate.of(2026, 1, 7), "11.10", "11.10")));

        BacktestResult result = new BacktestEngine().run(
                strategy, series, new BigDecimal("100000"), zeroCost());

        assertEquals(LocalDate.of(2026, 1, 6), result.getTrades().get(0).getTradeDate());
    }

    @Test
    void shouldDeferBuyAtChiNextTwentyPercentLimitUp() {
        Strategy strategy = enterOnFirstBar();
        BarSeries series = BarSeries.from(List.of(
                bar("300750", LocalDate.of(2026, 1, 5), "10.00", "10.00"),
                bar("300750", LocalDate.of(2026, 1, 6), "12.00", "12.00"),
                bar("300750", LocalDate.of(2026, 1, 7), "11.90", "11.90")));

        BacktestResult result = new BacktestEngine().run(
                strategy, series, new BigDecimal("100000"), zeroCost());

        assertEquals(LocalDate.of(2026, 1, 7), result.getTrades().get(0).getTradeDate());
    }

    @Test
    void shouldDeferBuyAtBeijingThirtyPercentLimitUp() {
        Strategy strategy = enterOnFirstBar();
        BarSeries series = BarSeries.from(List.of(
                bar("920001", LocalDate.of(2026, 1, 5), "10.00", "10.00"),
                bar("920001", LocalDate.of(2026, 1, 6), "13.00", "13.00"),
                bar("920001", LocalDate.of(2026, 1, 7), "12.90", "12.90")));

        BacktestResult result = new BacktestEngine().run(
                strategy, series, new BigDecimal("100000"), zeroCost());

        assertEquals(LocalDate.of(2026, 1, 7), result.getTrades().get(0).getTradeDate());
    }

    @Test
    void shouldAllowTwentyPercentOnePriceRiseOnBeijingExchange() {
        Strategy strategy = enterOnFirstBar();
        BarSeries series = BarSeries.from(List.of(
                bar("920001", LocalDate.of(2026, 1, 5), "10.00", "10.00"),
                bar("920001", LocalDate.of(2026, 1, 6), "12.00", "12.00"),
                bar("920001", LocalDate.of(2026, 1, 7), "12.10", "12.10")));

        BacktestResult result = new BacktestEngine().run(
                strategy, series, new BigDecimal("100000"), zeroCost());

        assertEquals(LocalDate.of(2026, 1, 6), result.getTrades().get(0).getTradeDate());
    }

    @Test
    void shouldDeferBuyAtMainBoardStFivePercentLimitUp() {
        Strategy strategy = enterOnFirstBar();
        BarSeries series = BarSeries.from(List.of(
                bar("600519", LocalDate.of(2026, 1, 5), "10.00", "10.00"),
                bar("600519", LocalDate.of(2026, 1, 6), "10.50", "10.50"),
                bar("600519", LocalDate.of(2026, 1, 7), "10.40", "10.40")), true);

        BacktestResult result = new BacktestEngine().run(
                strategy, series, new BigDecimal("100000"), zeroCost());

        assertEquals(LocalDate.of(2026, 1, 7), result.getTrades().get(0).getTradeDate());
    }

    private Strategy enterOnFirstBar() {
        Strategy strategy = mock(Strategy.class);
        when(strategy.shouldEnter(any(), anyInt())).thenAnswer(invocation -> invocation.getArgument(1, Integer.class) == 0);
        when(strategy.shouldExit(any(), anyInt(), anyInt(), any())).thenReturn(false);
        when(strategy.strategyId()).thenReturn("TEST");
        return strategy;
    }

    private BacktestCostConfig zeroCost() {
        return BacktestCostConfig.of(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private BarDaily bar(LocalDate tradeDate, String openPrice, String closePrice) {
        return bar("600519", tradeDate, openPrice, closePrice);
    }

    private BarDaily bar(String code, LocalDate tradeDate, String openPrice, String closePrice) {
        BigDecimal open = new BigDecimal(openPrice);
        BigDecimal close = new BigDecimal(closePrice);
        return BarDaily.builder()
                .code(code)
                .tradeDate(tradeDate)
                .openPrice(open)
                .highPrice(open.max(close))
                .lowPrice(open.min(close))
                .closePrice(close)
                .volume(new BigDecimal("1000000"))
                .build();
    }
}
