package com.awe.apex.quant.bot.service;

import com.awe.apex.quant.domain.dto.BotAskReq;
import com.awe.apex.quant.domain.dto.BotAskResp;
import com.awe.apex.quant.domain.dto.BotHoldingRiskItem;
import com.awe.apex.quant.domain.dto.BotHoldingRiskResp;
import com.awe.apex.quant.domain.dto.DecisionAdviceResp;
import com.awe.apex.quant.domain.dto.MarketBriefingResp;
import com.awe.apex.quant.domain.dto.ObservePoolResp;
import com.awe.apex.quant.domain.dto.ObservePoolSaveReq;
import com.awe.apex.quant.domain.dto.PortfolioSummaryResp;
import com.awe.apex.quant.domain.dto.PortfolioTopHoldingResp;
import com.awe.apex.quant.domain.dto.StockAnalysisFreshnessResp;
import com.awe.apex.quant.domain.dto.StockAnalysisResp;
import com.awe.apex.quant.domain.dto.StockSearchItem;
import com.awe.apex.quant.bot.service.IBotHoldingRiskService;
import com.awe.apex.quant.service.IDecisionService;
import com.awe.apex.quant.service.IMarketBriefingService;
import com.awe.apex.quant.service.IObservePoolService;
import com.awe.apex.quant.service.IPortfolioService;
import com.awe.apex.quant.service.IStockAnalysisService;
import com.awe.apex.quant.service.IStockService;
import com.awe.apex.quant.bot.service.impl.BotQuestionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
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
    private IObservePoolService observePoolService;

    @BeforeEach
    void setUp() {
        service = new BotQuestionServiceImpl();
        stockService = mock(IStockService.class);
        stockAnalysisService = mock(IStockAnalysisService.class);
        marketBriefingService = mock(IMarketBriefingService.class);
        botHoldingRiskService = mock(IBotHoldingRiskService.class);
        portfolioService = mock(IPortfolioService.class);
        decisionService = mock(IDecisionService.class);
        observePoolService = mock(IObservePoolService.class);
        ReflectionTestUtils.setField(service, "stockService", stockService);
        ReflectionTestUtils.setField(service, "stockAnalysisService", stockAnalysisService);
        ReflectionTestUtils.setField(service, "marketBriefingService", marketBriefingService);
        ReflectionTestUtils.setField(service, "botHoldingRiskService", botHoldingRiskService);
        ReflectionTestUtils.setField(service, "portfolioService", portfolioService);
        ReflectionTestUtils.setField(service, "decisionService", decisionService);
        ReflectionTestUtils.setField(service, "observePoolService", observePoolService);
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
    void addsExactStockNameToObservePool() {
        when(stockService.search("贵州茅台", 10)).thenReturn(List.of(StockSearchItem.builder()
                .code("600519").name("贵州茅台").market("SH").build()));
        when(observePoolService.list(null, null, "600519")).thenReturn(List.of());

        BotAskResp response = service.ask(request("把贵州茅台加入观察池"));

        assertEquals("OBSERVE_ADD", response.getIntent());
        assertEquals("600519", response.getStockCode());
        assertEquals("贵州茅台", response.getStockName());
        assertTrue(response.getAnswer().contains("已将贵州茅台（600519）加入观察池"));
        ArgumentCaptor<ObservePoolSaveReq> requestCaptor = ArgumentCaptor.forClass(ObservePoolSaveReq.class);
        verify(observePoolService).save(requestCaptor.capture());
        assertEquals("600519", requestCaptor.getValue().getCode());
        assertEquals("贵州茅台", requestCaptor.getValue().getName());
        assertEquals("SH", requestCaptor.getValue().getMarket());
        assertEquals("BUY", requestCaptor.getValue().getSide());
        assertEquals("WATCHING", requestCaptor.getValue().getStatus());
        assertEquals("微信 Bot 手动加入", requestCaptor.getValue().getReason());
        assertEquals("微信Bot,手动", requestCaptor.getValue().getTags());
        assertEquals(3, requestCaptor.getValue().getPriority());
        verify(stockAnalysisService, never()).analyze(anyString(), anyString(), anyInt(), anyBoolean(), anyBoolean());
    }

    @Test
    void addsStockCodeToObservePool() {
        when(stockService.search("600519", 10)).thenReturn(List.of(StockSearchItem.builder()
                .code("600519").name("贵州茅台").market("SH").build()));
        when(observePoolService.list(null, null, "600519")).thenReturn(List.of());

        BotAskResp response = service.ask(request("帮我关注 600519"));

        assertEquals("OBSERVE_ADD", response.getIntent());
        assertTrue(response.getAnswer().contains("贵州茅台（600519）"));
        verify(observePoolService).save(any(ObservePoolSaveReq.class));
        verify(stockAnalysisService, never()).analyze(anyString(), anyString(), anyInt(), anyBoolean(), anyBoolean());
    }

    @Test
    void addsStockFromNaturalFollowRequest() {
        when(stockService.search("贵州茅台", 10)).thenReturn(List.of(StockSearchItem.builder()
                .code("600519").name("贵州茅台").market("SH").build()));
        when(observePoolService.list(null, null, "600519")).thenReturn(List.of());

        BotAskResp response = service.ask(request("我想关注一下贵州茅台"));

        assertEquals("OBSERVE_ADD", response.getIntent());
        verify(observePoolService).save(any(ObservePoolSaveReq.class));
    }

    @Test
    void addsStockWhenObservePoolPhraseContainsModifiers() {
        when(stockService.search("中兴通讯", 10)).thenReturn(List.of(StockSearchItem.builder()
                .code("000063").name("中兴通讯").market("SZ").build()));
        when(observePoolService.list(null, null, "000063")).thenReturn(List.of());

        BotAskResp response = service.ask(request("请把中兴通讯加入到我的观察池"));

        assertEquals("OBSERVE_ADD", response.getIntent());
        verify(observePoolService).save(any(ObservePoolSaveReq.class));
    }

    @Test
    void negatedFollowRequestDoesNotWriteObservePool() {
        when(portfolioService.listPortfolios(false)).thenReturn(List.of());
        when(stockService.search(anyString(), anyInt())).thenReturn(List.of());

        BotAskResp response = service.ask(request("我不想关注贵州茅台"));

        assertNotEquals("OBSERVE_ADD", response.getIntent());
        verify(observePoolService, never()).save(any(ObservePoolSaveReq.class));
    }

    @Test
    void existingObserveItemIsNotOverwritten() {
        when(stockService.search("宁德时代", 10)).thenReturn(List.of(StockSearchItem.builder()
                .code("300750").name("宁德时代").market("SZ").build()));
        when(observePoolService.list(null, null, "300750")).thenReturn(List.of(ObservePoolResp.builder()
                .code("300750").name("宁德时代").build()));

        BotAskResp response = service.ask(request("将宁德时代加到观察池"));

        assertEquals("OBSERVE_ADD", response.getIntent());
        assertTrue(response.getAnswer().contains("已在观察池中"));
        verify(observePoolService, never()).save(any(ObservePoolSaveReq.class));
    }

    @Test
    void unknownStockIsNotAddedToObservePool() {
        when(stockService.search("虚构科技", 10)).thenReturn(List.of());

        BotAskResp response = service.ask(request("把虚构科技加入观察池"));

        assertEquals("OBSERVE_ADD_UNRESOLVED", response.getIntent());
        assertTrue(response.getAnswer().contains("未加入观察池"));
        verify(observePoolService, never()).save(any(ObservePoolSaveReq.class));
    }

    @Test
    void ambiguousStockNameIsNotAddedToObservePool() {
        when(stockService.search("同名科技", 10)).thenReturn(List.of(
                StockSearchItem.builder().code("600001").name("同名科技").market("SH").build(),
                StockSearchItem.builder().code("000001").name("同名科技").market("SZ").build()));

        BotAskResp response = service.ask(request("把同名科技加入观察池"));

        assertEquals("OBSERVE_ADD_AMBIGUOUS", response.getIntent());
        assertTrue(response.getAnswer().contains("找到多个同名标的"));
        assertTrue(response.getAnswer().contains("未加入观察池"));
        verify(observePoolService, never()).save(any(ObservePoolSaveReq.class));
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

        BotAskResp strategy = service.ask(request("今天什么策略，有什么意见"));
        assertEquals("TODAY_DECISION", strategy.getIntent());
        assertTrue(strategy.getAnswer().contains("控制仓位"));
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
        assertFalse(response.getAnswer().contains("总权益："));
        assertFalse(response.getAnswer().contains("128000.50"));
        assertTrue(response.getAnswer().contains("宁德时代"));
        verify(portfolioService).detail(10L);
    }

    @Test
    void answersTodayPnlFromDefaultPortfolio() {
        PortfolioSummaryResp defaultPortfolio = PortfolioSummaryResp.builder()
                .id(1L).name("我的持仓").isDefault(true).positionCount(2)
                .todayPnl(new BigDecimal("-1280.50"))
                .todayPct(new BigDecimal("-1.36"))
                .quoteTime(LocalDateTime.of(2026, 8, 13, 14, 55))
                .updateTime(LocalDateTime.of(2026, 8, 13, 14, 55))
                .build();
        when(portfolioService.listPortfolios(false)).thenReturn(List.of(defaultPortfolio));
        when(portfolioService.detail(1L)).thenReturn(defaultPortfolio);

        BotAskResp response = service.ask(request("我今天亏多少"));

        assertEquals("PORTFOLIO_TODAY_PNL", response.getIntent());
        assertTrue(response.getAnswer().contains("今日亏损：1280.50 元"));
        assertTrue(response.getAnswer().contains("今日涨跌幅：-1.36%"));
        assertTrue(response.getAnswer().contains("行情最早时间：2026-08-13T14:55"));
        verify(portfolioService).detail(1L);
    }

    @Test
    void explainsWhenDefaultPortfolioHasNoPositionsForTodayPnlQuestion() {
        PortfolioSummaryResp defaultPortfolio = PortfolioSummaryResp.builder()
                .id(1L).name("我的持仓").isDefault(true).positionCount(0)
                .build();
        when(portfolioService.listPortfolios(false)).thenReturn(List.of(defaultPortfolio));
        when(portfolioService.detail(1L)).thenReturn(defaultPortfolio);

        BotAskResp response = service.ask(request("今天赚了多少"));

        assertEquals("PORTFOLIO_TODAY_PNL", response.getIntent());
        assertTrue(response.getAnswer().contains("默认组合暂无持仓"));
        assertEquals("YELLOW", response.getDataLevel());
    }

    @Test
    void includesHoldingAdviceAndQuoteTimeForNamedPortfolioAdviceQuestion() {
        PortfolioSummaryResp summary = PortfolioSummaryResp.builder()
                .id(9L).name("疯锅").positionCount(1).totalEquity(new BigDecimal("100000"))
                .cashBalance(BigDecimal.ZERO).totalPnl(new BigDecimal("-5000"))
                .quoteTime(LocalDateTime.of(2026, 8, 13, 15, 5))
                .updateTime(LocalDateTime.of(2026, 8, 1, 10, 0))
                .missingQuoteCount(0)
                .holdings(List.of(com.awe.apex.quant.domain.entity.PortfolioHolding.builder()
                        .code("000063").name("中兴通讯").verdict("持有不加仓")
                        .advice("反弹优先减仓").weightPct(new BigDecimal("35"))
                        .stopLoss(new BigDecimal("32.50")).build()))
                .build();
        when(portfolioService.listPortfolios(false)).thenReturn(List.of(summary));
        when(portfolioService.detail(9L)).thenReturn(summary);

        BotAskResp response = service.ask(request("针对疯锅的持仓，你有什么投资建议？"));

        assertEquals("PORTFOLIO_SUMMARY", response.getIntent());
        assertTrue(response.getAnswer().contains("单票建议"));
        assertTrue(response.getAnswer().contains("反弹优先减仓"));
        assertTrue(response.getAnswer().contains("行情最早时间：2026-08-13T15:05"));
        assertEquals("2026-08-13T15:05", response.getDataAsOf());
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
