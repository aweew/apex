package com.awe.apex.quant.service.impl;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.domain.dto.BacktestRunReq;
import com.awe.apex.quant.domain.entity.BacktestJob;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.market.DailyBarClient;
import com.awe.apex.quant.service.IBarDailyService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BacktestBenchmarkCoverageTest {

    private final BarDailyMapper barDailyMapper = mock(BarDailyMapper.class);
    private final IBarDailyService barDailyService = mock(IBarDailyService.class);
    private final BacktestServiceImpl service = spy(new BacktestServiceImpl());

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, BarDaily.class);
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "barDailyMapper", barDailyMapper);
        ReflectionTestUtils.setField(service, "barDailyService", barDailyService);
    }

    @Test
    void shouldCompareBuyHoldCurvesWithinActualStrategyCoverage() {
        LocalDate requestedBeginDate = LocalDate.of(2024, 1, 1);
        LocalDate requestedEndDate = LocalDate.of(2024, 12, 31);
        LocalDate actualBeginDate = LocalDate.of(2024, 3, 4);
        LocalDate actualEndDate = LocalDate.of(2024, 10, 31);
        BacktestRunReq request = new BacktestRunReq();
        request.setCode("600519");
        request.setStrategyId("S1");
        request.setBeginDate(requestedBeginDate.toString());
        request.setEndDate(requestedEndDate.toString());
        BacktestJob job = BacktestJob.builder()
                .id(7L)
                .code("600519")
                .beginDate(actualBeginDate)
                .endDate(actualEndDate)
                .initCash(new BigDecimal("1000000.00"))
                .totalReturn(new BigDecimal("0.100000"))
                .build();
        List<BarDaily> stockBars = bars("600519", actualBeginDate, actualEndDate, new BigDecimal("100"));
        List<BarDaily> benchmarkBars = bars("000300", actualBeginDate, actualEndDate, new BigDecimal("200"));
        when(barDailyMapper.selectCount(any())).thenReturn(0L);
        when(barDailyMapper.selectList(any())).thenReturn(stockBars, benchmarkBars, benchmarkBars, stockBars);
        doReturn(job).when(service).run(request);
        doReturn(List.of()).when(service).listEquities(7L);

        service.compareBenchmark(request, "000300");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<BarDaily>> queryCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(barDailyMapper, times(2)).selectList(queryCaptor.capture());
        for (LambdaQueryWrapper<BarDaily> query : queryCaptor.getAllValues()) {
            query.getSqlSegment();
            assertTrue(query.getParamNameValuePairs().containsValue(actualBeginDate),
                    query.getParamNameValuePairs().toString());
            assertTrue(query.getParamNameValuePairs().containsValue(actualEndDate),
                    query.getParamNameValuePairs().toString());
            assertFalse(query.getParamNameValuePairs().containsValue(requestedBeginDate));
            assertFalse(query.getParamNameValuePairs().containsValue(requestedEndDate));
        }
        verify(barDailyService, times(2)).syncBars(any());
    }

    @Test
    void shouldRejectUnregisteredBenchmarkBeforeLoadingBars() {
        BacktestRunReq request = request();

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.compareBenchmark(request, "600519"));

        assertEquals("基准仅支持已登记指数代码", exception.getMessage());
        verify(barDailyMapper, never()).selectCount(any());
    }

    @Test
    void shouldRejectBenchmarkMissingActualStrategyBoundary() {
        LocalDate actualBeginDate = LocalDate.of(2024, 3, 4);
        LocalDate actualEndDate = LocalDate.of(2024, 10, 31);
        BacktestRunReq request = request();
        BacktestJob job = BacktestJob.builder()
                .id(7L)
                .code("600519")
                .beginDate(actualBeginDate)
                .endDate(actualEndDate)
                .initCash(new BigDecimal("1000000.00"))
                .totalReturn(new BigDecimal("0.100000"))
                .build();
        List<BarDaily> stockBars = bars("600519", actualBeginDate, actualEndDate, new BigDecimal("100"));
        List<BarDaily> benchmarkBars = bars(
                "000300", actualBeginDate.plusDays(30), actualEndDate, new BigDecimal("200"));
        when(barDailyMapper.selectCount(any())).thenReturn(0L);
        when(barDailyMapper.selectList(any())).thenReturn(stockBars, benchmarkBars);
        doReturn(job).when(service).run(request);
        doReturn(List.of()).when(service).listEquities(7L);

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.compareBenchmark(request, "000300"));

        assertEquals("日线未完整覆盖请求区间: 000300", exception.getMessage());
    }

    private BacktestRunReq request() {
        BacktestRunReq request = new BacktestRunReq();
        request.setCode("600519");
        request.setStrategyId("S1");
        request.setBeginDate("2024-01-01");
        request.setEndDate("2024-12-31");
        return request;
    }

    private List<BarDaily> bars(String code, LocalDate beginDate, LocalDate endDate,
                                BigDecimal beginPrice) {
        List<BarDaily> bars = new ArrayList<>();
        LocalDate tradeDate = beginDate;
        int index = 0;
        while (!tradeDate.isAfter(endDate)) {
            BigDecimal price = beginPrice.add(BigDecimal.valueOf(index).movePointLeft(1));
            bars.add(BarDaily.builder()
                    .code(code)
                    .tradeDate(tradeDate)
                    .openPrice(price)
                    .highPrice(price.add(BigDecimal.ONE))
                    .lowPrice(price.subtract(BigDecimal.ONE))
                    .closePrice(price)
                    .volume(BigDecimal.valueOf(1000000))
                    .source(DailyBarClient.SOURCE_EASTMONEY)
                    .build());
            tradeDate = tradeDate.plusDays(1);
            index++;
        }
        return bars;
    }
}
