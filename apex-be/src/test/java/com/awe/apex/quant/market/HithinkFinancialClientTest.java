package com.awe.apex.quant.market;

import com.awe.apex.quant.config.HithinkFinancialProperties;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HithinkFinancialClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (Objects.nonNull(server)) {
            server.stop(0);
        }
    }

    @Test
    void mapsSnapshotToApexStockBasic() {
        HithinkFinancialClient client = new HithinkFinancialClient();
        HithinkFinancialProperties properties = new HithinkFinancialProperties();
        ReflectionTestUtils.setField(client, "properties", properties);

        HithinkSnapshotItem item = HithinkSnapshotItem.builder()
                .thscode("600519.SH")
                .ticker("600519")
                .lastPrice(new BigDecimal("1488.88"))
                .priceChangeRatioPct(new BigDecimal("1.74"))
                .timestamp(1789094400000L)
                .build();

        StockBasic basic = client.toStockBasic(item);

        assertEquals("600519", basic.getCode());
        assertEquals("SH", basic.getMarket());
        assertEquals(new BigDecimal("1488.88"), basic.getLatestPrice());
        assertEquals(new BigDecimal("1.74"), basic.getPctChg());
        assertEquals("hithink", basic.getSource());
        assertEquals(LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(1789094400000L), ZoneId.of("Asia/Shanghai")),
                basic.getQuoteTime());
    }

    @Test
    void parsesSnapshotResponseAndDropsRowsWithoutPositivePrice() {
        HithinkFinancialClient client = new HithinkFinancialClient();
        ReflectionTestUtils.setField(client, "properties", enabledProperties());

        Map<String, StockBasic> result = client.parseSnapshotResponse("""
                {
                  "code": 0,
                  "message": "ok",
                  "request_id": "req-1",
                  "data": {
                    "timestamp": 1789094400000,
                    "item": [
                      {
                        "thscode": "600519.SH",
                        "ticker": "600519",
                        "last_price": 1488.88,
                        "price_change_ratio_pct": 1.74
                      },
                      {
                        "thscode": "000001.SZ",
                        "ticker": "000001",
                        "last_price": null,
                        "price_change_ratio_pct": null
                      }
                    ]
                  }
                }
                """);

        assertEquals(1, result.size());
        assertEquals(new BigDecimal("1488.88"), result.get("600519").getLatestPrice());
    }

    @Test
    void parsesValuationResponse() {
        HithinkFinancialClient client = new HithinkFinancialClient();
        ReflectionTestUtils.setField(client, "properties", enabledProperties());

        Map<String, StockBasic> result = client.parseValuationResponse("""
                {
                  "code": 0,
                  "request_id": "req-2",
                  "data": {
                    "timestamp": 1789094400000,
                    "total": 1,
                    "item": [
                      {
                        "thscode": "600519.SH",
                        "pe_ttm": 21.5,
                        "pb_mrq": 8.2
                      }
                    ]
                  }
                }
                """);

        assertEquals(new BigDecimal("21.5"), result.get("600519").getPeTtm());
        assertEquals(new BigDecimal("8.2"), result.get("600519").getPb());
    }

    @Test
    void rejectsBusinessErrorEvenWhenHttpResponseIsSuccessful() {
        HithinkFinancialClient client = new HithinkFinancialClient();
        ReflectionTestUtils.setField(client, "properties", enabledProperties());

        assertThrows(RuntimeException.class, () -> client.parseSnapshotResponse("""
                {
                  "code": 2003,
                  "message": "invalid key",
                  "request_id": "req-3",
                  "data": null
                }
                """));
    }

    @Test
    void sendsApiKeyAndParsesSnapshotOverHttp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/a-share/prices/snapshot", exchange -> {
            assertEquals("test-key", exchange.getRequestHeaders().getFirst("X-api-key"));
            byte[] body = """
                    {"code":0,"request_id":"req-4","data":{"timestamp":1789094400000,
                    "item":[{"thscode":"600519.SH","ticker":"600519","last_price":1488.88,
                    "price_change_ratio_pct":1.74}]}}
                    """.getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        HithinkFinancialClient client = new HithinkFinancialClient();
        HithinkFinancialProperties properties = enabledProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        ReflectionTestUtils.setField(client, "properties", properties);

        StockBasic result = client.fetchSnapshot("600519");

        assertEquals(new BigDecimal("1488.88"), result.getLatestPrice());
    }

    private HithinkFinancialProperties enabledProperties() {
        HithinkFinancialProperties properties = new HithinkFinancialProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        return properties;
    }
}
