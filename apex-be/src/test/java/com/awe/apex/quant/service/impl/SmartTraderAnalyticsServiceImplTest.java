package com.awe.apex.quant.service.impl;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.entity.Trade;
import com.awe.apex.quant.domain.entity.Trader;
import com.awe.apex.quant.domain.entity.TraderPortfolioDaily;
import com.awe.apex.quant.domain.entity.TraderPosition;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.SmartMoneyFactorMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.mapper.TradeMapper;
import com.awe.apex.quant.mapper.TraderMapper;
import com.awe.apex.quant.mapper.TraderPortfolioDailyMapper;
import com.awe.apex.quant.mapper.TraderPositionMapper;
import com.awe.apex.quant.mapper.TraderProfileMapper;
import com.awe.apex.quant.mapper.TraderRankingDailyMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmartTraderAnalyticsServiceImplTest {

    private SmartTraderAnalyticsServiceImpl service;
    private TradeMapper tradeMapper;
    private TraderPositionMapper positionMapper;
    private TraderPortfolioDailyMapper portfolioDailyMapper;
    private BarDailyMapper barDailyMapper;

    @BeforeEach
    void setUp() {
        service = new SmartTraderAnalyticsServiceImpl();
        TraderMapper traderMapper = mock(TraderMapper.class);
        tradeMapper = mock(TradeMapper.class);
        positionMapper = mock(TraderPositionMapper.class);
        portfolioDailyMapper = mock(TraderPortfolioDailyMapper.class);
        barDailyMapper = mock(BarDailyMapper.class);
        ReflectionTestUtils.setField(service, "traderMapper", traderMapper);
        ReflectionTestUtils.setField(service, "tradeMapper", tradeMapper);
        ReflectionTestUtils.setField(service, "traderPositionMapper", positionMapper);
        ReflectionTestUtils.setField(service, "traderPortfolioDailyMapper", portfolioDailyMapper);
        ReflectionTestUtils.setField(service, "traderRankingDailyMapper", mock(TraderRankingDailyMapper.class));
        ReflectionTestUtils.setField(service, "traderProfileMapper", mock(TraderProfileMapper.class));
        ReflectionTestUtils.setField(service, "smartMoneyFactorMapper", mock(SmartMoneyFactorMapper.class));
        ReflectionTestUtils.setField(service, "stockBasicMapper", mock(StockBasicMapper.class));
        ReflectionTestUtils.setField(service, "barDailyMapper", barDailyMapper);
        when(traderMapper.selectList(any())).thenReturn(List.of(Trader.builder().id(1L).name("张三")
                .initialCapital(new BigDecimal("10000")).status("ACTIVE").build()));
        when(portfolioDailyMapper.selectList(any())).thenReturn(List.of());
        when(portfolioDailyMapper.selectOne(any())).thenReturn(null);
        when(barDailyMapper.selectRecentBars(any(), any(), any(Integer.class))).thenReturn(List.of(BarDaily.builder()
                .closePrice(new BigDecimal("12.00")).build()));
        doAnswer(invocation -> { TraderPortfolioDaily daily = invocation.getArgument(0); daily.setId(1L); return 1; })
                .when(portfolioDailyMapper).insert(any(TraderPortfolioDaily.class));
    }

    @Test
    void rebuildsWeightedCostAndUsesHistoricalCloseForSnapshot() {
        LocalDate date = LocalDate.of(2026, 8, 13);
        when(tradeMapper.selectList(any())).thenReturn(List.of(
                trade("BUY", 100, "10.00", date.atTime(9, 30)),
                trade("BUY", 100, "14.00", date.atTime(10, 0))));

        service.rebuild(date);

        ArgumentCaptor<TraderPosition> positionCaptor = ArgumentCaptor.forClass(TraderPosition.class);
        verify(positionMapper).insert(positionCaptor.capture());
        assertEquals(200, positionCaptor.getValue().getQuantity());
        assertEquals(0, new BigDecimal("12.0000").compareTo(positionCaptor.getValue().getAvgCost()));
        assertEquals(0, new BigDecimal("2400.00").compareTo(positionCaptor.getValue().getMarketValue()));
    }

    @Test
    void rejectsSellBeyondReconstructableHoldings() {
        LocalDate date = LocalDate.of(2026, 8, 13);
        when(tradeMapper.selectList(any())).thenReturn(List.of(trade("SELL", 100, "10.00", date.atTime(9, 30))));

        BusinessException error = assertThrows(BusinessException.class, () -> service.rebuild(date));

        assertEquals("交易者缺少足够历史持仓，不能生成账户快照", error.getMessage());
    }

    private Trade trade(String side, int quantity, String price, LocalDateTime time) {
        BigDecimal tradePrice = new BigDecimal(price);
        return Trade.builder().traderId(1L).symbol("300750").stockName("宁德时代").side(side).quantity(quantity)
                .price(tradePrice).amount(tradePrice.multiply(BigDecimal.valueOf(quantity))).tradeTime(time).status("VALID").build();
    }
}
