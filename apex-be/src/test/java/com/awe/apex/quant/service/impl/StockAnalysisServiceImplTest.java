package com.awe.apex.quant.service.impl;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.service.IStockService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockAnalysisServiceImplTest {

    @Test
    void baseAnalysisShouldNotWaitForExternalQuoteRefresh() {
        StockAnalysisServiceImpl analysisService = new StockAnalysisServiceImpl();
        IStockService stockService = mock(IStockService.class);
        when(stockService.detail("600519", 120, false)).thenThrow(new BusinessException("stop after local read"));
        ReflectionTestUtils.setField(analysisService, "stockService", stockService);

        assertThrows(BusinessException.class,
                () -> analysisService.analyze("600519", "BUY", 120, false, false));

        verify(stockService, never()).syncBasic(anyString());
    }
}
