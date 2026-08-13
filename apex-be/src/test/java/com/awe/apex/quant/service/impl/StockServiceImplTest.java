package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.mapper.WatchlistMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 个股详情数据状态
 */
class StockServiceImplTest {

    @Test
    void marksEmptyDailyBarsAsSyncRequiredWithReason() {
        StockServiceImpl service = new StockServiceImpl();
        StockBasicMapper stockBasicMapper = mock(StockBasicMapper.class);
        BarDailyMapper barDailyMapper = mock(BarDailyMapper.class);
        WatchlistMapper watchlistMapper = mock(WatchlistMapper.class);
        ReflectionTestUtils.setField(service, "stockBasicMapper", stockBasicMapper);
        ReflectionTestUtils.setField(service, "barDailyMapper", barDailyMapper);
        ReflectionTestUtils.setField(service, "watchlistMapper", watchlistMapper);
        when(barDailyMapper.selectList(any())).thenReturn(List.of());

        var detail = service.detail("600519", 120, false);

        assertTrue(detail.getNeedSyncBars());
        assertEquals("EMPTY", detail.getBarStatus());
        assertTrue(detail.getMissingDataReason().contains("日线"));
    }

    @Test
    void marksOldDailyBarsAsStale() {
        StockServiceImpl service = new StockServiceImpl();
        StockBasicMapper stockBasicMapper = mock(StockBasicMapper.class);
        BarDailyMapper barDailyMapper = mock(BarDailyMapper.class);
        WatchlistMapper watchlistMapper = mock(WatchlistMapper.class);
        ReflectionTestUtils.setField(service, "stockBasicMapper", stockBasicMapper);
        ReflectionTestUtils.setField(service, "barDailyMapper", barDailyMapper);
        ReflectionTestUtils.setField(service, "watchlistMapper", watchlistMapper);
        when(barDailyMapper.selectList(any())).thenReturn(List.of(BarDaily.builder()
                .code("600519").tradeDate(LocalDate.of(2020, 1, 2)).build()));

        var detail = service.detail("600519", 120, false);

        assertTrue(detail.getNeedSyncBars());
        assertEquals("STALE", detail.getBarStatus());
    }
}
