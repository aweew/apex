package com.awe.apex.quant.service.impl;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.context.ApexUserContext;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaperDecisionOrderServiceTest {

    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");

    private final DailyActionMapper dailyActionMapper = mock(DailyActionMapper.class);
    private final DecisionRunMapper decisionRunMapper = mock(DecisionRunMapper.class);
    private final ApexUserContext userContext = mock(ApexUserContext.class);
    private final PaperServiceImpl service = new PaperServiceImpl();

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
        ReflectionTestUtils.setField(service, "userContext", userContext);
        when(userContext.currentUserIdOrNull()).thenReturn(7L);
    }

    @Test
    void rejectsHistoricalDecisionBeforeReadingRunOrAccount() {
        DailyAction action = executableBuy(marketDate().minusDays(1));
        when(dailyActionMapper.selectOne(any())).thenReturn(action);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.orderFromDecision(11L, 3L, null));

        assertTrue(exception.getMessage().contains("当日 LIVE 决策"));
        verify(decisionRunMapper, never()).selectOne(any());
    }

    @Test
    void rejectsNonLiveOrUnpublishedOrUnreadyDecisionRun() {
        DailyAction action = executableBuy(marketDate());
        when(dailyActionMapper.selectOne(any())).thenReturn(action);
        when(decisionRunMapper.selectOne(any())).thenReturn(DecisionRun.builder()
                .id(21L)
                .userId(7L)
                .actionDate(action.getActionDate())
                .mode("REPLAY")
                .status("SUCCESS")
                .published(1)
                .dataLevel("GREEN")
                .build());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.orderFromDecision(11L, 3L, null));

        assertTrue(exception.getMessage().contains("LIVE"));
        ArgumentCaptor<Wrapper<DailyAction>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(dailyActionMapper).selectOne(queryCaptor.capture());
        assertTrue(queryCaptor.getValue().getSqlSegment().contains("user_id"));
    }

    @Test
    void rejectsDecisionItemThatDidNotPassExecutionGate() {
        DailyAction action = executableBuy(marketDate());
        action.setExecutableHint(0);
        when(dailyActionMapper.selectOne(any())).thenReturn(action);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.orderFromDecision(11L, 3L, null));

        assertTrue(exception.getMessage().contains("风控门禁"));
        verify(decisionRunMapper, never()).selectOne(any());
    }

    @Test
    void rejectsUnpublishedOrUnreadyLiveDecisionRun() {
        DailyAction action = executableBuy(marketDate());
        when(dailyActionMapper.selectOne(any())).thenReturn(action);
        when(decisionRunMapper.selectOne(any())).thenReturn(liveRun(action, 0, "GREEN"));

        BusinessException unpublished = assertThrows(BusinessException.class,
                () -> service.orderFromDecision(11L, 3L, null));
        assertTrue(unpublished.getMessage().contains("尚未正式发布"));

        when(decisionRunMapper.selectOne(any())).thenReturn(liveRun(action, 1, "RED"));
        BusinessException unready = assertThrows(BusinessException.class,
                () -> service.orderFromDecision(11L, 3L, null));
        assertTrue(unready.getMessage().contains("市场数据未就绪"));
    }

    @Test
    void rejectsInvalidExplicitTargetWeightBeforeReadingAccount() {
        DailyAction action = executableBuy(marketDate());
        when(dailyActionMapper.selectOne(any())).thenReturn(action);
        when(decisionRunMapper.selectOne(any())).thenReturn(liveRun(action, 1, "GREEN"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.orderFromDecision(11L, 3L, BigDecimal.ONE));

        assertTrue(exception.getMessage().contains("目标仓位"));
    }

    private DailyAction executableBuy(LocalDate actionDate) {
        return DailyAction.builder()
                .id(11L)
                .userId(7L)
                .runId(21L)
                .actionDate(actionDate)
                .code("600001")
                .action("BUY")
                .executableHint(1)
                .build();
    }

    private DecisionRun liveRun(DailyAction action, Integer published, String dataLevel) {
        return DecisionRun.builder()
                .id(action.getRunId())
                .userId(7L)
                .actionDate(action.getActionDate())
                .mode("LIVE")
                .status("SUCCESS")
                .published(published)
                .dataLevel(dataLevel)
                .build();
    }

    private LocalDate marketDate() {
        return LocalDate.now(SHANGHAI_ZONE);
    }
}
