package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.dto.PortfolioHoldingSaveReq;
import com.awe.apex.quant.domain.entity.Portfolio;
import com.awe.apex.quant.domain.entity.PortfolioHolding;
import com.awe.apex.quant.domain.entity.MyHolding;
import com.awe.apex.quant.domain.enums.PortfolioTradeSourceEnum;
import com.awe.apex.quant.mapper.MyHoldingMapper;
import com.awe.apex.quant.mapper.PortfolioDailyMapper;
import com.awe.apex.quant.mapper.PortfolioHoldingMapper;
import com.awe.apex.quant.mapper.PortfolioMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.service.IPortfolioTradeRecordService;
import com.awe.apex.quant.service.ApexUserAuthService;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioTradeMutationTest {

    private final PortfolioServiceImpl service = new PortfolioServiceImpl();
    private final PortfolioMapper portfolioMapper = mock(PortfolioMapper.class);
    private final PortfolioHoldingMapper portfolioHoldingMapper = mock(PortfolioHoldingMapper.class);
    private final PortfolioDailyMapper portfolioDailyMapper = mock(PortfolioDailyMapper.class);
    private final MyHoldingMapper myHoldingMapper = mock(MyHoldingMapper.class);
    private final StockBasicMapper stockBasicMapper = mock(StockBasicMapper.class);
    private final IPortfolioTradeRecordService tradeRecordService = mock(IPortfolioTradeRecordService.class);
    private final ApexUserAuthService userAuthService = mock(ApexUserAuthService.class);
    private final ApexUserContext userContext = mock(ApexUserContext.class);

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), PortfolioHolding.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), MyHolding.class);
        ReflectionTestUtils.setField(service, "portfolioMapper", portfolioMapper);
        ReflectionTestUtils.setField(service, "portfolioHoldingMapper", portfolioHoldingMapper);
        ReflectionTestUtils.setField(service, "portfolioDailyMapper", portfolioDailyMapper);
        ReflectionTestUtils.setField(service, "myHoldingMapper", myHoldingMapper);
        ReflectionTestUtils.setField(service, "stockBasicMapper", stockBasicMapper);
        ReflectionTestUtils.setField(service, "tradeRecordService", tradeRecordService);
        ReflectionTestUtils.setField(service, "userAuthService", userAuthService);
        ReflectionTestUtils.setField(service, "userContext", userContext);
        when(userContext.currentUserIdOrNull()).thenReturn(7L);
    }

    @Test
    void savingLowerQuantityRecordsOnlyTheReduction() {
        Portfolio portfolio = Portfolio.builder().id(11L).userId(7L).name("疯锅").ownerLabel("张三").isDefault(0).build();
        PortfolioHolding existing = PortfolioHolding.builder()
                .id(21L).portfolioId(11L).code("600519").name("贵州茅台").quantity(500).build();
        when(portfolioMapper.selectById(11L)).thenReturn(portfolio);
        when(portfolioHoldingMapper.selectById(21L)).thenReturn(existing);
        when(portfolioDailyMapper.selectOne(any())).thenReturn(null);
        PortfolioHoldingSaveReq request = new PortfolioHoldingSaveReq();
        request.setId(21L);
        request.setCode("600519");
        request.setName("贵州茅台");
        request.setQuantity(200);
        request.setCostPrice(new BigDecimal("1500"));
        request.setTradePrice(new BigDecimal("1520.50"));
        request.setTradeTime(LocalDateTime.of(2026, 8, 17, 14, 10));

        service.saveHolding(11L, request);

        verify(tradeRecordService).recordChange(portfolio, "600519", "贵州茅台", 500, 200,
                new BigDecimal("1520.50"), LocalDateTime.of(2026, 8, 17, 14, 10),
                PortfolioTradeSourceEnum.PORTFOLIO_WEB, null);
    }

    @Test
    void deletingHoldingRecordsClearBeforeReturning() {
        Portfolio portfolio = Portfolio.builder().id(11L).userId(7L).name("疯锅").isDefault(0).build();
        PortfolioHolding existing = PortfolioHolding.builder()
                .id(21L).portfolioId(11L).code("600519").name("贵州茅台").quantity(100).build();
        when(portfolioMapper.selectById(11L)).thenReturn(portfolio);
        when(portfolioHoldingMapper.selectById(21L)).thenReturn(existing);
        when(portfolioDailyMapper.selectOne(any())).thenReturn(null);

        service.removeHolding(11L, 21L);

        verify(tradeRecordService).recordChange(portfolio, "600519", "贵州茅台", 100, 0,
                null, null, PortfolioTradeSourceEnum.PORTFOLIO_WEB, null);
    }

    @Test
    void adminChangingOtherUsersDefaultPortfolioMirrorsToPortfolioOwner() {
        when(userContext.currentUserIdOrNull()).thenReturn(1L);
        when(userAuthService.isAdmin(1L)).thenReturn(true);
        Portfolio otherDefault = Portfolio.builder()
                .id(11L)
                .userId(9L)
                .name("郑十万")
                .isDefault(1)
                .build();
        PortfolioHolding existing = PortfolioHolding.builder()
                .id(21L).portfolioId(11L).code("600519").name("贵州茅台").quantity(100).build();
        when(portfolioMapper.selectById(11L)).thenReturn(otherDefault);
        when(portfolioHoldingMapper.selectById(21L)).thenReturn(existing);
        when(portfolioDailyMapper.selectOne(any())).thenReturn(null);
        when(myHoldingMapper.selectOne(any())).thenReturn(null);
        PortfolioHoldingSaveReq request = new PortfolioHoldingSaveReq();
        request.setId(21L);
        request.setCode("600519");
        request.setName("贵州茅台");
        request.setQuantity(200);

        service.saveHolding(11L, request);

        ArgumentCaptor<Wrapper<MyHolding>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(myHoldingMapper).selectOne(queryCaptor.capture());
        AbstractWrapper<?, ?, ?> query = (AbstractWrapper<?, ?, ?>) queryCaptor.getValue();
        assertTrue(query.getSqlSegment().contains("user_id"));
        assertTrue(query.getParamNameValuePairs().containsValue(9L));
        ArgumentCaptor<MyHolding> holdingCaptor = ArgumentCaptor.forClass(MyHolding.class);
        verify(myHoldingMapper).insert(holdingCaptor.capture());
        assertEquals(9L, holdingCaptor.getValue().getUserId());
    }
}
