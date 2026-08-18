package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.decision.DecisionRunManager;
import com.awe.apex.quant.domain.bo.DecisionDataCutoffBO;
import com.awe.apex.quant.domain.dto.DecisionTodayResp;
import com.awe.apex.quant.domain.dto.MarketBriefingResp;
import com.awe.apex.quant.domain.entity.DailyAction;
import com.awe.apex.quant.domain.entity.DecisionRun;
import com.awe.apex.quant.mapper.DailyActionMapper;
import com.awe.apex.quant.mapper.DecisionRunMapper;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DecisionTodayServiceTest {

    private final DailyActionMapper dailyActionMapper = mock(DailyActionMapper.class);
    private final DecisionRunMapper decisionRunMapper = mock(DecisionRunMapper.class);
    private final DecisionRunManager decisionRunManager = mock(DecisionRunManager.class);
    private final ApexUserContext userContext = mock(ApexUserContext.class);
    private final DecisionServiceImpl service = new DecisionServiceImpl();

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant builderAssistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(builderAssistant, DailyAction.class);
        TableInfoHelper.initTableInfo(builderAssistant, DecisionRun.class);
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "dailyActionMapper", dailyActionMapper);
        ReflectionTestUtils.setField(service, "decisionRunMapper", decisionRunMapper);
        ReflectionTestUtils.setField(service, "decisionRunManager", decisionRunManager);
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
}
