package com.awe.apex.quant.bot.service;

import com.awe.apex.quant.bot.service.impl.BotToolServiceImpl;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.dto.BotHoldingInput;
import com.awe.apex.quant.domain.dto.BotToolReq;
import com.awe.apex.quant.domain.dto.PortfolioHoldingSaveReq;
import com.awe.apex.quant.domain.dto.PortfolioSummaryResp;
import com.awe.apex.quant.domain.entity.Portfolio;
import com.awe.apex.quant.domain.entity.PortfolioHolding;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.domain.enums.PortfolioTradeSourceEnum;
import com.awe.apex.quant.mapper.PortfolioMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.service.IPortfolioService;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BotToolServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Portfolio.class);
    }

    private BotToolServiceImpl service;
    private IPortfolioService portfolioService;
    private PortfolioMapper portfolioMapper;
    private StockBasicMapper stockBasicMapper;
    private ApexUserContext userContext;

    @BeforeEach
    void setUp() {
        service = new BotToolServiceImpl();
        portfolioService = mock(IPortfolioService.class);
        portfolioMapper = mock(PortfolioMapper.class);
        stockBasicMapper = mock(StockBasicMapper.class);
        userContext = mock(ApexUserContext.class);
        when(userContext.currentUserId()).thenReturn(7L);
        when(userContext.runAsUser(anyLong(), any(Supplier.class))).thenAnswer(invocation -> {
            Supplier<?> task = invocation.getArgument(1);
            return task.get();
        });
        ReflectionTestUtils.setField(service, "portfolioService", portfolioService);
        ReflectionTestUtils.setField(service, "portfolioMapper", portfolioMapper);
        ReflectionTestUtils.setField(service, "stockBasicMapper", stockBasicMapper);
        ReflectionTestUtils.setField(service, "userContext", userContext);
        ReflectionTestUtils.setField(service, "callAuditMapper", mock(com.awe.apex.quant.mapper.BotCallAuditMapper.class));
        ReflectionTestUtils.setField(service, "smartTraderAnalyticsService", mock(com.awe.apex.quant.service.ISmartTraderAnalyticsService.class));
    }

    @Test
    void importsHoldingForPortfolioOwnedByOtherUser() {
        when(portfolioMapper.selectList(any())).thenReturn(List.of(Portfolio.builder()
                .id(8L).userId(18L).name("来哥").status("ACTIVE").build()));
        when(stockBasicMapper.selectList(any())).thenReturn(List.of(StockBasic.builder()
                .code("603456").name("九洲药业").build()));
        when(portfolioService.detail(8L)).thenReturn(PortfolioSummaryResp.builder().holdings(List.of()).build());

        BotHoldingInput holding = new BotHoldingInput();
        holding.setName("九洲药业");
        holding.setQuantity(1000);
        holding.setCostPrice(new BigDecimal("24.057"));
        BotToolReq request = new BotToolReq();
        request.setOperation("HOLDING_IMPORT");
        request.setUserId("wechat-user");
        request.setConversationId("wechat-conversation");
        request.setRequestId("req-import-1");
        request.setPortfolioName("来哥");
        request.setHoldings(List.of(holding));

        service.execute(request);

        ArgumentCaptor<PortfolioHoldingSaveReq> requestCaptor = ArgumentCaptor.forClass(PortfolioHoldingSaveReq.class);
        verify(portfolioService).saveHolding(anyLong(), requestCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(PortfolioTradeSourceEnum.WECHAT_BOT),
                org.mockito.ArgumentMatchers.eq("req-import-1:603456"));
        verify(portfolioService).refreshQuotes(8L, false);
        assertEquals("603456", requestCaptor.getValue().getCode());
        assertEquals("九洲药业", requestCaptor.getValue().getName());
        ArgumentCaptor<Wrapper<Portfolio>> portfolioQueryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(portfolioMapper).selectList(portfolioQueryCaptor.capture());
        AbstractWrapper<?, ?, ?> portfolioQuery = (AbstractWrapper<?, ?, ?>) portfolioQueryCaptor.getValue();
        assertFalse(portfolioQuery.getSqlSegment().contains("user_id"));
        verify(userContext).runAsUser(eq(18L), any(Supplier.class));
    }

    @Test
    void botImportUpdatesByDifferenceWithoutReplacingUnchangedHolding() {
        Portfolio portfolio = Portfolio.builder().id(8L).userId(18L).name("来哥").status("ACTIVE").build();
        when(portfolioMapper.selectList(any())).thenReturn(List.of(portfolio));
        PortfolioHolding unchanged = PortfolioHolding.builder()
                .id(1L).portfolioId(8L).code("600519").name("贵州茅台")
                .quantity(100).costPrice(new BigDecimal("1500")).build();
        PortfolioHolding reduced = PortfolioHolding.builder()
                .id(2L).portfolioId(8L).code("000001").name("平安银行")
                .quantity(500).costPrice(new BigDecimal("12")).build();
        when(portfolioService.detail(8L)).thenReturn(PortfolioSummaryResp.builder()
                .holdings(List.of(unchanged, reduced)).build());

        BotHoldingInput first = new BotHoldingInput();
        first.setCode("600519");
        first.setName("贵州茅台");
        first.setQuantity(100);
        first.setCostPrice(new BigDecimal("1500"));
        BotHoldingInput second = new BotHoldingInput();
        second.setCode("000001");
        second.setName("平安银行");
        second.setQuantity(200);
        second.setCostPrice(new BigDecimal("12"));
        BotToolReq request = new BotToolReq();
        request.setOperation("HOLDING_IMPORT");
        request.setUserId("wechat-user");
        request.setConversationId("wechat-conversation");
        request.setRequestId("req-import-2");
        request.setPortfolioName("来哥");
        request.setHoldings(List.of(first, second));

        service.execute(request);

        ArgumentCaptor<PortfolioHoldingSaveReq> requestCaptor = ArgumentCaptor.forClass(PortfolioHoldingSaveReq.class);
        verify(portfolioService).saveHolding(org.mockito.ArgumentMatchers.eq(8L), requestCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(PortfolioTradeSourceEnum.WECHAT_BOT),
                org.mockito.ArgumentMatchers.eq("req-import-2:000001"));
        assertEquals(2L, requestCaptor.getValue().getId());
        assertEquals(200, requestCaptor.getValue().getQuantity());
        verify(portfolioService, org.mockito.Mockito.never()).removeHolding(anyLong(), anyLong(),
                any(), any());
    }
}
