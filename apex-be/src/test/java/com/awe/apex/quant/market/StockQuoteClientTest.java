package com.awe.apex.quant.market;

import com.awe.apex.quant.domain.entity.StockBasic;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StockQuoteClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (Objects.nonNull(server)) {
            server.stop(0);
        }
    }

    @Test
    void mapsEastMoneyPeVariantsToTheirCorrectFields() throws Exception {
        JsonNode data = OBJECT_MAPPER.readTree("{\"f162\":1502,\"f163\":1988,\"f164\":1979}");
        StockBasic basic = new StockBasic();

        StockQuoteClient.applyEastMoneyValuationFields(basic, data);

        assertEquals(new BigDecimal("15.0200"), basic.getPeDynamic());
        assertEquals(new BigDecimal("19.8800"), basic.getPeStatic());
        assertEquals(new BigDecimal("19.7900"), basic.getPeTtm());
    }

    @Test
    void mapsTencentPeToDynamicRatherThanTtm() {
        String[] parts = new String[47];
        parts[39] = "15.02";
        StockBasic basic = new StockBasic();

        StockQuoteClient.applyTencentValuationFields(basic, parts);

        assertEquals(new BigDecimal("15.02"), basic.getPeDynamic());
        assertNull(basic.getPeTtm());
    }

    @Test
    void fallsBackToTencentWhenSinaQuoteRequestFails() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/sina", exchange -> {
            exchange.sendResponseHeaders(502, -1);
            exchange.close();
        });
        server.createContext("/tencent", exchange -> {
            String[] parts = new String[33];
            java.util.Arrays.fill(parts, "");
            parts[1] = "平安银行";
            parts[2] = "000001";
            parts[3] = "10.50";
            parts[32] = "1.23";
            byte[] response = ("v_sz000001=\"" + String.join("~", parts) + "\";")
                    .getBytes(Charset.forName("GBK"));
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        StockQuoteClient client = new StockQuoteClient();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        ReflectionTestUtils.setField(client, "sinaQuoteUrl", baseUrl + "/sina?list=");
        ReflectionTestUtils.setField(client, "tencentQuoteUrl", baseUrl + "/tencent?q=");

        StockBasic basic = client.fetchRealtime("000001");

        assertEquals("平安银行", basic.getName());
        assertEquals(new BigDecimal("10.50"), basic.getLatestPrice());
        assertEquals(new BigDecimal("1.23"), basic.getPctChg());
        assertEquals("tencent-rt", basic.getSource());
    }

    @Test
    void fastRealtimeShouldReturnSinaQuoteWithoutRequestingTencent() throws Exception {
        AtomicInteger tencentRequests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/sina", exchange -> {
            byte[] response = "var hq_str_sz000001=\"平安银行,10.00,10.00,10.50,10.80,9.90,10.20,10.30,1000,100000\";"
                    .getBytes(Charset.forName("GBK"));
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/tencent", exchange -> {
            tencentRequests.incrementAndGet();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();

        StockQuoteClient client = new StockQuoteClient();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        ReflectionTestUtils.setField(client, "sinaQuoteUrl", baseUrl + "/sina?list=");
        ReflectionTestUtils.setField(client, "tencentQuoteUrl", baseUrl + "/tencent?q=");

        StockBasic basic = client.fetchRealtimeFast("000001");

        assertEquals(new BigDecimal("10.50"), basic.getLatestPrice());
        assertEquals(new BigDecimal("5.0000"), basic.getPctChg());
        assertEquals("sina", basic.getSource());
        assertEquals(0, tencentRequests.get());
    }

    @Test
    void fastRealtimeShouldFallbackToTencentWhenSinaPriceIsZero() throws Exception {
        AtomicInteger tencentRequests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/sina", exchange -> {
            byte[] response = "var hq_str_sz000001=\"平安银行,10.00,10.00,0.00,10.80,9.90,10.20,10.30,1000,100000\";"
                    .getBytes(Charset.forName("GBK"));
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/tencent", exchange -> {
            tencentRequests.incrementAndGet();
            String[] parts = new String[33];
            java.util.Arrays.fill(parts, "");
            parts[1] = "平安银行";
            parts[2] = "000001";
            parts[3] = "10.50";
            parts[32] = "1.23";
            byte[] response = ("v_sz000001=\"" + String.join("~", parts) + "\";")
                    .getBytes(Charset.forName("GBK"));
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        StockQuoteClient client = new StockQuoteClient();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        ReflectionTestUtils.setField(client, "sinaQuoteUrl", baseUrl + "/sina?list=");
        ReflectionTestUtils.setField(client, "tencentQuoteUrl", baseUrl + "/tencent?q=");

        StockBasic basic = client.fetchRealtimeFast("000001");

        assertEquals(new BigDecimal("10.50"), basic.getLatestPrice());
        assertEquals(1, tencentRequests.get());
    }
}
