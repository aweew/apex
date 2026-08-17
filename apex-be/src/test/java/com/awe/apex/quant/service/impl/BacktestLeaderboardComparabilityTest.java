package com.awe.apex.quant.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.awe.apex.quant.domain.dto.StrategyLeaderboardItemResp;
import com.awe.apex.quant.domain.entity.BacktestJob;
import com.awe.apex.quant.mapper.BacktestJobMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BacktestLeaderboardComparabilityTest {

    private static final Long CURRENT_USER_ID = 7L;
    private static final String STRATEGY_IDS = "S1,S2,S3";

    private final BacktestJobMapper backtestJobMapper = mock(BacktestJobMapper.class);
    private final BacktestServiceImpl service = new BacktestServiceImpl();

    private MockedStatic<StpUtil> stpUtil;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, BacktestJob.class);
    }

    @BeforeEach
    void setUp() {
        stpUtil = mockStatic(StpUtil.class);
        stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(CURRENT_USER_ID);
        ReflectionTestUtils.setField(service, "backtestJobMapper", backtestJobMapper);
    }

    @AfterEach
    void tearDown() {
        stpUtil.close();
    }

    @Test
    void shouldAggregateOnlyCompleteComparisonBatchesWithIdenticalAuditScope() {
        List<BacktestJob> jobs = new ArrayList<>();
        jobs.add(comparableJob("complete", "S1", "0.10", "1.20"));
        jobs.add(comparableJob("complete", "S2", "0.20", "1.40"));
        jobs.add(comparableJob("complete", "S3", "-0.05", "0.60"));
        jobs.add(equivalentScaleJob("S1", "0.10", "1.20"));
        jobs.add(equivalentScaleJob("S2", "0.20", "1.40"));
        jobs.add(equivalentScaleJob("S3", "-0.05", "0.60"));
        BacktestJob otherConfigS1 = comparableJob("other-config", "S1", "5.00", "5.00");
        BacktestJob otherConfigS2 = comparableJob("other-config", "S2", "5.00", "5.00");
        BacktestJob otherConfigS3 = comparableJob("other-config", "S3", "5.00", "5.00");
        otherConfigS1.setComparisonConfigFingerprint("b".repeat(64));
        otherConfigS2.setComparisonConfigFingerprint("b".repeat(64));
        otherConfigS3.setComparisonConfigFingerprint("b".repeat(64));
        jobs.add(otherConfigS1);
        jobs.add(otherConfigS2);
        jobs.add(otherConfigS3);
        BacktestJob otherCostS1 = comparableJob("other-cost", "S1", "4.00", "4.00");
        BacktestJob otherCostS2 = comparableJob("other-cost", "S2", "4.00", "4.00");
        BacktestJob otherCostS3 = comparableJob("other-cost", "S3", "4.00", "4.00");
        otherCostS1.setCommissionRate(new BigDecimal("0.00200000"));
        otherCostS2.setCommissionRate(new BigDecimal("0.00200000"));
        otherCostS3.setCommissionRate(new BigDecimal("0.00200000"));
        jobs.add(otherCostS1);
        jobs.add(otherCostS2);
        jobs.add(otherCostS3);
        jobs.addAll(differentCohortJobs("other-cash", "initCash", new BigDecimal("2000000.00")));
        jobs.addAll(differentCohortJobs("other-tax", "stampTaxRate", new BigDecimal("0.00200000")));
        jobs.addAll(differentCohortJobs("other-buy-slip", "buySlippage", new BigDecimal("0.00200000")));
        jobs.addAll(differentCohortJobs("other-sell-slip", "sellSlippage", new BigDecimal("0.00200000")));
        jobs.add(comparableJob("incomplete", "S1", "9.00", "9.00"));
        jobs.add(comparableJob("incomplete", "S2", "9.00", "9.00"));
        jobs.add(comparableJob("drifted", "S1", "8.00", "8.00"));
        jobs.add(comparableJob("drifted", "S2", "8.00", "8.00"));
        BacktestJob driftedJob = comparableJob("drifted", "S3", "8.00", "8.00");
        driftedJob.setCommissionRate(new BigDecimal("0.00200000"));
        jobs.add(driftedJob);
        jobs.add(comparableJob("missing-metric", "S1", "6.00", "6.00"));
        jobs.add(comparableJob("missing-metric", "S2", "6.00", "6.00"));
        BacktestJob missingMetricJob = comparableJob("missing-metric", "S3", "6.00", "6.00");
        missingMetricJob.setSharpe(null);
        jobs.add(missingMetricJob);
        jobs.add(BacktestJob.builder()
                .strategyId("S1")
                .status("SUCCESS")
                .totalReturn(new BigDecimal("7.00"))
                .sharpe(new BigDecimal("7.00"))
                .build());
        for (String strategyId : List.of("S1", "S2", "S3")) {
            BacktestJob missingCashJob = comparableJob("missing-cash", strategyId, "7.00", "7.00");
            missingCashJob.setInitCash(null);
            jobs.add(missingCashJob);
        }
        when(backtestJobMapper.selectList(any())).thenReturn(jobs);

        List<StrategyLeaderboardItemResp> leaderboard = service.strategyLeaderboard(100);

        assertEquals(3, leaderboard.size());
        assertEquals("S2", leaderboard.get(0).getStrategyId());
        assertEquals(2, leaderboard.get(0).getJobCount());
        assertEquals(new BigDecimal("0.200000"), leaderboard.get(0).getAvgReturn());
        assertEquals("parameters-S2", leaderboard.get(0).getStrategyParameters());
        assertEquals("a".repeat(64), leaderboard.get(0).getComparisonConfigFingerprint());
        assertEquals("NEXT_OPEN_V4", leaderboard.get(0).getExecutionModelVersion());
        assertEquals("QFQ", leaderboard.get(0).getPriceAdjustment());
        assertEquals(new BigDecimal("1000000.00"), leaderboard.get(0).getInitCash());
        assertEquals(new BigDecimal("0.00050000"), leaderboard.get(0).getCommissionRate());
        assertEquals(new BigDecimal("0.00050000"), leaderboard.get(0).getStampTaxRate());
        assertEquals(new BigDecimal("0.00100000"), leaderboard.get(0).getBuySlippage());
        assertEquals(new BigDecimal("0.00100000"), leaderboard.get(0).getSellSlippage());
        assertEquals("S1", leaderboard.get(1).getStrategyId());
        assertEquals("S3", leaderboard.get(2).getStrategyId());
    }

    @Test
    void shouldLoadEnoughRowsForRequestedComparisonBatchCount() {
        when(backtestJobMapper.selectList(any())).thenAnswer(invocation -> {
            Wrapper<BacktestJob> query = invocation.getArgument(0);
            assertTrue(query.getSqlSegment().toLowerCase().contains("limit 3"));
            return List.of();
        });

        List<StrategyLeaderboardItemResp> leaderboard = service.strategyLeaderboard(1);

        assertTrue(leaderboard.isEmpty());
    }

    @Test
    void shouldContinuePastIncompleteRowsToFillRequestedBatchCount() {
        BacktestJob incompleteS1 = comparableJob("incomplete", "S1", "9.00", "9.00");
        incompleteS1.setId(9L);
        BacktestJob incompleteS2 = comparableJob("incomplete", "S2", "9.00", "9.00");
        incompleteS2.setId(8L);
        BacktestJob completeS1 = comparableJob("complete", "S1", "0.10", "1.20");
        completeS1.setId(7L);
        BacktestJob completeS2 = comparableJob("complete", "S2", "0.20", "1.40");
        completeS2.setId(6L);
        BacktestJob completeS3 = comparableJob("complete", "S3", "-0.05", "0.60");
        completeS3.setId(5L);
        when(backtestJobMapper.selectList(any()))
                .thenReturn(List.of(incompleteS1, incompleteS2, completeS1))
                .thenReturn(List.of(completeS2, completeS3));

        List<StrategyLeaderboardItemResp> leaderboard = service.strategyLeaderboard(1);

        assertEquals(3, leaderboard.size());
        assertEquals(1, leaderboard.get(0).getJobCount());
        verify(backtestJobMapper, times(2)).selectList(any());
    }

    @Test
    void shouldOrderEqualSharpeStrategiesByStrategyId() {
        when(backtestJobMapper.selectList(any())).thenReturn(List.of(
                comparableJob("tie", "S3", "0.10", "1.00"),
                comparableJob("tie", "S1", "0.10", "1.00"),
                comparableJob("tie", "S2", "0.10", "1.00")));

        List<StrategyLeaderboardItemResp> leaderboard = service.strategyLeaderboard(1);

        assertEquals(List.of("S1", "S2", "S3"),
                leaderboard.stream().map(StrategyLeaderboardItemResp::getStrategyId).toList());
    }

    private BacktestJob equivalentScaleJob(String strategyId, String totalReturn, String sharpe) {
        BacktestJob job = comparableJob("equivalent-scale", strategyId, totalReturn, sharpe);
        job.setInitCash(new BigDecimal("1000000.0"));
        job.setCommissionRate(new BigDecimal("0.0005"));
        job.setStampTaxRate(new BigDecimal("0.0005"));
        job.setBuySlippage(new BigDecimal("0.001"));
        job.setSellSlippage(new BigDecimal("0.001"));
        return job;
    }

    private List<BacktestJob> differentCohortJobs(String batchId, String fieldName, BigDecimal fieldValue) {
        List<BacktestJob> jobs = new ArrayList<>();
        for (String strategyId : List.of("S1", "S2", "S3")) {
            BacktestJob job = comparableJob(batchId, strategyId, "4.00", "4.00");
            if ("initCash".equals(fieldName)) {
                job.setInitCash(fieldValue);
            } else if ("stampTaxRate".equals(fieldName)) {
                job.setStampTaxRate(fieldValue);
            } else if ("buySlippage".equals(fieldName)) {
                job.setBuySlippage(fieldValue);
            } else if ("sellSlippage".equals(fieldName)) {
                job.setSellSlippage(fieldValue);
            }
            jobs.add(job);
        }
        return jobs;
    }

    private BacktestJob comparableJob(String batchId, String strategyId, String totalReturn, String sharpe) {
        return BacktestJob.builder()
                .userId(CURRENT_USER_ID)
                .code("600519")
                .strategyId(strategyId)
                .beginDate(LocalDate.of(2025, 1, 2))
                .endDate(LocalDate.of(2025, 12, 31))
                .initCash(new BigDecimal("1000000.00"))
                .totalReturn(new BigDecimal(totalReturn))
                .sharpe(new BigDecimal(sharpe))
                .maxDrawdown(new BigDecimal("0.10"))
                .status("SUCCESS")
                .comparisonBatchId(batchId)
                .comparisonStrategyIds(STRATEGY_IDS)
                .strategyParameters("parameters-" + strategyId)
                .comparisonConfigFingerprint("a".repeat(64))
                .commissionRate(new BigDecimal("0.00050000"))
                .stampTaxRate(new BigDecimal("0.00050000"))
                .buySlippage(new BigDecimal("0.00100000"))
                .sellSlippage(new BigDecimal("0.00100000"))
                .executionModelVersion("NEXT_OPEN_V4")
                .priceAdjustment("QFQ")
                .dataFingerprint("a".repeat(64))
                .build();
    }
}
