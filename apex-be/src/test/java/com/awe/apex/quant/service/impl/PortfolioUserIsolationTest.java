package com.awe.apex.quant.service.impl;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.dto.PortfolioSaveReq;
import com.awe.apex.quant.domain.dto.PortfolioSummaryResp;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        TableInfoHelper.initTableInfo(assistant, PortfolioHolding.class);
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
    void activeHoldingCodesUseAllCurrentUsersActivePortfoliosAndDeduplicate() {
        doReturn(Portfolio.builder().id(1L).userId(7L).build()).when(service).ensureDefaultPortfolio();
        when(portfolioMapper.selectList(any())).thenReturn(List.of(
                Portfolio.builder().id(1L).userId(7L).status("ACTIVE").build(),
                Portfolio.builder().id(2L).userId(7L).status("ACTIVE").build()));
        when(portfolioHoldingMapper.selectList(any())).thenReturn(List.of(
                PortfolioHolding.builder().portfolioId(1L).code("SH.600519").quantity(100).build(),
                PortfolioHolding.builder().portfolioId(2L).code("600519").quantity(200).build(),
                PortfolioHolding.builder().portfolioId(2L).code("000001").quantity(50).build(),
                PortfolioHolding.builder().portfolioId(2L).code("300750").quantity(0).build()));

        List<String> codes = service.listActiveHoldingCodes();

        assertEquals(List.of("600519", "000001"), codes);
        ArgumentCaptor<Wrapper<Portfolio>> portfolioQueryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(portfolioMapper).selectList(portfolioQueryCaptor.capture());
        assertUserFilter(portfolioQueryCaptor.getValue());
        assertTrue(portfolioQueryCaptor.getValue().getSqlSegment().contains("status"));
        ArgumentCaptor<Wrapper<PortfolioHolding>> holdingQueryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(portfolioHoldingMapper).selectList(holdingQueryCaptor.capture());
        assertTrue(holdingQueryCaptor.getValue().getSqlSegment().contains("portfolio_id"));
        assertTrue(holdingQueryCaptor.getValue().getSqlSegment().contains("quantity"));
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
    void portfolioListIsSharedAndUsesCurrentUserView() {
        doReturn(Portfolio.builder().id(1L).userId(7L).build()).when(service).ensureDefaultPortfolio();
        Portfolio ownPortfolio = Portfolio.builder()
                .id(1L)
                .userId(7L)
                .name("Awe")
                .ownerLabel("Awe")
                .isDefault(1)
                .status("ACTIVE")
                .build();
        Portfolio otherPortfolio = Portfolio.builder()
                .id(5L)
                .userId(9L)
                .name("郑十万")
                .ownerLabel("郑十万")
                .isDefault(1)
                .status("ACTIVE")
                .build();
        when(portfolioMapper.selectList(any())).thenReturn(List.of(ownPortfolio, otherPortfolio));
        when(portfolioHoldingMapper.selectList(any())).thenReturn(List.of());

        List<PortfolioSummaryResp> portfolios = service.listPortfolios(false);

        assertEquals(2, portfolios.size());
        assertEquals("我的持仓", portfolios.get(0).getName());
        assertTrue(portfolios.get(0).getIsDefault());
        assertTrue(portfolios.get(0).getEditable());
        assertEquals("郑十万", portfolios.get(1).getName());
        assertFalse(portfolios.get(1).getIsDefault());
        assertFalse(portfolios.get(1).getEditable());
        ArgumentCaptor<Wrapper<Portfolio>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(portfolioMapper).selectList(queryCaptor.capture());
        assertFalse(queryCaptor.getValue().getSqlSegment().contains("user_id"));
    }

    @Test
    void otherUsersPortfolioDetailAndDailyHistoryAreReadable() {
        doReturn(Portfolio.builder().id(1L).userId(7L).build()).when(service).ensureDefaultPortfolio();
        Portfolio otherPortfolio = Portfolio.builder()
                .id(5L)
                .userId(9L)
                .name("郑十万")
                .isDefault(1)
                .status("ACTIVE")
                .build();
        when(portfolioMapper.selectById(5L)).thenReturn(otherPortfolio);
        when(portfolioHoldingMapper.selectList(any())).thenReturn(List.of());
        when(portfolioDailyMapper.selectList(any())).thenReturn(List.of(PortfolioDaily.builder().id(3L).build()));

        PortfolioSummaryResp detail = service.detail(5L);
        List<PortfolioDaily> dailyList = service.listDaily(5L, 30);

        assertEquals("郑十万", detail.getName());
        assertFalse(detail.getEditable());
        assertEquals(1, dailyList.size());
    }

    @Test
    void otherUsersPortfolioCannotBeModifiedOrSnapshotted() {
        doReturn(Portfolio.builder().id(1L).userId(7L).build()).when(service).ensureDefaultPortfolio();
        Portfolio otherPortfolio = Portfolio.builder()
                .id(5L)
                .userId(9L)
                .name("郑十万")
                .isDefault(1)
                .status("ACTIVE")
                .build();
        when(portfolioMapper.selectById(5L)).thenReturn(otherPortfolio);
        PortfolioSaveReq request = new PortfolioSaveReq();
        request.setId(5L);
        request.setName("修改后的名称");

        BusinessException saveException = assertThrows(BusinessException.class,
                () -> service.savePortfolio(request));
        BusinessException snapshotException = assertThrows(BusinessException.class,
                () -> service.snapshot(5L));

        assertEquals("无权修改该组合", saveException.getMessage());
        assertEquals("无权修改该组合", snapshotException.getMessage());
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
