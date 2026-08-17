package com.awe.apex.quant.controller;

import com.awe.apex.common.api.PageResponse;
import com.awe.apex.common.api.Result;
import com.awe.apex.quant.domain.dto.TradeRecordResp;
import com.awe.apex.quant.service.IPortfolioTradeRecordService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeRecordControllerTest {

    @Test
    void shouldExposeFilteredTradeRecordPage() {
        IPortfolioTradeRecordService tradeRecordService = mock(IPortfolioTradeRecordService.class);
        TradeRecordController controller = new TradeRecordController();
        ReflectionTestUtils.setField(controller, "tradeRecordService", tradeRecordService);
        PageResponse<TradeRecordResp> expected = new PageResponse<>();
        when(tradeRecordService.page(11L, "600519", "SELL", "WECHAT_BOT", 2, 30))
                .thenReturn(expected);

        Result<PageResponse<TradeRecordResp>> result = controller.page(
                11L, "600519", "SELL", "WECHAT_BOT", 2, 30);

        assertSame(expected, result.getData());
        verify(tradeRecordService).page(11L, "600519", "SELL", "WECHAT_BOT", 2, 30);
    }

    @Test
    void shouldExposeStockKlineMarkers() {
        IPortfolioTradeRecordService tradeRecordService = mock(IPortfolioTradeRecordService.class);
        TradeRecordController controller = new TradeRecordController();
        ReflectionTestUtils.setField(controller, "tradeRecordService", tradeRecordService);
        List<TradeRecordResp> expected = List.of(TradeRecordResp.builder().id(8L).build());
        when(tradeRecordService.listMarkers("600519")).thenReturn(expected);

        Result<List<TradeRecordResp>> result = controller.markers("600519");

        assertSame(expected, result.getData());
        verify(tradeRecordService).listMarkers("600519");
    }
}
