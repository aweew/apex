package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.domain.entity.DailyAction;
import com.awe.apex.quant.domain.entity.JournalTrade;
import com.awe.apex.quant.mapper.DailyActionMapper;
import com.awe.apex.quant.mapper.JournalTradeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JournalServiceImplTest {

    private final JournalServiceImpl service = new JournalServiceImpl();
    private final JournalTradeMapper journalTradeMapper = mock(JournalTradeMapper.class);
    private final DailyActionMapper dailyActionMapper = mock(DailyActionMapper.class);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "journalTradeMapper", journalTradeMapper);
        ReflectionTestUtils.setField(service, "dailyActionMapper", dailyActionMapper);
    }

    @Test
    void mapsReductionActionToSellTrade() {
        DailyAction reduction = DailyAction.builder()
                .id(8L)
                .actionDate(LocalDate.of(2026, 8, 13))
                .code("600000")
                .action("REDUCE")
                .strategyId("PORTFOLIO_DRAWDOWN")
                .build();
        when(dailyActionMapper.selectById(8L)).thenReturn(reduction);

        JournalTrade trade = service.fromAction(8L, new BigDecimal("10.20"), 300);

        assertEquals("SELL", trade.getSide());
        assertEquals(new BigDecimal("3060.00"), trade.getAmount());
        assertEquals(8L, trade.getRelatedActionId());
    }
}
