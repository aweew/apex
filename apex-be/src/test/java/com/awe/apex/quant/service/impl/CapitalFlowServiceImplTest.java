package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.domain.dto.CapitalFlowOverviewResp;
import com.awe.apex.quant.domain.dto.SectorBoardResp;
import com.awe.apex.quant.domain.dto.SyncJobResp;
import com.awe.apex.quant.domain.entity.DragonTigerItem;
import com.awe.apex.quant.domain.entity.NorthboundFlow;
import com.awe.apex.quant.domain.entity.StockFundFlow;
import com.awe.apex.quant.mapper.DragonTigerItemMapper;
import com.awe.apex.quant.mapper.NorthboundFlowMapper;
import com.awe.apex.quant.mapper.StockFundFlowMapper;
import com.awe.apex.quant.service.ISectorBoardService;
import com.awe.apex.quant.service.IDataSyncJobService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CapitalFlowServiceImplTest {

    @Test
    void overviewUsesLatestSnapshotsAndReusesSectorBoards() {
        NorthboundFlowMapper northboundFlowMapper = mock(NorthboundFlowMapper.class);
        StockFundFlowMapper stockFundFlowMapper = mock(StockFundFlowMapper.class);
        DragonTigerItemMapper dragonTigerItemMapper = mock(DragonTigerItemMapper.class);
        ISectorBoardService sectorBoardService = mock(ISectorBoardService.class);
        LocalDate tradeDate = LocalDate.of(2026, 8, 20);
        LocalDateTime syncedAt = tradeDate.atTime(18, 20);
        when(northboundFlowMapper.selectOne(any())).thenReturn(NorthboundFlow.builder()
                .tradeDate(tradeDate)
                .netBuyAmount(new BigDecimal("123000000.00"))
                .dataStatus("PUBLISHED")
                .syncedAt(syncedAt)
                .build());
        when(stockFundFlowMapper.selectList(any())).thenReturn(List.of(StockFundFlow.builder()
                .code("600519")
                .name("贵州茅台")
                .tradeDate(tradeDate)
                .mainNetInflow(new BigDecimal("80000000.00"))
                .syncedAt(syncedAt)
                .build()));
        when(dragonTigerItemMapper.selectList(any())).thenReturn(List.of(DragonTigerItem.builder()
                .code("000001")
                .name("平安银行")
                .tradeDate(tradeDate)
                .reason("日涨幅偏离值达7%")
                .netBuyAmount(new BigDecimal("9000000.00"))
                .syncedAt(syncedAt)
                .build()));
        SectorBoardResp industry = SectorBoardResp.builder().boardType("INDUSTRY").tradeDate(tradeDate).build();
        SectorBoardResp concept = SectorBoardResp.builder().boardType("CONCEPT").tradeDate(tradeDate).build();
        when(sectorBoardService.board("INDUSTRY", "netInflow", "desc", 12, null)).thenReturn(industry);
        when(sectorBoardService.board("CONCEPT", "netInflow", "desc", 12, null)).thenReturn(concept);
        CapitalFlowServiceImpl service = buildService(
                northboundFlowMapper, stockFundFlowMapper, dragonTigerItemMapper, sectorBoardService);

        CapitalFlowOverviewResp overview = service.overview(12);

        assertEquals("PUBLISHED", overview.getNorthboundFlow().getDataStatus());
        assertEquals(tradeDate, overview.getStockTradeDate());
        assertEquals(syncedAt, overview.getStockSyncedAt());
        assertEquals("600519", overview.getStockFlows().get(0).getCode());
        assertEquals(industry, overview.getIndustryFlows());
        assertEquals(concept, overview.getConceptFlows());
        assertEquals(tradeDate, overview.getDragonTigerTradeDate());
        assertEquals(syncedAt, overview.getDragonTigerSyncedAt());
        assertEquals("000001", overview.getDragonTigerItems().get(0).getCode());
        verify(sectorBoardService).board("INDUSTRY", "netInflow", "desc", 12, null);
        verify(sectorBoardService).board("CONCEPT", "netInflow", "desc", 12, null);
    }

    @Test
    void overviewKeepsUnavailableNorthboundNetBuyAmountNull() {
        NorthboundFlowMapper northboundFlowMapper = mock(NorthboundFlowMapper.class);
        StockFundFlowMapper stockFundFlowMapper = mock(StockFundFlowMapper.class);
        DragonTigerItemMapper dragonTigerItemMapper = mock(DragonTigerItemMapper.class);
        ISectorBoardService sectorBoardService = mock(ISectorBoardService.class);
        when(northboundFlowMapper.selectOne(any())).thenReturn(NorthboundFlow.builder()
                .tradeDate(LocalDate.of(2026, 8, 20))
                .netBuyAmount(null)
                .buyAmount(new BigDecimal("100000000.00"))
                .sellAmount(new BigDecimal("90000000.00"))
                .dataStatus("NOT_DISCLOSED")
                .build());
        when(stockFundFlowMapper.selectList(any())).thenReturn(List.of());
        when(dragonTigerItemMapper.selectList(any())).thenReturn(List.of());
        when(sectorBoardService.board(any(), any(), any(), any(), any())).thenReturn(SectorBoardResp.builder().build());
        CapitalFlowServiceImpl service = buildService(
                northboundFlowMapper, stockFundFlowMapper, dragonTigerItemMapper, sectorBoardService);

        CapitalFlowOverviewResp overview = service.overview(20);

        assertNull(overview.getNorthboundFlow().getNetBuyAmount());
        assertEquals("NOT_DISCLOSED", overview.getNorthboundFlow().getDataStatus());
    }

    @Test
    void manualFlowRefreshWaitsForCapitalAndSectorTasks() {
        NorthboundFlowMapper northboundFlowMapper = mock(NorthboundFlowMapper.class);
        StockFundFlowMapper stockFundFlowMapper = mock(StockFundFlowMapper.class);
        DragonTigerItemMapper dragonTigerItemMapper = mock(DragonTigerItemMapper.class);
        ISectorBoardService sectorBoardService = mock(ISectorBoardService.class);
        IDataSyncJobService dataSyncJobService = mock(IDataSyncJobService.class);
        when(stockFundFlowMapper.selectList(any())).thenReturn(List.of());
        when(dragonTigerItemMapper.selectList(any())).thenReturn(List.of());
        when(sectorBoardService.board(any(), any(), any(), any(), any())).thenReturn(SectorBoardResp.builder().build());
        when(dataSyncJobService.startSystemTask(any())).thenReturn(
                SyncJobResp.builder().id(501L).status("SUCCESS").build(),
                SyncJobResp.builder().id(502L).status("SUCCESS").build());
        CapitalFlowServiceImpl service = buildService(
                northboundFlowMapper, stockFundFlowMapper, dragonTigerItemMapper, sectorBoardService);
        ReflectionTestUtils.setField(service, "dataSyncJobService", dataSyncJobService);

        service.refresh("flow", 20);

        verify(dataSyncJobService).startSystemTask(org.mockito.ArgumentMatchers.argThat(request ->
                "CAPITAL_FLOW".equals(request.getTaskType()) && "flow".equals(request.getMode())));
        verify(dataSyncJobService).startSystemTask(org.mockito.ArgumentMatchers.argThat(request ->
                "SECTOR_QUOTE".equals(request.getTaskType())
                        && "INDUSTRY,CONCEPT,THEME".equals(request.getTypes())));
    }

    private CapitalFlowServiceImpl buildService(NorthboundFlowMapper northboundFlowMapper,
                                                StockFundFlowMapper stockFundFlowMapper,
                                                DragonTigerItemMapper dragonTigerItemMapper,
                                                ISectorBoardService sectorBoardService) {
        CapitalFlowServiceImpl service = new CapitalFlowServiceImpl();
        ReflectionTestUtils.setField(service, "northboundFlowMapper", northboundFlowMapper);
        ReflectionTestUtils.setField(service, "stockFundFlowMapper", stockFundFlowMapper);
        ReflectionTestUtils.setField(service, "dragonTigerItemMapper", dragonTigerItemMapper);
        ReflectionTestUtils.setField(service, "sectorBoardService", sectorBoardService);
        return service;
    }
}
