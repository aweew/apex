package com.awe.apex.quant.service.impl;

import com.awe.apex.common.util.JsonUtils;
import com.awe.apex.common.util.SpringUtils;
import com.awe.apex.quant.ai.KimiChatClient;
import com.awe.apex.quant.cache.RedisCacheService;
import com.awe.apex.quant.domain.dto.MarketBriefingResp;
import com.awe.apex.quant.domain.dto.PostMarketReportResp;
import com.awe.apex.quant.domain.dto.SectorBoardResp;
import com.awe.apex.quant.domain.entity.DragonTigerItem;
import com.awe.apex.quant.domain.entity.LimitUpPool;
import com.awe.apex.quant.domain.entity.MarketBriefingSnapshot;
import com.awe.apex.quant.domain.entity.MarketOpinion;
import com.awe.apex.quant.domain.entity.StockFundFlow;
import com.awe.apex.quant.mapper.DragonTigerItemMapper;
import com.awe.apex.quant.mapper.LimitUpPoolMapper;
import com.awe.apex.quant.mapper.MarketBriefingSnapshotMapper;
import com.awe.apex.quant.mapper.MarketOpinionMapper;
import com.awe.apex.quant.mapper.StockFundFlowMapper;
import com.awe.apex.quant.service.ISectorBoardService;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.context.ApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 盘后总结服务测试。
 */
class PostMarketReportServiceImplTest {

    @Mock
    private MarketBriefingSnapshotMapper marketBriefingSnapshotMapper;

    @Mock
    private LimitUpPoolMapper limitUpPoolMapper;

    @Mock
    private StockFundFlowMapper stockFundFlowMapper;

    @Mock
    private DragonTigerItemMapper dragonTigerItemMapper;

    @Mock
    private MarketOpinionMapper marketOpinionMapper;

    @Mock
    private ISectorBoardService sectorBoardService;

    @Mock
    private RedisCacheService redisCacheService;

    @Mock(answer = Answers.RETURNS_DEFAULTS)
    private KimiChatClient kimiChatClient;

    private PostMarketReportServiceImpl service;

    private ApplicationContext originalApplicationContext;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        originalApplicationContext = SpringUtils.getApplicationContext();
        ApplicationContext applicationContext = org.mockito.Mockito.mock(ApplicationContext.class);
        when(applicationContext.getBean(ObjectMapper.class)).thenReturn(new ObjectMapper().findAndRegisterModules());
        new SpringUtils().setApplicationContext(applicationContext);
        service = new PostMarketReportServiceImpl();
        ReflectionTestUtils.setField(service, "marketBriefingSnapshotMapper", marketBriefingSnapshotMapper);
        ReflectionTestUtils.setField(service, "limitUpPoolMapper", limitUpPoolMapper);
        ReflectionTestUtils.setField(service, "stockFundFlowMapper", stockFundFlowMapper);
        ReflectionTestUtils.setField(service, "dragonTigerItemMapper", dragonTigerItemMapper);
        ReflectionTestUtils.setField(service, "marketOpinionMapper", marketOpinionMapper);
        ReflectionTestUtils.setField(service, "sectorBoardService", sectorBoardService);
        ReflectionTestUtils.setField(service, "redisCacheService", redisCacheService);
        ReflectionTestUtils.setField(service, "kimiChatClient", kimiChatClient);
    }

    @AfterEach
    void tearDown() {
        new SpringUtils().setApplicationContext(originalApplicationContext);
    }

    @Test
    void shouldExposeLatestReportFromEighteenThirtyUntilNextMarketOpen() {
        assertFalse(PostMarketReportServiceImpl.isVisibleWindow(LocalDateTime.of(2026, 8, 31, 18, 29)));
        assertTrue(PostMarketReportServiceImpl.isVisibleWindow(LocalDateTime.of(2026, 8, 31, 18, 30)));
        assertTrue(PostMarketReportServiceImpl.isVisibleWindow(LocalDateTime.of(2026, 9, 1, 9, 29)));
        assertFalse(PostMarketReportServiceImpl.isVisibleWindow(LocalDateTime.of(2026, 9, 1, 9, 30)));
        assertTrue(PostMarketReportServiceImpl.isVisibleWindow(LocalDateTime.of(2026, 9, 5, 12, 0)));
        assertTrue(PostMarketReportServiceImpl.isVisibleWindow(LocalDateTime.of(2026, 9, 7, 9, 29)));
    }

    @Test
    void shouldNotReadOrGenerateOutsideVisibleWindow() {
        PostMarketReportResp report = service.latest(false, LocalDateTime.of(2026, 9, 1, 12, 0));

        assertNull(report);
        verifyNoInteractions(redisCacheService, marketBriefingSnapshotMapper, limitUpPoolMapper,
                stockFundFlowMapper, dragonTigerItemMapper, marketOpinionMapper, sectorBoardService);
    }

    @Test
    void shouldUseCacheOnlyWhenItMatchesLatestCompletedTradeDate() {
        PostMarketReportResp cachedReport = PostMarketReportResp.builder()
                .tradeDate(LocalDate.of(2026, 8, 31))
                .content("缓存报告")
                .build();
        when(redisCacheService.get(PostMarketReportServiceImpl.CACHE_KEY, PostMarketReportResp.class))
                .thenReturn(cachedReport);

        PostMarketReportResp report = service.latest(false, LocalDateTime.of(2026, 8, 31, 19, 0));

        assertEquals(cachedReport, report);
        verifyNoInteractions(marketBriefingSnapshotMapper, limitUpPoolMapper, stockFundFlowMapper,
                dragonTigerItemMapper, marketOpinionMapper, sectorBoardService);
    }

    @Test
    void shouldBuildLatestTradeDateReportAndMergeStarStocks() {
        LocalDate tradeDate = LocalDate.of(2026, 8, 31);
        MarketBriefingResp marketSnapshot = MarketBriefingResp.builder()
                .asOf(tradeDate)
                .stance("均衡")
                .stanceScore(62)
                .breadthUp(3100)
                .breadthDown(1800)
                .limitUpCount(71)
                .limitDownCount(5)
                .dataLevel("GREEN")
                .build();
        String marketSnapshotJson = JsonUtils.toJsonString(marketSnapshot);
        MarketBriefingSnapshot storedMarketSnapshot = MarketBriefingSnapshot.builder()
                .tradeDate(tradeDate)
                .payloadJson(marketSnapshotJson)
                .updateTime(tradeDate.atTime(15, 20))
                .build();
        when(marketBriefingSnapshotMapper.selectOne(any())).thenReturn(storedMarketSnapshot);
        when(sectorBoardService.board(any(), any(), any(), any(), any())).thenAnswer(invocation ->
                SectorBoardResp.builder().tradeDate(tradeDate).items(List.of()).build());
        when(sectorBoardService.mainline(tradeDate.toString(), 8)).thenReturn(List.of());
        when(limitUpPoolMapper.selectList(any())).thenReturn(List.of(
                LimitUpPool.builder().tradeDate(tradeDate).code("000001").name("甲公司")
                        .pctChg(BigDecimal.TEN).lianban(3).sealAmount(BigDecimal.valueOf(8_000_000))
                        .theme("机器人").syncedAt(tradeDate.atTime(17, 30)).build(),
                LimitUpPool.builder().tradeDate(tradeDate).code("000002").name("乙公司")
                        .pctChg(BigDecimal.TEN).lianban(1).sealAmount(BigDecimal.valueOf(20_000_000))
                        .theme("芯片").syncedAt(tradeDate.atTime(17, 30)).build()));
        when(stockFundFlowMapper.selectList(any())).thenReturn(List.of(
                StockFundFlow.builder().tradeDate(tradeDate).code("000001").name("甲公司")
                        .mainNetInflow(BigDecimal.valueOf(30_000_000)).mainNetInflowPct(BigDecimal.valueOf(12))
                        .syncedAt(tradeDate.atTime(18, 20)).build(),
                StockFundFlow.builder().tradeDate(tradeDate).code("000003").name("丙公司")
                        .pctChg(BigDecimal.valueOf(7)).mainNetInflow(BigDecimal.valueOf(50_000_000))
                        .mainNetInflowPct(BigDecimal.valueOf(15)).syncedAt(tradeDate.atTime(18, 20)).build()));
        when(dragonTigerItemMapper.selectList(any())).thenReturn(List.of(DragonTigerItem.builder()
                .tradeDate(tradeDate).code("000001").name("甲公司").netBuyAmount(BigDecimal.valueOf(10_000_000))
                .syncedAt(tradeDate.atTime(17, 30)).build()));
        when(marketOpinionMapper.selectList(any())).thenReturn(List.of(MarketOpinion.builder()
                .opinionType("ACTIVE_SEAT").subjectName("国泰君安上海江苏路")
                .actorName("作手新一").actorType("TRADER").actorConfidence("HIGH")
                .relatedCode("000001").relatedName("甲公司").direction("买入")
                .netAmount(BigDecimal.valueOf(10_000_000)).publishedAt(tradeDate.atTime(17, 40))
                .snapshotTime(tradeDate.atTime(18, 0)).build()));

        PostMarketReportResp report = service.generate(LocalDateTime.of(2026, 8, 31, 18, 30));

        assertEquals(tradeDate, report.getTradeDate());
        assertEquals("甲公司", report.getStarStocks().get(0).getName());
        assertEquals(BigDecimal.valueOf(30_000_000), report.getStarStocks().get(0).getMainNetInflow());
        assertTrue(report.getStarStocks().get(0).getReasons().contains("3连板"));
        assertEquals(3, report.getStarStocks().size());
        assertEquals(1, report.getDragonTigerItems().size());
        assertEquals("作手新一", report.getActiveSeats().get(0).getActorName());
        assertFalse(report.getContent().isBlank());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<MarketOpinion>> opinionQueryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(marketOpinionMapper).selectList(opinionQueryCaptor.capture());
        AbstractWrapper<?, ?, ?> opinionQuery = (AbstractWrapper<?, ?, ?>) opinionQueryCaptor.getValue();
        String opinionSql = opinionQuery.getSqlSegment();
        assertTrue(opinionSql.contains("published_at >="));
        assertTrue(opinionSql.contains("published_at <"));
        assertTrue(opinionQuery.getParamNameValuePairs().containsValue(tradeDate.atStartOfDay()));
        assertTrue(opinionQuery.getParamNameValuePairs().containsValue(tradeDate.plusDays(1).atStartOfDay()));
    }

    @Test
    void shouldDegradeToRedWhenClosingDataIsMissing() {
        when(sectorBoardService.board(any(), any(), any(), any(), any())).thenReturn(null);
        when(sectorBoardService.mainline(any(), any())).thenReturn(List.of());

        PostMarketReportResp report = service.generate(LocalDateTime.of(2026, 8, 31, 18, 30));

        assertEquals("RED", report.getDataLevel());
        assertEquals("DEGRADED", report.getContentLevel());
        assertTrue(report.getMissingData().contains("大盘收盘快照"));
        assertTrue(report.getContent().contains("大盘收盘快照暂未齐全"));
    }

    @Test
    void shouldFallbackToRuleReportWhenAiContentIsIncomplete() {
        when(sectorBoardService.board(any(), any(), any(), any(), any())).thenReturn(null);
        when(sectorBoardService.mainline(any(), any())).thenReturn(List.of());
        when(kimiChatClient.available()).thenReturn(true);
        when(kimiChatClient.chat(any(), any(), anyInt())).thenReturn("只有一句未经校验的结论");

        PostMarketReportResp report = service.generate(LocalDateTime.of(2026, 8, 31, 18, 30));

        assertEquals("RULE", report.getReportSource());
        assertTrue(report.getContent().contains("06｜风险与次日观察"));
    }
}
