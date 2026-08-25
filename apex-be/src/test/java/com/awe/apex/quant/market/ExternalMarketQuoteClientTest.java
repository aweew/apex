package com.awe.apex.quant.market;

import com.awe.apex.quant.domain.dto.ExternalMarketItemResp;
import com.awe.apex.common.util.SpringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExternalMarketQuoteClientTest {

    private static ApplicationContext originalApplicationContext;

    @BeforeAll
    static void setUpJsonUtils() {
        originalApplicationContext = SpringUtils.getApplicationContext();
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBean(ObjectMapper.class)).thenReturn(new ObjectMapper().findAndRegisterModules());
        new SpringUtils().setApplicationContext(applicationContext);
    }

    @AfterAll
    static void restoreJsonUtils() {
        new SpringUtils().setApplicationContext(originalApplicationContext);
    }

    @Test
    void parsesDollarIndexWithCurrentPriceAndPreviousClose() throws Exception {
        ExternalMarketQuoteClient client = new ExternalMarketQuoteClient();
        String response = """
                {"chart":{"result":[{"meta":{"regularMarketPrice":99.055,"chartPreviousClose":99.002,"regularMarketTime":1787628798},"timestamp":[1787544000,1787628798],"indicators":{"quote":[{"close":[99.0,99.055]}]}}],"error":null}}
                """;

        ExternalMarketItemResp item = client.parse(ExternalMarketIndicatorEnum.DOLLAR_INDEX, response);

        assertEquals("DOLLAR_INDEX", item.getCode());
        assertEquals("美元指数", item.getName());
        assertEquals(new BigDecimal("99.055"), item.getLatestPrice());
        assertEquals(new BigDecimal("0.05"), item.getPctChg());
        assertEquals(LocalDateTime.of(2026, 8, 25, 11, 33, 18), item.getQuoteTime());
        assertEquals("Yahoo Finance", item.getSource());
        assertTrue(item.isAvailable());
        assertTrue(item.getAShareImpact().contains("美元指数上涨"));
    }

    @Test
    void parsesTencentGoldFallbackWhenPrimarySourceIsUnavailable() {
        ExternalMarketQuoteClient client = new ExternalMarketQuoteClient();
        String response = "v_hf_GC=\"4688.48,-0.20,4690.40,4690.50,4755.00,4670.50,11:41:01,4697.80,4710.10,0,2,1,2026-08-25,纽约黄金\";";

        ExternalMarketItemResp item = client.parseTencentFutures(ExternalMarketIndicatorEnum.GOLD, response);

        assertEquals("GOLD", item.getCode());
        assertEquals(new BigDecimal("4688.48"), item.getLatestPrice());
        assertEquals(new BigDecimal("-0.20"), item.getPctChg());
        assertEquals("Tencent Finance", item.getSource());
        assertTrue(item.getAShareImpact().contains("黄金回落"));
    }

    @Test
    void parsesEastmoneyOffshoreRenminbiFallbackWhenPrimarySourceIsUnavailable() throws Exception {
        ExternalMarketQuoteClient client = new ExternalMarketQuoteClient();
        String response = """
                {"data":{"diff":[{"f2":6.7234,"f3":0.02,"f12":"USDCNH","f14":"美元兑离岸人民币","f124":1787629356}]}}
                """;

        ExternalMarketItemResp item = client.parseEastmoneyOffshoreRenminbi(
                ExternalMarketIndicatorEnum.OFFSHORE_RENMINBI, response);

        assertEquals("OFFSHORE_RENMINBI", item.getCode());
        assertEquals(new BigDecimal("6.7234"), item.getLatestPrice());
        assertEquals(new BigDecimal("0.02"), item.getPctChg());
        assertEquals("Eastmoney", item.getSource());
        assertTrue(item.getAShareImpact().contains("人民币数值上升"));
    }
}
