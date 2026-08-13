package com.awe.apex.quant.service.impl;

import cn.hutool.extra.spring.SpringUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.domain.dto.PortfolioSaveReq;
import com.awe.apex.quant.domain.dto.PortfolioSummaryResp;
import com.awe.apex.quant.domain.entity.Portfolio;
import com.awe.apex.quant.domain.entity.PortfolioDaily;
import com.awe.apex.quant.domain.entity.PortfolioHolding;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.mapper.PortfolioDailyMapper;
import com.awe.apex.quant.mapper.PortfolioHoldingMapper;
import com.awe.apex.quant.mapper.PortfolioMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortfolioCashServiceTest {

    private static GenericApplicationContext applicationContext;
    private static ApplicationContext originalApplicationContext;

    private PortfolioServiceImpl portfolioService;
    private PortfolioMapper portfolioMapper;
    private PortfolioHoldingMapper portfolioHoldingMapper;
    private PortfolioDailyMapper portfolioDailyMapper;
    private StockBasicMapper stockBasicMapper;

    @BeforeAll
    static void initJsonUtilsContext() {
        originalApplicationContext = SpringUtil.getApplicationContext();
        applicationContext = new GenericApplicationContext();
        applicationContext.registerBean(ObjectMapper.class, () -> new ObjectMapper().findAndRegisterModules());
        applicationContext.refresh();
        new SpringUtil().setApplicationContext(applicationContext);
    }

    @AfterAll
    static void closeJsonUtilsContext() {
        new SpringUtil().setApplicationContext(originalApplicationContext);
        applicationContext.close();
    }

    @BeforeEach
    void setUp() {
        portfolioMapper = mock(PortfolioMapper.class);
        portfolioHoldingMapper = mock(PortfolioHoldingMapper.class);
        portfolioDailyMapper = mock(PortfolioDailyMapper.class);
        stockBasicMapper = mock(StockBasicMapper.class);
        portfolioService = spy(new PortfolioServiceImpl());
        ReflectionTestUtils.setField(portfolioService, "portfolioMapper", portfolioMapper);
        ReflectionTestUtils.setField(portfolioService, "portfolioHoldingMapper", portfolioHoldingMapper);
        ReflectionTestUtils.setField(portfolioService, "portfolioDailyMapper", portfolioDailyMapper);
        ReflectionTestUtils.setField(portfolioService, "stockBasicMapper", stockBasicMapper);
        doReturn(Portfolio.builder().id(1L).build()).when(portfolioService).ensureDefaultPortfolio();
    }

    @Test
    void savePortfolioDefaultsCashToZeroWhenCreating() {
        PortfolioSaveReq request = new PortfolioSaveReq();
        request.setName("成长组合");

        Portfolio saved = portfolioService.savePortfolio(request);

        assertEquals(0, saved.getCashBalance().compareTo(BigDecimal.ZERO));
        verify(portfolioMapper).insert(saved);
    }

    @Test
    void savePortfolioKeepsExistingCashWhenUpdateOmitsCash() {
        Portfolio existing = Portfolio.builder()
                .id(2L)
                .name("成长组合")
                .status("ACTIVE")
                .isDefault(0)
                .cashBalance(new BigDecimal("12000.50"))
                .build();
        when(portfolioMapper.selectById(2L)).thenReturn(existing);
        PortfolioSaveReq request = new PortfolioSaveReq();
        request.setId(2L);
        request.setName("成长组合 V2");

        Portfolio saved = portfolioService.savePortfolio(request);

        assertEquals(0, saved.getCashBalance().compareTo(new BigDecimal("12000.50")));
        verify(portfolioMapper).updateById(existing);
    }

    @Test
    void savePortfolioUpdatesCashWhenProvided() {
        Portfolio existing = Portfolio.builder()
                .id(1L)
                .name("我的持仓")
                .status("ACTIVE")
                .isDefault(1)
                .cashBalance(new BigDecimal("1000"))
                .build();
        when(portfolioMapper.selectById(1L)).thenReturn(existing);
        PortfolioSaveReq request = new PortfolioSaveReq();
        request.setId(1L);
        request.setName("我的持仓");
        request.setCashBalance(new BigDecimal("2500.88"));

        Portfolio saved = portfolioService.savePortfolio(request);

        assertEquals(0, saved.getCashBalance().compareTo(new BigDecimal("2500.88")));
        verify(portfolioMapper).updateById(existing);
    }

    @Test
    void savePortfolioRefreshesExistingTodaySnapshotAfterCashChange() {
        Portfolio existing = Portfolio.builder()
                .id(1L)
                .name("我的持仓")
                .status("ACTIVE")
                .isDefault(1)
                .cashBalance(new BigDecimal("1000"))
                .build();
        when(portfolioMapper.selectById(1L)).thenReturn(existing);
        when(portfolioDailyMapper.selectOne(any())).thenReturn(PortfolioDaily.builder().id(8L).build());
        doReturn(PortfolioDaily.builder().id(8L).build()).when(portfolioService).snapshot(1L);
        PortfolioSaveReq request = new PortfolioSaveReq();
        request.setId(1L);
        request.setName("我的持仓");
        request.setCashBalance(new BigDecimal("2500"));

        portfolioService.savePortfolio(request);

        verify(portfolioService).snapshot(1L);
    }

    @Test
    void savePortfolioRejectsNegativeCash() {
        PortfolioSaveReq request = new PortfolioSaveReq();
        request.setName("成长组合");
        request.setCashBalance(new BigDecimal("-0.01"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> portfolioService.savePortfolio(request));

        assertEquals("组合现金不能小于0", error.getMessage());
    }

    @Test
    void listPortfoliosReturnsCashAndTotalEquity() {
        Portfolio portfolio = Portfolio.builder()
                .id(2L)
                .name("成长组合")
                .status("ACTIVE")
                .isDefault(0)
                .cashBalance(new BigDecimal("500.25"))
                .build();
        PortfolioHolding holding = PortfolioHolding.builder()
                .portfolioId(2L)
                .code("600519")
                .quantity(10)
                .costPrice(new BigDecimal("90"))
                .build();
        StockBasic stock = StockBasic.builder()
                .code("600519")
                .latestPrice(new BigDecimal("100"))
                .build();
        when(portfolioMapper.selectList(any())).thenReturn(List.of(portfolio));
        when(portfolioHoldingMapper.selectList(any())).thenReturn(List.of(holding));
        when(stockBasicMapper.selectList(any())).thenReturn(List.of(stock));

        PortfolioSummaryResp summary = portfolioService.listPortfolios(false).get(0);

        assertEquals(0, summary.getMarketValue().compareTo(new BigDecimal("1000.00")));
        assertEquals(0, summary.getCashBalance().compareTo(new BigDecimal("500.25")));
        assertEquals(0, summary.getTotalEquity().compareTo(new BigDecimal("1500.25")));
    }

    @Test
    void snapshotPersistsPortfolioCash() {
        PortfolioSummaryResp summary = PortfolioSummaryResp.builder()
                .id(2L)
                .marketValue(new BigDecimal("1000.00"))
                .cashBalance(new BigDecimal("500.25"))
                .positionCount(0)
                .holdings(List.of())
                .build();
        doReturn(summary).when(portfolioService).detail(2L);
        when(portfolioDailyMapper.selectOne(any())).thenReturn(null);
        when(portfolioDailyMapper.selectPeakEquityBefore(any(), any())).thenReturn(new BigDecimal("1800.00"));

        portfolioService.snapshot(2L);

        ArgumentCaptor<PortfolioDaily> captor = ArgumentCaptor.forClass(PortfolioDaily.class);
        verify(portfolioDailyMapper).insert(captor.capture());
        assertEquals(0, captor.getValue().getCash().compareTo(new BigDecimal("500.25")));
        assertEquals(0, captor.getValue().getTotalEquity().compareTo(new BigDecimal("1500.25")));
        assertEquals(0, captor.getValue().getPeakEquity().compareTo(new BigDecimal("1800.00")));
        assertEquals(0, captor.getValue().getDrawdown().compareTo(new BigDecimal("0.166528")));
    }
}
