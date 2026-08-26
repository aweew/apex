package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.ai.KimiChatClient;
import com.awe.apex.quant.bot.config.ApexBotProperties;
import com.awe.apex.quant.cache.RedisCacheService;
import com.awe.apex.common.util.SpringUtils;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.dto.DailyPreMarketReportResp;
import com.awe.apex.quant.domain.dto.DashboardHomeResp;
import com.awe.apex.quant.domain.dto.MorningBriefingResp;
import com.awe.apex.quant.domain.dto.PortfolioSummaryResp;
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
    void returnsCompleteRuleReportWhenAiIsUnavailable() {
        LocalDate tradeDate = LocalDate.of(2026, 8, 26);
        DashboardHomeResp.MarketBlock market = DashboardHomeResp.MarketBlock.builder()
                .asOf(LocalDate.of(2026, 8, 25))
                .stance("均衡")
                .stanceReason("指数震荡，个股结构分化")
                .positionAdvice("等待主线量价确认")
                .dataLevel("YELLOW")
                .indexLines(List.of("上证指数 3888.00 +0.30%"))
                .indexVolume(new BigDecimal("1800000000000"))
                .indexVolumeText("成交额 1.8 万亿元")
                .breadthUp(2800)
                .breadthDown(2200)
                .breadthFlat(100)
                .limitUpCount(65)
                .limitDownCount(8)
                .hotThemes(List.of("半导体", "机器人"))
                .hotThemeItems(List.of())
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
        PortfolioSummaryResp portfolio = PortfolioSummaryResp.builder()
                .id(11L)
                .name("我的持仓")
                .positionCount(1)
                .missingQuoteCount(0)
                .holdings(List.of(holding))
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
        assertEquals(1, report.getHoldingCount());
        assertTrue(report.getContent().contains("01｜昨日 A 股复盘"));
        assertTrue(report.getContent().contains("07｜Apex 当前组合"));
        assertTrue(report.getContent().contains(
                "组合：我的持仓｜持仓 1 只｜总权益 数据暂缺｜今日盈亏 数据暂缺"));
        assertTrue(report.getContent().contains("贵州茅台"));
        assertTrue(report.getContent().contains("11｜开盘后 30 分钟观察"));
        verify(portfolioService, never()).detail(12L);
        verify(redisCacheService).put(anyString(), any(DailyPreMarketReportResp.class), any());
    }

    @Test
    void fallsBackToRuleReportWhenAiOmitsHoldingCoverage() {
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
        String incompleteAiReport = "Apex 每日盘前研报\n"
                + "01｜昨日 A 股复盘\n02｜隔夜全球市场\n03｜今日重要消息\n04｜资金面\n"
                + "05｜市场情绪\n06｜今日重点方向 TOP 5\n07｜Apex 当前组合\n"
                + "08｜Apex 今日观察池\n09｜组合风险\n10｜今日策略判断\n"
                + "11｜开盘后 30 分钟观察\nApex 今日结论";
        when(userContext.currentUserId()).thenReturn(7L);
        when(redisCacheService.get(anyString(), eq(DailyPreMarketReportResp.class))).thenReturn(null);
        when(dashboardService.home(null, "我的自选", false)).thenReturn(dashboard);
        when(portfolioService.listPortfolios(false)).thenReturn(List.of(portfolioItem));
        when(portfolioService.detail(11L)).thenReturn(portfolio);
        when(kimiChatClient.available()).thenReturn(true);
        when(kimiChatClient.chat(anyString(), anyString(), eq(6000))).thenReturn(incompleteAiReport);

        DailyPreMarketReportResp report = service.latest(false);

        assertEquals("RULE", report.getReportSource());
        assertTrue(report.getContent().contains("600519"));
    }
}
