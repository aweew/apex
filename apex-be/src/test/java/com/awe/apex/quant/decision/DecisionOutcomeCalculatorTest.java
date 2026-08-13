package com.awe.apex.quant.decision;

import com.awe.apex.quant.backtest.BacktestCostConfig;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.entity.DailyAction;
import com.awe.apex.quant.domain.entity.DecisionOutcome;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.DecisionOutcomeMapper;
import com.awe.apex.quant.service.IConfigService;
import com.awe.apex.quant.service.impl.DecisionOutcomeServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DecisionOutcomeCalculatorTest {

    private static final LocalDate ACTION_DATE = LocalDate.of(2026, 8, 7);

    private final DecisionOutcomeServiceImpl service = new DecisionOutcomeServiceImpl();

    @Test
    void calculatesTradingBarReturnsExcessAndExcursionsAfterCosts() {
        DailyAction action = action();
        List<BarDaily> stockBars = bars("000001", new BigDecimal("100"), new BigDecimal("101"));
        List<BarDaily> benchmarkBars = bars("000300", new BigDecimal("200"), new BigDecimal("201"));

        DecisionOutcome outcome = service.calculateOutcome(action, stockBars, benchmarkBars, noCosts());

        assertEquals("COMPLETE", outcome.getStatus());
        assertEquals("COMPLETE", outcome.getQualityStatus());
        assertEquals(ACTION_DATE.plusDays(2), outcome.getEntryDate());
        assertEquals(new BigDecimal("100"), outcome.getEntryPrice());
        assertEquals(new BigDecimal("0.010000"), outcome.getReturn1d());
        assertEquals(new BigDecimal("0.030000"), outcome.getReturn3d());
        assertEquals(new BigDecimal("0.050000"), outcome.getReturn5d());
        assertEquals(new BigDecimal("0.100000"), outcome.getReturn10d());
        assertEquals(new BigDecimal("0.200000"), outcome.getReturn20d());
        assertEquals(new BigDecimal("0.005000"), outcome.getExcess1d());
        assertEquals(new BigDecimal("0.015000"), outcome.getExcess3d());
        assertEquals(new BigDecimal("0.025000"), outcome.getExcess5d());
        assertEquals(new BigDecimal("0.050000"), outcome.getExcess10d());
        assertEquals(new BigDecimal("0.100000"), outcome.getExcess20d());
        assertEquals(new BigDecimal("0.210000"), outcome.getMfe());
        assertEquals(new BigDecimal("-0.020000"), outcome.getMae());
        assertEquals(outcome.getReturn20d(), outcome.getNetReturn());
        assertNull(outcome.getStopHit());
        assertNull(outcome.getTargetHit());
    }

    @Test
    void appliesCommissionStampTaxAndSlippageToEveryMatureHorizon() {
        List<BarDaily> stockBars = constantBars("000001", new BigDecimal("100"));
        List<BarDaily> benchmarkBars = constantBars("000300", new BigDecimal("200"));
        BacktestCostConfig costs = BacktestCostConfig.of(
                new BigDecimal("0.001"),
                new BigDecimal("0.001"),
                new BigDecimal("0.001"),
                new BigDecimal("0.001"));

        DecisionOutcome outcome = service.calculateOutcome(action(), stockBars, benchmarkBars, costs);

        assertEquals(new BigDecimal("-0.004989"), outcome.getReturn1d());
        assertEquals(new BigDecimal("-0.004989"), outcome.getReturn20d());
        assertEquals(new BigDecimal("-0.004989"), outcome.getExcess1d());
    }

    @Test
    void keepsUnavailableHorizonsNullWhenTradingBarsAreNotMature() {
        List<BarDaily> stockBars = bars("000001", new BigDecimal("100"), new BigDecimal("101"))
                .subList(0, 3);
        List<BarDaily> benchmarkBars = bars("000300", new BigDecimal("200"), new BigDecimal("201"))
                .subList(0, 3);

        DecisionOutcome outcome = service.calculateOutcome(action(), stockBars, benchmarkBars, noCosts());

        assertEquals("PARTIAL", outcome.getStatus());
        assertEquals("PERIOD_NOT_MATURE", outcome.getQualityStatus());
        assertEquals(new BigDecimal("0.010000"), outcome.getReturn1d());
        assertEquals(new BigDecimal("0.030000"), outcome.getReturn3d());
        assertNull(outcome.getReturn5d());
        assertNull(outcome.getExcess5d());
        assertNull(outcome.getNetReturn());
    }

    @Test
    void marksMissingEntryOpenInvalidInsteadOfWritingZeroReturns() {
        List<BarDaily> stockBars = bars("000001", new BigDecimal("100"), new BigDecimal("101"));
        stockBars.get(0).setOpenPrice(null);

        DecisionOutcome outcome = service.calculateOutcome(action(), stockBars, List.of(), noCosts());

        assertEquals("INVALID", outcome.getStatus());
        assertEquals("MISSING_ENTRY_OPEN", outcome.getQualityStatus());
        assertEquals(ACTION_DATE.plusDays(2), outcome.getEntryDate());
        assertNull(outcome.getReturn1d());
        assertNull(outcome.getMfe());
    }

    @Test
    void preservesReturnsButMarksMissingBenchmarkAndLeavesExcessNull() {
        List<BarDaily> stockBars = bars("000001", new BigDecimal("100"), new BigDecimal("101"));

        DecisionOutcome outcome = service.calculateOutcome(action(), stockBars, List.of(), noCosts());

        assertEquals("PARTIAL", outcome.getStatus());
        assertEquals("MISSING_BENCHMARK", outcome.getQualityStatus());
        assertEquals(new BigDecimal("0.010000"), outcome.getReturn1d());
        assertEquals(new BigDecimal("0.200000"), outcome.getReturn20d());
        assertNull(outcome.getExcess1d());
        assertNull(outcome.getExcess20d());
    }

    @Test
    void recalculatesPendingFeatureSnapshotAndUpsertsBySnapshotIdentity() {
        DecisionOutcomeMapper outcomeMapper = mock(DecisionOutcomeMapper.class);
        BarDailyMapper barDailyMapper = mock(BarDailyMapper.class);
        IConfigService configService = mock(IConfigService.class);
        ReflectionTestUtils.setField(service, "decisionOutcomeMapper", outcomeMapper);
        ReflectionTestUtils.setField(service, "barDailyMapper", barDailyMapper);
        ReflectionTestUtils.setField(service, "configService", configService);

        DecisionOutcome pending = DecisionOutcome.builder()
                .featureSnapshotId(31L)
                .actionId(null)
                .runId(7L)
                .code("000001")
                .actionDate(ACTION_DATE)
                .build();
        List<BarDaily> stockBars = bars("000001", new BigDecimal("100"), new BigDecimal("101"));
        List<BarDaily> benchmarkBars = bars("000300", new BigDecimal("200"), new BigDecimal("201"));
        when(outcomeMapper.selectPendingOutcomes()).thenReturn(List.of(pending));
        when(barDailyMapper.selectOutcomeBars("000001", ACTION_DATE, 20)).thenReturn(stockBars);
        when(barDailyMapper.selectOutcomeBarsBetween("000300",
                stockBars.get(0).getTradeDate(), stockBars.get(19).getTradeDate())).thenReturn(benchmarkBars);
        when(configService.getDecimal("commission_rate", new BigDecimal("0.0005"))).thenReturn(BigDecimal.ZERO);
        when(configService.getDecimal("stamp_tax_rate", new BigDecimal("0.0005"))).thenReturn(BigDecimal.ZERO);
        when(configService.getDecimal("buy_slippage", new BigDecimal("0.001"))).thenReturn(BigDecimal.ZERO);
        when(configService.getDecimal("sell_slippage", new BigDecimal("0.001"))).thenReturn(BigDecimal.ZERO);
        when(outcomeMapper.upsert(org.mockito.ArgumentMatchers.any(DecisionOutcome.class))).thenReturn(1);

        int savedCount = service.calculatePendingOutcomes();

        assertEquals(1, savedCount);
        ArgumentCaptor<DecisionOutcome> captor = ArgumentCaptor.forClass(DecisionOutcome.class);
        verify(outcomeMapper).upsert(captor.capture());
        assertEquals(31L, captor.getValue().getFeatureSnapshotId());
        assertNull(captor.getValue().getActionId());
        assertEquals("COMPLETE", captor.getValue().getStatus());
    }

    @Test
    void failsWhenOutcomeUpsertDoesNotPersist() {
        DecisionOutcomeMapper outcomeMapper = mock(DecisionOutcomeMapper.class);
        BarDailyMapper barDailyMapper = mock(BarDailyMapper.class);
        IConfigService configService = mock(IConfigService.class);
        ReflectionTestUtils.setField(service, "decisionOutcomeMapper", outcomeMapper);
        ReflectionTestUtils.setField(service, "barDailyMapper", barDailyMapper);
        ReflectionTestUtils.setField(service, "configService", configService);
        DecisionOutcome pending = DecisionOutcome.builder()
                .featureSnapshotId(31L).runId(7L).code("000001").actionDate(ACTION_DATE).build();
        when(outcomeMapper.selectPendingOutcomes()).thenReturn(List.of(pending));
        when(barDailyMapper.selectOutcomeBars("000001", ACTION_DATE, 20)).thenReturn(List.of());
        when(configService.getDecimal(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(BigDecimal.class))).thenReturn(BigDecimal.ZERO);
        when(outcomeMapper.upsert(org.mockito.ArgumentMatchers.any(DecisionOutcome.class))).thenReturn(0);

        assertThrows(BusinessException.class, service::calculatePendingOutcomes);
    }

    @Test
    void acceptsMysqlDuplicateKeyUpdateAffectedRows() {
        DecisionOutcomeMapper outcomeMapper = mock(DecisionOutcomeMapper.class);
        BarDailyMapper barDailyMapper = mock(BarDailyMapper.class);
        IConfigService configService = mock(IConfigService.class);
        ReflectionTestUtils.setField(service, "decisionOutcomeMapper", outcomeMapper);
        ReflectionTestUtils.setField(service, "barDailyMapper", barDailyMapper);
        ReflectionTestUtils.setField(service, "configService", configService);
        DecisionOutcome pending = DecisionOutcome.builder()
                .featureSnapshotId(31L).runId(7L).code("000001").actionDate(ACTION_DATE).build();
        when(outcomeMapper.selectPendingOutcomes()).thenReturn(List.of(pending));
        when(barDailyMapper.selectOutcomeBars("000001", ACTION_DATE, 20)).thenReturn(List.of());
        when(configService.getDecimal(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(BigDecimal.class))).thenReturn(BigDecimal.ZERO);
        when(outcomeMapper.upsert(org.mockito.ArgumentMatchers.any(DecisionOutcome.class))).thenReturn(2);

        assertEquals(1, service.calculatePendingOutcomes());
    }

    private DailyAction action() {
        return DailyAction.builder()
                .id(11L)
                .runId(7L)
                .code("000001")
                .actionDate(ACTION_DATE)
                .build();
    }

    private BacktestCostConfig noCosts() {
        return BacktestCostConfig.of(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private List<BarDaily> bars(String code, BigDecimal open, BigDecimal firstClose) {
        List<BarDaily> result = new ArrayList<>();
        for (int index = 1; index <= 20; index++) {
            BigDecimal close = firstClose.add(BigDecimal.valueOf(index - 1));
            result.add(BarDaily.builder()
                    .code(code)
                    .tradeDate(ACTION_DATE.plusDays(index * 2L))
                    .openPrice(index == 1 ? open : close.subtract(BigDecimal.ONE))
                    .highPrice(close.add(BigDecimal.ONE))
                    .lowPrice(index == 1 ? open.subtract(new BigDecimal("2")) : close.subtract(BigDecimal.ONE))
                    .closePrice(close)
                    .build());
        }
        return result;
    }

    private List<BarDaily> constantBars(String code, BigDecimal price) {
        List<BarDaily> result = new ArrayList<>();
        for (int index = 1; index <= 20; index++) {
            result.add(BarDaily.builder()
                    .code(code)
                    .tradeDate(ACTION_DATE.plusDays(index * 2L))
                    .openPrice(price)
                    .highPrice(price)
                    .lowPrice(price)
                    .closePrice(price)
                    .build());
        }
        return result;
    }
}
