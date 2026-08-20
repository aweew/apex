package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.domain.dto.FactorCategoryResp;
import com.awe.apex.quant.domain.dto.FactorCenterResp;
import com.awe.apex.quant.domain.dto.AlphaComponentResp;
import com.awe.apex.quant.domain.dto.LimitUpLadderResp;
import com.awe.apex.quant.domain.dto.MarketBriefingResp;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.entity.NorthboundFlow;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.domain.entity.StockFinAbstract;
import com.awe.apex.quant.domain.entity.StockFinIndicator;
import com.awe.apex.quant.domain.entity.StockFundFlow;
import com.awe.apex.quant.factor.FactorCalculator;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.NorthboundFlowMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.mapper.StockFinAbstractMapper;
import com.awe.apex.quant.mapper.StockFinIndicatorMapper;
import com.awe.apex.quant.mapper.StockFundFlowMapper;
import com.awe.apex.quant.service.ILimitUpLadderService;
import com.awe.apex.quant.service.IMarketBriefingService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FactorCenterServiceImplTest {

    @Test
    void shouldBuildSixCategoriesAndFixedWeightAlphaScore() {
        LocalDate tradeDate = LocalDate.of(2026, 8, 20);
        StockBasicMapper stockBasicMapper = mock(StockBasicMapper.class);
        BarDailyMapper barDailyMapper = mock(BarDailyMapper.class);
        StockFinAbstractMapper stockFinAbstractMapper = mock(StockFinAbstractMapper.class);
        StockFinIndicatorMapper stockFinIndicatorMapper = mock(StockFinIndicatorMapper.class);
        StockFundFlowMapper stockFundFlowMapper = mock(StockFundFlowMapper.class);
        NorthboundFlowMapper northboundFlowMapper = mock(NorthboundFlowMapper.class);
        IMarketBriefingService marketBriefingService = mock(IMarketBriefingService.class);
        ILimitUpLadderService limitUpLadderService = mock(ILimitUpLadderService.class);

        when(stockBasicMapper.selectOne(any())).thenReturn(StockBasic.builder()
                .code("600519")
                .name("贵州茅台")
                .market("SH")
                .industry("白酒")
                .latestPrice(new BigDecimal("1500"))
                .peTtm(new BigDecimal("25"))
                .pb(new BigDecimal("8"))
                .build());
        when(barDailyMapper.selectList(any())).thenReturn(buildBars(tradeDate));
        when(stockFinAbstractMapper.selectList(any())).thenReturn(List.of(StockFinAbstract.builder()
                .reportDate(LocalDate.of(2026, 6, 30))
                .revenueYoy(new BigDecimal("12"))
                .netProfitYoy(new BigDecimal("15"))
                .roe(new BigDecimal("22"))
                .build()));
        when(stockFinIndicatorMapper.selectList(any())).thenReturn(List.of(
                StockFinIndicator.builder()
                        .reportDate(LocalDate.of(2026, 6, 30))
                        .roe(new BigDecimal("22"))
                        .build(),
                StockFinIndicator.builder()
                        .reportDate(LocalDate.of(2025, 6, 30))
                        .roe(new BigDecimal("20"))
                        .build()));
        when(stockFundFlowMapper.selectOne(any())).thenReturn(StockFundFlow.builder()
                .tradeDate(tradeDate)
                .mainNetInflow(new BigDecimal("80000000"))
                .build());
        when(northboundFlowMapper.selectOne(any())).thenReturn(NorthboundFlow.builder()
                .tradeDate(tradeDate)
                .netBuyAmount(new BigDecimal("123000000"))
                .dataStatus("PUBLISHED")
                .build());
        when(marketBriefingService.loadCachedBriefing()).thenReturn(MarketBriefingResp.builder()
                .asOf(tradeDate)
                .stance("均衡")
                .stanceScore(65)
                .dataSufficient(true)
                .breadthUp(3000)
                .breadthDown(1800)
                .build());
        when(limitUpLadderService.ladder(null)).thenReturn(LimitUpLadderResp.builder()
                .tradeDate(tradeDate)
                .maxLianban(5)
                .build());

        FactorCenterServiceImpl service = new FactorCenterServiceImpl();
        ReflectionTestUtils.setField(service, "stockBasicMapper", stockBasicMapper);
        ReflectionTestUtils.setField(service, "barDailyMapper", barDailyMapper);
        ReflectionTestUtils.setField(service, "stockFinAbstractMapper", stockFinAbstractMapper);
        ReflectionTestUtils.setField(service, "stockFinIndicatorMapper", stockFinIndicatorMapper);
        ReflectionTestUtils.setField(service, "stockFundFlowMapper", stockFundFlowMapper);
        ReflectionTestUtils.setField(service, "northboundFlowMapper", northboundFlowMapper);
        ReflectionTestUtils.setField(service, "marketBriefingService", marketBriefingService);
        ReflectionTestUtils.setField(service, "limitUpLadderService", limitUpLadderService);
        ReflectionTestUtils.setField(service, "factorCalculator", new FactorCalculator());

        FactorCenterResp result = service.query("600519");

        assertEquals(6, result.getCategories().size());
        assertEquals(new BigDecimal("100.00"), result.getCoverage());
        assertNotNull(result.getAlphaScore());
        assertEquals(List.of(new BigDecimal("30"), new BigDecimal("20"), new BigDecimal("20"),
                        new BigDecimal("15"), new BigDecimal("15")),
                result.getAlphaComponents().stream().map(component -> component.getWeight()).toList());
        FactorCategoryResp capitalCategory = result.getCategories().get(4);
        assertEquals("CAPITAL", capitalCategory.getKey());
        assertEquals(new BigDecimal("0.80"), capitalCategory.getFactors().get(0).getValue());
        assertEquals(new BigDecimal("1.23"), capitalCategory.getFactors().get(1).getValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldExcludeInsufficientMarketDataAndEmptyLimitUpSnapshot() {
        FactorCenterServiceImpl service = new FactorCenterServiceImpl();
        ReflectionTestUtils.setField(service, "factorCalculator", new FactorCalculator());
        MarketBriefingResp insufficientBriefing = MarketBriefingResp.builder()
                .asOf(LocalDate.of(2026, 8, 20))
                .stanceScore(80)
                .dataSufficient(false)
                .build();

        List<AlphaComponentResp> components = ReflectionTestUtils.invokeMethod(
                service, "buildAlphaComponents", List.of(), null, null, insufficientBriefing, null);
        FactorCategoryResp marketCategory = ReflectionTestUtils.invokeMethod(
                service, "buildMarketCategory", insufficientBriefing,
                LimitUpLadderResp.builder().maxLianban(0).build());

        assertNotNull(components);
        assertEquals(false, components.get(4).getAvailable());
        assertNotNull(marketCategory);
        assertEquals("MISSING", marketCategory.getFactors().get(2).getStatus());
    }

    private List<BarDaily> buildBars(LocalDate tradeDate) {
        List<BarDaily> dailyBars = new ArrayList<>();
        for (int index = 0; index < 121; index++) {
            BigDecimal closePrice = new BigDecimal("10").add(BigDecimal.valueOf(index).movePointLeft(1));
            dailyBars.add(BarDaily.builder()
                    .tradeDate(tradeDate.minusDays(120L - index))
                    .closePrice(closePrice)
                    .highPrice(closePrice.add(new BigDecimal("0.10")))
                    .lowPrice(closePrice.subtract(new BigDecimal("0.10")))
                    .amount(index == 120 ? new BigDecimal("150000000") : new BigDecimal("100000000"))
                    .build());
        }
        return dailyBars;
    }
}
