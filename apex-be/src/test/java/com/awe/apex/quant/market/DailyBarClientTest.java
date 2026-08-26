package com.awe.apex.quant.market;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DailyBarClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (Objects.nonNull(server)) {
            server.stop(0);
        }
    }

    @Test
    void eastMoneyFailuresShouldOpenCircuitAndUseSinaFallback() throws Exception {
        AtomicInteger eastMoneyRequests = new AtomicInteger();
        AtomicInteger sinaRequests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/eastmoney", exchange -> {
            eastMoneyRequests.incrementAndGet();
            exchange.sendResponseHeaders(502, -1);
            exchange.close();
        });
        server.createContext("/sina", exchange -> {
            sinaRequests.incrementAndGet();
            byte[] response = ("[{\"day\":\"2026-08-18\",\"open\":\"10.00\",\"high\":\"10.50\","
                    + "\"low\":\"9.80\",\"close\":\"10.20\",\"volume\":\"1000\"}]")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        DailyBarClient dailyBarClient = new DailyBarClient();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        ReflectionTestUtils.setField(dailyBarClient, "eastMoneyUrl", baseUrl + "/eastmoney");
        ReflectionTestUtils.setField(dailyBarClient, "sinaUrl", baseUrl + "/sina");

        for (int index = 0; index < 3; index++) {
            List<BarDaily> bars = dailyBarClient.fetchDailyBars("000001", "2026-08-18", "2026-08-18");
            assertEquals(1, bars.size());
            assertEquals(DailyBarClient.SOURCE_SINA, bars.get(0).getSource());
        }

        assertEquals(2, eastMoneyRequests.get());
        assertEquals(3, sinaRequests.get());
    }

    @Test
    void fastDailyBarsShouldUseSinaWithoutWaitingForEastMoney() throws Exception {
        AtomicInteger eastMoneyRequests = new AtomicInteger();
        AtomicInteger sinaRequests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/eastmoney", exchange -> {
            eastMoneyRequests.incrementAndGet();
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
        server.createContext("/sina", exchange -> {
            sinaRequests.incrementAndGet();
            byte[] response = ("[{\"day\":\"2026-08-18\",\"open\":\"10.00\",\"high\":\"10.50\","
                    + "\"low\":\"9.80\",\"close\":\"10.20\",\"volume\":\"1000\"}]")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        DailyBarClient dailyBarClient = new DailyBarClient();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        ReflectionTestUtils.setField(dailyBarClient, "eastMoneyUrl", baseUrl + "/eastmoney");
        ReflectionTestUtils.setField(dailyBarClient, "sinaUrl", baseUrl + "/sina");

        List<BarDaily> bars = dailyBarClient.fetchDailyBarsFast("000001", "2026-08-18", "2026-08-18");

        assertEquals(1, bars.size());
        assertEquals(DailyBarClient.SOURCE_SINA, bars.get(0).getSource());
        assertEquals(1, sinaRequests.get());
        assertEquals(0, eastMoneyRequests.get());
    }

    @Test
    void fastDailyBarsShouldTryEastMoneyWhileBatchCircuitIsOpen() throws Exception {
        AtomicInteger eastMoneyRequests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/eastmoney", exchange -> {
            eastMoneyRequests.incrementAndGet();
            byte[] response = ("{\"data\":{\"klines\":["
                    + "\"2026-08-25,10.00,10.20,10.50,9.80,1000,10000,0,2.00,0,1.50\""
                    + "]}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/sina", exchange -> {
            exchange.sendResponseHeaders(502, -1);
            exchange.close();
        });
        server.start();

        DailyBarClient dailyBarClient = new DailyBarClient();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        ReflectionTestUtils.setField(dailyBarClient, "eastMoneyUrl", baseUrl + "/eastmoney");
        ReflectionTestUtils.setField(dailyBarClient, "sinaUrl", baseUrl + "/sina");
        AtomicLong cooldownUntil = (AtomicLong) ReflectionTestUtils.getField(
                dailyBarClient, "eastMoneyCooldownUntil");
        cooldownUntil.set(System.currentTimeMillis() + 60_000L);

        List<BarDaily> bars = dailyBarClient.fetchDailyBarsFast(
                "920176", "2026-08-25", "2026-08-25");

        assertEquals(1, bars.size());
        assertEquals(DailyBarClient.SOURCE_EASTMONEY, bars.get(0).getSource());
        assertEquals(1, eastMoneyRequests.get());
    }

    @Test
    void batchDailyBarsShouldTryEastMoneyForHkWhileCircuitIsOpen() throws Exception {
        AtomicInteger eastMoneyRequests = new AtomicInteger();
        AtomicInteger sinaRequests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/eastmoney", exchange -> {
            eastMoneyRequests.incrementAndGet();
            byte[] response = ("{\"data\":{\"klines\":["
                    + "\"2026-08-25,38.00,38.50,39.00,37.80,1000,38500,0,1.20,0,0.80\""
                    + "]}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/sina", exchange -> {
            sinaRequests.incrementAndGet();
            exchange.sendResponseHeaders(502, -1);
            exchange.close();
        });
        server.start();

        DailyBarClient dailyBarClient = new DailyBarClient();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        ReflectionTestUtils.setField(dailyBarClient, "eastMoneyUrl", baseUrl + "/eastmoney");
        ReflectionTestUtils.setField(dailyBarClient, "sinaUrl", baseUrl + "/sina");
        AtomicLong cooldownUntil = (AtomicLong) ReflectionTestUtils.getField(
                dailyBarClient, "eastMoneyCooldownUntil");
        cooldownUntil.set(System.currentTimeMillis() + 60_000L);

        List<BarDaily> bars = dailyBarClient.fetchDailyBars(
                "01810", "2026-08-25", "2026-08-25");

        assertEquals(1, bars.size());
        assertEquals(DailyBarClient.SOURCE_EASTMONEY, bars.get(0).getSource());
        assertEquals(1, eastMoneyRequests.get());
        assertEquals(0, sinaRequests.get());
    }

    @Test
    void interruptedRequestShouldNotIncreaseEastMoneyFailureCount() {
        DailyBarClient dailyBarClient = new DailyBarClient();
        AtomicInteger failCount = (AtomicInteger) ReflectionTestUtils.getField(
                dailyBarClient, "eastMoneyFailCount");

        Thread.currentThread().interrupt();
        try {
            ReflectionTestUtils.invokeMethod(dailyBarClient, "markEastMoneyFailure",
                    "600000", new BusinessException("行情请求被中断"));
        } finally {
            Thread.interrupted();
        }

        assertEquals(0, failCount.get());
    }
}
