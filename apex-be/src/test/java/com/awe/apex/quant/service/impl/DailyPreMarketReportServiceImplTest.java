package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.ai.KimiChatClient;
import com.awe.apex.quant.bot.config.ApexBotProperties;
import com.awe.apex.quant.cache.RedisCacheService;
import com.awe.apex.common.util.SpringUtils;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.dto.DailyPreMarketReportResp;
import com.awe.apex.quant.domain.dto.DashboardHomeResp;
import com.awe.apex.quant.domain.dto.MarketHotThemeItem;
import com.awe.apex.quant.domain.dto.MorningBriefingResp;
import com.awe.apex.quant.domain.dto.NewsPulseResp;
import com.awe.apex.quant.domain.dto.OvernightMarketQuote;
import com.awe.apex.quant.domain.dto.PortfolioSummaryResp;
import com.awe.apex.quant.domain.dto.PreMarketEventImpactResp;
import com.awe.apex.quant.domain.entity.PortfolioHolding;
import com.awe.apex.quant.service.IDashboardService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyPreMarketReportServiceImplTest {

    private final ApexUserContext userContext = mock(ApexUserContext.class);
    private final IDashboardService dashboardService = mock(IDashboardService.class);
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
        assertEquals(1, report.getPortfolioCount());
        assertEquals(2, report.getHoldingCount());
        assertTrue(report.getContent().startsWith("今日投资机会｜"));
        assertTrue(report.getContent().contains("01｜市场状态"));
        assertTrue(report.getContent().contains("02｜资金风格"));
        assertTrue(report.getContent().contains("上涨 2800 / 下跌 2200"));
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

        assertTrue(report.getContent().contains("核心观点：今日先交易“英伟达发布最新季度财报”"));
        assertTrue(report.getContent().contains("1. 算力｜催化：英伟达发布最新季度财报"));
        assertFalse(report.getContent().contains("机器人"));
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
}
