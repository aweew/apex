package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.market.HithinkFinancialClient;
import com.awe.apex.quant.market.StockQuoteClient;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MyHoldingRealtimeQuoteTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), StockBasic.class);
    }

    @Test
    void realtimeRefreshSkipsValuationAndDailyBarSources() {
        MyHoldingServiceImpl service = new MyHoldingServiceImpl();
        StockBasicMapper stockBasicMapper = mock(StockBasicMapper.class);
        BarDailyMapper barDailyMapper = mock(BarDailyMapper.class);
        StockQuoteClient stockQuoteClient = mock(StockQuoteClient.class);
        LocalDateTime quoteTime = LocalDateTime.of(2026, 8, 18, 10, 0);
        StockBasic existing = StockBasic.builder().id(1L).code("600000").build();
        StockBasic realtime = StockBasic.builder()
                .code("600000")
                .name("浦发银行")
                .latestPrice(new BigDecimal("12.34"))
                .pctChg(new BigDecimal("1.23"))
                .quoteTime(quoteTime)
                .build();
        ReflectionTestUtils.setField(service, "stockBasicMapper", stockBasicMapper);
        ReflectionTestUtils.setField(service, "barDailyMapper", barDailyMapper);
        ReflectionTestUtils.setField(service, "stockQuoteClient", stockQuoteClient);
        when(stockBasicMapper.selectOne(any())).thenReturn(existing);
        when(stockQuoteClient.fetchRealtime("600000")).thenReturn(realtime);

        service.refreshRealtimeQuotesForCodes(List.of("600000"), false);

        verify(stockQuoteClient).fetchRealtime("600000");
        verify(stockQuoteClient, never()).fetchBasic(any());
        verify(barDailyMapper, never()).selectOne(any());
        verify(stockBasicMapper).updateById(existing);
    }

    @Test
    void realtimeRefreshUsesHithinkBatchSnapshotBeforeSingleQuoteFallback() {
        MyHoldingServiceImpl service = new MyHoldingServiceImpl();
        StockBasicMapper stockBasicMapper = mock(StockBasicMapper.class);
        BarDailyMapper barDailyMapper = mock(BarDailyMapper.class);
        StockQuoteClient stockQuoteClient = mock(StockQuoteClient.class);
        HithinkFinancialClient hithinkFinancialClient = mock(HithinkFinancialClient.class);
        StockBasic existing = StockBasic.builder().id(1L).code("600000").build();
        StockBasic batchQuote = StockBasic.builder()
                .code("600000")
                .market("SH")
                .latestPrice(new BigDecimal("12.34"))
                .pctChg(new BigDecimal("1.23"))
                .source("hithink")
                .quoteTime(LocalDateTime.of(2026, 8, 18, 10, 0))
                .build();
        ReflectionTestUtils.setField(service, "stockBasicMapper", stockBasicMapper);
        ReflectionTestUtils.setField(service, "barDailyMapper", barDailyMapper);
        ReflectionTestUtils.setField(service, "stockQuoteClient", stockQuoteClient);
        ReflectionTestUtils.setField(service, "hithinkFinancialClient", hithinkFinancialClient);
        when(stockBasicMapper.selectOne(any())).thenReturn(existing);
        when(hithinkFinancialClient.isAvailable()).thenReturn(true);
        when(hithinkFinancialClient.fetchSnapshots(List.of("600000"))).thenReturn(Map.of("600000", batchQuote));

        service.refreshRealtimeQuotesForCodes(List.of("600000"), false);

        verify(hithinkFinancialClient).fetchSnapshots(List.of("600000"));
        verify(stockQuoteClient, never()).fetchRealtime(any());
        verify(stockQuoteClient, never()).fetchBasic(any());
        verify(stockBasicMapper).updateById(existing);
    }

    @Test
    void fullRefreshUsesBatchSnapshotAndValuationWhenBothAreAvailable() {
        MyHoldingServiceImpl service = new MyHoldingServiceImpl();
        StockBasicMapper stockBasicMapper = mock(StockBasicMapper.class);
        BarDailyMapper barDailyMapper = mock(BarDailyMapper.class);
        StockQuoteClient stockQuoteClient = mock(StockQuoteClient.class);
        HithinkFinancialClient hithinkFinancialClient = mock(HithinkFinancialClient.class);
        StockBasic existing = StockBasic.builder().id(1L).code("600000").build();
        StockBasic batchQuote = StockBasic.builder()
                .code("600000")
                .market("SH")
                .latestPrice(new BigDecimal("12.34"))
                .pctChg(new BigDecimal("1.23"))
                .source("hithink")
                .quoteTime(LocalDateTime.of(2026, 8, 18, 10, 0))
                .build();
        StockBasic batchValuation = StockBasic.builder()
                .code("600000")
                .peTtm(new BigDecimal("8.2"))
                .pb(new BigDecimal("0.8"))
                .source("hithink")
                .build();
        ReflectionTestUtils.setField(service, "stockBasicMapper", stockBasicMapper);
        ReflectionTestUtils.setField(service, "barDailyMapper", barDailyMapper);
        ReflectionTestUtils.setField(service, "stockQuoteClient", stockQuoteClient);
        ReflectionTestUtils.setField(service, "hithinkFinancialClient", hithinkFinancialClient);
        when(stockBasicMapper.selectOne(any())).thenReturn(existing);
        when(hithinkFinancialClient.isAvailable()).thenReturn(true);
        when(hithinkFinancialClient.fetchSnapshots(List.of("600000"))).thenReturn(Map.of("600000", batchQuote));
        when(hithinkFinancialClient.fetchValuations(List.of("600000")))
                .thenReturn(Map.of("600000", batchValuation));

        service.refreshQuotesForCodes(List.of("600000"), false);

        verify(hithinkFinancialClient).fetchSnapshots(List.of("600000"));
        verify(hithinkFinancialClient).fetchValuations(List.of("600000"));
        verify(stockQuoteClient, never()).fetchBasic(any());
        verify(stockQuoteClient, never()).fetchRealtime(any());
        verify(stockBasicMapper).updateById(existing);
    }

    @Test
    void fullRefreshDoesNotSkipExistingPriceWhenValuationIsMissing() {
        MyHoldingServiceImpl service = new MyHoldingServiceImpl();
        StockBasicMapper stockBasicMapper = mock(StockBasicMapper.class);
        BarDailyMapper barDailyMapper = mock(BarDailyMapper.class);
        StockQuoteClient stockQuoteClient = mock(StockQuoteClient.class);
        StockBasic existing = StockBasic.builder()
                .id(1L)
                .code("600000")
                .latestPrice(new BigDecimal("12.00"))
                .build();
        StockBasic fetched = StockBasic.builder()
                .code("600000")
                .latestPrice(new BigDecimal("12.34"))
                .peTtm(new BigDecimal("8.2"))
                .pb(new BigDecimal("0.8"))
                .build();
        ReflectionTestUtils.setField(service, "stockBasicMapper", stockBasicMapper);
        ReflectionTestUtils.setField(service, "barDailyMapper", barDailyMapper);
        ReflectionTestUtils.setField(service, "stockQuoteClient", stockQuoteClient);
        when(stockBasicMapper.selectOne(any())).thenReturn(existing);
        when(stockQuoteClient.fetchBasic("600000")).thenReturn(fetched);

        service.refreshQuotesForCodes(List.of("600000"), true);

        verify(stockQuoteClient).fetchBasic("600000");
        verify(stockBasicMapper).updateById(existing);
    }
}
