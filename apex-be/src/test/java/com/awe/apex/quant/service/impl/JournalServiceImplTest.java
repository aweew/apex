package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.domain.entity.DailyAction;
import com.awe.apex.quant.domain.entity.JournalTrade;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.dto.JournalCreateReq;
import com.awe.apex.quant.mapper.DailyActionMapper;
import com.awe.apex.quant.mapper.JournalTradeMapper;
import com.awe.apex.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class JournalServiceImplTest {

    private final JournalServiceImpl service = new JournalServiceImpl();
    private final JournalTradeMapper journalTradeMapper = mock(JournalTradeMapper.class);
    private final DailyActionMapper dailyActionMapper = mock(DailyActionMapper.class);
    private final ApexUserContext userContext = mock(ApexUserContext.class);

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, JournalTrade.class);
        TableInfoHelper.initTableInfo(assistant, DailyAction.class);
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "journalTradeMapper", journalTradeMapper);
        ReflectionTestUtils.setField(service, "dailyActionMapper", dailyActionMapper);
        ReflectionTestUtils.setField(service, "userContext", userContext);
        when(userContext.currentUserId()).thenReturn(7L);
    }

    @Test
    void mapsReductionActionToSellTrade() {
        DailyAction reduction = DailyAction.builder()
                .id(8L)
                .actionDate(LocalDate.of(2026, 8, 13))
                .code("600000")
                .action("REDUCE")
                .strategyId("PORTFOLIO_DRAWDOWN")
                .build();
        when(dailyActionMapper.selectOne(any())).thenReturn(reduction);

        JournalTrade trade = service.fromAction(8L, new BigDecimal("10.20"), 300);

        assertEquals("SELL", trade.getSide());
        assertEquals(new BigDecimal("3060.00"), trade.getAmount());
        assertEquals(8L, trade.getRelatedActionId());
        assertEquals(7L, trade.getUserId());
    }

    @Test
    void createWritesCurrentOwner() {
        JournalCreateReq request = new JournalCreateReq();
        request.setTradeDate("2026-08-16");
        request.setCode("600519");
        request.setSide("BUY");
        request.setPrice(new BigDecimal("10.00"));
        request.setQuantity(100);

        JournalTrade trade = service.create(request);

        assertEquals(7L, trade.getUserId());
    }

    @Test
    void latestFiltersCurrentUser() {
        when(journalTradeMapper.selectList(any())).thenReturn(List.of());

        service.latest(50);

        ArgumentCaptor<Wrapper<JournalTrade>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(journalTradeMapper).selectList(queryCaptor.capture());
        assertUserFilter(queryCaptor.getValue());
    }

    @Test
    void fromActionFiltersCurrentUser() {
        when(dailyActionMapper.selectOne(any())).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> service.fromAction(8L, new BigDecimal("10.20"), 300));

        ArgumentCaptor<Wrapper<DailyAction>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(dailyActionMapper).selectOne(queryCaptor.capture());
        assertUserFilter(queryCaptor.getValue());
    }

    @Test
    void createRejectsForeignRelatedAction() {
        when(dailyActionMapper.selectOne(any())).thenReturn(null);
        JournalCreateReq request = new JournalCreateReq();
        request.setTradeDate("2026-08-16");
        request.setCode("600519");
        request.setSide("BUY");
        request.setPrice(new BigDecimal("10.00"));
        request.setQuantity(100);
        request.setRelatedActionId(99L);

        assertThrows(BusinessException.class, () -> service.create(request));
    }

    private void assertUserFilter(Wrapper<?> query) {
        assertTrue(query.getSqlSegment().contains("user_id"));
        AbstractWrapper<?, ?, ?> abstractQuery = (AbstractWrapper<?, ?, ?>) query;
        assertTrue(abstractQuery.getParamNameValuePairs().containsValue(7L));
    }
}
