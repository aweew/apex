package com.awe.apex.quant.service.impl;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.dto.ScreenerMarketSnapshot;
import com.awe.apex.quant.domain.dto.ScreenerMarketSnapshotBatch;
import com.awe.apex.quant.domain.dto.ScreenerStrategyResp;
import com.awe.apex.quant.domain.dto.ScreenerStrategyRuleResp;
import com.awe.apex.quant.domain.dto.ScreenerStrategyRunReq;
import com.awe.apex.quant.domain.dto.ScreenerStrategyRunResp;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.LimitUpPoolMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.market.ScreenerMarketSnapshotClient;
import com.awe.apex.quant.screener.ScreenerIntradayReviewService;
import com.awe.apex.quant.screener.ScreenerMetricCalculator;
import com.awe.apex.quant.screener.ScreenerStrategyRuleEvaluator;
import com.awe.apex.quant.service.IScreenerStrategyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScreenerStrategyExecutionServiceImplTest {

    private ScreenerStrategyExecutionServiceImpl service;
    private IScreenerStrategyService strategyService;
    private ScreenerMarketSnapshotClient snapshotClient;

    @BeforeEach
    void setUp() {
        service = new ScreenerStrategyExecutionServiceImpl();
        strategyService = mock(IScreenerStrategyService.class);
        snapshotClient = mock(ScreenerMarketSnapshotClient.class);
        ScreenerStrategyRuleEvaluator evaluator = new ScreenerStrategyRuleEvaluator();
        ReflectionTestUtils.setField(evaluator, "metricCalculator", new ScreenerMetricCalculator());
        ApexUserContext userContext = mock(ApexUserContext.class);
        when(userContext.currentUserIdOrNull()).thenReturn(7L);
        StockBasicMapper stockBasicMapper = mock(StockBasicMapper.class);
        when(stockBasicMapper.selectList(any())).thenReturn(List.of());
        ReflectionTestUtils.setField(service, "userContext", userContext);
        ReflectionTestUtils.setField(service, "strategyService", strategyService);
        ReflectionTestUtils.setField(service, "snapshotClient", snapshotClient);
        ReflectionTestUtils.setField(service, "ruleEvaluator", evaluator);
        ReflectionTestUtils.setField(service, "metricCalculator", new ScreenerMetricCalculator());
        ReflectionTestUtils.setField(service, "intradayReviewService", mock(ScreenerIntradayReviewService.class));
        ReflectionTestUtils.setField(service, "stockBasicMapper", stockBasicMapper);
        ReflectionTestUtils.setField(service, "barDailyMapper", mock(BarDailyMapper.class));
        ReflectionTestUtils.setField(service, "limitUpPoolMapper", mock(LimitUpPoolMapper.class));
        ReflectionTestUtils.setField(service, "intradayMaxCandidates", 120);
    }

    @Test
    void shouldReturnDegradedStatusWhenRealtimeSnapshotFails() {
        ScreenerStrategyResp strategy = strategy(List.of());
        when(strategyService.resolveRunnable(null, "TEST")).thenReturn(strategy);
        when(snapshotClient.fetchAll()).thenThrow(new BusinessException("upstream timeout"));

        ScreenerStrategyRunResp result = service.run(ScreenerStrategyRunReq.builder()
                .templateKey("TEST")
                .build());

        assertEquals(0, result.getMatchedCount());
        assertTrue(result.getDataStatus().getDegraded());
        assertEquals("SNAPSHOT_FETCH_FAILED", result.getDataStatus().getIssues().get(0).getIssueType());
    }

    @Test
    void shouldReturnEvidenceForSnapshotRuleMatch() {
        ScreenerStrategyRuleResp rule = ScreenerStrategyRuleResp.builder()
                .ruleType("PCT_CHG")
                .ruleName("当日涨跌幅")
                .operatorCode("BETWEEN")
                .operatorName("区间")
                .minValue(new BigDecimal("3"))
                .maxValue(new BigDecimal("5"))
                .summary("当日涨幅 3% - 5%")
                .build();
        when(strategyService.resolveRunnable(null, "TEST")).thenReturn(strategy(List.of(rule)));
        when(snapshotClient.fetchAll()).thenReturn(ScreenerMarketSnapshotBatch.builder()
                .asOf(LocalDateTime.of(2026, 8, 18, 10, 30))
                .items(List.of(ScreenerMarketSnapshot.builder()
                        .code("600001")
                        .name("示例股份")
                        .market("SH")
                        .pctChg(new BigDecimal("4.2"))
                        .build()))
                .build());

        ScreenerStrategyRunResp result = service.run(ScreenerStrategyRunReq.builder()
                .templateKey("TEST")
                .limit(20)
                .build());

        assertEquals(1, result.getMatchedCount());
        assertEquals("4.2", result.getMatches().get(0).getEvidence().get(0).getActualValue());
        assertEquals("当日涨幅 3% - 5%", result.getMatches().get(0).getEvidence().get(0).getExpectedValue());
        assertFalse(result.getDataStatus().getDegraded());
    }

    private ScreenerStrategyResp strategy(List<ScreenerStrategyRuleResp> rules) {
        return ScreenerStrategyResp.builder()
                .templateKey("TEST")
                .name("测试策略")
                .template(true)
                .enabled(true)
                .rules(rules)
                .build();
    }
}
