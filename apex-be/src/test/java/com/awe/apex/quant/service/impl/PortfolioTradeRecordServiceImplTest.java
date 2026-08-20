package com.awe.apex.quant.service.impl;

import com.awe.apex.common.api.PageResponse;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.dto.TradeRecordResp;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.entity.JournalTrade;
import com.awe.apex.quant.domain.entity.Portfolio;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.domain.enums.PortfolioTradeChangeTypeEnum;
import com.awe.apex.quant.domain.enums.PortfolioTradePriceSourceEnum;
import com.awe.apex.quant.domain.enums.PortfolioTradeSideEnum;
import com.awe.apex.quant.domain.enums.PortfolioTradeSourceEnum;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.JournalTradeMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortfolioTradeRecordServiceImplTest {

    private final PortfolioTradeRecordServiceImpl service = new PortfolioTradeRecordServiceImpl();
    private final JournalTradeMapper journalTradeMapper = mock(JournalTradeMapper.class);
    private final StockBasicMapper stockBasicMapper = mock(StockBasicMapper.class);
    private final BarDailyMapper barDailyMapper = mock(BarDailyMapper.class);
    private final ApexUserContext userContext = mock(ApexUserContext.class);

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, JournalTrade.class);
        TableInfoHelper.initTableInfo(assistant, StockBasic.class);
        TableInfoHelper.initTableInfo(assistant, BarDaily.class);
    }

    @BeforeEach
    void setUp() {
        reset(journalTradeMapper, stockBasicMapper, barDailyMapper, userContext);
        ReflectionTestUtils.setField(service, "journalTradeMapper", journalTradeMapper);
        ReflectionTestUtils.setField(service, "stockBasicMapper", stockBasicMapper);
        ReflectionTestUtils.setField(service, "barDailyMapper", barDailyMapper);
        ReflectionTestUtils.setField(service, "userContext", userContext);
        when(userContext.currentUserId()).thenReturn(7L);
    }

    @Test
    void openingPositionCreatesEstimatedBuyFromMarketSnapshot() {
        Portfolio portfolio = Portfolio.builder().id(11L).name("疯锅").ownerLabel("张三").build();
        when(stockBasicMapper.selectOne(any())).thenReturn(StockBasic.builder()
                .code("600519")
                .name("贵州茅台")
                .latestPrice(new BigDecimal("1518.20"))
                .build());

        JournalTrade trade = service.recordChange(portfolio, "600519", "贵州茅台", 0, 100,
                null, LocalDateTime.of(2026, 8, 17, 10, 30),
                PortfolioTradeSourceEnum.PORTFOLIO_WEB, null);

        assertEquals(PortfolioTradeSideEnum.BUY.getCode(), trade.getSide());
        assertEquals(PortfolioTradeChangeTypeEnum.OPEN.getCode(), trade.getChangeType());
        assertEquals(100, trade.getQuantity());
        assertEquals(0, trade.getBeforeQuantity());
        assertEquals(100, trade.getAfterQuantity());
        assertEquals(new BigDecimal("1518.20"), trade.getPrice());
        assertEquals(PortfolioTradePriceSourceEnum.MARKET_SNAPSHOT.getCode(), trade.getPriceSource());
        assertEquals(1, trade.getEstimated());
        assertEquals(7L, trade.getUserId());
        assertEquals("张三", trade.getOwnerLabel());
        verify(journalTradeMapper).insert(trade);
    }

    @Test
    void reductionUsesReportedPriceAndExactQuantityDelta() {
        Portfolio portfolio = Portfolio.builder().id(11L).name("疯锅").build();

        JournalTrade trade = service.recordChange(portfolio, "000001", "平安银行", 500, 200,
                new BigDecimal("12.36"), LocalDateTime.of(2026, 8, 17, 14, 20),
                PortfolioTradeSourceEnum.PORTFOLIO_WEB, null);

        assertEquals(PortfolioTradeSideEnum.SELL.getCode(), trade.getSide());
        assertEquals(PortfolioTradeChangeTypeEnum.REDUCE.getCode(), trade.getChangeType());
        assertEquals(300, trade.getQuantity());
        assertEquals(new BigDecimal("12.36"), trade.getPrice());
        assertEquals(PortfolioTradePriceSourceEnum.USER_REPORTED.getCode(), trade.getPriceSource());
        assertEquals(0, trade.getEstimated());
    }

    @Test
    void adminOperationRecordsTradeForPortfolioOwner() {
        when(userContext.currentUserId()).thenReturn(1L);
        Portfolio portfolio = Portfolio.builder().id(11L).userId(9L).name("郑十万").build();

        JournalTrade trade = service.recordChange(portfolio, "000001", "平安银行", 100, 200,
                new BigDecimal("12.36"), LocalDateTime.of(2026, 8, 20, 10, 30),
                PortfolioTradeSourceEnum.PORTFOLIO_WEB, null);

        assertEquals(9L, trade.getUserId());
    }

    @Test
    void unchangedQuantityDoesNotCreateTrade() {
        JournalTrade trade = service.recordChange(Portfolio.builder().id(11L).build(),
                "000001", "平安银行", 200, 200, null, null,
                PortfolioTradeSourceEnum.PORTFOLIO_WEB, null);

        assertNull(trade);
        verify(journalTradeMapper, never()).insert(any(JournalTrade.class));
    }

    @Test
    void repeatedBotRequestReturnsExistingTrade() {
        JournalTrade existing = JournalTrade.builder()
                .id(99L)
                .sourceRef("req-1:000001")
                .beforeQuantity(0)
                .afterQuantity(100)
                .build();
        when(journalTradeMapper.selectOne(any())).thenReturn(existing);

        JournalTrade trade = service.recordChange(Portfolio.builder().id(11L).build(),
                "000001", "平安银行", 0, 100, null, null,
                PortfolioTradeSourceEnum.WECHAT_BOT, "req-1:000001");

        assertEquals(99L, trade.getId());
        verify(journalTradeMapper, never()).insert(any(JournalTrade.class));
    }

    @Test
    void reusedBotRequestWithDifferentQuantityIsRejected() {
        JournalTrade existing = JournalTrade.builder()
                .id(99L)
                .sourceRef("req-1:000001")
                .beforeQuantity(0)
                .afterQuantity(100)
                .build();
        when(journalTradeMapper.selectOne(any())).thenReturn(existing);

        assertThrows(BusinessException.class, () -> service.recordChange(
                Portfolio.builder().id(11L).build(), "000001", "平安银行", 100, 200,
                null, null, PortfolioTradeSourceEnum.WECHAT_BOT, "req-1:000001"));

        verify(journalTradeMapper, never()).insert(any(JournalTrade.class));
    }

    @Test
    void markerQueryReadsSharedPortfolioTradesAndCalculatesPostSellPerformance() {
        JournalTrade sell = JournalTrade.builder()
                .id(8L)
                .userId(7L)
                .tradeDate(LocalDate.of(2026, 8, 10))
                .tradeTime(LocalDateTime.of(2026, 8, 10, 10, 0))
                .code("600519")
                .stockName("贵州茅台")
                .side("SELL")
                .price(new BigDecimal("100.00"))
                .quantity(100)
                .portfolioName("疯锅")
                .ownerLabel("张三")
                .build();
        when(journalTradeMapper.selectList(any())).thenReturn(List.of(sell));
        when(stockBasicMapper.selectList(any())).thenReturn(List.of(StockBasic.builder()
                .code("600519")
                .market("SH")
                .latestPrice(new BigDecimal("110.00"))
                .build()));
        when(barDailyMapper.selectList(any())).thenReturn(List.of(
                BarDaily.builder().code("600519").tradeDate(LocalDate.of(2026, 8, 11))
                        .highPrice(new BigDecimal("120.00")).lowPrice(new BigDecimal("95.00")).build(),
                BarDaily.builder().code("600519").tradeDate(LocalDate.of(2026, 8, 12))
                        .highPrice(new BigDecimal("108.00")).lowPrice(new BigDecimal("80.00")).build()));

        List<TradeRecordResp> markers = service.listMarkers("600519");

        assertEquals(1, markers.size());
        assertEquals(new BigDecimal("10.00"), markers.get(0).getLatestReturnPct());
        assertEquals(new BigDecimal("20.00"), markers.get(0).getMaxRisePct());
        assertEquals(new BigDecimal("-20.00"), markers.get(0).getMaxFallPct());
        ArgumentCaptor<Wrapper<JournalTrade>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(journalTradeMapper).selectList(queryCaptor.capture());
        AbstractWrapper<?, ?, ?> query = (AbstractWrapper<?, ?, ?>) queryCaptor.getValue();
        assertFalse(query.getSqlSegment().contains("user_id"));
        assertFalse(query.getParamNameValuePairs().containsValue(7L));
        assertTrue(query.getParamNameValuePairs().containsValue("600519"));
        assertTrue(query.getSqlSegment().toUpperCase().contains("TRADE_DATE DESC"));
        verify(userContext).currentUserId();
    }

    @Test
    void pageQueryReadsAllSharedPortfolioRecords() {
        when(journalTradeMapper.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

        PageResponse<TradeRecordResp> response = service.page(null, null, null, null, 1, 20);

        assertEquals(0L, response.getTotal());
        ArgumentCaptor<Wrapper<JournalTrade>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(journalTradeMapper).selectPage(any(), queryCaptor.capture());
        AbstractWrapper<?, ?, ?> query = (AbstractWrapper<?, ?, ?>) queryCaptor.getValue();
        assertFalse(query.getSqlSegment().contains("user_id"));
        assertFalse(query.getParamNameValuePairs().containsValue(7L));
        verify(userContext).currentUserId();
    }
}
