package com.awe.apex.quant.bot.service;

import com.awe.apex.quant.bot.service.impl.BotToolServiceImpl;
import com.awe.apex.quant.domain.dto.BotHoldingInput;
import com.awe.apex.quant.domain.dto.BotToolReq;
import com.awe.apex.quant.domain.dto.PortfolioHoldingSaveReq;
import com.awe.apex.quant.domain.dto.PortfolioSummaryResp;
import com.awe.apex.quant.domain.entity.Portfolio;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.mapper.PortfolioMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.service.IPortfolioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BotToolServiceImplTest {

    private BotToolServiceImpl service;
    private IPortfolioService portfolioService;
    private PortfolioMapper portfolioMapper;
    private StockBasicMapper stockBasicMapper;

    @BeforeEach
    void setUp() {
        service = new BotToolServiceImpl();
        portfolioService = mock(IPortfolioService.class);
        portfolioMapper = mock(PortfolioMapper.class);
        stockBasicMapper = mock(StockBasicMapper.class);
        ReflectionTestUtils.setField(service, "portfolioService", portfolioService);
        ReflectionTestUtils.setField(service, "portfolioMapper", portfolioMapper);
        ReflectionTestUtils.setField(service, "stockBasicMapper", stockBasicMapper);
        ReflectionTestUtils.setField(service, "callAuditMapper", mock(com.awe.apex.quant.mapper.BotCallAuditMapper.class));
        ReflectionTestUtils.setField(service, "smartTraderAnalyticsService", mock(com.awe.apex.quant.service.ISmartTraderAnalyticsService.class));
    }

    @Test
    void importsHoldingResolvedByExactStockName() {
        when(portfolioMapper.selectList(any())).thenReturn(List.of(Portfolio.builder()
                .id(8L).name("来哥").status("ACTIVE").build()));
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
        request.setPortfolioName("来哥");
        request.setHoldings(List.of(holding));

        service.execute(request);

        ArgumentCaptor<PortfolioHoldingSaveReq> requestCaptor = ArgumentCaptor.forClass(PortfolioHoldingSaveReq.class);
        verify(portfolioService).saveHolding(anyLong(), requestCaptor.capture());
        verify(portfolioService).refreshQuotes(8L, false);
        assertEquals("603456", requestCaptor.getValue().getCode());
        assertEquals("九洲药业", requestCaptor.getValue().getName());
    }
}
