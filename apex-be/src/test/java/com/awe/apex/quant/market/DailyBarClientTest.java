package com.awe.apex.quant.market;

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
}
