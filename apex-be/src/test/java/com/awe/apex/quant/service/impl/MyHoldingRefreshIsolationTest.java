package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.entity.MyHolding;
import com.awe.apex.quant.mapper.MyHoldingMapper;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MyHoldingRefreshIsolationTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), MyHolding.class);
    }

    @Test
    void refreshQueriesOnlyCurrentUsersHoldings() {
        MyHoldingServiceImpl service = new MyHoldingServiceImpl();
        MyHoldingMapper myHoldingMapper = mock(MyHoldingMapper.class);
        ApexUserContext userContext = mock(ApexUserContext.class);
        ReflectionTestUtils.setField(service, "myHoldingMapper", myHoldingMapper);
        ReflectionTestUtils.setField(service, "userContext", userContext);
        when(userContext.currentUserId()).thenReturn(7L);
        when(myHoldingMapper.selectList(any())).thenReturn(List.of());

        service.refreshQuotes(true);

        ArgumentCaptor<Wrapper<MyHolding>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(myHoldingMapper, times(2)).selectList(queryCaptor.capture());
        for (Wrapper<MyHolding> query : queryCaptor.getAllValues()) {
            assertTrue(query.getSqlSegment().contains("user_id"));
            AbstractWrapper<?, ?, ?> abstractQuery = (AbstractWrapper<?, ?, ?>) query;
            assertTrue(abstractQuery.getParamNameValuePairs().containsValue(7L));
        }
    }
}
