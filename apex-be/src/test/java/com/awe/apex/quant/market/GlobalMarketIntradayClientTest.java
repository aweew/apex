package com.awe.apex.quant.market;

import com.awe.apex.quant.domain.dto.GlobalMarketIntradayResp;
import com.awe.apex.quant.domain.dto.IntradayKlineBar;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalMarketIntradayClientTest {

    @Test
    void resolvesSupportedGlobalMarketSymbols() {
        GlobalMarketIntradayClient client = new GlobalMarketIntradayClient();

        assertEquals("100.NDX", ReflectionTestUtils.invokeMethod(client, "resolveSecId", "usIXIC"));
        assertEquals("100.DJIA", ReflectionTestUtils.invokeMethod(client, "resolveSecId", "usDJI"));
        assertEquals("100.SPX", ReflectionTestUtils.invokeMethod(client, "resolveSecId", "usINX"));
        assertEquals("104.CN00Y", ReflectionTestUtils.invokeMethod(client, "resolveSecId", "hf_CHA50CFD"));
    }

    @Test
    void parsesAndCompactsEastMoneyIntradayBars() {
        GlobalMarketIntradayClient client = new GlobalMarketIntradayClient();
        ArrayNode trends = JsonNodeFactory.instance.arrayNode();
        for (int index = 0; index < 121; index++) {
            int totalMinutes = 21 * 60 + 30 + index;
            String datetime = String.format("2026-08-31 %02d:%02d", totalMinutes / 60, totalMinutes % 60);
            BigDecimal openPrice = BigDecimal.valueOf(100 + index);
            BigDecimal closePrice = openPrice.add(BigDecimal.ONE);
            trends.add(datetime + "," + openPrice + "," + closePrice + ","
                    + closePrice.add(BigDecimal.ONE) + "," + openPrice.subtract(BigDecimal.ONE) + ",10,0,100.5");
        }
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        ObjectNode data = root.putObject("data");
        data.put("prePrice", "99.5");
        data.set("trends", trends);

        GlobalMarketIntradayResp intraday = ReflectionTestUtils.invokeMethod(client, "parse", "usIXIC", root);

        assertEquals(new BigDecimal("99.5"), intraday.getPreviousClose());
        assertTrue(intraday.getBars().size() <= 60);
        IntradayKlineBar firstBar = intraday.getBars().get(0);
        assertEquals(new BigDecimal("100"), firstBar.getOpenPrice());
        assertEquals(new BigDecimal("103"), firstBar.getClosePrice());
        assertEquals(new BigDecimal("104"), firstBar.getHighPrice());
        assertEquals(new BigDecimal("99"), firstBar.getLowPrice());
    }
}
