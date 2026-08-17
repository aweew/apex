package com.awe.apex.quant.service.impl;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.dto.PortfolioSaveReq;
import com.awe.apex.quant.domain.entity.MyHolding;
import com.awe.apex.quant.domain.entity.Portfolio;
import com.awe.apex.quant.domain.entity.PortfolioDaily;
import com.awe.apex.quant.domain.entity.PortfolioHolding;
import com.awe.apex.quant.mapper.MyHoldingMapper;
import com.awe.apex.quant.mapper.PortfolioDailyMapper;
import com.awe.apex.quant.mapper.PortfolioHoldingMapper;
import com.awe.apex.quant.mapper.PortfolioMapper;
import com.awe.apex.quant.service.IPortfolioTradeRecordService;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PortfolioUserIsolationTest {

    private final PortfolioMapper portfolioMapper = mock(PortfolioMapper.class);
    private final PortfolioHoldingMapper portfolioHoldingMapper = mock(PortfolioHoldingMapper.class);
    private final PortfolioDailyMapper portfolioDailyMapper = mock(PortfolioDailyMapper.class);
    private final MyHoldingMapper myHoldingMapper = mock(MyHoldingMapper.class);
    private final ApexUserContext userContext = mock(ApexUserContext.class);
    private final IPortfolioTradeRecordService tradeRecordService = mock(IPortfolioTradeRecordService.class);
    private final PortfolioServiceImpl service = spy(new PortfolioServiceImpl());

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Portfolio.class);
        TableInfoHelper.initTableInfo(assistant, MyHolding.class);
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "portfolioMapper", portfolioMapper);
        ReflectionTestUtils.setField(service, "portfolioHoldingMapper", portfolioHoldingMapper);
        ReflectionTestUtils.setField(service, "portfolioDailyMapper", portfolioDailyMapper);
        ReflectionTestUtils.setField(service, "myHoldingMapper", myHoldingMapper);
        ReflectionTestUtils.setField(service, "tradeRecordService", tradeRecordService);
        ReflectionTestUtils.setField(service, "userContext", userContext);
        when(userContext.currentUserIdOrNull()).thenReturn(7L);
    }

    @Test
    void newPortfolioStoresCurrentOwner() {
        when(portfolioMapper.selectCount(any())).thenReturn(0L);
        PortfolioSaveReq request = new PortfolioSaveReq();
        request.setName("成长组合");

        Portfolio portfolio = service.savePortfolio(request);

        assertEquals(7L, portfolio.getUserId());
    }

    @Test
    void bulkPortfolioOperationsFilterCurrentUser() {
        doReturn(Portfolio.builder().id(1L).userId(7L).build()).when(service).ensureDefaultPortfolio();
        when(portfolioMapper.selectList(any())).thenReturn(List.of());

        service.snapshotAll();
        service.refreshQuotesAll(false);

        ArgumentCaptor<Wrapper<Portfolio>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(portfolioMapper, times(2)).selectList(queryCaptor.capture());
        for (Wrapper<Portfolio> query : queryCaptor.getAllValues()) {
            assertUserFilter(query);
        }
    }

    @Test
    void defaultHoldingMirrorScopesLookupAndWritesOwner() {
        when(myHoldingMapper.selectOne(any())).thenReturn(null);
        PortfolioHolding holding = PortfolioHolding.builder()
                .code("600519")
                .quantity(100)
                .costPrice(new BigDecimal("100"))
                .build();

        ReflectionTestUtils.invokeMethod(service, "mirrorToMyHolding", holding);

        ArgumentCaptor<Wrapper<MyHolding>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(myHoldingMapper).selectOne(queryCaptor.capture());
        assertUserFilter(queryCaptor.getValue());
        ArgumentCaptor<MyHolding> holdingCaptor = ArgumentCaptor.forClass(MyHolding.class);
        verify(myHoldingMapper).insert(holdingCaptor.capture());
        assertEquals(7L, holdingCaptor.getValue().getUserId());
    }

    @Test
    void removingDefaultHoldingScopesMirrorDeleteLookup() {
        Portfolio portfolio = Portfolio.builder().id(1L).userId(7L).isDefault(1).build();
        PortfolioHolding holding = PortfolioHolding.builder()
                .id(10L)
                .portfolioId(1L)
                .code("600519")
                .build();
        when(portfolioMapper.selectById(1L)).thenReturn(portfolio);
        when(portfolioHoldingMapper.selectById(10L)).thenReturn(holding);
        when(myHoldingMapper.selectOne(any())).thenReturn(null);
        when(portfolioDailyMapper.selectOne(any())).thenReturn(null);

        service.removeHolding(1L, 10L);

        ArgumentCaptor<Wrapper<MyHolding>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(myHoldingMapper).selectOne(queryCaptor.capture());
        assertUserFilter(queryCaptor.getValue());
    }

    @Test
    void rejectsPrivatePortfolioOperationsWithoutUserContext() {
        when(userContext.currentUserIdOrNull()).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.listPortfolios(false));

        assertEquals("未获取到当前用户", exception.getMessage());
        verifyNoInteractions(portfolioMapper);
    }

    private void assertUserFilter(Wrapper<?> query) {
        assertTrue(query.getSqlSegment().contains("user_id"));
        AbstractWrapper<?, ?, ?> abstractQuery = (AbstractWrapper<?, ?, ?>) query;
        assertTrue(abstractQuery.getParamNameValuePairs().containsValue(7L));
    }
}
