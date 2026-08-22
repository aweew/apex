package com.awe.apex.quant.ai;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KimiChatClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void preservesConversationRolesAndRetriesRateLimit() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            int currentRequest = requestCount.incrementAndGet();
            byte[] response = (currentRequest == 1
                    ? "{\"error\":{\"message\":\"rate limited\"}}"
                    : "{\"choices\":[{\"message\":{\"content\":\"增强结论\"}}]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(currentRequest == 1 ? 429 : 200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        AiChatProperties properties = new AiChatProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setTimeoutMs(3000);
        KimiChatClient client = new KimiChatClient();
        ReflectionTestUtils.setField(client, "properties", properties);

        String answer = client.chatMessages(List.of(
                KimiChatMessage.builder().role("system").content("系统约束").build(),
                KimiChatMessage.builder().role("user").content("第一问").build(),
                KimiChatMessage.builder().role("assistant").content("第一答").build(),
                KimiChatMessage.builder().role("user").content("继续分析").build()), 500);

        assertEquals("增强结论", answer);
        assertEquals(2, requestCount.get());
        assertTrue(requestBody.get().matches("(?s).*\\\"role\\\":\\\"system\\\".*\\\"role\\\":\\\"user\\\".*\\\"role\\\":\\\"assistant\\\".*\\\"role\\\":\\\"user\\\".*"));
    }
}
