package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.dto.HoldingTradeReq;
import com.awe.apex.quant.domain.entity.MyHolding;
import com.awe.apex.quant.domain.entity.Portfolio;
import com.awe.apex.quant.domain.entity.PortfolioHolding;
import com.awe.apex.quant.domain.enums.PortfolioTradeSourceEnum;
import com.awe.apex.quant.mapper.MyHoldingMapper;
import com.awe.apex.quant.service.IPortfolioService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MyHoldingTradeTest {

    private final MyHoldingServiceImpl service = new MyHoldingServiceImpl();
    private final MyHoldingMapper myHoldingMapper = mock(MyHoldingMapper.class);
    private final IPortfolioService portfolioService = mock(IPortfolioService.class);
    private final ApexUserContext userContext = mock(ApexUserContext.class);

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), MyHolding.class);
        ReflectionTestUtils.setField(service, "myHoldingMapper", myHoldingMapper);
        ReflectionTestUtils.setField(service, "portfolioService", portfolioService);
        ReflectionTestUtils.setField(service, "userContext", userContext);
        when(userContext.currentUserId()).thenReturn(7L);
    }

    @Test
    void realHoldingTradeUsesDefaultPortfolioAndHoldingWebSource() {
        MyHolding existing = MyHolding.builder()
                .id(31L)
                .userId(7L)
                .code("600519")
                .name("贵州茅台")
                .quantity(100)
                .build();
        MyHolding updated = MyHolding.builder()
                .id(31L)
                .userId(7L)
                .code("600519")
                .name("贵州茅台")
                .quantity(150)
                .build();
        when(myHoldingMapper.selectById(31L)).thenReturn(existing);
        when(portfolioService.ensureDefaultPortfolio()).thenReturn(Portfolio.builder().id(1L).build());
        when(portfolioService.tradeHolding(eq(1L), any(), eq(PortfolioTradeSourceEnum.HOLDING_WEB)))
                .thenReturn(PortfolioHolding.builder().id(41L).code("600519").quantity(150).build());
        when(myHoldingMapper.selectOne(any())).thenReturn(updated);
        LocalDateTime tradeTime = LocalDateTime.of(2026, 8, 18, 10, 30);
        HoldingTradeReq request = new HoldingTradeReq();
        request.setHoldingId(31L);
        request.setSide("BUY");
        request.setQuantity(50);
        request.setTradePrice(new BigDecimal("1520.50"));
        request.setTradeTime(tradeTime);

        MyHolding result = service.tradeHolding(request);

        assertEquals(150, result.getQuantity());
        ArgumentCaptor<HoldingTradeReq> requestCaptor = ArgumentCaptor.forClass(HoldingTradeReq.class);
        verify(portfolioService).tradeHolding(eq(1L), requestCaptor.capture(),
                eq(PortfolioTradeSourceEnum.HOLDING_WEB));
        HoldingTradeReq portfolioRequest = requestCaptor.getValue();
        assertEquals("600519", portfolioRequest.getCode());
        assertEquals("BUY", portfolioRequest.getSide());
        assertEquals(50, portfolioRequest.getQuantity());
        assertEquals(new BigDecimal("1520.50"), portfolioRequest.getTradePrice());
        assertEquals(tradeTime, portfolioRequest.getTradeTime());
    }
}
