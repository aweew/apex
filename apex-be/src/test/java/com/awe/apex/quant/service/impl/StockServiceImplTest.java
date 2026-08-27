package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.domain.dto.StockSearchItem;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.mapper.WatchlistMapper;
import com.awe.apex.quant.market.StockQuoteClient;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 个股详情数据状态
 */
class StockServiceImplTest {

    @Test
    void syncQuoteShouldOnlyFetchRealtimeQuote() {
        StockServiceImpl service = new StockServiceImpl();
        StockQuoteClient stockQuoteClient = mock(StockQuoteClient.class);
        StockBasicMapper stockBasicMapper = mock(StockBasicMapper.class);
        when(stockQuoteClient.fetchRealtimeFast("600519")).thenReturn(StockBasic.builder()
                .code("600519")
                .name("贵州茅台")
                .market("SH")
                .build());
        when(stockBasicMapper.selectOne(any())).thenReturn(null);
        ReflectionTestUtils.setField(service, "stockQuoteClient", stockQuoteClient);
        ReflectionTestUtils.setField(service, "stockBasicMapper", stockBasicMapper);

        service.syncQuote("600519");

        verify(stockQuoteClient).fetchRealtimeFast("600519");
        verify(stockQuoteClient, never()).fetchRealtime("600519");
        verify(stockQuoteClient, never()).fetchBasic("600519");
    }

    @Test
    void syncQuoteShouldPreserveExistingValuationAndIndustry() {
        StockServiceImpl service = new StockServiceImpl();
        StockQuoteClient stockQuoteClient = mock(StockQuoteClient.class);
        StockBasicMapper stockBasicMapper = mock(StockBasicMapper.class);
        StockBasic existing = StockBasic.builder()
                .code("600519")
                .industry("白酒")
                .peTtm(new BigDecimal("20.5"))
                .pb(new BigDecimal("8.2"))
                .build();
        when(stockQuoteClient.fetchRealtimeFast("600519")).thenReturn(StockBasic.builder()
                .code("600519")
                .name("贵州茅台")
                .market("SH")
                .latestPrice(new BigDecimal("1420.00"))
                .build());
        when(stockBasicMapper.selectOne(any())).thenReturn(existing);
        ReflectionTestUtils.setField(service, "stockQuoteClient", stockQuoteClient);
        ReflectionTestUtils.setField(service, "stockBasicMapper", stockBasicMapper);

        StockBasic result = service.syncQuote("600519");

        assertEquals("白酒", result.getIndustry());
        assertEquals(new BigDecimal("20.5"), result.getPeTtm());
        assertEquals(new BigDecimal("8.2"), result.getPb());
        assertEquals(new BigDecimal("1420.00"), result.getLatestPrice());
        assertEquals("gzmt", result.getPinyinAbbr());
    }

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

    @Test
    void searchShouldMatchUppercasePinyinInitials() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), StockBasic.class);
        StockServiceImpl service = new StockServiceImpl();
        StockBasicMapper stockBasicMapper = mock(StockBasicMapper.class);
        WatchlistMapper watchlistMapper = mock(WatchlistMapper.class);
        when(watchlistMapper.selectList(any())).thenReturn(List.of());
        when(stockBasicMapper.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<StockBasic> queryWrapper = invocation.getArgument(0);
            if (!queryWrapper.getSqlSegment().contains("pinyin_abbr")) {
                return List.of();
            }
            assertTrue(queryWrapper.getParamNameValuePairs().containsValue("gzmt%"));
            return List.of(StockBasic.builder()
                    .code("600519")
                    .name("贵州茅台")
                    .market("SH")
                    .build());
        });
        ReflectionTestUtils.setField(service, "stockBasicMapper", stockBasicMapper);
        ReflectionTestUtils.setField(service, "watchlistMapper", watchlistMapper);

        List<StockSearchItem> result = service.search("GZMT", 10);

        assertEquals(1, result.size());
        assertEquals("600519", result.get(0).getCode());
        assertEquals("贵州茅台", result.get(0).getName());
    }

    @Test
    void searchShouldKeepChineseNameMatching() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), StockBasic.class);
        StockServiceImpl service = new StockServiceImpl();
        StockBasicMapper stockBasicMapper = mock(StockBasicMapper.class);
        WatchlistMapper watchlistMapper = mock(WatchlistMapper.class);
        when(watchlistMapper.selectList(any())).thenReturn(List.of());
        when(stockBasicMapper.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<StockBasic> queryWrapper = invocation.getArgument(0);
            assertTrue(queryWrapper.getSqlSegment().contains("name"));
            assertFalse(queryWrapper.getSqlSegment().contains("pinyin_abbr"));
            return List.of();
        });
        ReflectionTestUtils.setField(service, "stockBasicMapper", stockBasicMapper);
        ReflectionTestUtils.setField(service, "watchlistMapper", watchlistMapper);

        service.search("茅台", 10);

        verify(stockBasicMapper).selectList(any());
    }

    @Test
    void searchShouldKeepEnglishNameMatching() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), StockBasic.class);
        StockServiceImpl service = new StockServiceImpl();
        StockBasicMapper stockBasicMapper = mock(StockBasicMapper.class);
        WatchlistMapper watchlistMapper = mock(WatchlistMapper.class);
        when(watchlistMapper.selectList(any())).thenReturn(List.of());
        when(stockBasicMapper.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<StockBasic> queryWrapper = invocation.getArgument(0);
            if (!queryWrapper.getSqlSegment().contains("name")) {
                return List.of();
            }
            return List.of(StockBasic.builder()
                    .code("588000")
                    .name("科创50ETF")
                    .market("SH")
                    .build());
        });
        ReflectionTestUtils.setField(service, "stockBasicMapper", stockBasicMapper);
        ReflectionTestUtils.setField(service, "watchlistMapper", watchlistMapper);

        List<StockSearchItem> result = service.search("ETF", 10);

        assertEquals(1, result.size());
        assertEquals("588000", result.get(0).getCode());
    }
}
