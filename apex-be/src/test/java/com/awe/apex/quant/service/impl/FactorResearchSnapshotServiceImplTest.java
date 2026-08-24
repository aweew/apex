package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.entity.FactorResearchSnapshot;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.domain.entity.StockFinAbstract;
import com.awe.apex.quant.domain.entity.StockFinIndicator;
import com.awe.apex.quant.factor.FactorCalculator;
import com.awe.apex.quant.factor.ResearchScoreCalculator;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.FactorResearchSnapshotMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.mapper.StockFinAbstractMapper;
import com.awe.apex.quant.mapper.StockFinIndicatorMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FactorResearchSnapshotServiceImplTest {

    @Test
    void shouldPublishIndustryPercentilesAsImmutableDailySnapshots() {
        LocalDate tradeDate = LocalDate.of(2026, 8, 21);
        StockBasicMapper stockBasicMapper = mock(StockBasicMapper.class);
        BarDailyMapper barDailyMapper = mock(BarDailyMapper.class);
        StockFinAbstractMapper abstractMapper = mock(StockFinAbstractMapper.class);
        StockFinIndicatorMapper indicatorMapper = mock(StockFinIndicatorMapper.class);
        FactorResearchSnapshotMapper snapshotMapper = mock(FactorResearchSnapshotMapper.class);
        when(stockBasicMapper.selectList(any())).thenReturn(securities());
        when(barDailyMapper.selectFactorResearchBars(any(), any())).thenReturn(bars(tradeDate));
        when(abstractMapper.selectFactorResearchAbstracts(tradeDate)).thenReturn(abstracts(tradeDate));
        when(indicatorMapper.selectFactorResearchIndicators(tradeDate)).thenReturn(indicators(tradeDate));
        when(snapshotMapper.selectCount(any())).thenReturn(0L);

        FactorResearchSnapshotServiceImpl service = new FactorResearchSnapshotServiceImpl();
        ReflectionTestUtils.setField(service, "stockBasicMapper", stockBasicMapper);
        ReflectionTestUtils.setField(service, "barDailyMapper", barDailyMapper);
        ReflectionTestUtils.setField(service, "stockFinAbstractMapper", abstractMapper);
        ReflectionTestUtils.setField(service, "stockFinIndicatorMapper", indicatorMapper);
        ReflectionTestUtils.setField(service, "factorResearchSnapshotMapper", snapshotMapper);
        ReflectionTestUtils.setField(service, "factorCalculator", new FactorCalculator());
        ReflectionTestUtils.setField(service, "researchScoreCalculator", new ResearchScoreCalculator());

        service.publish(tradeDate);

        ArgumentCaptor<FactorResearchSnapshot> captor = ArgumentCaptor.forClass(FactorResearchSnapshot.class);
        verify(snapshotMapper, times(5)).insert(captor.capture());
        FactorResearchSnapshot strongest = captor.getAllValues().get(4);
        assertEquals(new BigDecimal("100.00"), strongest.getQualityPercentile());
        assertNotNull(strongest.getResearchScore());
        assertEquals(new BigDecimal("100.00"), strongest.getCoverage());
    }

    private List<StockBasic> securities() {
        List<StockBasic> securities = new ArrayList<>();
        for (int index = 1; index <= 5; index++) {
            securities.add(StockBasic.builder().code("60000" + index).name("证券" + index).market("SH")
                    .industry("测试行业").peTtm(BigDecimal.valueOf(10L + index)).build());
        }
        return securities;
    }

    private List<BarDaily> bars(LocalDate tradeDate) {
        List<BarDaily> bars = new ArrayList<>();
        for (int securityIndex = 1; securityIndex <= 5; securityIndex++) {
            addBars(bars, "60000" + securityIndex, tradeDate, BigDecimal.valueOf(10L + securityIndex));
        }
        addBars(bars, "000300", tradeDate, new BigDecimal("4000"));
        return bars;
    }

    private void addBars(List<BarDaily> bars, String code, LocalDate tradeDate, BigDecimal initialClose) {
        for (int index = 0; index <= 60; index++) {
            bars.add(BarDaily.builder().code(code).tradeDate(tradeDate.minusDays(60L - index))
                    .closePrice(initialClose.add(BigDecimal.valueOf(index).movePointLeft(1)))
                    .amount(index == 60 ? new BigDecimal("200") : new BigDecimal("100")).build());
        }
    }

    private List<StockFinAbstract> abstracts(LocalDate tradeDate) {
        List<StockFinAbstract> abstracts = new ArrayList<>();
        for (int index = 1; index <= 5; index++) {
            abstracts.add(StockFinAbstract.builder().code("60000" + index).reportDate(tradeDate.minusMonths(2))
                    .netProfitYoy(BigDecimal.valueOf(index * 10L)).build());
        }
        return abstracts;
    }

    private List<StockFinIndicator> indicators(LocalDate tradeDate) {
        List<StockFinIndicator> indicators = new ArrayList<>();
        for (int index = 1; index <= 5; index++) {
            indicators.add(StockFinIndicator.builder().code("60000" + index).reportDate(tradeDate.minusMonths(2))
                    .roe(BigDecimal.valueOf(index * 5L)).build());
        }
        return indicators;
    }
}
