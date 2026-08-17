package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.entity.ObservePool;
import com.awe.apex.quant.domain.entity.Watchlist;
import com.awe.apex.quant.mapper.ObservePoolMapper;
import com.awe.apex.quant.mapper.WatchlistMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ValuationUniverseIsolationTest {

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Watchlist.class);
        TableInfoHelper.initTableInfo(assistant, ObservePool.class);
    }

    @Test
    void watchlistScreenFiltersCurrentUser() {
        ValuationServiceImpl service = new ValuationServiceImpl();
        WatchlistMapper watchlistMapper = mock(WatchlistMapper.class);
        ApexUserContext userContext = mock(ApexUserContext.class);
        ReflectionTestUtils.setField(service, "watchlistMapper", watchlistMapper);
        ReflectionTestUtils.setField(service, "userContext", userContext);
        when(userContext.currentUserId()).thenReturn(7L);
        when(watchlistMapper.selectList(any())).thenReturn(List.of());

        service.screen("watchlist", 20, null);

        ArgumentCaptor<Wrapper<Watchlist>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(watchlistMapper).selectList(queryCaptor.capture());
        assertUserFilter(queryCaptor.getValue());
    }

    @Test
    void observeScreenFiltersCurrentUser() {
        ValuationServiceImpl service = new ValuationServiceImpl();
        ObservePoolMapper observePoolMapper = mock(ObservePoolMapper.class);
        ApexUserContext userContext = mock(ApexUserContext.class);
        ReflectionTestUtils.setField(service, "observePoolMapper", observePoolMapper);
        ReflectionTestUtils.setField(service, "userContext", userContext);
        when(userContext.currentUserId()).thenReturn(7L);
        when(observePoolMapper.selectList(any())).thenReturn(List.of());

        service.screen("observe", 20, null);

        ArgumentCaptor<Wrapper<ObservePool>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(observePoolMapper).selectList(queryCaptor.capture());
        assertUserFilter(queryCaptor.getValue());
    }

    private void assertUserFilter(Wrapper<?> query) {
        assertTrue(query.getSqlSegment().contains("user_id"));
        AbstractWrapper<?, ?, ?> abstractQuery = (AbstractWrapper<?, ?, ?>) query;
        assertTrue(abstractQuery.getParamNameValuePairs().containsValue(7L));
    }
}
