package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.decision.DecisionRunManager;
import com.awe.apex.quant.domain.bo.DecisionDataCutoffBO;
import com.awe.apex.quant.domain.dto.DecisionTodayResp;
import com.awe.apex.quant.domain.dto.MarketBriefingResp;
import com.awe.apex.quant.domain.entity.DailyAction;
import com.awe.apex.quant.domain.entity.DecisionRun;
import com.awe.apex.quant.domain.entity.MarketNews;
import com.awe.apex.quant.mapper.DailyActionMapper;
import com.awe.apex.quant.mapper.DecisionRunMapper;
import com.awe.apex.quant.mapper.MarketNewsMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DecisionTodayServiceTest {

    private final DailyActionMapper dailyActionMapper = mock(DailyActionMapper.class);
    private final DecisionRunMapper decisionRunMapper = mock(DecisionRunMapper.class);
    private final DecisionRunManager decisionRunManager = mock(DecisionRunManager.class);
    private final MarketNewsMapper marketNewsMapper = mock(MarketNewsMapper.class);
    private final StockBasicMapper stockBasicMapper = mock(StockBasicMapper.class);
    private final ApexUserContext userContext = mock(ApexUserContext.class);
    private final DecisionServiceImpl service = new DecisionServiceImpl();

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant builderAssistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(builderAssistant, DailyAction.class);
        TableInfoHelper.initTableInfo(builderAssistant, DecisionRun.class);
        TableInfoHelper.initTableInfo(builderAssistant, MarketNews.class);
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "dailyActionMapper", dailyActionMapper);
        ReflectionTestUtils.setField(service, "decisionRunMapper", decisionRunMapper);
        ReflectionTestUtils.setField(service, "decisionRunManager", decisionRunManager);
        ReflectionTestUtils.setField(service, "marketNewsMapper", marketNewsMapper);
        ReflectionTestUtils.setField(service, "stockBasicMapper", stockBasicMapper);
        ReflectionTestUtils.setField(service, "userContext", userContext);
        when(userContext.currentUserId()).thenReturn(7L);
    }

    @Test
    void reportsGeneratedDecisionWhenPublishedRunContainsNoActions() {
        LocalDate actionDate = LocalDate.now();
        DecisionRun run = DecisionRun.builder()
                .id(21L)
                .userId(7L)
                .runNo("RUN-21")
                .mode("LIVE")
                .actionDate(actionDate)
                .asOfTime(LocalDateTime.now())
                .status("SUCCESS")
                .published(1)
                .dataCutoffJson("cutoff")
                .build();
        when(dailyActionMapper.selectList(any())).thenReturn(List.of());
        when(decisionRunMapper.selectOne(any())).thenReturn(run);
        when(decisionRunManager.parseDataCutoff("cutoff")).thenReturn(DecisionDataCutoffBO.builder()
                .marketDataAsOf(actionDate.minusDays(1))
                .build());
        MarketBriefingResp briefing = MarketBriefingResp.builder()
                .asOf(actionDate.minusDays(1))
                .stance("均衡")
                .hotThemes(List.of())
                .build();

        DecisionTodayResp response = service.today(actionDate, "我的自选", briefing);

        assertTrue(response.getGenerated());
        assertEquals(actionDate.minusDays(1), response.getDataAsOf());
        assertEquals("RUN-21", response.getRunNo());
        assertTrue(response.getMessage().contains("已生成"));
        ArgumentCaptor<Wrapper<DecisionRun>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(decisionRunMapper).selectOne(queryCaptor.capture());
        String sql = queryCaptor.getValue().getSqlSegment();
        assertTrue(sql.contains("user_id"));
        assertTrue(sql.contains("action_date"));
        assertTrue(sql.contains("status"));
        assertTrue(sql.contains("published"));
    }

    @Test
    void addsStructuredHighlightsAndDirectRecentNewsToDecisionItems() {
        LocalDate actionDate = LocalDate.now();
        DailyAction action = DailyAction.builder()
                .id(1L)
                .actionDate(actionDate)
                .code("600001")
                .name("示例股份")
                .action("BUY")
                .strategyId("S2")
                .strategiesCsv("S1,S2")
                .mainlineMatch(1)
                .mainlineName("半导体")
                .executableHint(1)
                .valuationSummary("估值处于行业低位")
                .fundNote("ROE 12.50%")
                .scoreExplain("回调缩量，等待确认")
                .build();
        MarketNews news = MarketNews.builder()
                .source("eastmoney")
                .relatedCodes("600001,300001")
                .title("示例股份披露订单进展")
                .publishedAt(LocalDateTime.now().minusHours(2))
                .build();
        when(dailyActionMapper.selectList(any())).thenReturn(List.of(action));
        when(decisionRunMapper.selectOne(any())).thenReturn(null);
        when(stockBasicMapper.selectList(any())).thenReturn(List.of());
        when(marketNewsMapper.selectList(any())).thenReturn(List.of(news));
        MarketBriefingResp briefing = MarketBriefingResp.builder()
                .asOf(actionDate)
                .stance("均衡")
                .hotThemes(List.of())
                .build();

        DecisionTodayResp response = service.today(actionDate, "我的自选", briefing);

        assertEquals(List.of("主线 · 半导体", "多策略共振 · S1+S2", "估值处于行业低位"),
                response.getBuys().get(0).getHighlights());
        assertEquals("近7日收录1条，最新：示例股份披露订单进展", response.getBuys().get(0).getNewsSummary());
        assertEquals("示例股份披露订单进展", response.getBuys().get(0).getRecentNews().get(0).getTitle());
    }

    @Test
    void skipsNewsEnrichmentWhenNoBuySuggestionCanBeExecuted() {
        LocalDate actionDate = LocalDate.now();
        DailyAction action = DailyAction.builder()
                .id(1L)
                .actionDate(actionDate)
                .code("600001")
                .name("示例股份")
                .action("BUY")
                .executableHint(0)
                .build();
        when(dailyActionMapper.selectList(any())).thenReturn(List.of(action));
        when(decisionRunMapper.selectOne(any())).thenReturn(null);
        when(stockBasicMapper.selectList(any())).thenReturn(List.of());
        MarketBriefingResp briefing = MarketBriefingResp.builder()
                .asOf(actionDate)
                .stance("均衡")
                .hotThemes(List.of())
                .build();

        DecisionTodayResp response = service.today(actionDate, "我的自选", briefing);

        assertNull(response.getBuys().get(0).getHighlights());
        assertNull(response.getBuys().get(0).getRecentNews());
        assertNull(response.getBuys().get(0).getNewsSummary());
    }
}
