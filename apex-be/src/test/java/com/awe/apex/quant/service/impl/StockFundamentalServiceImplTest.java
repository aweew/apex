package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.domain.dto.StockFundamentalResp;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.domain.entity.StockFinAbstract;
import com.awe.apex.quant.domain.entity.StockFinReportItem;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.mapper.StockFinAbstractMapper;
import com.awe.apex.quant.mapper.StockFinIndicatorMapper;
import com.awe.apex.quant.mapper.StockFinReportItemMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StockFundamentalServiceImplTest {

    @Test
    void queryShouldBuildFinancialQualityUsingSameReportPeriod() {
        StockFinAbstractMapper abstractMapper = mock(StockFinAbstractMapper.class);
        StockFinIndicatorMapper indicatorMapper = mock(StockFinIndicatorMapper.class);
        StockFinReportItemMapper reportItemMapper = mock(StockFinReportItemMapper.class);
        StockBasicMapper stockBasicMapper = mock(StockBasicMapper.class);
        LocalDate reportDate = LocalDate.of(2025, 12, 31);

        when(abstractMapper.selectList(any())).thenReturn(List.of(StockFinAbstract.builder()
                .code("600519")
                .reportDate(reportDate)
                .netProfit(new BigDecimal("1000000000"))
                .build()));
        when(indicatorMapper.selectList(any())).thenReturn(List.of());
        when(reportItemMapper.selectCount(any())).thenReturn(3L);
        when(reportItemMapper.selectList(any())).thenReturn(List.of(
                reportItem("cashflow", reportDate, "经营活动产生的现金流量净额", "1100000000"),
                reportItem("cashflow", reportDate, "购建固定资产、无形资产和其他长期资产支付的现金", "200000000"),
                reportItem("balance", reportDate, "应收账款", "500000000")
        ));
        when(stockBasicMapper.selectOne(any())).thenReturn(StockBasic.builder()
                .code("600519")
                .totalMv(new BigDecimal("20000000000"))
                .build());

        StockFundamentalServiceImpl service = new StockFundamentalServiceImpl();
        ReflectionTestUtils.setField(service, "stockFinAbstractMapper", abstractMapper);
        ReflectionTestUtils.setField(service, "stockFinIndicatorMapper", indicatorMapper);
        ReflectionTestUtils.setField(service, "stockFinReportItemMapper", reportItemMapper);
        ReflectionTestUtils.setField(service, "stockBasicMapper", stockBasicMapper);

        StockFundamentalResp response = service.query("600519", 40, 12);

        assertNotNull(response.getFinancialQuality());
        assertEquals(reportDate, response.getFinancialQuality().getReportDate());
        assertEquals(new BigDecimal("1100000000"), response.getFinancialQuality().getOperatingCashFlow());
        assertEquals(new BigDecimal("500000000"), response.getFinancialQuality().getAccountsReceivable());
        assertEquals(new BigDecimal("1.10"), response.getFinancialQuality().getCashConversionRatio());
        assertEquals(new BigDecimal("900000000"), response.getFinancialQuality().getFreeCashFlow());
        assertEquals(new BigDecimal("22.22"), response.getFinancialQuality().getPriceToFreeCashFlow());
    }

    private StockFinReportItem reportItem(String statementType, LocalDate reportDate, String itemName, String itemValue) {
        return StockFinReportItem.builder()
                .statementType(statementType)
                .reportDate(reportDate)
                .itemName(itemName)
                .itemValue(new BigDecimal(itemValue))
                .build();
    }
}
