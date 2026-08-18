package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.dto.DecisionItemResp;
import com.awe.apex.quant.domain.entity.ObservePool;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.ObservePoolMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ObservePoolUserIsolationTest {

    private final ObservePoolMapper observePoolMapper = mock(ObservePoolMapper.class);
    private final StockBasicMapper stockBasicMapper = mock(StockBasicMapper.class);
    private final BarDailyMapper barDailyMapper = mock(BarDailyMapper.class);
    private final ApexUserContext userContext = mock(ApexUserContext.class);
    private final ObservePoolServiceImpl service = new ObservePoolServiceImpl();

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ObservePool.class);
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "observePoolMapper", observePoolMapper);
        ReflectionTestUtils.setField(service, "stockBasicMapper", stockBasicMapper);
        ReflectionTestUtils.setField(service, "barDailyMapper", barDailyMapper);
        ReflectionTestUtils.setField(service, "userContext", userContext);
        when(userContext.currentUserId()).thenReturn(7L);
        when(observePoolMapper.selectList(any())).thenReturn(List.of());
    }

    @Test
    void alertAndRefreshQueriesFilterCurrentUser() {
        service.listReadyAlerts(6);
        service.refresh();

        ArgumentCaptor<Wrapper<ObservePool>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(observePoolMapper, times(2)).selectList(queryCaptor.capture());
        for (Wrapper<ObservePool> query : queryCaptor.getAllValues()) {
            assertUserFilter(query);
        }
    }

    @Test
    void activeCodeQueryFiltersCurrentUserAndArchivedRows() {
        when(observePoolMapper.selectList(any())).thenReturn(List.of(
                ObservePool.builder().code("600519").build(),
                ObservePool.builder().code("600519").build(),
                ObservePool.builder().code("000858").build()));

        List<String> codes = service.listActiveCodes();

        assertEquals(List.of("600519", "000858"), codes);
        ArgumentCaptor<Wrapper<ObservePool>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(observePoolMapper).selectList(queryCaptor.capture());
        Wrapper<ObservePool> query = queryCaptor.getValue();
        assertUserFilter(query);
        assertTrue(query.getSqlSegment().contains("status"));
        assertTrue(((AbstractWrapper<?, ?, ?>) query).getParamNameValuePairs().containsValue("ARCHIVED"));
    }

    @Test
    void decisionSyncScopesQueriesAndWritesOwner() {
        when(stockBasicMapper.selectOne(any())).thenReturn(StockBasic.builder()
                .code("600519")
                .name("贵州茅台")
                .latestPrice(new BigDecimal("100"))
                .build());
        when(observePoolMapper.selectOne(any())).thenReturn(null);
        DecisionItemResp item = DecisionItemResp.builder()
                .code("600519")
                .name("贵州茅台")
                .action("MOOD")
                .strategyId("MOOD")
                .score(new BigDecimal("70"))
                .reason("情绪观察")
                .build();

        service.syncFromDecision(List.of(item), List.of());

        ArgumentCaptor<Wrapper<ObservePool>> listCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(observePoolMapper, times(2)).selectList(listCaptor.capture());
        for (Wrapper<ObservePool> query : listCaptor.getAllValues()) {
            assertUserFilter(query);
        }
        ArgumentCaptor<Wrapper<ObservePool>> oneCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(observePoolMapper, times(3)).selectOne(oneCaptor.capture());
        for (Wrapper<ObservePool> query : oneCaptor.getAllValues()) {
            assertUserFilter(query);
        }
        ArgumentCaptor<ObservePool> rowCaptor = ArgumentCaptor.forClass(ObservePool.class);
        verify(observePoolMapper).insert(rowCaptor.capture());
        assertEquals(7L, rowCaptor.getValue().getUserId());
    }

    private void assertUserFilter(Wrapper<?> query) {
        assertTrue(query.getSqlSegment().contains("user_id"));
        AbstractWrapper<?, ?, ?> abstractQuery = (AbstractWrapper<?, ?, ?>) query;
        assertTrue(abstractQuery.getParamNameValuePairs().containsValue(7L));
    }
}
