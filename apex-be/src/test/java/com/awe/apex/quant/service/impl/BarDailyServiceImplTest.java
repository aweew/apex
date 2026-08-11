package com.awe.apex.quant.service.impl;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.domain.dto.BarSyncReq;
import com.awe.apex.quant.domain.dto.BarSyncResp;
import com.awe.apex.quant.mapper.DataSyncLogMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.market.DailyBarClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BarDailyServiceImplTest {

    @Test
    void syncBarsShouldRejectOversizedRequest() {
        BarDailyServiceImpl barDailyService = new BarDailyServiceImpl();
        BarSyncReq request = new BarSyncReq();
        List<String> codes = new ArrayList<>();
        for (int index = 0; index < 81; index++) {
            codes.add(String.valueOf(600000 + index));
        }
        request.setCodes(codes);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> barDailyService.syncBars(request));

        assertEquals("单次最多同步 80 个证券代码，全 A 日线请使用同步中心任务", exception.getMessage());
    }

    @Test
    void syncBarsShouldProcessCodesInBoundedBatches() {
        BarDailyServiceImpl barDailyService = new BarDailyServiceImpl();
        DailyBarClient dailyBarClient = mock(DailyBarClient.class);
        StockBasicMapper stockBasicMapper = mock(StockBasicMapper.class);
        DataSyncLogMapper dataSyncLogMapper = mock(DataSyncLogMapper.class);
        AtomicInteger activeTasks = new AtomicInteger();
        AtomicInteger maxActiveTasks = new AtomicInteger();
        AtomicInteger callCount = new AtomicInteger();
        when(dailyBarClient.fetchDailyBars(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            callCount.incrementAndGet();
            int currentActive = activeTasks.incrementAndGet();
            maxActiveTasks.accumulateAndGet(currentActive, Math::max);
            try {
                Thread.sleep(30);
                return List.of();
            } finally {
                activeTasks.decrementAndGet();
            }
        });
        when(stockBasicMapper.selectOne(any())).thenReturn(null);
        ReflectionTestUtils.setField(barDailyService, "dailyBarClient", dailyBarClient);
        ReflectionTestUtils.setField(barDailyService, "stockBasicMapper", stockBasicMapper);
        ReflectionTestUtils.setField(barDailyService, "dataSyncLogMapper", dataSyncLogMapper);
        BarSyncReq request = new BarSyncReq();
        request.setCodes(List.of("600000", "600001", "600002"));

        BarSyncResp response = barDailyService.syncBars(request);

        assertEquals(3, callCount.get());
        assertTrue(maxActiveTasks.get() <= 2);
        assertEquals(3, response.getSuccessCount());
        assertEquals(3, response.getDetails().size());
    }
}
