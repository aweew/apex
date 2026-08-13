package com.awe.apex.quant.bot.service;

import com.awe.apex.quant.domain.dto.BotAskReq;
import com.awe.apex.quant.domain.dto.BotAskResp;
import com.awe.apex.quant.domain.dto.BotHoldingRiskItem;
import com.awe.apex.quant.domain.dto.BotHoldingRiskResp;
import com.awe.apex.quant.domain.dto.DecisionAdviceResp;
import com.awe.apex.quant.domain.dto.MarketBriefingResp;
import com.awe.apex.quant.domain.dto.PortfolioSummaryResp;
import com.awe.apex.quant.domain.dto.PortfolioTopHoldingResp;
import com.awe.apex.quant.domain.dto.StockAnalysisFreshnessResp;
import com.awe.apex.quant.domain.dto.StockAnalysisResp;
import com.awe.apex.quant.domain.dto.StockSearchItem;
import com.awe.apex.quant.bot.service.IBotHoldingRiskService;
import com.awe.apex.quant.service.IDecisionService;
import com.awe.apex.quant.service.IMarketBriefingService;
import com.awe.apex.quant.service.IPortfolioService;
import com.awe.apex.quant.service.IStockAnalysisService;
import com.awe.apex.quant.service.IStockService;
import com.awe.apex.quant.bot.service.impl.BotQuestionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BotQuestionServiceImplTest {

    private BotQuestionServiceImpl service;
    private IStockService stockService;
    private IStockAnalysisService stockAnalysisService;
    private IMarketBriefingService marketBriefingService;
    private IBotHoldingRiskService botHoldingRiskService;
    private IPortfolioService portfolioService;
    private IDecisionService decisionService;

    @BeforeEach
    void setUp() {
        service = new BotQuestionServiceImpl();
        stockService = mock(IStockService.class);
        stockAnalysisService = mock(IStockAnalysisService.class);
        marketBriefingService = mock(IMarketBriefingService.class);
        botHoldingRiskService = mock(IBotHoldingRiskService.class);
        portfolioService = mock(IPortfolioService.class);
        decisionService = mock(IDecisionService.class);
        ReflectionTestUtils.setField(service, "stockService", stockService);
        ReflectionTestUtils.setField(service, "stockAnalysisService", stockAnalysisService);
        ReflectionTestUtils.setField(service, "marketBriefingService", marketBriefingService);
        ReflectionTestUtils.setField(service, "botHoldingRiskService", botHoldingRiskService);
        ReflectionTestUtils.setField(service, "portfolioService", portfolioService);
        ReflectionTestUtils.setField(service, "decisionService", decisionService);
    }

    @Test
    void routesChineseStockQuestionToStockAnalysis() {
        when(stockService.search(anyString(), anyInt())).thenReturn(List.of(StockSearchItem.builder()
                .code("300750").name("宁德时代").build()));
        when(stockAnalysisService.analyze(anyString(), anyString(), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(StockAnalysisResp.builder()
                        .code("300750").name("宁德时代").latestPrice(new BigDecimal("288.80"))
                        .pctChg(new BigDecimal("1.25")).stance("可跟踪")
                        .summary("趋势保持完整").actionHint("等待回踩确认")
                        .riskFlags(List.of("估值偏高"))
                        .freshness(StockAnalysisFreshnessResp.builder()
                                .lastBarDate(LocalDate.of(2026, 8, 13)).barsStale(false).note("数据已就绪").build())
                        .build());

        BotAskResp response = service.ask(request("宁德时代现在还能买吗"));

        assertEquals("STOCK_ANALYSIS", response.getIntent());
        assertEquals("300750", response.getStockCode());
        assertTrue(response.getAnswer().contains("可跟踪"));
        assertTrue(response.getAnswer().contains("2026-08-13"));
        verify(stockService).search("宁德时代", 5);
        verify(stockAnalysisService).analyze("300750", "BUY", 120, true, false);
    }

    @Test
    void routesMarketRiskAndDecisionQuestions() {
        when(marketBriefingService.briefing(false)).thenReturn(MarketBriefingResp.builder()
                .asOf(LocalDate.of(2026, 8, 13)).stance("均衡").stanceReason("量价中性")
                .positionAdvice("五成仓位").dataLevel("GREEN").build());
        BotAskResp market = service.ask(request("今天大盘怎么样"));
        assertEquals("MARKET_BRIEFING", market.getIntent());
        assertTrue(market.getAnswer().contains("五成仓位"));

        when(botHoldingRiskService.analyze()).thenReturn(BotHoldingRiskResp.builder()
                .holdingCount(1).quotedCount(1).criticalCount(1).warnCount(0)
                .dataAsOf("2026-08-13T10:00:00")
                .alerts(List.of(BotHoldingRiskItem.builder()
                        .level("CRITICAL").code("600000").name("浦发银行")
                        .message("现价 9.80 已触及止损价 10.00").build()))
                .build());
        BotAskResp risk = service.ask(request("我的持仓风险怎么样"));
        assertEquals("PORTFOLIO_RISK", risk.getIntent());
        assertTrue(risk.getAnswer().contains("触及止损"));

        when(decisionService.advice(any())).thenReturn(DecisionAdviceResp.builder()
                .actionDate(LocalDate.of(2026, 8, 13)).summary("控制仓位，等待确认")
                .targetExposure(new BigDecimal("0.40")).actions(List.of()).build());
        BotAskResp decision = service.ask(request("今天应该买什么"));
        assertEquals("TODAY_DECISION", decision.getIntent());
        assertTrue(decision.getAnswer().contains("控制仓位"));
    }

    @Test
    void routesNamedPortfolioBeforeStockLookup() {
        PortfolioSummaryResp summary = PortfolioSummaryResp.builder()
                .id(10L).name("郑十万").positionCount(3)
                .totalEquity(new BigDecimal("128000.50"))
                .cashBalance(new BigDecimal("28000.50"))
                .totalPnl(new BigDecimal("8000.00"))
                .todayPnl(new BigDecimal("1200.00"))
                .todayPct(new BigDecimal("0.95"))
                .updateTime(LocalDateTime.of(2026, 8, 13, 14, 55))
                .topHoldings(List.of(PortfolioTopHoldingResp.builder()
                        .code("300750").name("宁德时代")
                        .weightPct(new BigDecimal("35.20"))
                        .pctChg(new BigDecimal("1.25"))
                        .build()))
                .build();
        when(portfolioService.listPortfolios(false)).thenReturn(List.of(summary));
        when(portfolioService.detail(10L)).thenReturn(summary);

        BotAskResp response = service.ask(request("郑十万是组合，现在怎么样"));

        assertEquals("PORTFOLIO_SUMMARY", response.getIntent());
        assertTrue(response.getAnswer().contains("组合：郑十万"));
        assertTrue(response.getAnswer().contains("总权益：128000.50"));
        assertTrue(response.getAnswer().contains("宁德时代"));
        verify(portfolioService).detail(10L);
    }

    private BotAskReq request(String question) {
        BotAskReq request = new BotAskReq();
        request.setRequestId("wx-1");
        request.setUserId("awe");
        request.setConversationId("chat-1");
        request.setQuestion(question);
        return request;
    }
}
