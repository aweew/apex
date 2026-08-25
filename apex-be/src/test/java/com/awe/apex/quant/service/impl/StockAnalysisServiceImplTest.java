package com.awe.apex.quant.service.impl;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.domain.dto.StockAnalysisNewsResp;
import com.awe.apex.quant.domain.entity.MarketNews;
import com.awe.apex.quant.mapper.MarketNewsMapper;
import com.awe.apex.quant.service.IStockService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockAnalysisServiceImplTest {

    @Test
    void baseAnalysisShouldNotWaitForExternalQuoteRefresh() {
        StockAnalysisServiceImpl analysisService = new StockAnalysisServiceImpl();
        IStockService stockService = mock(IStockService.class);
        when(stockService.detail("600519", 120, false)).thenThrow(new BusinessException("stop after local read"));
        ReflectionTestUtils.setField(analysisService, "stockService", stockService);

        assertThrows(BusinessException.class,
                () -> analysisService.analyze("600519", "BUY", 120, false, false));

        verify(stockService, never()).syncBasic(anyString());
    }

    @Test
    void recentNewsPrioritizesRelatedCodeAndPreservesTraceableFields() {
        StockAnalysisServiceImpl analysisService = new StockAnalysisServiceImpl();
        MarketNewsMapper marketNewsMapper = mock(MarketNewsMapper.class);
        ReflectionTestUtils.setField(analysisService, "marketNewsMapper", marketNewsMapper);
        LocalDateTime publishedAt = LocalDateTime.of(2026, 8, 25, 10, 30);
        when(marketNewsMapper.selectList(any())).thenReturn(List.of(
                MarketNews.builder().source("eastmoney").title("电池产业链动态")
                        .relatedCodes("300750,002594").publishedAt(publishedAt).url("https://example.com/code").build(),
                MarketNews.builder().source("cls").title("宁德时代发布新品")
                        .publishedAt(publishedAt.minusHours(1)).url("https://example.com/name").build(),
                MarketNews.builder().source("sina").title("无关标题")
                        .publishedAt(publishedAt.minusHours(2)).build()));

        List<StockAnalysisNewsResp> response = ReflectionTestUtils.invokeMethod(
                analysisService, "loadRecentNews", "300750", "宁德时代");

        assertEquals(2, response.size());
        assertEquals("代码关联", response.get(0).getMatchType());
        assertEquals("名称命中", response.get(1).getMatchType());
        assertEquals("https://example.com/code", response.get(0).getUrl());
        assertEquals(publishedAt, response.get(0).getPublishedAt());
    }
}
