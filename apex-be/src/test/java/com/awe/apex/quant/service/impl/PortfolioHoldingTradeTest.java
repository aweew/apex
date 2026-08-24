package com.awe.apex.quant.service.impl;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.dto.HoldingTradeReq;
import com.awe.apex.quant.domain.entity.JournalTrade;
import com.awe.apex.quant.domain.entity.Portfolio;
import com.awe.apex.quant.domain.entity.PortfolioHolding;
import com.awe.apex.quant.domain.enums.PortfolioTradeSourceEnum;
import com.awe.apex.quant.mapper.MyHoldingMapper;
import com.awe.apex.quant.mapper.PortfolioDailyMapper;
import com.awe.apex.quant.mapper.PortfolioHoldingMapper;
import com.awe.apex.quant.mapper.PortfolioMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.service.IPortfolioTradeRecordService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortfolioHoldingTradeTest {

    private final PortfolioServiceImpl service = new PortfolioServiceImpl();
    private final PortfolioMapper portfolioMapper = mock(PortfolioMapper.class);
    private final PortfolioHoldingMapper portfolioHoldingMapper = mock(PortfolioHoldingMapper.class);
    private final PortfolioDailyMapper portfolioDailyMapper = mock(PortfolioDailyMapper.class);
    private final MyHoldingMapper myHoldingMapper = mock(MyHoldingMapper.class);
    private final StockBasicMapper stockBasicMapper = mock(StockBasicMapper.class);
    private final IPortfolioTradeRecordService tradeRecordService = mock(IPortfolioTradeRecordService.class);
    private final ApexUserContext userContext = mock(ApexUserContext.class);

    private final Portfolio portfolio = Portfolio.builder()
            .id(11L)
            .userId(7L)
            .name("疯锅")
            .ownerLabel("张三")
            .isDefault(0)
            .build();

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                PortfolioHolding.class);
        ReflectionTestUtils.setField(service, "portfolioMapper", portfolioMapper);
        ReflectionTestUtils.setField(service, "portfolioHoldingMapper", portfolioHoldingMapper);
        ReflectionTestUtils.setField(service, "portfolioDailyMapper", portfolioDailyMapper);
        ReflectionTestUtils.setField(service, "myHoldingMapper", myHoldingMapper);
        ReflectionTestUtils.setField(service, "stockBasicMapper", stockBasicMapper);
        ReflectionTestUtils.setField(service, "tradeRecordService", tradeRecordService);
        ReflectionTestUtils.setField(service, "userContext", userContext);
        when(userContext.currentUserIdOrNull()).thenReturn(7L);
        when(portfolioMapper.selectById(11L)).thenReturn(portfolio);
        when(portfolioDailyMapper.selectOne(any())).thenReturn(null);
    }

    @Test
    void buyingExistingHoldingUsesWeightedAverageCost() {
        PortfolioHolding existing = holding(100, "10.0000");
        when(portfolioHoldingMapper.selectById(21L)).thenReturn(existing);
        LocalDateTime tradeTime = LocalDateTime.of(2026, 8, 18, 10, 15);

        PortfolioHolding result = service.tradeHolding(11L,
                tradeRequest("BUY", 50, "16.0000", tradeTime));

        assertEquals(150, result.getQuantity());
        assertEquals(new BigDecimal("12.0000"), result.getCostPrice());
        verify(tradeRecordService).recordChange(portfolio, "600519", "贵州茅台", 100, 150,
                new BigDecimal("16.0000"), tradeTime, PortfolioTradeSourceEnum.PORTFOLIO_WEB, null);
    }

    @Test
    void partiallySellingKeepsHoldingCost() {
        PortfolioHolding existing = holding(100, "10.0000");
        when(portfolioHoldingMapper.selectById(21L)).thenReturn(existing);
        LocalDateTime tradeTime = LocalDateTime.of(2026, 8, 18, 13, 45);

        PortfolioHolding result = service.tradeHolding(11L,
                tradeRequest("SELL", 40, "12.5000", tradeTime));

        assertEquals(60, result.getQuantity());
        assertEquals(new BigDecimal("10.0000"), result.getCostPrice());
        verify(tradeRecordService).recordChange(portfolio, "600519", "贵州茅台", 100, 60,
                new BigDecimal("12.5000"), tradeTime, PortfolioTradeSourceEnum.PORTFOLIO_WEB, null);
    }

    @Test
    void sellingAllDeletesHoldingAndRecordsActualTrade() {
        PortfolioHolding existing = holding(100, "10.0000");
        when(portfolioHoldingMapper.selectById(21L)).thenReturn(existing);
        LocalDateTime tradeTime = LocalDateTime.of(2026, 8, 18, 14, 50);

        PortfolioHolding result = service.tradeHolding(11L,
                tradeRequest("SELL", 100, "13.2000", tradeTime));

        assertNull(result);
        verify(portfolioHoldingMapper).deleteById(21L);
        verify(tradeRecordService).recordChange(portfolio, "600519", "贵州茅台", 100, 0,
                new BigDecimal("13.2000"), tradeTime, PortfolioTradeSourceEnum.PORTFOLIO_WEB, null);
    }

    @Test
    void sellingMoreThanCurrentQuantityIsRejected() {
        when(portfolioHoldingMapper.selectById(21L)).thenReturn(holding(100, "10.0000"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.tradeHolding(11L, tradeRequest("SELL", 101, "13.2000", null)));

        assertEquals("卖出数量不能超过当前持仓", exception.getMessage());
        verify(portfolioHoldingMapper, never()).deleteById(any());
        verify(tradeRecordService, never()).recordChange(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void nonPositiveTradePriceIsRejected() {
        when(portfolioHoldingMapper.selectById(21L)).thenReturn(holding(100, "10.0000"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.tradeHolding(11L, tradeRequest("BUY", 10, "0", null)));

        assertEquals("成交价必须大于0", exception.getMessage());
    }

    @Test
    void duplicateBotBuyReturnsTheAlreadyUpdatedHolding() {
        PortfolioHolding existing = holding(200, "12.0000");
        when(portfolioHoldingMapper.selectById(21L)).thenReturn(existing);
        when(tradeRecordService.findBySourceRef(7L, "600519", PortfolioTradeSourceEnum.WECHAT_BOT,
                "wechat-message-1:600519")).thenReturn(JournalTrade.builder()
                .portfolioId(11L).code("600519").side("BUY").quantity(100).afterQuantity(200)
                .price(new BigDecimal("14.0000")).build());

        PortfolioHolding result = service.tradeHolding(11L, tradeRequest("BUY", 100, "14.0000", null),
                PortfolioTradeSourceEnum.WECHAT_BOT, "wechat-message-1:600519");

        assertSame(existing, result);
        verify(portfolioHoldingMapper, never()).update(any(), any());
        verify(tradeRecordService, never()).recordChange(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    private PortfolioHolding holding(Integer quantity, String costPrice) {
        return PortfolioHolding.builder()
                .id(21L)
                .portfolioId(11L)
                .code("600519")
                .name("贵州茅台")
                .quantity(quantity)
                .costPrice(new BigDecimal(costPrice))
                .build();
    }

    private HoldingTradeReq tradeRequest(String side, Integer quantity, String tradePrice,
                                         LocalDateTime tradeTime) {
        HoldingTradeReq request = new HoldingTradeReq();
        request.setHoldingId(21L);
        request.setSide(side);
        request.setQuantity(quantity);
        request.setTradePrice(new BigDecimal(tradePrice));
        request.setTradeTime(tradeTime);
        return request;
    }
}
