package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.ai.KimiChatClient;
import com.awe.apex.quant.cache.RedisCacheService;
import com.awe.apex.quant.domain.dto.MarketBriefingResp;
import com.awe.apex.quant.domain.dto.WeekendFridaySnapshotResp;
import com.awe.apex.quant.domain.dto.WeekendIndexPerformanceResp;
import com.awe.apex.quant.domain.dto.WeekendTradingThemeResp;
import com.awe.apex.quant.domain.entity.IndexBar;
import com.awe.apex.quant.domain.entity.MarketNews;
import com.awe.apex.quant.domain.entity.MarketOpinion;
import com.awe.apex.quant.mapper.IndexBarMapper;
import com.awe.apex.quant.mapper.MarketNewsMapper;
import com.awe.apex.quant.mapper.MarketOpinionMapper;
import com.awe.apex.quant.service.IMarketBriefingService;
import com.awe.apex.quant.service.IMarketOpinionService;
import com.awe.apex.quant.service.INewsService;
import com.awe.apex.quant.domain.dto.WeekendMarketReportResp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * 周末研报服务测试。
 */
class WeekendMarketReportServiceImplTest {

    @Mock
    private MarketNewsMapper marketNewsMapper;

    @Mock
    private MarketOpinionMapper marketOpinionMapper;

    @Mock
    private IndexBarMapper indexBarMapper;

    @Mock
    private IMarketBriefingService marketBriefingService;

    @Mock
    private INewsService newsService;

    @Mock
    private IMarketOpinionService marketOpinionService;

    @Mock
    private RedisCacheService redisCacheService;

    @Mock(answer = Answers.RETURNS_DEFAULTS)
    private KimiChatClient kimiChatClient;

    private WeekendMarketReportServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new WeekendMarketReportServiceImpl();
        ReflectionTestUtils.setField(service, "marketNewsMapper", marketNewsMapper);
        ReflectionTestUtils.setField(service, "marketOpinionMapper", marketOpinionMapper);
        ReflectionTestUtils.setField(service, "indexBarMapper", indexBarMapper);
        ReflectionTestUtils.setField(service, "marketBriefingService", marketBriefingService);
        ReflectionTestUtils.setField(service, "newsService", newsService);
        ReflectionTestUtils.setField(service, "marketOpinionService", marketOpinionService);
        ReflectionTestUtils.setField(service, "redisCacheService", redisCacheService);
        ReflectionTestUtils.setField(service, "kimiChatClient", kimiChatClient);
    }

    @Test
    void shouldBuildWeeklyIndexesAndDeduplicateWeekendNews() {
        LocalDate friday = previousCompletedFriday(LocalDate.now());
        List<LocalDate> weekDates = com.awe.apex.quant.market.TradingCalendar.recentTradingDays(friday, 5);
        List<IndexBar> bars = new ArrayList<>();
        for (String code : List.of("CN_SH", "CN_SZ", "CN_CYB", "CN_KC50", "CN_BJ50")) {
            bars.add(IndexBar.builder().code(code).name(code)
                    .tradeDate(com.awe.apex.quant.market.TradingCalendar.prevTradingDay(weekDates.get(0)))
                    .closePrice(BigDecimal.valueOf(99)).build());
            for (int i = 0; i < weekDates.size(); i++) {
                bars.add(IndexBar.builder().code(code).name(code).tradeDate(weekDates.get(i))
                        .closePrice(BigDecimal.valueOf(100 + i)).pctChg(BigDecimal.ONE).build());
            }
        }
        when(indexBarMapper.selectList(any())).thenReturn(bars);
        when(marketBriefingService.loadCachedBriefing()).thenReturn(MarketBriefingResp.builder()
                .asOf(friday).stance("均衡").breadthUp(2000).breadthDown(1500).breadthFlat(100)
                .limitUpCount(50).limitDownCount(5).hotThemes(List.of("人工智能")).build());
        LocalDateTime newsTime = friday.atTime(16, 0);
        when(marketNewsMapper.selectList(any())).thenReturn(List.of(
                MarketNews.builder().id(1L).source("cls").externalId("a").title("半导体设备公司发布重大并购公告")
                        .relatedCodes("688001").url("https://example.com/a").publishedAt(newsTime).build(),
                MarketNews.builder().id(2L).source("eastmoney").externalId("b").title("半导体设备公司发布重大并购公告")
                        .relatedCodes("688001").url("https://example.com/b").publishedAt(newsTime.plusMinutes(1)).build()));
        when(marketOpinionMapper.selectList(any())).thenReturn(List.of(MarketOpinion.builder()
                .opinionType("INSTITUTION").subjectName("机构甲").title("先进封装景气度上行")
                .relatedName("示例科技").direction("买入").url("https://example.com/report")
                .publishedAt(newsTime).build()));

        WeekendMarketReportResp report = service.generate();

        assertEquals(friday, report.getLastTradeDate());
        assertEquals(5, report.getIndexPerformance().size());
        assertEquals(0, BigDecimal.valueOf(5.05).compareTo(
                report.getIndexPerformance().get(0).getWeeklyReturn().setScale(2, java.math.RoundingMode.HALF_UP)));
        assertEquals(1, report.getWeekendNews().size());
        assertFalse(report.getContent().isBlank());
    }

    @Test
    void shouldMarkRedWhenFridaySnapshotAndIndexesAreMissing() {
        when(indexBarMapper.selectList(any())).thenReturn(List.of());
        when(marketBriefingService.loadCachedBriefing()).thenReturn(null);
        when(marketNewsMapper.selectList(any())).thenReturn(List.of());
        when(marketOpinionMapper.selectList(any())).thenReturn(List.of());

        WeekendMarketReportResp report = service.generate();

        assertEquals("RED", report.getDataLevel());
        assertFalse(report.getMissingData().isEmpty());
        assertNotNull(report.getContent());
    }

    @Test
    void shouldUseLatestCacheWithoutGenerating() {
        WeekendMarketReportResp cached = WeekendMarketReportResp.builder()
                .reportDate(LocalDate.now())
                .weekEndDate(previousCompletedFriday(LocalDate.now()))
                .build();
        when(redisCacheService.get(WeekendMarketReportServiceImpl.CACHE_KEY, WeekendMarketReportResp.class)).thenReturn(cached);

        WeekendMarketReportResp report = service.latest(false);

        assertEquals(cached, report);
    }

    @Test
    void shouldUseLatestFridayForWeekendAndPreviousWeekForWeekday() {
        LocalDate sundayResult = ReflectionTestUtils.invokeMethod(service, "resolveLastCompletedTradeDate",
                LocalDate.of(2026, 8, 30));
        LocalDate wednesdayResult = ReflectionTestUtils.invokeMethod(service, "resolveLastCompletedTradeDate",
                LocalDate.of(2026, 8, 26));

        assertEquals(LocalDate.of(2026, 8, 28), sundayResult);
        assertEquals(LocalDate.of(2026, 8, 21), wednesdayResult);
    }

    @Test
    void shouldCapCompletedReportAtSundayNinePm() {
        LocalDateTime weekdayGenerationTime = LocalDateTime.of(2026, 9, 2, 12, 0);
        LocalDateTime cutoff = ReflectionTestUtils.invokeMethod(service, "resolveReportCutoff",
                LocalDate.of(2026, 8, 28), weekdayGenerationTime);
        LocalDateTime earlyWeekendCutoff = ReflectionTestUtils.invokeMethod(service, "resolveReportCutoff",
                LocalDate.of(2026, 8, 28), LocalDateTime.of(2026, 8, 30, 18, 0));

        assertEquals(LocalDateTime.of(2026, 8, 30, 21, 0), cutoff);
        assertEquals(LocalDateTime.of(2026, 8, 30, 18, 0), earlyWeekendCutoff);
    }

    @Test
    void shouldKeepMostImportantNewsBeforeApplyingLimit() {
        LocalDate friday = previousCompletedFriday(LocalDate.now());
        List<MarketNews> newsRows = new ArrayList<>();
        for (int index = 0; index < 30; index++) {
            newsRows.add(MarketNews.builder()
                    .id((long) index)
                    .source("cls")
                    .externalId("news-" + index)
                    .title(index == 29 ? "国务院发布半导体产业重磅政策" : "示例股份发布上半年净利润增长" + index)
                    .relatedCodes("688001")
                    .url("https://example.com/news/" + index)
                    .publishedAt(friday.atTime(15, 0).plusMinutes(index))
                    .build());
        }
        when(indexBarMapper.selectList(any())).thenReturn(List.of());
        when(marketBriefingService.loadCachedBriefing()).thenReturn(null);
        when(marketNewsMapper.selectList(any())).thenReturn(newsRows);
        when(marketOpinionMapper.selectList(any())).thenReturn(List.of());

        WeekendMarketReportResp report = service.generate();

        assertEquals(8, report.getWeekendNews().size());
        assertTrue(report.getWeekendNews().stream()
                .anyMatch(item -> "国务院发布半导体产业重磅政策".equals(item.getTitle())));
    }

    @Test
    void shouldRejectUntraceableIrrelevantMalformedAndCrossSourceDuplicateNews() {
        LocalDate friday = previousCompletedFriday(LocalDate.now());
        LocalDateTime newsTime = friday.atTime(16, 0);
        when(indexBarMapper.selectList(any())).thenReturn(List.of());
        when(marketBriefingService.loadCachedBriefing()).thenReturn(null);
        when(marketNewsMapper.selectList(any())).thenReturn(List.of(
                MarketNews.builder().id(1L).source("cls").externalId("football").title("中足联通报两队并处罚球员")
                        .url("https://example.com/football").publishedAt(newsTime).build(),
                MarketNews.builder().id(2L).source("sina").externalId("oil").title("布伦特原油暗盘突破88美元，日内涨超0%")
                        .url("https://example.com/oil").publishedAt(newsTime.plusMinutes(1)).build(),
                MarketNews.builder().id(3L).source("ths").externalId("no-url").title("芯片公司获得重大订单")
                        .relatedCodes("688002").publishedAt(newsTime.plusMinutes(2)).build(),
                MarketNews.builder().id(4L).source("cls").externalId("semiconductor-a").title("财联社8月30日电，半导体设备公司获得重大订单")
                        .relatedCodes("688003").url("https://example.com/semiconductor-a")
                        .publishedAt(newsTime.plusMinutes(3)).build(),
                MarketNews.builder().id(5L).source("sina").externalId("semiconductor-b").title("半导体设备公司获得重大订单")
                        .relatedCodes("688003").url("https://example.com/semiconductor-b")
                        .publishedAt(newsTime.plusMinutes(4)).build()));
        when(marketOpinionMapper.selectList(any())).thenReturn(List.of());

        WeekendMarketReportResp report = service.generate();

        assertEquals(1, report.getWeekendNews().size());
        assertTrue(report.getWeekendNews().get(0).getTitle().contains("半导体设备公司获得重大订单"));
        assertFalse(report.getContent().contains("中足联"));
        assertFalse(report.getContent().contains("涨超0%"));
    }

    @Test
    void shouldKeepOnlyOpinionsWithTargetThesisAndTraceableSource() {
        LocalDate friday = previousCompletedFriday(LocalDate.now());
        LocalDateTime opinionTime = friday.atTime(16, 0);
        when(indexBarMapper.selectList(any())).thenReturn(List.of());
        when(marketBriefingService.loadCachedBriefing()).thenReturn(null);
        when(marketNewsMapper.selectList(any())).thenReturn(List.of());
        when(marketOpinionMapper.selectList(any())).thenReturn(List.of(
                MarketOpinion.builder().opinionType("INSTITUTION").subjectName("东吴证券")
                        .direction("增持").publishedAt(opinionTime).build(),
                MarketOpinion.builder().opinionType("INSTITUTION").subjectName("开源证券")
                        .title("先进封装需求进入验证期").summary("订单和产能利用率是后续验证重点")
                        .direction("买入").relatedCode("600001").relatedName("示例科技").topic("半导体")
                        .url("https://example.com/institution-report").publishedAt(opinionTime.plusMinutes(1)).build()));

        WeekendMarketReportResp report = service.generate();

        assertEquals(1, report.getMarketOpinions().size());
        assertTrue(report.getContent().contains("开源证券｜示例科技｜买入"));
        assertTrue(report.getContent().contains("先进封装需求进入验证期"));
        assertFalse(report.getContent().contains("东吴证券：增持"));
    }

    @Test
    void shouldNotPromoteFridayHotspotsWithoutWeekendCatalyst() {
        LocalDate friday = previousCompletedFriday(LocalDate.now());
        when(indexBarMapper.selectList(any())).thenReturn(List.of());
        when(marketBriefingService.loadCachedBriefing()).thenReturn(MarketBriefingResp.builder()
                .asOf(friday).stance("均衡").hotThemes(List.of("粮食概念", "转基因", "农业种植")).build());
        when(marketNewsMapper.selectList(any())).thenReturn(List.of());
        when(marketOpinionMapper.selectList(any())).thenReturn(List.of());

        WeekendMarketReportResp report = service.generate();

        assertEquals(1, report.getTradingThemes().size());
        assertEquals("本周末无新增主线", report.getTradingThemes().get(0).getTheme());
        assertFalse(report.getContent().contains("粮食概念｜催化"));
        assertFalse(report.getContent().contains("转基因｜催化"));
    }

    @Test
    void shouldBuildThemeOnlyFromIndependentWeekendSignalsWithQuantifiedConditions() {
        LocalDate friday = previousCompletedFriday(LocalDate.now());
        LocalDateTime newsTime = friday.atTime(16, 0);
        when(indexBarMapper.selectList(any())).thenReturn(List.of());
        when(marketBriefingService.loadCachedBriefing()).thenReturn(MarketBriefingResp.builder()
                .asOf(friday).stance("均衡").hotThemes(List.of("粮食概念")).build());
        when(marketNewsMapper.selectList(any())).thenReturn(List.of(
                MarketNews.builder().id(1L).source("cls").externalId("dram")
                        .title("DRAM测试设备公司获得重大订单").relatedCodes("688001")
                        .url("https://example.com/dram").publishedAt(newsTime).build(),
                MarketNews.builder().id(2L).source("eastmoney").externalId("gpu")
                        .title("GPU公司上半年净利润同比扭亏").relatedCodes("688002")
                        .url("https://example.com/gpu").publishedAt(newsTime.plusMinutes(1)).build()));
        when(marketOpinionMapper.selectList(any())).thenReturn(List.of());

        WeekendMarketReportResp report = service.generate();

        assertEquals("半导体", report.getTradingThemes().get(0).getTheme());
        assertTrue(report.getTradingThemes().get(0).getCatalyst().contains("DRAM"));
        assertTrue(report.getTradingThemes().get(0).getCatalyst().contains("GPU"));
        assertTrue(report.getTradingThemes().get(0).getConfirmation().contains("前30分钟"));
        assertTrue(report.getTradingThemes().get(0).getConfirmation().contains("60%"));
        assertTrue(report.getTradingThemes().get(0).getInvalidation().contains("40%"));
    }

    @Test
    void shouldDescribeStrongIndexDivergenceInsteadOfCopyingBalancedStance() {
        WeekendFridaySnapshotResp snapshot = WeekendFridaySnapshotResp.builder().stance("均衡").build();
        List<WeekendIndexPerformanceResp> indexes = List.of(
                WeekendIndexPerformanceResp.builder().name("上证指数").weeklyReturn(BigDecimal.valueOf(1.92)).build(),
                WeekendIndexPerformanceResp.builder().name("深证成指").weeklyReturn(BigDecimal.valueOf(1.85)).build(),
                WeekendIndexPerformanceResp.builder().name("创业板指").weeklyReturn(BigDecimal.valueOf(1.21)).build(),
                WeekendIndexPerformanceResp.builder().name("科创50").weeklyReturn(BigDecimal.valueOf(5.69)).build(),
                WeekendIndexPerformanceResp.builder().name("北证50").weeklyReturn(BigDecimal.valueOf(2.03)).build());

        String marketStatus = ReflectionTestUtils.invokeMethod(service, "resolveMarketStatus", snapshot, indexes);

        assertEquals("结构性偏强，科创50领涨", marketStatus);
    }

    @Test
    void shouldCalculateWeeklyReturnFromPreviousTradingClose() {
        LocalDate friday = previousCompletedFriday(LocalDate.now());
        LocalDate monday = friday.minusDays(4);
        LocalDate previousFriday = com.awe.apex.quant.market.TradingCalendar.prevTradingDay(monday);
        List<IndexBar> bars = List.of(
                IndexBar.builder().code("CN_SH").name("上证指数").tradeDate(previousFriday)
                        .closePrice(BigDecimal.valueOf(98)).build(),
                IndexBar.builder().code("CN_SH").name("上证指数").tradeDate(monday)
                        .closePrice(BigDecimal.valueOf(100)).build(),
                IndexBar.builder().code("CN_SH").name("上证指数").tradeDate(friday)
                        .closePrice(BigDecimal.valueOf(104)).pctChg(BigDecimal.ONE).build());
        when(indexBarMapper.selectList(any())).thenReturn(bars);
        when(marketBriefingService.loadCachedBriefing()).thenReturn(null);
        when(marketNewsMapper.selectList(any())).thenReturn(List.of());
        when(marketOpinionMapper.selectList(any())).thenReturn(List.of());

        WeekendMarketReportResp report = service.generate();

        assertEquals(0, BigDecimal.valueOf(6.12).compareTo(
                report.getIndexPerformance().get(0).getWeeklyReturn().setScale(2, java.math.RoundingMode.HALF_UP)));
    }

    @Test
    void shouldRejectStaleFridaySnapshot() {
        LocalDate friday = previousCompletedFriday(LocalDate.now());
        when(indexBarMapper.selectList(any())).thenReturn(List.of());
        when(marketBriefingService.loadCachedBriefing()).thenReturn(MarketBriefingResp.builder()
                .asOf(com.awe.apex.quant.market.TradingCalendar.prevTradingDay(friday))
                .stance("强势").hotThemes(List.of("过期热点")).build());
        when(marketNewsMapper.selectList(any())).thenReturn(List.of());
        when(marketOpinionMapper.selectList(any())).thenReturn(List.of());

        WeekendMarketReportResp report = service.generate();

        assertEquals(null, report.getFridaySnapshot());
        assertFalse(report.getCoreView().contains("强势"));
        assertFalse(report.getContent().contains("过期热点"));
    }

    @Test
    void shouldRejectAiReportThatAddsThemeOutsideRuleContext() {
        WeekendMarketReportResp context = WeekendMarketReportResp.builder()
                .tradingThemes(List.of(WeekendTradingThemeResp.builder().theme("半导体").build()))
                .build();
        String modelContent = """
                核心观点：结构性行情
                最大风险：量能不足
                01｜上周走势
                02｜周五收盘
                03｜周末消息
                04｜机构与大 V 观点
                05｜下周交易主线
                - 农业｜催化：半导体消息｜确认：量价确认｜失效：宽度转弱
                06｜市场剧本与风险
                """;

        Boolean complete = ReflectionTestUtils.invokeMethod(service, "isCompleteReport", modelContent, context);

        assertFalse(Boolean.TRUE.equals(complete));
    }

    @Test
    void shouldRejectAiReportThatInventsEvidenceWhenContextIsEmpty() {
        WeekendMarketReportResp context = WeekendMarketReportResp.builder()
                .tradingThemes(List.of(WeekendTradingThemeResp.builder()
                        .theme("本周末无新增主线")
                        .catalyst("未形成集中催化")
                        .confirmation("等待确认")
                        .invalidation("出现新主线后失效")
                        .build()))
                .build();
        String modelContent = """
                核心观点：暂无主线
                最大风险：量能不足
                01｜上周走势
                02｜周五收盘
                03｜周末消息
                - 虚构公司获得重大订单
                04｜机构与大 V 观点
                - 虚构机构发布买入评级
                05｜下周交易主线
                - 本周末无新增主线｜催化：未形成集中催化｜确认：等待确认｜失效：出现新主线后失效
                06｜市场剧本与风险
                """;

        Boolean complete = ReflectionTestUtils.invokeMethod(service, "isCompleteReport", modelContent, context);

        assertFalse(Boolean.TRUE.equals(complete));
    }

    @Test
    void shouldKeepQualifiedClsNewsWithAuditableSourceIndex() {
        LocalDate friday = previousCompletedFriday(LocalDate.now());
        when(indexBarMapper.selectList(any())).thenReturn(List.of());
        when(marketBriefingService.loadCachedBriefing()).thenReturn(null);
        when(marketNewsMapper.selectList(any())).thenReturn(List.of(MarketNews.builder()
                .id(1L).source("cls").externalId("cls-dram-001")
                .title("DRAM测试设备公司获得重大订单").relatedCodes("688001")
                .publishedAt(friday.atTime(16, 0)).build()));
        when(marketOpinionMapper.selectList(any())).thenReturn(List.of());

        WeekendMarketReportResp report = service.generate();

        assertEquals(1, report.getWeekendNews().size());
        assertTrue(report.getContent().contains("来源索引 cls#cls-dram-001"));
        assertTrue(report.getMissingData().contains("部分消息缺少原文链接，仅保留来源索引"));
    }

    @Test
    void shouldMarkReportYellowWhenRefreshFailsButStoredDataIsComplete() {
        LocalDate friday = previousCompletedFriday(LocalDate.now());
        List<LocalDate> weekDates = com.awe.apex.quant.market.TradingCalendar.recentTradingDays(friday, 5);
        List<IndexBar> bars = new ArrayList<>();
        for (String code : List.of("CN_SH", "CN_SZ", "CN_CYB", "CN_KC50", "CN_BJ50")) {
            bars.add(IndexBar.builder().code(code).name(code)
                    .tradeDate(com.awe.apex.quant.market.TradingCalendar.prevTradingDay(weekDates.get(0)))
                    .closePrice(BigDecimal.valueOf(99)).build());
            for (int index = 0; index < weekDates.size(); index++) {
                bars.add(IndexBar.builder().code(code).name(code).tradeDate(weekDates.get(index))
                        .closePrice(BigDecimal.valueOf(100 + index)).pctChg(BigDecimal.ONE).build());
            }
        }
        LocalDateTime evidenceTime = friday.atTime(16, 0);
        when(indexBarMapper.selectList(any())).thenReturn(bars);
        when(marketBriefingService.loadCachedBriefing()).thenReturn(MarketBriefingResp.builder()
                .asOf(friday).stance("均衡").breadthUp(2000).breadthDown(1500).build());
        when(marketNewsMapper.selectList(any())).thenReturn(List.of(MarketNews.builder()
                .source("eastmoney").externalId("news-1").title("半导体公司获得重大订单")
                .relatedCodes("688001").url("https://example.com/news-1").publishedAt(evidenceTime).build()));
        when(marketOpinionMapper.selectList(any())).thenReturn(List.of(MarketOpinion.builder()
                .opinionType("INSTITUTION").subjectName("机构甲").title("半导体订单进入验证期")
                .summary("关注订单兑现").direction("买入").topic("半导体")
                .url("https://example.com/report-1").publishedAt(evidenceTime).build()));
        doThrow(new IllegalStateException("news unavailable")).when(newsService).refresh(any(), any());
        doThrow(new IllegalStateException("opinion unavailable")).when(marketOpinionService).refresh();

        WeekendMarketReportResp report = service.refresh();

        assertEquals("YELLOW", report.getDataLevel());
        assertTrue(report.getMissingData().contains("周末资讯刷新失败"));
        assertTrue(report.getMissingData().contains("市场观点刷新失败"));
    }

    @Test
    void shouldExtractEmergingPolicyThemeOutsideBuiltInKeywordList() {
        LocalDate friday = previousCompletedFriday(LocalDate.now());
        when(indexBarMapper.selectList(any())).thenReturn(List.of());
        when(marketBriefingService.loadCachedBriefing()).thenReturn(null);
        when(marketNewsMapper.selectList(any())).thenReturn(List.of(MarketNews.builder()
                .source("eastmoney").externalId("robot-policy")
                .title("工信部发布人形机器人产业支持政策")
                .url("https://example.com/robot-policy").publishedAt(friday.atTime(16, 0)).build()));
        when(marketOpinionMapper.selectList(any())).thenReturn(List.of());

        WeekendMarketReportResp report = service.generate();

        assertEquals("人形机器人", report.getTradingThemes().get(0).getTheme());
    }

    @Test
    void shouldKeepMarketRiskWhenOnlySourceLinkIsMissing() {
        WeekendFridaySnapshotResp snapshot = WeekendFridaySnapshotResp.builder().volumeLabel("缩量 -1.09%").build();

        String maxRisk = ReflectionTestUtils.invokeMethod(service, "buildMaxRisk", snapshot,
                List.of("部分消息缺少原文链接，仅保留来源索引"));

        assertTrue(maxRisk.contains("周五缩量"));
    }

    private LocalDate previousCompletedFriday(LocalDate reportDate) {
        if (reportDate.getDayOfWeek().getValue() >= 6) {
            return com.awe.apex.quant.market.TradingCalendar.latestTradingDayOnOrBefore(reportDate);
        }
        LocalDate monday = reportDate;
        while (monday.getDayOfWeek().getValue() != 1) {
            monday = monday.minusDays(1);
        }
        return com.awe.apex.quant.market.TradingCalendar.latestTradingDayOnOrBefore(monday.minusDays(1));
    }
}
