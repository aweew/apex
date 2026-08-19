package com.awe.apex.quant.service.impl;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.domain.bo.DecisionMarketScanReq;
import com.awe.apex.quant.domain.bo.DecisionMarketSnapshot;
import com.awe.apex.quant.domain.dto.SignalConfluenceResp;
import com.awe.apex.quant.domain.entity.DecisionMarketScan;
import com.awe.apex.quant.domain.entity.DecisionMarketSignal;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.domain.entity.StrategySignalEntity;
import com.awe.apex.quant.mapper.DecisionMarketScanMapper;
import com.awe.apex.quant.mapper.DecisionMarketSignalMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.service.ISignalService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DecisionMarketSignalServiceTest {

    private final DecisionMarketScanMapper scanMapper = mock(DecisionMarketScanMapper.class);
    private final DecisionMarketSignalMapper signalMapper = mock(DecisionMarketSignalMapper.class);
    private final StockBasicMapper stockBasicMapper = mock(StockBasicMapper.class);
    private final ISignalService signalService = mock(ISignalService.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    private final DecisionMarketSignalServiceImpl service = new DecisionMarketSignalServiceImpl();

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                DecisionMarketScan.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                DecisionMarketSignal.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), StockBasic.class);
        ReflectionTestUtils.setField(service, "decisionMarketScanMapper", scanMapper);
        ReflectionTestUtils.setField(service, "decisionMarketSignalMapper", signalMapper);
        ReflectionTestUtils.setField(service, "stockBasicMapper", stockBasicMapper);
        ReflectionTestUtils.setField(service, "signalService", signalService);
        ReflectionTestUtils.setField(service, "transactionTemplate", transactionTemplate);
        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        when(scanMapper.insert(any(DecisionMarketScan.class))).thenAnswer(invocation -> {
            DecisionMarketScan scan = invocation.getArgument(0);
            scan.setId(31L);
            return 1;
        });
        when(scanMapper.updateById(any(DecisionMarketScan.class))).thenReturn(1);
        when(signalMapper.deleteByScanId(any())).thenReturn(1);
        when(signalMapper.insert(any(DecisionMarketSignal.class))).thenReturn(1);
    }

    @Test
    void refreshReusesSameScopeAndReplacesSignals() {
        DecisionMarketScan existing = DecisionMarketScan.builder()
                .id(31L)
                .actionDate(LocalDate.of(2026, 8, 19))
                .universeBatchNo("U-1")
                .includeBj(false)
                .status("SUCCESS")
                .build();
        when(scanMapper.selectOne(any())).thenReturn(existing);
        when(signalService.scan(any(), any())).thenReturn(List.of(
                signal("600519", "S1", "BUY"),
                signal("000001", "S2", "SELL")));

        DecisionMarketSnapshot snapshot = service.refresh(request(), null);

        assertEquals(31L, snapshot.getScanId());
        assertEquals(1, snapshot.getSignals().size());
        assertEquals("BUY", snapshot.getSignals().get(0).getSide());
        verify(scanMapper, times(2)).updateById(existing);
        verify(signalMapper).deleteByScanId(31L);
        verify(signalMapper).insert(any(DecisionMarketSignal.class));
        verify(signalService).scan(any(), any());
    }

    @Test
    void zeroSignalRefreshStillCreatesSuccessfulSnapshot() {
        when(scanMapper.selectOne(any())).thenReturn(null);
        when(signalService.scan(any(), any())).thenReturn(List.of());

        DecisionMarketSnapshot snapshot = service.refresh(request(), null);

        assertEquals(31L, snapshot.getScanId());
        assertEquals(0, snapshot.getSignals().size());
        verify(scanMapper).insert(any(DecisionMarketScan.class));
        verify(signalMapper).deleteByScanId(31L);
    }

    @Test
    void requireRejectsMissingExactSnapshot() {
        when(scanMapper.selectOne(any())).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.require(LocalDate.of(2026, 8, 19), "U-1", false));

        assertEquals("当日共享市场决策尚未生成，请等待系统同步完成", exception.getMessage());
    }

    @Test
    void requireReturnsSignalsFromMatchedScan() {
        when(scanMapper.selectOne(any())).thenReturn(DecisionMarketScan.builder()
                .id(31L)
                .actionDate(LocalDate.of(2026, 8, 19))
                .universeBatchNo("U-1")
                .includeBj(false)
                .status("SUCCESS")
                .universeCount(4000)
                .signalCount(1)
                .build());
        when(signalMapper.selectList(any())).thenReturn(List.of(DecisionMarketSignal.builder()
                .scanId(31L)
                .code("600519")
                .strategyId("S1")
                .side("BUY")
                .signalDate(LocalDate.of(2026, 8, 19))
                .score(new BigDecimal("88"))
                .build()));

        DecisionMarketSnapshot snapshot = service.require(LocalDate.of(2026, 8, 19), "U-1", false);

        assertEquals(1, snapshot.getSignals().size());
        assertEquals("600519", snapshot.getSignals().get(0).getCode());
    }

    @Test
    void confluenceUsesOnlyCompletedScansFromRequestedMarketScope() {
        when(scanMapper.selectList(any())).thenReturn(List.of(
                DecisionMarketScan.builder().id(31L).includeBj(false).status("SUCCESS").build()));
        when(signalMapper.selectList(any())).thenReturn(List.of(
                storedSignal(31L, "600519", "S1", new BigDecimal("80")),
                storedSignal(31L, "600519", "S2", new BigDecimal("90"))));
        when(stockBasicMapper.selectList(any())).thenReturn(List.of(
                StockBasic.builder().code("600519").name("贵州茅台").build()));

        SignalConfluenceResp response = service.confluence(5, 2, LocalDate.of(2026, 8, 19), false);

        assertEquals(1, response.getItems().size());
        assertEquals("贵州茅台", response.getItems().get(0).getName());
        assertEquals(2, response.getItems().get(0).getStrategyCount());
    }

    private DecisionMarketScanReq request() {
        return DecisionMarketScanReq.builder()
                .actionDate(LocalDate.of(2026, 8, 19))
                .universeBatchNo("U-1")
                .includeBj(false)
                .universeCount(2)
                .hotScanCount(0)
                .codes(List.of("600519", "000001"))
                .build();
    }

    private StrategySignalEntity signal(String code, String strategyId, String side) {
        return StrategySignalEntity.builder()
                .code(code)
                .strategyId(strategyId)
                .signalDate(LocalDate.of(2026, 8, 19))
                .side(side)
                .score(new BigDecimal("80"))
                .build();
    }

    private DecisionMarketSignal storedSignal(Long scanId, String code, String strategyId, BigDecimal score) {
        return DecisionMarketSignal.builder()
                .scanId(scanId)
                .code(code)
                .strategyId(strategyId)
                .signalDate(LocalDate.of(2026, 8, 19))
                .side("BUY")
                .score(score)
                .build();
    }
}
