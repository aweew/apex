package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.domain.dto.ValuationResp;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.domain.entity.StockFinAbstract;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.mapper.StockFinAbstractMapper;
import com.awe.apex.quant.mapper.StockFinIndicatorMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ValuationGrowthQualityTest {

    @Test
    void verifiedGrowthUsesGrowthQualityAdjustmentInsteadOfMechanicalIndustryPePenalty() {
        ValuationResp response = evaluate(new BigDecimal("40"), new BigDecimal("35"), new BigDecimal("18"));

        assertEquals("FAIR", response.getLevel());
        assertTrue(response.getFairPe().compareTo(new BigDecimal("45")) > 0);
        assertTrue(response.getDimensions().stream()
                .anyMatch(item -> "growth".equals(item.getKey())
                        && "高增长已验证".equals(item.getVerdict())
                        && new BigDecimal("0.20").compareTo(item.getWeight()) == 0));
        assertTrue(response.getBullPoints().contains("营收利润同步增长，成长估值已按质量修正"));
    }

    @Test
    void unverifiedGrowthKeepsConservativeFairPeAndDefaultGrowthWeight() {
        ValuationResp response = evaluate(new BigDecimal("40"), new BigDecimal("35"), new BigDecimal("7"));

        assertTrue(response.getFairPe().compareTo(new BigDecimal("40")) < 0);
        assertTrue(response.getDimensions().stream()
                .anyMatch(item -> "growth".equals(item.getKey())
                        && "高增长待验证".equals(item.getVerdict())
                        && new BigDecimal("0.08").compareTo(item.getWeight()) == 0));
    }

    private ValuationResp evaluate(BigDecimal revenueYoy, BigDecimal profitYoy, BigDecimal roe) {
        ValuationServiceImpl service = new ValuationServiceImpl();
        StockBasicMapper stockBasicMapper = mock(StockBasicMapper.class);
        StockFinAbstractMapper stockFinAbstractMapper = mock(StockFinAbstractMapper.class);
        StockFinIndicatorMapper stockFinIndicatorMapper = mock(StockFinIndicatorMapper.class);
        ReflectionTestUtils.setField(service, "stockBasicMapper", stockBasicMapper);
        ReflectionTestUtils.setField(service, "stockFinAbstractMapper", stockFinAbstractMapper);
        ReflectionTestUtils.setField(service, "stockFinIndicatorMapper", stockFinIndicatorMapper);

        StockBasic target = StockBasic.builder().code("300001").name("科技成长")
                .industry("软件开发").latestPrice(new BigDecimal("50"))
                .peTtm(new BigDecimal("50")).pb(new BigDecimal("4")).build();
        List<StockBasic> peers = List.of(
                peer("300002", "28"), peer("300003", "29"), peer("300004", "30"), peer("300005", "30"),
                peer("300006", "30"), peer("300007", "31"), peer("300008", "31"), peer("300009", "32"));
        StockFinAbstract financial = StockFinAbstract.builder().revenueYoy(revenueYoy).netProfitYoy(profitYoy)
                .roe(roe).debtRatio(new BigDecimal("35")).netMargin(new BigDecimal("20"))
                .build();
        when(stockBasicMapper.selectOne(any())).thenReturn(target);
        when(stockBasicMapper.selectList(any())).thenReturn(peers);
        when(stockFinAbstractMapper.selectOne(any())).thenReturn(financial);

        return service.evaluate("300001");
    }

    private StockBasic peer(String code, String peTtm) {
        return StockBasic.builder().code(code).peTtm(new BigDecimal(peTtm)).pb(new BigDecimal("3")).build();
    }
}
