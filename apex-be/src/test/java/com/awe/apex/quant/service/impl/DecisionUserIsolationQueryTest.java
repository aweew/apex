package com.awe.apex.quant.service.impl;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.entity.DailyAction;
import com.awe.apex.quant.mapper.DailyActionMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DecisionUserIsolationQueryTest {

    private final DecisionServiceImpl service = new DecisionServiceImpl();
    private final DailyActionMapper dailyActionMapper = mock(DailyActionMapper.class);
    private final ApexUserContext userContext = mock(ApexUserContext.class);

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), DailyAction.class);
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "dailyActionMapper", dailyActionMapper);
        ReflectionTestUtils.setField(service, "userContext", userContext);
        when(userContext.currentUserId()).thenReturn(7L);
        when(dailyActionMapper.selectList(any())).thenReturn(List.of());
    }

    @Test
    void adviceFiltersActionsByCurrentUser() {
        assertThrows(BusinessException.class,
                () -> service.advice(LocalDate.of(2026, 8, 16)));

        assertUserFilterCaptured();
    }

    @Test
    void historyFiltersDatesByCurrentUser() {
        service.history(10);

        assertUserFilterCaptured();
    }

    @Test
    void attributionFiltersDatesByCurrentUser() {
        service.attribution(10);

        assertUserFilterCaptured();
    }

    private void assertUserFilterCaptured() {
        ArgumentCaptor<Wrapper<DailyAction>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(dailyActionMapper).selectList(queryCaptor.capture());
        Wrapper<DailyAction> query = queryCaptor.getValue();
        assertTrue(query.getSqlSegment().contains("user_id"));
        AbstractWrapper<?, ?, ?> abstractQuery = (AbstractWrapper<?, ?, ?>) query;
        assertTrue(abstractQuery.getParamNameValuePairs().containsValue(7L));
    }
}
