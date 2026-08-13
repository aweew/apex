package com.awe.apex.quant.bot.service;

import com.awe.apex.quant.bot.service.impl.BotHoldingRiskServiceImpl;
import com.awe.apex.quant.domain.dto.BotHoldingRiskResp;
import com.awe.apex.quant.domain.entity.MyHolding;
import com.awe.apex.quant.service.IMyHoldingService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BotHoldingRiskServiceImplTest {

    @Test
    void analyzesLatestRealHoldingQuotes() {
        IMyHoldingService myHoldingService = mock(IMyHoldingService.class);
        when(myHoldingService.listHoldingsLite()).thenReturn(List.of(
                MyHolding.builder()
                        .code("600000").name("浦发银行")
                        .quantity(1000).costPrice(new BigDecimal("11.00"))
                        .stopLoss(new BigDecimal("10.00")).marketPrice(new BigDecimal("9.80"))
                        .marketValue(new BigDecimal("9800.00")).pnlPct(new BigDecimal("-0.1091"))
                        .quoteTime(LocalDateTime.of(2026, 8, 13, 10, 3)).build(),
                MyHolding.builder()
                        .code("300750").name("宁德时代")
                        .quantity(100).costPrice(new BigDecimal("260.00"))
                        .stopLoss(new BigDecimal("250.00")).marketPrice(new BigDecimal("255.00"))
                        .marketValue(new BigDecimal("25500.00")).pnlPct(new BigDecimal("-0.0192"))
                        .quoteTime(LocalDateTime.of(2026, 8, 13, 10, 5)).build(),
                MyHolding.builder().code("000001").name("平安银行").quantity(1000).build()));

        BotHoldingRiskServiceImpl service = new BotHoldingRiskServiceImpl();
        ReflectionTestUtils.setField(service, "myHoldingService", myHoldingService);

        BotHoldingRiskResp risk = service.analyze();

        assertEquals(3, risk.getHoldingCount());
        assertEquals(2, risk.getQuotedCount());
        assertEquals(1, risk.getCriticalCount());
        assertEquals(2, risk.getWarnCount());
        assertEquals("2026-08-13T10:03", risk.getDataAsOf());
        assertTrue(risk.getAlerts().stream().anyMatch(item -> item.getMessage().contains("触及止损")));
        assertTrue(risk.getAlerts().stream().anyMatch(item -> item.getMessage().contains("接近止损")));
        assertTrue(risk.getAlerts().stream().anyMatch(item -> item.getMessage().contains("缺少最新行情")));
    }
}
