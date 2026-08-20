package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.dto.PortfolioSummaryResp;
import com.awe.apex.quant.domain.entity.Portfolio;
import com.awe.apex.quant.domain.entity.PortfolioIntradaySnapshot;
import com.awe.apex.quant.mapper.PortfolioIntradaySnapshotMapper;
import com.awe.apex.quant.mapper.PortfolioMapper;
import com.awe.apex.quant.service.IPortfolioService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class PortfolioIntradayServiceImplTest {

    private final ApexUserContext userContext = mock(ApexUserContext.class);
    private final PortfolioMapper portfolioMapper = mock(PortfolioMapper.class);
    private final PortfolioIntradaySnapshotMapper snapshotMapper = mock(PortfolioIntradaySnapshotMapper.class);
    private final IPortfolioService portfolioService = mock(IPortfolioService.class);
    private final PortfolioIntradayServiceImpl service = new PortfolioIntradayServiceImpl();

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Portfolio.class);
        TableInfoHelper.initTableInfo(assistant, PortfolioIntradaySnapshot.class);
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "userContext", userContext);
        ReflectionTestUtils.setField(service, "portfolioMapper", portfolioMapper);
        ReflectionTestUtils.setField(service, "snapshotMapper", snapshotMapper);
        ReflectionTestUtils.setField(service, "portfolioService", portfolioService);
        when(userContext.currentUserIdOrNull()).thenReturn(7L);
    }

    @Test
    void snapshotUsesFiveMinuteBucketAndCurrentReturnMetrics() {
        Portfolio portfolio = Portfolio.builder().id(1L).userId(7L).status("ACTIVE").build();
        when(portfolioMapper.selectById(1L)).thenReturn(portfolio);
        when(portfolioService.intradaySummary(1L)).thenReturn(PortfolioSummaryResp.builder()
                .totalEquity(new BigDecimal("125000.00"))
                .todayPnl(new BigDecimal("1250.00"))
                .todayPct(new BigDecimal("1.0101"))
                .positionCount(6)
                .build());
        when(snapshotMapper.selectOne(any())).thenReturn(null);

        service.snapshot(1L, LocalDateTime.of(2026, 8, 21, 10, 8, 47));

        ArgumentCaptor<PortfolioIntradaySnapshot> snapshotCaptor =
                ArgumentCaptor.forClass(PortfolioIntradaySnapshot.class);
        verify(snapshotMapper).insert(snapshotCaptor.capture());
        PortfolioIntradaySnapshot snapshot = snapshotCaptor.getValue();
        assertEquals(LocalDateTime.of(2026, 8, 21, 10, 5), snapshot.getSnapshotTime());
        assertEquals(new BigDecimal("125000.00"), snapshot.getTotalEquity());
        assertEquals(new BigDecimal("1250.00"), snapshot.getTodayPnl());
        assertEquals(new BigDecimal("1.0101"), snapshot.getTodayPct());
        assertEquals(6, snapshot.getPositionCount());
    }

    @Test
    void listIntradayVerifiesPortfolioExistsAndReturnsAscendingPoints() {
        when(portfolioMapper.selectById(8L)).thenReturn(Portfolio.builder().id(8L).userId(9L).build());
        when(snapshotMapper.selectList(any())).thenReturn(List.of());

        service.list(8L, LocalDate.of(2026, 8, 21));

        ArgumentCaptor<Wrapper<PortfolioIntradaySnapshot>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(snapshotMapper).selectList(queryCaptor.capture());
        assertTrue(queryCaptor.getValue().getSqlSegment().contains("snapshot_time"));
    }

    @Test
    void snapshotUpdatesExistingFiveMinuteBucketInsteadOfInsertingDuplicate() {
        Portfolio portfolio = Portfolio.builder().id(1L).userId(7L).status("ACTIVE").build();
        PortfolioIntradaySnapshot existingSnapshot = PortfolioIntradaySnapshot.builder()
                .id(9L)
                .portfolioId(1L)
                .snapshotTime(LocalDateTime.of(2026, 8, 21, 10, 5))
                .todayPct(new BigDecimal("0.1000"))
                .build();
        when(portfolioMapper.selectById(1L)).thenReturn(portfolio);
        when(portfolioService.intradaySummary(1L)).thenReturn(PortfolioSummaryResp.builder()
                .totalEquity(new BigDecimal("126000.00"))
                .todayPnl(new BigDecimal("2250.00"))
                .todayPct(new BigDecimal("1.8182"))
                .positionCount(6)
                .build());
        when(snapshotMapper.selectOne(any())).thenReturn(existingSnapshot);

        PortfolioIntradaySnapshot result = service.snapshot(
                1L, LocalDateTime.of(2026, 8, 21, 10, 9, 59));

        assertEquals(new BigDecimal("1.8182"), result.getTodayPct());
        verify(snapshotMapper).updateById(existingSnapshot);
        verify(snapshotMapper, never()).insert(any(PortfolioIntradaySnapshot.class));
    }
}
