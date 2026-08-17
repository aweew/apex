package com.awe.apex.quant.decision;

import cn.hutool.extra.spring.SpringUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.domain.dto.PortfolioSummaryResp;
import com.awe.apex.quant.domain.dto.DecisionPortfolioHolding;
import com.awe.apex.quant.domain.entity.DecisionPortfolioSnapshot;
import com.awe.apex.quant.domain.entity.DecisionRun;
import com.awe.apex.quant.domain.entity.Portfolio;
import com.awe.apex.quant.domain.entity.PortfolioDaily;
import com.awe.apex.quant.mapper.DecisionPortfolioSnapshotMapper;
import com.awe.apex.quant.service.IConfigService;
import com.awe.apex.quant.service.IPortfolioService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DecisionPortfolioSnapshotManagerTest {

    private static final LocalDate ACTION_DATE = LocalDate.of(2026, 8, 6);
    private static GenericApplicationContext applicationContext;
    private static ApplicationContext originalApplicationContext;

    private DecisionPortfolioSnapshotManager manager;
    private IPortfolioService portfolioService;
    private IConfigService configService;
    private DecisionPortfolioSnapshotMapper snapshotMapper;

    @BeforeAll
    static void initJsonUtilsContext() {
        originalApplicationContext = SpringUtil.getApplicationContext();
        applicationContext = new GenericApplicationContext();
        applicationContext.registerBean(ObjectMapper.class, () -> new ObjectMapper().findAndRegisterModules());
        applicationContext.refresh();
        new SpringUtil().setApplicationContext(applicationContext);
    }

    @AfterAll
    static void closeJsonUtilsContext() {
        new SpringUtil().setApplicationContext(originalApplicationContext);
        applicationContext.close();
    }

    @BeforeEach
    void setUp() {
        manager = new DecisionPortfolioSnapshotManager();
        portfolioService = mock(IPortfolioService.class);
        configService = mock(IConfigService.class);
        snapshotMapper = mock(DecisionPortfolioSnapshotMapper.class);
        ReflectionTestUtils.setField(manager, "portfolioService", portfolioService);
        ReflectionTestUtils.setField(manager, "configService", configService);
        ReflectionTestUtils.setField(manager, "snapshotMapper", snapshotMapper);
        when(configService.getDecimal("atr_stop_mult", new BigDecimal("2.0")))
                .thenReturn(new BigDecimal("2.0"));
        when(configService.getDecimal("atr_take_mult", new BigDecimal("3.0")))
                .thenReturn(new BigDecimal("3.0"));
        when(snapshotMapper.insert(any(DecisionPortfolioSnapshot.class))).thenReturn(1);
    }

    @Test
    void livePersistsDefaultPortfolioEquityAndDrawdown() {
        Portfolio portfolio = Portfolio.builder().id(3L).build();
        PortfolioDaily daily = PortfolioDaily.builder()
                .marketValue(new BigDecimal("70000"))
                .cash(new BigDecimal("30000"))
                .totalEquity(new BigDecimal("100000"))
                .peakEquity(new BigDecimal("110000"))
                .drawdown(new BigDecimal("0.090909"))
                .build();
        when(portfolioService.ensureDefaultPortfolio()).thenReturn(portfolio);
        when(portfolioService.snapshot(3L)).thenReturn(daily);
        when(portfolioService.detail(3L)).thenReturn(PortfolioSummaryResp.builder().holdings(List.of()).build());

        DecisionPortfolioSnapshot result = manager.resolve(liveContext(), run(9L), balanceRegime(), riskLimits());

        assertEquals(new BigDecimal("0.700000"), result.getExposureRatio());
        assertEquals(new BigDecimal("0.090909"), result.getDrawdown());
        assertEquals(new BigDecimal("0.50"), result.getExposureLimit());
        assertEquals(new BigDecimal("0.15"), result.getSingleStockLimit());
        assertEquals(new BigDecimal("0.30"), result.getIndustryLimit());
        assertEquals(new BigDecimal("2.0"), result.getAtrStopMultiplier());
        assertEquals(new BigDecimal("3.0"), result.getAtrTakeMultiplier());
        verify(snapshotMapper).insert(result);
    }

    @Test
    void replayUsesHistoricalSnapshotAndNeverReadsCurrentPortfolio() {
        when(snapshotMapper.selectHistorical(7L, ACTION_DATE)).thenReturn(DecisionPortfolioSnapshot.builder()
                .runId(1L)
                .portfolioId(3L)
                .actionDate(ACTION_DATE)
                .cash(new BigDecimal("25000"))
                .marketValue(new BigDecimal("75000"))
                .totalEquity(new BigDecimal("100000"))
                .peakEquity(new BigDecimal("105000"))
                .drawdown(new BigDecimal("0.047619"))
                .exposureRatio(new BigDecimal("0.750000"))
                .exposureLimit(new BigDecimal("0.50"))
                .singleStockLimit(new BigDecimal("0.15"))
                .industryLimit(new BigDecimal("0.30"))
                .atrStopMultiplier(new BigDecimal("2.0"))
                .atrTakeMultiplier(new BigDecimal("3.0"))
                .holdingPayload("[]")
                .industryExposureJson("{}")
                .build());

        DecisionPortfolioSnapshot result = manager.resolve(replayContext(), run(10L), balanceRegime(), null);

        assertEquals(10L, result.getRunId());
        assertEquals(new BigDecimal("25000"), result.getCash());
        verify(portfolioService, never()).ensureDefaultPortfolio();
        ArgumentCaptor<DecisionPortfolioSnapshot> captor = ArgumentCaptor.forClass(DecisionPortfolioSnapshot.class);
        verify(snapshotMapper).insert(captor.capture());
        assertEquals(10L, captor.getValue().getRunId());
    }

    @Test
    void replayFailsClosedWhenHistoricalPortfolioSnapshotIsMissing() {
        when(snapshotMapper.selectHistorical(7L, ACTION_DATE)).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> manager.resolve(replayContext(), run(10L), balanceRegime(), null));
        verify(portfolioService, never()).ensureDefaultPortfolio();
    }

    @Test
    void replayFailsClosedWhenHistoricalAtrParametersAreMissing() {
        when(snapshotMapper.selectHistorical(7L, ACTION_DATE)).thenReturn(DecisionPortfolioSnapshot.builder()
                .runId(1L)
                .portfolioId(3L)
                .actionDate(ACTION_DATE)
                .exposureLimit(new BigDecimal("0.50"))
                .singleStockLimit(new BigDecimal("0.15"))
                .industryLimit(new BigDecimal("0.30"))
                .holdingPayload("[]")
                .industryExposureJson("{}")
                .build());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> manager.resolve(replayContext(), run(10L), null, null));

        assertTrue(exception.getMessage().contains("冻结ATR参数"));
        verify(portfolioService, never()).ensureDefaultPortfolio();
    }

    @Test
    void persistsRiskLevelsIntoDecisionSnapshotPayload() {
        DecisionPortfolioSnapshot snapshot = DecisionPortfolioSnapshot.builder()
                .id(18L)
                .holdings(List.of(DecisionPortfolioHolding.builder()
                        .code("000001")
                        .stopLoss(new BigDecimal("9.20"))
                        .takeProfit(new BigDecimal("12.00"))
                        .build()))
                .build();
        when(snapshotMapper.updateById(snapshot)).thenReturn(1);

        manager.updateHoldingRiskLevels(snapshot);

        assertTrue(snapshot.getHoldingPayload().contains("\"stopLoss\":9.20"));
        assertTrue(snapshot.getHoldingPayload().contains("\"takeProfit\":12.00"));
        verify(snapshotMapper).updateById(snapshot);
    }

    @Test
    void failsClosedWhenLiveSnapshotCannotBePersisted() {
        Portfolio portfolio = Portfolio.builder().id(3L).build();
        PortfolioDaily daily = PortfolioDaily.builder()
                .cash(new BigDecimal("100000"))
                .marketValue(BigDecimal.ZERO)
                .totalEquity(new BigDecimal("100000"))
                .peakEquity(new BigDecimal("100000"))
                .drawdown(BigDecimal.ZERO)
                .build();
        when(portfolioService.ensureDefaultPortfolio()).thenReturn(portfolio);
        when(portfolioService.snapshot(3L)).thenReturn(daily);
        when(portfolioService.detail(3L)).thenReturn(PortfolioSummaryResp.builder().holdings(List.of()).build());
        when(snapshotMapper.insert(any(DecisionPortfolioSnapshot.class))).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> manager.resolve(liveContext(), run(9L), balanceRegime(), riskLimits()));

        assertTrue(exception.getMessage().contains("组合快照保存失败"));
    }

    private DecisionContext liveContext() {
        return DecisionContext.builder()
                .actionDate(ACTION_DATE)
                .mode(DecisionMode.LIVE)
                .build();
    }

    private DecisionContext replayContext() {
        return DecisionContext.builder()
                .actionDate(ACTION_DATE)
                .mode(DecisionMode.REPLAY)
                .build();
    }

    private DecisionRun run(Long id) {
        return DecisionRun.builder().id(id).userId(7L).actionDate(ACTION_DATE).build();
    }

    private MarketRegimeResult balanceRegime() {
        return MarketRegimeResult.builder()
                .marketRegime(MarketRegimeEnum.BALANCE)
                .totalExposureLimit(new BigDecimal("0.50"))
                .reason("市场均衡")
                .build();
    }

    private com.awe.apex.quant.domain.dto.RiskOverviewResp riskLimits() {
        return com.awe.apex.quant.domain.dto.RiskOverviewResp.builder()
                .totalLimit(new BigDecimal("0.60"))
                .singleLimit(new BigDecimal("0.15"))
                .industryLimit(new BigDecimal("0.30"))
                .build();
    }
}
