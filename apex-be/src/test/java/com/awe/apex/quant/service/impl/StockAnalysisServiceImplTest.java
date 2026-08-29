package com.awe.apex.quant.service.impl;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.domain.dto.StockAnalysisCapitalResp;
import com.awe.apex.quant.domain.dto.StockAnalysisNewsResp;
import com.awe.apex.quant.domain.dto.StockAnalysisResp;
import com.awe.apex.quant.domain.dto.StockAnalysisTechResp;
import com.awe.apex.quant.domain.dto.StockDetailResp;
import com.awe.apex.quant.domain.dto.ValuationResp;
import com.awe.apex.quant.domain.entity.MarketNews;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.mapper.MarketNewsMapper;
import com.awe.apex.quant.service.IStockService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Test
    void conclusionForcesLowConfidenceBullishViewWithMeasurableInvalidation() {
        StockAnalysisServiceImpl analysisService = new StockAnalysisServiceImpl();
        StockBasic stockBasic = StockBasic.builder()
                .code("300750")
                .name("宁德时代")
                .latestPrice(new BigDecimal("22.00"))
                .build();
        StockAnalysisTechResp tech = StockAnalysisTechResp.builder()
                .regime("NEUTRAL")
                .regimeLabel("中性震荡")
                .grade("NEUTRAL")
                .summary("价格处于震荡区间")
                .hitCount(4)
                .total(8)
                .ma20(new BigDecimal("20.00"))
                .build();
        ValuationResp valuation = ValuationResp.builder()
                .score(new BigDecimal("80"))
                .summary("估值低于行业中位数")
                .bullPoints(List.of("估值低于行业中位数"))
                .build();

        StockAnalysisResp response = ReflectionTestUtils.invokeMethod(analysisService, "buildConclusion",
                stockBasic, tech, valuation, StockAnalysisCapitalResp.builder().build(), List.of(), null,
                StockDetailResp.builder().bars(List.of()).needSyncBars(false).build());

        assertEquals("看多", response.getStance());
        assertTrue(response.getActionHint().contains("未来20个交易日看多（低置信度）"));
        assertTrue(response.getActionHint().contains("核心依据：估值低于行业中位数"));
        assertTrue(response.getActionHint().contains("看错条件：收盘跌破 MA20（20.00元）"));
        assertFalse(response.getActionHint().matches(".*(观望|关注|等待|暂不开仓).*"));
    }

    @Test
    void conclusionForcesBearishViewInsteadOfNeutralWording() {
        StockAnalysisServiceImpl analysisService = new StockAnalysisServiceImpl();
        StockBasic stockBasic = StockBasic.builder()
                .code("600519")
                .name("贵州茅台")
                .latestPrice(new BigDecimal("18.00"))
                .build();
        StockAnalysisTechResp tech = StockAnalysisTechResp.builder()
                .regime("NEUTRAL")
                .regimeLabel("中性震荡")
                .grade("NEUTRAL")
                .summary("价格处于震荡区间")
                .hitCount(3)
                .total(8)
                .ma20(new BigDecimal("20.00"))
                .build();
        ValuationResp valuation = ValuationResp.builder()
                .score(new BigDecimal("50"))
                .summary("估值高于行业中位数")
                .bearPoints(List.of("估值高于行业中位数"))
                .build();

        StockAnalysisResp response = ReflectionTestUtils.invokeMethod(analysisService, "buildConclusion",
                stockBasic, tech, valuation, StockAnalysisCapitalResp.builder().build(), List.of(), null,
                StockDetailResp.builder().bars(List.of()).needSyncBars(false).build());

        assertEquals("看空", response.getStance());
        assertTrue(response.getActionHint().contains("未来20个交易日看空（中等置信度）"));
        assertTrue(response.getActionHint().contains("核心依据：估值高于行业中位数"));
        assertTrue(response.getActionHint().contains("看错条件：收盘站上 MA20（20.00元）"));
        assertFalse(response.getActionHint().matches(".*(中性|观望|关注|等待|暂不开仓).*"));
    }

    @Test
    void incompleteDimensionsStillGiveDirectionButNeverHighConfidence() {
        StockAnalysisServiceImpl analysisService = new StockAnalysisServiceImpl();
        StockBasic stockBasic = StockBasic.builder()
                .code("000001")
                .name("平安银行")
                .latestPrice(new BigDecimal("10.00"))
                .build();
        StockAnalysisTechResp tech = StockAnalysisTechResp.builder()
                .regime("INSUFFICIENT")
                .regimeLabel("日线不足")
                .grade("NEUTRAL")
                .summary("日线不足，技术结构暂无法完整评估")
                .build();

        StockAnalysisResp response = ReflectionTestUtils.invokeMethod(analysisService, "buildConclusion",
                stockBasic, tech, null, StockAnalysisCapitalResp.builder().build(), List.of(), null,
                StockDetailResp.builder().bars(List.of()).needSyncBars(true).build());

        assertEquals("看空", response.getStance());
        assertTrue(response.getActionHint().contains("未来20个交易日看空（低置信度）"));
        assertTrue(response.getActionHint().contains("技术/估值数据不足"));
    }
}
