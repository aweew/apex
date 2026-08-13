package com.awe.apex.quant.service.impl;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.decision.DecisionContext;
import com.awe.apex.quant.decision.DecisionDataPolicy;
import com.awe.apex.quant.decision.DecisionMode;
import com.awe.apex.quant.decision.DecisionPerformanceCalibrator;
import com.awe.apex.quant.decision.MarketRegimeResolver;
import com.awe.apex.quant.decision.MarketRegimeResult;
import com.awe.apex.quant.domain.dto.MarketBriefingResp;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.entity.MarketBriefingSnapshot;
import com.awe.apex.quant.domain.entity.MyHolding;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.MarketBriefingSnapshotMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DecisionReplayServiceTest {

    private static final LocalDate ACTION_DATE = LocalDate.of(2026, 8, 6);

    private final MarketBriefingSnapshotMapper briefingMapper = mock(MarketBriefingSnapshotMapper.class);
    private final BarDailyMapper barDailyMapper = mock(BarDailyMapper.class);
    private final DecisionServiceImpl service = new DecisionServiceImpl();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "marketBriefingSnapshotMapper", briefingMapper);
        ReflectionTestUtils.setField(service, "barDailyMapper", barDailyMapper);
    }

    @Test
    void replayRequiresHistoricalBriefingSnapshot() {
        when(briefingMapper.selectOne(any())).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.resolveRunBriefing(replayContext()));
    }

    @Test
    void replayMarksSnapshotAsDegraded() throws Exception {
        MarketBriefingResp stored = MarketBriefingResp.builder()
                .asOf(ACTION_DATE)
                .stance("均衡")
                .dataLevel("GREEN")
                .dataSufficient(true)
                .build();
        when(briefingMapper.selectOne(any())).thenReturn(MarketBriefingSnapshot.builder()
                .tradeDate(ACTION_DATE)
                .payloadJson(new ObjectMapper().findAndRegisterModules().writeValueAsString(stored))
                .build());

        MarketBriefingResp replay = service.resolveRunBriefing(replayContext());

        assertEquals("YELLOW", replay.getDataLevel());
        assertFalse(replay.getDataSufficient());
        assertEquals(ACTION_DATE, replay.getAsOf());
    }

    @Test
    void replayClearsCurrentMetadataAndUsesHistoricalClose() {
        StockBasic basic = StockBasic.builder()
                .code("000001")
                .latestPrice(new BigDecimal("20"))
                .pctChg(new BigDecimal("9"))
                .peTtm(new BigDecimal("12"))
                .pb(new BigDecimal("2"))
                .circMv(new BigDecimal("100000"))
                .industry("银行")
                .stFlag(1)
                .build();
        Map<String, StockBasic> basics = new HashMap<>();
        basics.put(basic.getCode(), basic);
        when(barDailyMapper.selectList(any())).thenReturn(List.of(BarDaily.builder()
                .code("000001")
                .tradeDate(ACTION_DATE)
                .closePrice(new BigDecimal("10.50"))
                .pctChg(new BigDecimal("1.25"))
                .build()));

        service.applyReplayPrices(basics, ACTION_DATE);

        assertEquals(new BigDecimal("10.50"), basic.getLatestPrice());
        assertEquals(new BigDecimal("1.25"), basic.getPctChg());
        assertNull(basic.getPeTtm());
        assertNull(basic.getPb());
        assertNull(basic.getCircMv());
        assertNull(basic.getIndustry());
        assertNull(basic.getStFlag());
    }

    @Test
    void replayDoesNotUsePerformanceLearnedAfterHistoricalDecision() {
        DecisionPerformanceCalibrator calibrator = mock(DecisionPerformanceCalibrator.class);
        ReflectionTestUtils.setField(service, "performanceCalibrator", calibrator);

        Map<String, BigDecimal> adjustments = service.resolvePerformanceAdjustments(DecisionMode.REPLAY);

        assertTrue(adjustments.isEmpty());
        verifyNoInteractions(calibrator);
    }

    @Test
    void replayDoesNotResolveCurrentMarketRegime() {
        MarketRegimeResolver marketRegimeResolver = mock(MarketRegimeResolver.class);
        ReflectionTestUtils.setField(service, "marketRegimeResolver", marketRegimeResolver);

        MarketRegimeResult result = service.resolveRunMarketRegime(replayContext());

        assertNull(result);
        verifyNoInteractions(marketRegimeResolver);
    }

    @Test
    void liveResolvesMarketRegimeForDecisionDate() {
        MarketRegimeResolver marketRegimeResolver = mock(MarketRegimeResolver.class);
        MarketRegimeResult expected = MarketRegimeResult.builder().build();
        ReflectionTestUtils.setField(service, "marketRegimeResolver", marketRegimeResolver);
        when(marketRegimeResolver.resolve(ACTION_DATE)).thenReturn(expected);
        DecisionContext context = DecisionContext.builder()
                .actionDate(ACTION_DATE)
                .mode(DecisionMode.LIVE)
                .build();

        MarketRegimeResult result = service.resolveRunMarketRegime(context);

        assertEquals(expected, result);
        verify(marketRegimeResolver).resolve(ACTION_DATE);
    }

    @Test
    void fillsMissingHoldingRiskLevelsUsingFrozenAtrMultipliers() {
        List<BarDaily> bars = new java.util.ArrayList<>();
        for (int dayOffset = 0; dayOffset < 15; dayOffset++) {
            bars.add(BarDaily.builder()
                    .code("000001")
                    .tradeDate(ACTION_DATE.minusDays(dayOffset))
                    .highPrice(new BigDecimal("102"))
                    .lowPrice(new BigDecimal("98"))
                    .closePrice(new BigDecimal("100"))
                    .build());
        }
        when(barDailyMapper.selectList(any())).thenReturn(bars);
        MyHolding holding = MyHolding.builder()
                .code("000001")
                .costPrice(new BigDecimal("100"))
                .build();

        Boolean changed = ReflectionTestUtils.invokeMethod(
                service, "ensureHoldingStopTake", holding, new BigDecimal("105"), ACTION_DATE,
                new BigDecimal("1.5"), new BigDecimal("2.5"));

        assertTrue(Boolean.TRUE.equals(changed));
        assertEquals(new BigDecimal("94.00"), holding.getStopLoss());
        assertEquals(new BigDecimal("110.00"), holding.getTakeProfit());
    }

    private DecisionContext replayContext() {
        return DecisionContext.builder()
                .actionDate(ACTION_DATE)
                .asOfTime(LocalDateTime.of(ACTION_DATE, LocalTime.of(23, 59, 59)))
                .mode(DecisionMode.REPLAY)
                .dataPolicy(DecisionDataPolicy.POINT_IN_TIME)
                .build();
    }
}
