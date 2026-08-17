package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.entity.DailyAction;
import com.awe.apex.quant.domain.entity.PaperAccount;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.domain.entity.StrategySignalEntity;
import com.awe.apex.quant.domain.entity.Watchlist;
import com.awe.apex.quant.mapper.DailyActionMapper;
import com.awe.apex.quant.mapper.PaperPositionMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.mapper.WatchlistMapper;
import com.awe.apex.quant.service.IPaperService;
import com.awe.apex.quant.service.ISignalService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DailyActionServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, DailyAction.class);
        TableInfoHelper.initTableInfo(assistant, Watchlist.class);
    }

    @Test
    void reusesPublishedDecisionInsteadOfOverwritingIt() {
        DailyActionMapper dailyActionMapper = mock(DailyActionMapper.class);
        ISignalService signalService = mock(ISignalService.class);
        IPaperService paperService = mock(IPaperService.class);
        ApexUserContext userContext = mock(ApexUserContext.class);
        DailyActionServiceImpl service = new DailyActionServiceImpl();
        ReflectionTestUtils.setField(service, "dailyActionMapper", dailyActionMapper);
        ReflectionTestUtils.setField(service, "signalService", signalService);
        ReflectionTestUtils.setField(service, "paperService", paperService);
        ReflectionTestUtils.setField(service, "userContext", userContext);
        when(userContext.currentUserId()).thenReturn(7L);
        DailyAction reduction = DailyAction.builder()
                .id(1L)
                .runId(9L)
                .actionDate(LocalDate.of(2026, 8, 7))
                .code("000001")
                .action("REDUCE")
                .build();
        when(dailyActionMapper.selectList(any())).thenReturn(List.of(reduction));

        List<DailyAction> actions = service.run(LocalDate.of(2026, 8, 7));

        assertEquals(List.of(reduction), actions);
        verify(dailyActionMapper, never()).delete(any());
        verifyNoInteractions(signalService, paperService);
    }

    @Test
    void listByDateFiltersCurrentUser() {
        DailyActionMapper dailyActionMapper = mock(DailyActionMapper.class);
        ApexUserContext userContext = mock(ApexUserContext.class);
        DailyActionServiceImpl service = new DailyActionServiceImpl();
        ReflectionTestUtils.setField(service, "dailyActionMapper", dailyActionMapper);
        ReflectionTestUtils.setField(service, "userContext", userContext);
        when(userContext.currentUserId()).thenReturn(7L);
        when(dailyActionMapper.selectList(any())).thenReturn(List.of());

        service.listByDate(LocalDate.of(2026, 8, 16));

        ArgumentCaptor<Wrapper<DailyAction>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(dailyActionMapper).selectList(queryCaptor.capture());
        assertUserFilter(queryCaptor.getValue());
    }

    @Test
    void runScopesDeleteAndWatchlistAndWritesCurrentOwner() {
        DailyActionMapper dailyActionMapper = mock(DailyActionMapper.class);
        ISignalService signalService = mock(ISignalService.class);
        IPaperService paperService = mock(IPaperService.class);
        PaperPositionMapper paperPositionMapper = mock(PaperPositionMapper.class);
        WatchlistMapper watchlistMapper = mock(WatchlistMapper.class);
        StockBasicMapper stockBasicMapper = mock(StockBasicMapper.class);
        ApexUserContext userContext = mock(ApexUserContext.class);
        DailyActionServiceImpl service = new DailyActionServiceImpl();
        ReflectionTestUtils.setField(service, "dailyActionMapper", dailyActionMapper);
        ReflectionTestUtils.setField(service, "signalService", signalService);
        ReflectionTestUtils.setField(service, "paperService", paperService);
        ReflectionTestUtils.setField(service, "paperPositionMapper", paperPositionMapper);
        ReflectionTestUtils.setField(service, "watchlistMapper", watchlistMapper);
        ReflectionTestUtils.setField(service, "stockBasicMapper", stockBasicMapper);
        ReflectionTestUtils.setField(service, "userContext", userContext);
        when(userContext.currentUserId()).thenReturn(7L);
        when(dailyActionMapper.selectList(any())).thenReturn(List.of());
        when(signalService.run(any())).thenReturn(List.of(StrategySignalEntity.builder()
                .code("600519")
                .strategyId("S1")
                .side("BUY")
                .build()));
        when(paperService.defaultAccount()).thenReturn(PaperAccount.builder().id(11L).build());
        when(paperPositionMapper.selectList(any())).thenReturn(List.of());
        when(stockBasicMapper.selectList(any())).thenReturn(List.of(StockBasic.builder()
                .code("600519")
                .name("贵州茅台")
                .build()));
        when(watchlistMapper.selectList(any())).thenReturn(List.of());

        List<DailyAction> actions = service.run(LocalDate.of(2026, 8, 16));

        assertEquals(7L, actions.get(0).getUserId());
        ArgumentCaptor<Wrapper<DailyAction>> deleteCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(dailyActionMapper).delete(deleteCaptor.capture());
        assertUserFilter(deleteCaptor.getValue());
        ArgumentCaptor<Wrapper<Watchlist>> watchlistCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(watchlistMapper).selectList(watchlistCaptor.capture());
        assertUserFilter(watchlistCaptor.getValue());
    }

    private void assertUserFilter(Wrapper<?> query) {
        assertTrue(query.getSqlSegment().contains("user_id"));
        AbstractWrapper<?, ?, ?> abstractQuery = (AbstractWrapper<?, ?, ?>) query;
        assertTrue(abstractQuery.getParamNameValuePairs().containsValue(7L));
    }
}
