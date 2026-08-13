package com.awe.apex.quant.service.impl;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.domain.dto.TradeEventIngestReq;
import com.awe.apex.quant.domain.dto.TradeEventIngestResp;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.domain.entity.Trader;
import com.awe.apex.quant.domain.entity.TradeEvent;
import com.awe.apex.quant.domain.entity.Trade;
import com.awe.apex.quant.domain.entity.TradeEvidence;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.mapper.TradeEvidenceMapper;
import com.awe.apex.quant.mapper.TradeEventMapper;
import com.awe.apex.quant.mapper.TradeMapper;
import com.awe.apex.quant.mapper.TraderMapper;
import cn.hutool.extra.spring.SpringUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeEventServiceImplTest {

    private static GenericApplicationContext applicationContext;
    private static ApplicationContext originalApplicationContext;

    private TradeEventServiceImpl service;
    private TraderMapper traderMapper;
    private TradeEventMapper tradeEventMapper;
    private TradeEvidenceMapper tradeEvidenceMapper;
    private TradeMapper tradeMapper;
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
        service = new TradeEventServiceImpl();
        traderMapper = mock(TraderMapper.class);
        tradeEventMapper = mock(TradeEventMapper.class);
        tradeEvidenceMapper = mock(TradeEvidenceMapper.class);
        tradeMapper = mock(TradeMapper.class);
        stockBasicMapper = mock(StockBasicMapper.class);
        ReflectionTestUtils.setField(service, "traderMapper", traderMapper);
        ReflectionTestUtils.setField(service, "tradeEventMapper", tradeEventMapper);
        ReflectionTestUtils.setField(service, "tradeEvidenceMapper", tradeEvidenceMapper);
        ReflectionTestUtils.setField(service, "tradeMapper", tradeMapper);
        ReflectionTestUtils.setField(service, "stockBasicMapper", stockBasicMapper);
        doAnswer(invocation -> {
            Trader trader = invocation.getArgument(0);
            trader.setId(101L);
            return 1;
        }).when(traderMapper).insert(any(Trader.class));
        doAnswer(invocation -> {
            TradeEvent tradeEvent = invocation.getArgument(0);
            tradeEvent.setId(201L);
            return 1;
        }).when(tradeEventMapper).insert(any(TradeEvent.class));
    }

    @Test
    void ingestsHighConfidenceBuyAsConfirmedTrade() {
        when(stockBasicMapper.selectOne(any())).thenReturn(StockBasic.builder()
                .code("300750").name("宁德时代").build());
        TradeEventIngestReq request = tradeRequest(new BigDecimal("0.96"));

        TradeEventIngestResp response = service.ingest(request);

        assertEquals(201L, response.getTradeEventId());
        assertEquals("CONFIRMED", response.getStatus());
        ArgumentCaptor<TradeEvent> eventCaptor = ArgumentCaptor.forClass(TradeEvent.class);
        verify(tradeEventMapper).insert(eventCaptor.capture());
        assertEquals("300750", eventCaptor.getValue().getSymbol());
        assertEquals("CONFIRMED", eventCaptor.getValue().getStatus());
        verify(tradeMapper).insert(any(Trade.class));
    }

    @Test
    void keepsMediumConfidenceTradePendingConfirmation() {
        when(stockBasicMapper.selectOne(any())).thenReturn(StockBasic.builder()
                .code("300750").name("宁德时代").build());

        TradeEventIngestResp response = service.ingest(tradeRequest(new BigDecimal("0.80")));

        assertEquals("PENDING_CONFIRM", response.getStatus());
        verify(tradeMapper, never()).insert(any(Trade.class));
    }

    @Test
    void rejectsLowConfidenceEventWithoutTrade() {
        when(stockBasicMapper.selectOne(any())).thenReturn(StockBasic.builder()
                .code("300750").name("宁德时代").build());

        TradeEventIngestResp response = service.ingest(tradeRequest(new BigDecimal("0.79")));

        assertEquals("REJECTED", response.getStatus());
        verify(tradeMapper, never()).insert(any(Trade.class));
    }

    @Test
    void returnsExistingEventForSameIdempotencyKey() {
        when(tradeEventMapper.selectOne(any())).thenReturn(TradeEvent.builder()
                .id(18L).status("CONFIRMED").build());

        TradeEventIngestResp response = service.ingest(tradeRequest(new BigDecimal("0.96")));

        assertEquals(18L, response.getTradeEventId());
        assertEquals("CONFIRMED", response.getStatus());
        verify(traderMapper, never()).insert(any(Trader.class));
        verify(tradeEventMapper, never()).insert(any(TradeEvent.class));
    }

    @Test
    void rejectsUnknownStockBeforeWritingFinancialFacts() {
        when(stockBasicMapper.selectOne(any())).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.ingest(tradeRequest(new BigDecimal("0.96"))));

        assertEquals("股票代码不存在或已失效", error.getMessage());
        verify(tradeEventMapper, never()).insert(any(TradeEvent.class));
        verify(tradeEvidenceMapper, never()).insert(any(TradeEvidence.class));
    }

    @Test
    void doesNotAutomaticallyConfirmClearWithoutPositionEngine() {
        when(stockBasicMapper.selectOne(any())).thenReturn(StockBasic.builder()
                .code("300750").name("宁德时代").build());
        TradeEventIngestReq request = tradeRequest(new BigDecimal("0.99"));
        request.setSide("CLEAR");
        request.setQuantity(null);
        request.setPrice(null);

        TradeEventIngestResp response = service.ingest(request);

        assertEquals("PENDING_CONFIRM", response.getStatus());
        verify(tradeMapper, never()).insert(any(Trade.class));
    }

    private TradeEventIngestReq tradeRequest(BigDecimal confidence) {
        TradeEventIngestReq request = new TradeEventIngestReq();
        request.setTraderName("张三");
        request.setEventType("TRADE");
        request.setSide("BUY");
        request.setSymbol("300750");
        request.setStockName("宁德时代");
        request.setQuantity(500);
        request.setPrice(new BigDecimal("378.5"));
        request.setConfidence(confidence);
        request.setSource("WECHAT_TEXT");
        request.setRawText("张三刚刚买入500股宁德时代，378.5");
        request.setIdempotencyKey("wechat-msg-001");
        return request;
    }
}
