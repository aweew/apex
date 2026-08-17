package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.domain.dto.SignalRunReq;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.StrategySignalMapper;
import com.awe.apex.quant.strategy.Strategy;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SignalServiceImplTest {

    @Test
    void runShouldEvaluateEachBarBatchBeforeLoadingTheNextBatch() {
        SignalServiceImpl signalService = new SignalServiceImpl();
        BarDailyMapper barDailyMapper = mock(BarDailyMapper.class);
        StrategySignalMapper strategySignalMapper = mock(StrategySignalMapper.class);
        Strategy strategy = mock(Strategy.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        AtomicInteger queryCount = new AtomicInteger();
        AtomicInteger evaluatedCount = new AtomicInteger();
        List<Integer> reportedProgress = new ArrayList<>();

        when(barDailyMapper.selectList(any())).thenAnswer(invocation -> {
            int batchIndex = queryCount.getAndIncrement();
            assertEquals(batchIndex * 40, evaluatedCount.get());
            int batchSize = batchIndex < 2 ? 40 : 1;
            return buildBars(batchIndex * 40, batchSize);
        });
        when(strategy.evaluate(any(), any())).thenAnswer(invocation -> {
            evaluatedCount.incrementAndGet();
            return null;
        });
        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        ReflectionTestUtils.setField(signalService, "barDailyMapper", barDailyMapper);
        ReflectionTestUtils.setField(signalService, "strategySignalMapper", strategySignalMapper);
        ReflectionTestUtils.setField(signalService, "strategies", List.of(strategy));
        ReflectionTestUtils.setField(signalService, "transactionTemplate", transactionTemplate);
        SignalRunReq request = new SignalRunReq();
        List<String> codes = new ArrayList<>();
        for (int index = 0; index < 81; index++) {
            codes.add(String.format("%06d", index));
        }
        request.setCodes(codes);

        signalService.run(request, (completed, total, message) -> {
            assertEquals(81, total);
            reportedProgress.add(completed);
        });

        assertEquals(3, queryCount.get());
        assertEquals(81, evaluatedCount.get());
        assertEquals(List.of(40, 80, 81), reportedProgress);
    }

    private List<BarDaily> buildBars(int startIndex, int codeCount) {
        List<BarDaily> bars = new ArrayList<>();
        LocalDate firstDate = LocalDate.of(2026, 1, 1);
        for (int codeIndex = startIndex; codeIndex < startIndex + codeCount; codeIndex++) {
            String code = String.format("%06d", codeIndex);
            for (int day = 0; day < 60; day++) {
                bars.add(BarDaily.builder()
                        .code(code)
                        .tradeDate(firstDate.plusDays(day))
                        .openPrice(BigDecimal.TEN)
                        .highPrice(BigDecimal.TEN)
                        .lowPrice(BigDecimal.TEN)
                        .closePrice(BigDecimal.TEN)
                        .volume(BigDecimal.ONE)
                        .build());
            }
        }
        return bars;
    }
}
