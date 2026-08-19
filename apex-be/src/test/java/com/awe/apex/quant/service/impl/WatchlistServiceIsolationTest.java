package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.entity.Watchlist;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WatchlistServiceIsolationTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Watchlist.class);
    }

    @Test
    void quoteAndCodeQueriesFilterCurrentUser() {
        WatchlistServiceImpl service = new WatchlistServiceImpl();
        WatchlistMapper watchlistMapper = mock(WatchlistMapper.class);
        ApexUserContext userContext = mock(ApexUserContext.class);
        ReflectionTestUtils.setField(service, "baseMapper", watchlistMapper);
        ReflectionTestUtils.setField(service, "userContext", userContext);
        when(userContext.currentUserId()).thenReturn(7L);
        when(watchlistMapper.selectList(any())).thenReturn(List.of());

        assertEquals(List.of(), service.listWatchlistCodes("我的自选"));
        assertEquals(0, service.refreshQuotes("我的自选", 80, false).get("successCount"));

        ArgumentCaptor<Wrapper<Watchlist>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(watchlistMapper, times(2)).selectList(queryCaptor.capture());
        for (Wrapper<Watchlist> query : queryCaptor.getAllValues()) {
            assertTrue(query.getSqlSegment().contains("user_id"));
            AbstractWrapper<?, ?, ?> abstractQuery = (AbstractWrapper<?, ?, ?>) query;
            assertTrue(abstractQuery.getParamNameValuePairs().containsValue(7L));
        }
    }
}
