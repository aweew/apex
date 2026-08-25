package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.domain.dto.MarketBriefingResp;
import com.awe.apex.quant.domain.entity.MarketBreadthForecast;
import com.awe.apex.quant.mapper.MarketBreadthForecastMapper;
import com.awe.apex.quant.service.IMarketBriefingService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketBreadthForecastServiceImplTest {

    @Test
    void shouldSettleForecastUsingClosingBreadthWithoutFlatStocks() {
        MarketBreadthForecastServiceImpl service = new MarketBreadthForecastServiceImpl();
        MarketBreadthForecastMapper forecastMapper = mock(MarketBreadthForecastMapper.class);
        IMarketBriefingService marketBriefingService = mock(IMarketBriefingService.class);
        MarketBreadthForecast forecast = MarketBreadthForecast.builder()
                .tradeDate(LocalDate.of(2026, 8, 25))
                .predictedUpRatio(new BigDecimal("62.00"))
                .predictedDownRatio(new BigDecimal("38.00"))
                .factorSummary("隔夜指数偏多；消息面偏多")
                .build();
        MarketBriefingResp closingBriefing = MarketBriefingResp.builder()
                .asOf(LocalDate.of(2026, 8, 25))
                .breadthUp(3100)
                .breadthDown(1900)
                .breadthFlat(100)
                .build();

        ReflectionTestUtils.setField(service, "marketBreadthForecastMapper", forecastMapper);
        ReflectionTestUtils.setField(service, "marketBriefingService", marketBriefingService);
        when(forecastMapper.selectOne(any())).thenReturn(forecast);
        when(marketBriefingService.briefing(true)).thenReturn(closingBriefing);

        String message = service.settleAfterClose(LocalDate.of(2026, 8, 25));

        assertEquals("", message);
        assertEquals(new BigDecimal("62.00"), forecast.getActualUpRatio());
        assertEquals(new BigDecimal("38.00"), forecast.getActualDownRatio());
        assertEquals(new BigDecimal("0.00"), forecast.getAbsoluteError());
        assertTrue(forecast.getDirectionHit());
        verify(forecastMapper).updateById(forecast);
    }

    @Test
    void shouldKeepForecastPendingWhenClosingBreadthDoesNotCoverTradeDate() {
        MarketBreadthForecastServiceImpl service = new MarketBreadthForecastServiceImpl();
        MarketBreadthForecastMapper forecastMapper = mock(MarketBreadthForecastMapper.class);
        IMarketBriefingService marketBriefingService = mock(IMarketBriefingService.class);
        MarketBreadthForecast forecast = MarketBreadthForecast.builder()
                .tradeDate(LocalDate.of(2026, 8, 25))
                .predictedUpRatio(new BigDecimal("62.00"))
                .build();
        MarketBriefingResp staleBriefing = MarketBriefingResp.builder()
                .asOf(LocalDate.of(2026, 8, 22))
                .breadthUp(3100)
                .breadthDown(1900)
                .build();

        ReflectionTestUtils.setField(service, "marketBreadthForecastMapper", forecastMapper);
        ReflectionTestUtils.setField(service, "marketBriefingService", marketBriefingService);
        when(forecastMapper.selectOne(any())).thenReturn(forecast);
        when(marketBriefingService.briefing(true)).thenReturn(staleBriefing);

        String message = service.settleAfterClose(LocalDate.of(2026, 8, 25));

        assertEquals("盘前涨跌比回测未结算：收盘市场简报未覆盖 2026-08-25", message);
        verify(forecastMapper, org.mockito.Mockito.never()).updateById(any(MarketBreadthForecast.class));
    }
}
