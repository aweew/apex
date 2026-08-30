package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.ai.KimiChatClient;
import com.awe.apex.quant.bot.config.ApexBotProperties;
import com.awe.apex.quant.cache.RedisCacheService;
import com.awe.apex.common.util.SpringUtils;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.dto.DailyPreMarketReportResp;
import com.awe.apex.quant.domain.dto.DecisionItemResp;
import com.awe.apex.quant.domain.dto.DecisionTodayResp;
import com.awe.apex.quant.domain.dto.DashboardHomeResp;
import com.awe.apex.quant.domain.dto.MarketHotThemeItem;
import com.awe.apex.quant.domain.dto.MorningBriefingResp;
import com.awe.apex.quant.domain.dto.NewsPulseResp;
import com.awe.apex.quant.domain.dto.ObservePoolResp;
import com.awe.apex.quant.domain.dto.OvernightMarketQuote;
import com.awe.apex.quant.domain.dto.PortfolioSummaryResp;
import com.awe.apex.quant.domain.dto.PreMarketEventImpactResp;
import com.awe.apex.quant.domain.entity.PortfolioHolding;
import com.awe.apex.quant.service.IDashboardService;
import com.awe.apex.quant.service.IDecisionService;
import com.awe.apex.quant.service.IPortfolioService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyPreMarketReportServiceImplTest {

    private final ApexUserContext userContext = mock(ApexUserContext.class);
    private final IDashboardService dashboardService = mock(IDashboardService.class);
    private final IDecisionService decisionService = mock(IDecisionService.class);
    private final IPortfolioService portfolioService = mock(IPortfolioService.class);
    private final KimiChatClient kimiChatClient = mock(KimiChatClient.class);
    private final RedisCacheService redisCacheService = mock(RedisCacheService.class);
    private final DailyPreMarketReportServiceImpl service = new DailyPreMarketReportServiceImpl();
    private ApplicationContext originalApplicationContext;

    @BeforeEach
    void setUp() {
        originalApplicationContext = SpringUtils.getApplicationContext();
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBean(ObjectMapper.class)).thenReturn(new ObjectMapper().findAndRegisterModules());
        new SpringUtils().setApplicationContext(applicationContext);
        ReflectionTestUtils.setField(service, "userContext", userContext);
        ReflectionTestUtils.setField(service, "dashboardService", dashboardService);
        ReflectionTestUtils.setField(service, "decisionService", decisionService);
        ReflectionTestUtils.setField(service, "portfolioService", portfolioService);
        ReflectionTestUtils.setField(service, "kimiChatClient", kimiChatClient);
        ReflectionTestUtils.setField(service, "redisCacheService", redisCacheService);
        ReflectionTestUtils.setField(service, "botProperties", new ApexBotProperties());
    }

    @AfterEach
    void tearDown() {
        new SpringUtils().setApplicationContext(originalApplicationContext);
    }

    @Test
    void returnsCompactRuleReportWithoutMissingPlaceholdersOrOrdinaryHoldings() {
        LocalDate tradeDate = LocalDate.of(2026, 8, 26);
        DashboardHomeResp.MarketBlock market = DashboardHomeResp.MarketBlock.builder()
                .asOf(LocalDate.of(2026, 8, 25))
                .stance("均衡")
                .stanceReason("指数震荡，个股结构分化")
                .positionAdvice("等待主线量价确认")
                .dataLevel("YELLOW")
                .volumeLabel("缩量 -1.21%")
                .indexLines(List.of("上证指数 3888.00 +0.30%"))
                .indexVolume(new BigDecimal("1800000000000"))
                .indexVolumeText("成交额 1.8 万亿元")
                .breadthUp(2800)
                .breadthDown(2200)
                .breadthFlat(100)
                .limitUpCount(65)
                .limitDownCount(8)
                .hotThemes(List.of("半导体", "机器人"))
                .hotThemeItems(List.of(MarketHotThemeItem.builder()
                        .name("机器人")
                        .pctChg(new BigDecimal("2.10"))
                        .build()))
                .indexes(List.of())
                .build();
        MorningBriefingResp morning = MorningBriefingResp.builder()
                .tradeDate(tradeDate)
                .generatedAt(LocalDateTime.of(2026, 8, 26, 6, 40))
                .dataLevel("YELLOW")
                .indexQuotes(List.of())
                .asiaQuotes(List.of())
                .externalMarketItems(List.of())
                .summary("外围数据不完整，等待开盘确认")
                .build();
        DashboardHomeResp dashboard = DashboardHomeResp.builder()
                .market(market)
                .morningBriefing(morning)
                .observeAlerts(List.of())
                .build();
        PortfolioSummaryResp portfolioItem = PortfolioSummaryResp.builder()
                .id(11L)
                .name("我的持仓")
                .editable(true)
                .build();
        PortfolioSummaryResp sharedPortfolio = PortfolioSummaryResp.builder()
                .id(12L)
                .name("其他用户组合")
                .editable(false)
                .build();
        PortfolioHolding holding = PortfolioHolding.builder()
                .code("600519")
                .name("贵州茅台")
                .quantity(100)
                .costPrice(new BigDecimal("1500"))
                .marketPrice(new BigDecimal("1520"))
                .quoteTime(LocalDateTime.of(2026, 8, 25, 15, 0))
                .weightPct(new BigDecimal("25"))
                .build();
        PortfolioHolding ordinaryHolding = PortfolioHolding.builder()
                .code("000001")
                .name("平安银行")
                .quantity(100)
                .costPrice(new BigDecimal("10"))
                .marketPrice(new BigDecimal("10.10"))
                .quoteTime(LocalDateTime.of(2026, 8, 25, 15, 0))
                .weightPct(new BigDecimal("5"))
                .pctChg(new BigDecimal("0.30"))
                .build();
        PortfolioSummaryResp portfolio = PortfolioSummaryResp.builder()
                .id(11L)
                .name("我的持仓")
                .positionCount(2)
                .missingQuoteCount(0)
                .holdings(List.of(holding, ordinaryHolding))
                .build();
        when(userContext.currentUserId()).thenReturn(7L);
        when(redisCacheService.get(anyString(), eq(DailyPreMarketReportResp.class))).thenReturn(null);
        when(dashboardService.home(null, "我的自选", false)).thenReturn(dashboard);
        when(portfolioService.listPortfolios(false)).thenReturn(List.of(portfolioItem, sharedPortfolio));
        when(portfolioService.detail(11L)).thenReturn(portfolio);
        when(kimiChatClient.available()).thenReturn(false);

        DailyPreMarketReportResp report = service.latest(false);

        assertEquals("RULE", report.getReportSource());
        assertEquals("偏强", report.getMarketStatus());
        assertEquals(62, report.getSentimentScore());
        assertEquals(1, report.getPortfolioCount());
        assertEquals(2, report.getHoldingCount());
        assertTrue(report.getContent().startsWith("今日投资机会｜"));
        assertTrue(report.getContent().contains("01｜市场状态"));
        assertTrue(report.getContent().contains("02｜资金风格"));
        assertTrue(report.getContent().contains("上涨 2800 / 下跌 2200"));
        assertTrue(report.getContent().contains("情绪 62 / 100（偏强）"));
        assertFalse(report.getContent().contains("状态：均衡"));
        assertTrue(report.getContent().contains("缩量 -1.21%"));
        assertTrue(report.getContent().contains("04｜持仓应对"));
        assertTrue(report.getContent().contains("贵州茅台"));
        assertFalse(report.getContent().contains("平安银行"));
        assertFalse(report.getContent().contains("关键变量"));
        assertFalse(report.getContent().contains("S 级"));
        assertFalse(report.getContent().contains("数据暂缺"));
        assertFalse(report.getContent().contains("未获取"));
        assertFalse(report.getContent().contains("观察不等于推荐买入"));
        assertFalse(report.getContent().contains("观察池"));
        assertTrue(report.getContent().contains("05｜开盘剧本"));
        assertTrue(report.getContent().length() < 3500);
        verify(portfolioService, never()).detail(12L);
        verify(redisCacheService).put(anyString(), any(DailyPreMarketReportResp.class), any());
    }

    @Test
    void recommendsSpecificStocksWithOpinionEvidenceTriggerAndInvalidation() {
        LocalDate tradeDate = LocalDate.of(2026, 8, 31);
        DashboardHomeResp.HomeActionItem topBuy = DashboardHomeResp.HomeActionItem.builder()
                .code("600519")
                .name("贵州茅台")
                .action("BUY")
                .strategyId("S2")
                .score(new BigDecimal("86"))
                .suggestedWeight(new BigDecimal("0.10"))
                .mainlineMatch(true)
                .mainlineName("白酒")
                .valuationLabel("估值偏低")
                .executableHint(true)
                .reason("盈利质量稳定，回调结构满足策略条件")
                .linkHint("主线、估值与回调信号形成共振")
                .exitRule("收盘跌破 1450 元离场")
                .build();
        DashboardHomeResp dashboard = DashboardHomeResp.builder()
                .market(DashboardHomeResp.MarketBlock.builder()
                        .asOf(tradeDate.minusDays(3))
                        .dataLevel("GREEN")
                        .volumeTrend("缩量")
                        .breadthUp(2800)
                        .breadthDown(2200)
                        .limitUpCount(60)
                        .limitDownCount(8)
                        .hotThemes(List.of("白酒"))
                        .build())
                .morningBriefing(MorningBriefingResp.builder()
                        .tradeDate(tradeDate)
                        .dataLevel("GREEN")
                        .build())
                .decision(DashboardHomeResp.DecisionBlock.builder()
                        .actionDate(tradeDate)
                        .hasToday(true)
                        .topBuys(List.of(topBuy))
                        .build())
                .observeAlerts(List.of())
                .build();
        when(userContext.currentUserId()).thenReturn(7L);
        when(redisCacheService.get(anyString(), eq(DailyPreMarketReportResp.class))).thenReturn(null);
        when(dashboardService.home(null, "我的自选", false)).thenReturn(dashboard);
        when(portfolioService.listPortfolios(false)).thenReturn(List.of());
        when(kimiChatClient.available()).thenReturn(false);

        DailyPreMarketReportResp report = service.latest(false);

        assertTrue(report.getContent().startsWith("今日投资机会｜缩量轮动 · 强势延续"));
        assertFalse(report.getContent().lines().findFirst().orElse("").contains("贵州茅台"));
        assertTrue(report.getContent().contains("首选个股：贵州茅台 600519"));
        assertTrue(report.getContent().contains("03｜个股推荐"));
        assertTrue(report.getContent().contains("1. 贵州茅台 600519｜级别：首选"));
        assertTrue(report.getContent().contains("观点："));
        assertTrue(report.getContent().contains("依据："));
        assertTrue(report.getContent().contains("触发："));
        assertTrue(report.getContent().contains("失效：收盘跌破 1450 元离场"));
        assertTrue(report.getContent().contains("仓位：10%"));
    }

    @Test
    void onlyUsesFreshDecisionObserveCandidatesAsStockPickSupplements() {
        LocalDate tradeDate = LocalDate.of(2026, 8, 31);
        DashboardHomeResp.HomeActionItem topBuy = DashboardHomeResp.HomeActionItem.builder()
                .code("600519")
                .name("贵州茅台")
                .action("BUY")
                .reason("当日决策首选")
                .build();
        ObservePoolResp freshCandidate = ObservePoolResp.builder()
                .code("300750")
                .name("宁德时代")
                .side("BUY")
                .status("TRIGGERED")
                .reason("回踩关键支撑后重新转强")
                .triggerLabel("站上 520 元")
                .stopLoss(new BigDecimal("498"))
                .setupStyle("回调买入")
                .decisionUpdatedAt(tradeDate.atTime(8, 10))
                .build();
        ObservePoolResp staleCandidate = ObservePoolResp.builder()
                .code("000001")
                .name("平安银行")
                .side("BUY")
                .status("TRIGGERED")
                .reason("旧决策遗留观察")
                .decisionUpdatedAt(tradeDate.minusDays(3).atTime(15, 0))
                .build();
        DashboardHomeResp dashboard = DashboardHomeResp.builder()
                .market(DashboardHomeResp.MarketBlock.builder()
                        .asOf(tradeDate.minusDays(3))
                        .dataLevel("GREEN")
                        .build())
                .morningBriefing(MorningBriefingResp.builder()
                        .tradeDate(tradeDate)
                        .dataLevel("GREEN")
                        .build())
                .decision(DashboardHomeResp.DecisionBlock.builder()
                        .actionDate(tradeDate)
                        .hasToday(true)
                        .topBuys(List.of(topBuy))
                        .build())
                .observeAlerts(List.of(freshCandidate, staleCandidate))
                .build();
        when(userContext.currentUserId()).thenReturn(7L);
        when(redisCacheService.get(anyString(), eq(DailyPreMarketReportResp.class))).thenReturn(null);
        when(dashboardService.home(null, "我的自选", false)).thenReturn(dashboard);
        when(portfolioService.listPortfolios(false)).thenReturn(List.of());
        when(kimiChatClient.available()).thenReturn(false);

        DailyPreMarketReportResp report = service.latest(false);

        assertTrue(report.getContent().contains("2. 宁德时代 300750｜级别：触发"));
        assertTrue(report.getContent().contains("依据：回踩关键支撑后重新转强"));
        assertTrue(report.getContent().contains("触发：9:45 前确认：站上 520 元"));
        assertTrue(report.getContent().contains("失效：跌破止损价 498"));
        assertFalse(report.getContent().contains("平安银行"));
    }

    @Test
    void usesLastCompletedTradingDayDecisionAsWeekendObservationPicks() {
        LocalDate tradeDate = LocalDate.of(2026, 8, 31);
        LocalDate marketDataDate = LocalDate.of(2026, 8, 28);
        DashboardHomeResp dashboard = DashboardHomeResp.builder()
                .market(DashboardHomeResp.MarketBlock.builder()
                        .asOf(marketDataDate)
                        .dataLevel("GREEN")
                        .volumeTrend("缩量")
                        .breadthUp(2892)
                        .breadthDown(2325)
                        .limitUpCount(83)
                        .limitDownCount(3)
                        .build())
                .morningBriefing(MorningBriefingResp.builder()
                        .tradeDate(tradeDate)
                        .dataLevel("GREEN")
                        .build())
                .decision(DashboardHomeResp.DecisionBlock.builder()
                        .actionDate(LocalDate.of(2026, 8, 30))
                        .hasToday(false)
                        .topBuys(List.of())
                        .build())
                .observeAlerts(List.of())
                .build();
        DecisionItemResp previousBuy = DecisionItemResp.builder()
                .actionDate(marketDataDate)
                .code("688575")
                .name("亚辉龙")
                .action("BUY")
                .strategyId("S3")
                .score(new BigDecimal("92.81"))
                .suggestedWeight(BigDecimal.ZERO)
                .valuationLabel("数据不足")
                .reason("20 日新高且量比大于 1.5，两个策略共振")
                .exitRule("跌破突破日低点离场")
                .executableHint(false)
                .build();
        when(userContext.currentUserId()).thenReturn(7L);
        when(redisCacheService.get(anyString(), eq(DailyPreMarketReportResp.class))).thenReturn(null);
        when(dashboardService.home(null, "我的自选", false)).thenReturn(dashboard);
        when(decisionService.today(marketDataDate, "我的自选")).thenReturn(DecisionTodayResp.builder()
                .actionDate(marketDataDate)
                .generated(true)
                .buys(List.of(previousBuy))
                .build());
        when(portfolioService.listPortfolios(false)).thenReturn(List.of());
        when(kimiChatClient.available()).thenReturn(false);

        DailyPreMarketReportResp report = service.latest(false);

        assertTrue(report.getContent().startsWith("今日投资机会｜缩量轮动 · 强势延续"));
        assertFalse(report.getContent().lines().findFirst().orElse("").contains("亚辉龙"));
        assertTrue(report.getContent().contains("1. 亚辉龙 688575｜级别：观察首选"));
        assertTrue(report.getContent().contains("依据：决策日 2026-08-28，20 日新高且量比大于 1.5，两个策略共振"));
        assertTrue(report.getContent().contains("核心观点：情绪偏强但量能收缩，预计以结构性轮动为主；高开不追，放量承接才算主线确认。"));
        String coreView = report.getContent().lines()
                .filter(contentLine -> contentLine.startsWith("核心观点："))
                .findFirst().orElse("");
        assertFalse(coreView.contains("亚辉龙"));
        assertFalse(coreView.contains("688575"));
        assertTrue(report.getContent().contains("触发：9:45 后仍不破开盘价且持续强于沪深 300，再纳入开仓候选"));
        assertFalse(report.getContent().contains("盘前观察排序"));
        assertFalse(report.getContent().contains("开仓门禁"));
        assertFalse(report.getContent().contains("决策评分"));
        assertFalse(report.getContent().contains("仓位：0%"));
        assertFalse(report.getContent().contains("数据不足提供安全边际"));
        assertFalse(report.getContent().contains("依据：决策日 2026-08-28，数据不足"));
        verify(decisionService).today(marketDataDate, "我的自选");
    }

    @Test
    void acceptsAiReportWhenEveryStockPickFactMatchesTheNormalizedCandidate() {
        LocalDate tradeDate = LocalDate.of(2026, 8, 31);
        DashboardHomeResp.HomeActionItem topBuy = DashboardHomeResp.HomeActionItem.builder()
                .code("600519")
                .name("贵州茅台")
                .action("BUY")
                .score(new BigDecimal("86"))
                .executableHint(true)
                .reason("回调结构满足策略条件")
                .exitRule("收盘跌破 1450 元离场")
                .build();
        DashboardHomeResp dashboard = DashboardHomeResp.builder()
                .market(DashboardHomeResp.MarketBlock.builder()
                        .asOf(tradeDate.minusDays(3))
                        .dataLevel("GREEN")
                        .build())
                .morningBriefing(MorningBriefingResp.builder()
                        .tradeDate(tradeDate)
                        .dataLevel("GREEN")
                        .build())
                .decision(DashboardHomeResp.DecisionBlock.builder()
                        .actionDate(tradeDate)
                        .hasToday(true)
                        .topBuys(List.of(topBuy))
                        .build())
                .build();
        String validAiReport = "今日投资机会｜回踩结构 · 主线共振\n"
                + "日期：2026-08-31\n"
                + "核心观点：市场仍在轮动，今天不追一致性高开，只做放量承接。\n"
                + "首选个股：贵州茅台 600519。\n"
                + "最大风险：贵州茅台未满足执行条件。\n"
                + "01｜市场状态\n盘面风险偏好中性。\n"
                + "03｜个股推荐\n"
                + "1. 贵州茅台 600519｜级别：首选；观点：当日买入候选中优先级靠前，只做确认后的强势延续，不提前赌开盘方向；"
                + "依据：回调结构满足策略条件，综合评分 86；"
                + "触发：9:45 后仍不破开盘价且持续强于沪深 300；"
                + "失效：收盘跌破 1450 元离场。\n"
                + "05｜开盘剧本\n"
                + "偏强｜上涨家数扩大。\n震荡｜指数横盘。\n转弱｜下跌家数扩大。";
        when(userContext.currentUserId()).thenReturn(7L);
        when(redisCacheService.get(anyString(), eq(DailyPreMarketReportResp.class))).thenReturn(null);
        when(dashboardService.home(null, "我的自选", false)).thenReturn(dashboard);
        when(portfolioService.listPortfolios(false)).thenReturn(List.of());
        when(kimiChatClient.available()).thenReturn(true);
        String stockCentricAiReport = validAiReport.replace(
                "核心观点：市场仍在轮动，今天不追一致性高开，只做放量承接。",
                "核心观点：贵州茅台（600519）是今日首选，确认后执行。");
        when(kimiChatClient.chat(anyString(), anyString(), eq(2600)))
                .thenReturn(validAiReport, stockCentricAiReport);

        DailyPreMarketReportResp report = service.latest(false);
        DailyPreMarketReportResp rejectedReport = service.latest(true);

        assertEquals("AI", report.getReportSource());
        assertEquals(validAiReport, report.getContent());
        assertEquals("RULE", rejectedReport.getReportSource());
        String rejectedCoreView = rejectedReport.getContent().lines()
                .filter(contentLine -> contentLine.startsWith("核心观点："))
                .findFirst().orElse("");
        assertFalse(rejectedCoreView.contains("贵州茅台"));
        assertFalse(rejectedCoreView.contains("600519"));
    }

    @Test
    void fallsBackWhenAiInventsAStockOutsideTheNormalizedCandidates() {
        LocalDate tradeDate = LocalDate.of(2026, 8, 31);
        DashboardHomeResp.HomeActionItem topBuy = DashboardHomeResp.HomeActionItem.builder()
                .code("600519")
                .name("贵州茅台")
                .action("BUY")
                .score(new BigDecimal("86"))
                .executableHint(true)
                .reason("回调结构满足策略条件")
                .exitRule("收盘跌破 1450 元离场")
                .build();
        DashboardHomeResp dashboard = DashboardHomeResp.builder()
                .market(DashboardHomeResp.MarketBlock.builder()
                        .asOf(tradeDate.minusDays(3))
                        .dataLevel("GREEN")
                        .build())
                .morningBriefing(MorningBriefingResp.builder()
                        .tradeDate(tradeDate)
                        .dataLevel("GREEN")
                        .build())
                .decision(DashboardHomeResp.DecisionBlock.builder()
                        .actionDate(tradeDate)
                        .hasToday(true)
                        .topBuys(List.of(topBuy))
                        .build())
                .build();
        String inventedStockReport = "今日个股观点｜平安银行优先\n"
                + "日期：2026-08-31\n"
                + "核心观点：今日首选平安银行。\n"
                + "首选个股：平安银行 000001。\n"
                + "最大风险：跌破开盘价。\n"
                + "01｜市场状态\n盘面震荡。\n"
                + "03｜个股推荐\n"
                + "1. 平安银行 000001｜级别：首选；观点：银行主线占优；依据：综合评分 90；"
                + "触发：9:45 前强于沪深 300；失效：跌破开盘价。\n"
                + "05｜开盘剧本\n"
                + "偏强｜上涨家数扩大。\n震荡｜指数横盘。\n转弱｜下跌家数扩大。";
        when(userContext.currentUserId()).thenReturn(7L);
        when(redisCacheService.get(anyString(), eq(DailyPreMarketReportResp.class))).thenReturn(null);
        when(dashboardService.home(null, "我的自选", false)).thenReturn(dashboard);
        when(portfolioService.listPortfolios(false)).thenReturn(List.of());
        when(kimiChatClient.available()).thenReturn(true);
        when(kimiChatClient.chat(anyString(), anyString(), eq(2600))).thenReturn(inventedStockReport);

        DailyPreMarketReportResp report = service.latest(false);

        assertEquals("RULE", report.getReportSource());
        assertTrue(report.getContent().contains("贵州茅台 600519"));
        assertFalse(report.getContent().contains("平安银行 000001"));
    }

    @Test
    void reportsDirectionChangesAgainstPreviousTradingReport() {
        DailyPreMarketReportResp previousReport = DailyPreMarketReportResp.builder()
                .tradeDate(LocalDate.of(2026, 8, 28))
                .content("今日投资机会｜旧报告\n03｜投资机会\n"
                        + "1. 消费｜催化：旧催化；确认：旧确认；失效：旧失效。\n"
                        + "2. 算力｜催化：旧催化；确认：旧确认；失效：旧失效。")
                .build();
        DashboardHomeResp dashboard = DashboardHomeResp.builder()
                .market(DashboardHomeResp.MarketBlock.builder()
                        .asOf(LocalDate.of(2026, 8, 28))
                        .stance("均衡")
                        .dataLevel("GREEN")
                        .breadthUp(2600)
                        .breadthDown(2400)
                        .limitUpCount(60)
                        .limitDownCount(10)
                        .hotThemes(List.of("算力", "地产"))
                        .hotThemeItems(List.of(
                                MarketHotThemeItem.builder().name("算力").pctChg(new BigDecimal("1.20")).build(),
                                MarketHotThemeItem.builder().name("地产").pctChg(new BigDecimal("0.80")).build()))
                        .build())
                .morningBriefing(MorningBriefingResp.builder()
                        .tradeDate(LocalDate.of(2026, 8, 31))
                        .dataLevel("GREEN")
                        .build())
                .build();
        when(userContext.currentUserId()).thenReturn(7L);
        when(redisCacheService.get(anyString(), eq(DailyPreMarketReportResp.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, String.class).endsWith(":2026-08-28")
                        ? previousReport : null);
        when(dashboardService.home(null, "我的自选", false)).thenReturn(dashboard);
        when(portfolioService.listPortfolios(false)).thenReturn(List.of());
        when(kimiChatClient.available()).thenReturn(false);

        DailyPreMarketReportResp report = service.latest(true);

        assertTrue(report.getFocusChanges().contains("新增方向：地产"));
        assertTrue(report.getFocusChanges().contains("延续方向：算力"));
        assertTrue(report.getFocusChanges().contains("降级方向：消费"));
        assertTrue(report.getContent().contains("9:45 前"));
        assertTrue(report.getContent().contains("较昨日同期放大 20%"));
        verify(redisCacheService).put(anyString(), eq(report), any());
    }

    @Test
    void labelsStockRecommendationChangesExplicitly() {
        DailyPreMarketReportResp previousReport = DailyPreMarketReportResp.builder()
                .tradeDate(LocalDate.of(2026, 8, 28))
                .content("今日个股观点｜旧报告\n03｜个股推荐\n"
                        + "1. 宁德时代 300750｜级别：首选；观点：旧观点；依据：旧依据；触发：旧触发；失效：旧失效。\n"
                        + "2. 平安银行 000001｜级别：备选；观点：旧观点；依据：旧依据；触发：旧触发；失效：旧失效。")
                .build();
        String currentContent = "今日个股观点｜新报告\n03｜个股推荐\n"
                + "1. 中际旭创 300308｜级别：首选；观点：新观点；依据：新依据；触发：新触发；失效：新失效。\n"
                + "2. 宁德时代 300750｜级别：备选；观点：延续观点；依据：延续依据；触发：延续触发；失效：延续失效。";

        List<String> focusChanges = ReflectionTestUtils.invokeMethod(service, "resolveFocusChanges",
                previousReport, currentContent, LocalDate.of(2026, 8, 31));

        assertEquals(List.of(
                "新增推荐：中际旭创 300308",
                "延续推荐：宁德时代 300750",
                "退出推荐：平安银行 000001"), focusChanges);
    }

    @Test
    void reportsMissingA50GoldenDragonAndChinaConceptQuotesSeparately() {
        LocalDate tradeDate = LocalDate.of(2026, 8, 28);
        MorningBriefingResp morning = MorningBriefingResp.builder()
                .tradeDate(tradeDate)
                .dataLevel("YELLOW")
                .indexQuotes(List.of())
                .asiaQuotes(List.of())
                .externalMarketItems(List.of())
                .chinaConceptQuotes(List.of(OvernightMarketQuote.builder()
                        .symbol("usBABA")
                        .name("阿里巴巴")
                        .pctChg(new BigDecimal("-2.94"))
                        .build()))
                .build();
        DashboardHomeResp dashboard = DashboardHomeResp.builder()
                .market(DashboardHomeResp.MarketBlock.builder()
                        .asOf(tradeDate.minusDays(1))
                        .dataLevel("YELLOW")
                        .build())
                .morningBriefing(morning)
                .build();
        when(userContext.currentUserId()).thenReturn(7L);
        when(redisCacheService.get(anyString(), eq(DailyPreMarketReportResp.class))).thenReturn(null);
        when(dashboardService.home(null, "我的自选", false)).thenReturn(dashboard);
        when(portfolioService.listPortfolios(false)).thenReturn(List.of());
        when(kimiChatClient.available()).thenReturn(false);

        DailyPreMarketReportResp report = service.latest(false);

        assertTrue(report.getMissingData().contains("富时 A50 期指"));
        assertTrue(report.getMissingData().contains("纳斯达克中国金龙指数"));
        assertTrue(report.getMissingData().contains("中概股代表行情"));
    }

    @Test
    void putsNvidiaEarningsBeforeYesterdayHotTheme() {
        LocalDate tradeDate = LocalDate.of(2026, 8, 27);
        PreMarketEventImpactResp nvidiaEarnings = PreMarketEventImpactResp.builder()
                .eventType("EARNINGS")
                .impactScope("THEME")
                .direction("待验证")
                .priority(5)
                .title("英伟达发布最新季度财报")
                .themes(List.of("算力", "光模块", "半导体"))
                .impactExplanation("业绩指引与盘后价格反应会重定价全球 AI 风险偏好。")
                .verificationStatus("媒体报道待核验")
                .build();
        MorningBriefingResp morning = MorningBriefingResp.builder()
                .tradeDate(tradeDate)
                .dataLevel("GREEN")
                .newsPulse(NewsPulseResp.builder().eventImpacts(List.of(nvidiaEarnings)).build())
                .build();
        DashboardHomeResp.MarketBlock market = DashboardHomeResp.MarketBlock.builder()
                .asOf(tradeDate.minusDays(1))
                .stance("均衡")
                .dataLevel("GREEN")
                .hotThemeItems(List.of(MarketHotThemeItem.builder()
                        .name("机器人")
                        .pctChg(new BigDecimal("3.20"))
                        .build()))
                .build();
        DashboardHomeResp dashboard = DashboardHomeResp.builder()
                .market(market)
                .morningBriefing(morning)
                .build();
        when(userContext.currentUserId()).thenReturn(7L);
        when(redisCacheService.get(anyString(), eq(DailyPreMarketReportResp.class))).thenReturn(null);
        when(dashboardService.home(null, "我的自选", false)).thenReturn(dashboard);
        when(portfolioService.listPortfolios(false)).thenReturn(List.of());
        when(kimiChatClient.available()).thenReturn(false);

        DailyPreMarketReportResp report = service.latest(false);

        assertTrue(report.getContent().contains(
                "核心观点：算力受隔夜事件催化，但市场仍按结构性机会定价；只有板块成交与个股扩散共振，催化才算主线。"));
        String coreView = report.getContent().lines()
                .filter(contentLine -> contentLine.startsWith("核心观点："))
                .findFirst().orElse("");
        assertFalse(coreView.contains("英伟达"));
        assertTrue(report.getContent().contains("1. 算力｜催化：英伟达发布最新季度财报"));
        String opportunitySection = report.getContent().substring(
                report.getContent().indexOf("03｜投资机会"),
                report.getContent().indexOf("05｜开盘剧本"));
        assertEquals(opportunitySection.indexOf("英伟达发布最新季度财报"),
                opportunitySection.lastIndexOf("英伟达发布最新季度财报"));
        assertFalse(report.getContent().contains("机器人"));
    }

    @Test
    void rejectsUnrelatedStockAnnouncementAsCoreView() {
        LocalDate tradeDate = LocalDate.of(2026, 8, 31);
        String announcementTitle = "凌霄泵业：财务总监陆凤娟拟减持不超过0.07%股份";
        PreMarketEventImpactResp unrelatedAnnouncement = PreMarketEventImpactResp.builder()
                .eventType("ANNOUNCEMENT")
                .impactScope("STOCK")
                .direction("利空")
                .priority(5)
                .title(announcementTitle)
                .relatedCodes(List.of("002884"))
                .officialSource(true)
                .build();
        MorningBriefingResp morning = MorningBriefingResp.builder()
                .tradeDate(tradeDate)
                .dataLevel("GREEN")
                .newsPulse(NewsPulseResp.builder().eventImpacts(List.of(unrelatedAnnouncement)).build())
                .build();
        DashboardHomeResp dashboard = DashboardHomeResp.builder()
                .market(DashboardHomeResp.MarketBlock.builder()
                        .asOf(LocalDate.of(2026, 8, 28))
                        .stance("均衡")
                        .dataLevel("GREEN")
                        .hotThemeItems(List.of(MarketHotThemeItem.builder().name("科技巨头").build()))
                        .build())
                .morningBriefing(morning)
                .build();
        String invalidAiReport = "今日投资机会｜凌霄泵业减持影响定价\n"
                + "日期：2026-08-31\n"
                + "核心观点：今日先交易“" + announcementTitle + "”带来的预期变化。\n"
                + "最大风险：开盘走势与判断背离。\n"
                + "01｜市场状态\n盘面均衡。\n"
                + "03｜投资机会\n"
                + "1. 科技巨头｜催化：盘前信号居前；确认：9:45 前强于沪深 300；失效：跌破开盘价。\n"
                + "05｜开盘剧本\n"
                + "偏强｜上涨家数扩大。\n震荡｜指数横盘。\n转弱｜下跌家数扩大。";
        when(userContext.currentUserId()).thenReturn(7L);
        when(redisCacheService.get(anyString(), eq(DailyPreMarketReportResp.class))).thenReturn(null);
        when(dashboardService.home(null, "我的自选", false)).thenReturn(dashboard);
        when(portfolioService.listPortfolios(false)).thenReturn(List.of());
        when(kimiChatClient.available()).thenReturn(true);
        when(kimiChatClient.chat(anyString(), anyString(), eq(2600))).thenReturn(invalidAiReport);

        DailyPreMarketReportResp report = service.latest(false);

        assertEquals("RULE", report.getReportSource());
        assertFalse(report.getContent().contains(announcementTitle));
        assertFalse(report.getContent().contains("今日先交易“凌霄泵业"));
        verify(kimiChatClient).chat(anyString(), argThat(prompt -> !prompt.contains(announcementTitle)), eq(2600));
    }

    @Test
    void keepsHeldStockAnnouncementInTheReport() {
        LocalDate tradeDate = LocalDate.of(2026, 8, 31);
        String announcementTitle = "润泽科技发布重大合同公告";
        PreMarketEventImpactResp holdingAnnouncement = PreMarketEventImpactResp.builder()
                .eventType("ANNOUNCEMENT")
                .impactScope("STOCK")
                .direction("利好")
                .priority(4)
                .title(announcementTitle)
                .relatedCodes(List.of("300442"))
                .build();
        DashboardHomeResp dashboard = DashboardHomeResp.builder()
                .market(DashboardHomeResp.MarketBlock.builder()
                        .asOf(LocalDate.of(2026, 8, 28))
                        .stance("均衡")
                        .dataLevel("GREEN")
                        .build())
                .morningBriefing(MorningBriefingResp.builder()
                        .tradeDate(tradeDate)
                        .dataLevel("GREEN")
                        .newsPulse(NewsPulseResp.builder().eventImpacts(List.of(holdingAnnouncement)).build())
                        .build())
                .build();
        PortfolioSummaryResp portfolioItem = PortfolioSummaryResp.builder()
                .id(11L)
                .name("我的持仓")
                .editable(true)
                .build();
        PortfolioSummaryResp portfolio = PortfolioSummaryResp.builder()
                .id(11L)
                .name("我的持仓")
                .holdings(List.of(PortfolioHolding.builder()
                        .code("300442")
                        .name("润泽科技")
                        .build()))
                .build();
        when(userContext.currentUserId()).thenReturn(7L);
        when(redisCacheService.get(anyString(), eq(DailyPreMarketReportResp.class))).thenReturn(null);
        when(dashboardService.home(null, "我的自选", false)).thenReturn(dashboard);
        when(portfolioService.listPortfolios(false)).thenReturn(List.of(portfolioItem));
        when(portfolioService.detail(11L)).thenReturn(portfolio);
        when(kimiChatClient.available()).thenReturn(false);

        DailyPreMarketReportResp report = service.latest(false);

        String coreView = report.getContent().lines()
                .filter(contentLine -> contentLine.startsWith("核心观点："))
                .findFirst().orElse("");
        assertFalse(coreView.contains("润泽科技"));
        assertFalse(coreView.contains("300442"));
        assertFalse(coreView.contains(announcementTitle));
        assertTrue(report.getContent().contains(announcementTitle));
        assertTrue(report.getContent().contains("润泽科技 300442"));
    }

    @Test
    void fallsBackToRuleReportWhenAiUsesMissingDataPlaceholders() {
        LocalDate tradeDate = LocalDate.of(2026, 8, 26);
        DashboardHomeResp.MarketBlock market = DashboardHomeResp.MarketBlock.builder()
                .asOf(LocalDate.of(2026, 8, 25))
                .stance("均衡")
                .dataLevel("GREEN")
                .build();
        MorningBriefingResp morning = MorningBriefingResp.builder()
                .tradeDate(tradeDate)
                .dataLevel("GREEN")
                .build();
        DashboardHomeResp dashboard = DashboardHomeResp.builder()
                .market(market)
                .morningBriefing(morning)
                .build();
        PortfolioSummaryResp portfolioItem = PortfolioSummaryResp.builder()
                .id(11L)
                .name("我的持仓")
                .editable(true)
                .build();
        PortfolioHolding holding = PortfolioHolding.builder()
                .code("600519")
                .name("贵州茅台")
                .build();
        PortfolioSummaryResp portfolio = PortfolioSummaryResp.builder()
                .id(11L)
                .name("我的持仓")
                .holdings(List.of(holding))
                .build();
        String incompleteAiReport = "今日投资机会｜机器人修复\n"
                + "核心观点：均衡\n"
                + "01｜市场状态\n行业资金：数据暂缺\n"
                + "02｜资金风格\n外围市场未获取\n"
                + "03｜投资机会\n机器人\n"
                + "05｜开盘剧本\n偏强｜量价增强";
        when(userContext.currentUserId()).thenReturn(7L);
        when(redisCacheService.get(anyString(), eq(DailyPreMarketReportResp.class))).thenReturn(null);
        when(dashboardService.home(null, "我的自选", false)).thenReturn(dashboard);
        when(portfolioService.listPortfolios(false)).thenReturn(List.of(portfolioItem));
        when(portfolioService.detail(11L)).thenReturn(portfolio);
        when(kimiChatClient.available()).thenReturn(true);
        when(kimiChatClient.chat(anyString(), anyString(), eq(2600))).thenReturn(incompleteAiReport);

        DailyPreMarketReportResp report = service.latest(false);

        assertEquals("RULE", report.getReportSource());
        assertFalse(report.getContent().contains("数据暂缺"));
        assertFalse(report.getContent().contains("未获取"));
    }

    @Test
    void omitsCapitalStyleWhenOnlyWeakSentimentExistsWithoutDirectionEvidence() {
        LocalDate tradeDate = LocalDate.of(2026, 8, 28);
        DashboardHomeResp.MarketBlock market = DashboardHomeResp.MarketBlock.builder()
                .asOf(tradeDate.minusDays(1))
                .stance("偏弱")
                .stanceReason("下跌家数明显多于上涨家数")
                .dataLevel("YELLOW")
                .breadthUp(900)
                .breadthDown(4100)
                .limitUpCount(20)
                .limitDownCount(70)
                .build();
        MorningBriefingResp morning = MorningBriefingResp.builder()
                .tradeDate(tradeDate)
                .dataLevel("YELLOW")
                .build();
        DashboardHomeResp dashboard = DashboardHomeResp.builder()
                .market(market)
                .morningBriefing(morning)
                .build();
        when(userContext.currentUserId()).thenReturn(7L);
        when(redisCacheService.get(anyString(), eq(DailyPreMarketReportResp.class))).thenReturn(null);
        when(dashboardService.home(null, "我的自选", false)).thenReturn(dashboard);
        when(portfolioService.listPortfolios(false)).thenReturn(List.of());
        when(kimiChatClient.available()).thenReturn(false);

        DailyPreMarketReportResp report = service.latest(false);

        assertFalse(report.getContent().contains("02｜资金风格"));
        assertFalse(report.getContent().contains("风险偏好正在修复"));
    }

    @Test
    void fallsBackWhenAiOpportunityHasNoRiskOrValidationConditions() {
        LocalDate tradeDate = LocalDate.of(2026, 8, 28);
        PreMarketEventImpactResp event = PreMarketEventImpactResp.builder()
                .eventType("EARNINGS")
                .impactScope("THEME")
                .priority(5)
                .title("英伟达发布季度财报")
                .themes(List.of("算力"))
                .build();
        MorningBriefingResp morning = MorningBriefingResp.builder()
                .tradeDate(tradeDate)
                .dataLevel("GREEN")
                .newsPulse(NewsPulseResp.builder().eventImpacts(List.of(event)).build())
                .build();
        DashboardHomeResp dashboard = DashboardHomeResp.builder()
                .market(DashboardHomeResp.MarketBlock.builder()
                        .asOf(tradeDate.minusDays(1))
                        .stance("均衡")
                        .dataLevel("GREEN")
                        .build())
                .morningBriefing(morning)
                .build();
        String emptyOpinion = "今日投资机会｜算力获得催化\n"
                + "核心观点：算力方向今日占优。\n"
                + "01｜市场状态\n市场结构均衡。\n"
                + "03｜投资机会\n1. 算力｜英伟达财报催化。\n"
                + "05｜开盘剧本\n偏强｜算力上涨。";
        when(userContext.currentUserId()).thenReturn(7L);
        when(redisCacheService.get(anyString(), eq(DailyPreMarketReportResp.class))).thenReturn(null);
        when(dashboardService.home(null, "我的自选", false)).thenReturn(dashboard);
        when(portfolioService.listPortfolios(false)).thenReturn(List.of());
        when(kimiChatClient.available()).thenReturn(true);
        when(kimiChatClient.chat(anyString(), anyString(), eq(2600))).thenReturn(emptyOpinion);

        DailyPreMarketReportResp report = service.latest(false);

        assertEquals("RULE", report.getReportSource());
        assertTrue(report.getContent().contains("最大风险："));
        assertTrue(report.getContent().contains("确认："));
        assertTrue(report.getContent().contains("失效："));
    }

    @Test
    void fallsBackWhenAiRepeatsTheSameCatalystAcrossOpportunities() {
        LocalDate tradeDate = LocalDate.of(2026, 8, 28);
        PreMarketEventImpactResp event = PreMarketEventImpactResp.builder()
                .eventType("EARNINGS")
                .impactScope("THEME")
                .priority(5)
                .title("英伟达发布季度财报")
                .themes(List.of("算力", "半导体"))
                .build();
        MorningBriefingResp morning = MorningBriefingResp.builder()
                .tradeDate(tradeDate)
                .dataLevel("GREEN")
                .newsPulse(NewsPulseResp.builder().eventImpacts(List.of(event)).build())
                .build();
        DashboardHomeResp dashboard = DashboardHomeResp.builder()
                .market(DashboardHomeResp.MarketBlock.builder()
                        .asOf(tradeDate.minusDays(1))
                        .stance("均衡")
                        .dataLevel("GREEN")
                        .build())
                .morningBriefing(morning)
                .build();
        String duplicateCatalystReport = "今日投资机会｜算力获得业绩催化\n"
                + "核心观点：只做开盘确认后的强势方向。\n"
                + "最大风险：核心方向高开低走。\n"
                + "01｜市场状态\n状态：均衡。\n"
                + "03｜投资机会\n"
                + "1. 算力｜催化：英伟达发布季度财报；确认：算力板块放量；失效：核心股跌破开盘价。\n"
                + "2. 半导体｜催化：英伟达发布季度财报；确认：半导体板块放量；失效：核心股跌破开盘价。\n"
                + "05｜开盘剧本\n"
                + "偏强｜核心方向放量。\n"
                + "震荡｜指数缩量横盘。\n"
                + "转弱｜核心方向高开低走。";
        when(userContext.currentUserId()).thenReturn(7L);
        when(redisCacheService.get(anyString(), eq(DailyPreMarketReportResp.class))).thenReturn(null);
        when(dashboardService.home(null, "我的自选", false)).thenReturn(dashboard);
        when(portfolioService.listPortfolios(false)).thenReturn(List.of());
        when(kimiChatClient.available()).thenReturn(true);
        when(kimiChatClient.chat(anyString(), anyString(), eq(2600))).thenReturn(duplicateCatalystReport);

        DailyPreMarketReportResp report = service.latest(false);

        assertEquals("RULE", report.getReportSource());
        assertFalse(report.getContent().contains("2. 半导体｜催化：英伟达发布季度财报"));
    }
}
