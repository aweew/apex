package com.awe.apex.quant.bot.client;

import com.awe.apex.quant.bot.config.ApexBotProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeClawMessageClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void skipsWhenChannelIsDisabled() {
        ApexBotProperties properties = properties(false, "http://127.0.0.1:1", "user@im.wechat");
        WeClawMessageClient client = client(properties);

        assertFalse(client.sendText("测试消息"));
    }

    @Test
    void postsTextToWeClawApi() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/send", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().close();
        });
        server.start();

        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        WeClawMessageClient client = client(properties(true, baseUrl, "user@im.wechat"));

        assertTrue(client.sendText("宁德时代触发提醒"));
        assertTrue(requestBody.get().contains("user@im.wechat"));
        assertTrue(requestBody.get().contains("宁德时代触发提醒"));
    }

    private WeClawMessageClient client(ApexBotProperties properties) {
        WeClawMessageClient client = new WeClawMessageClient();
        ReflectionTestUtils.setField(client, "properties", properties);
        ReflectionTestUtils.setField(client, "objectMapper", new ObjectMapper());
        return client;
    }

    private ApexBotProperties properties(boolean enabled, String baseUrl, String recipient) {
        ApexBotProperties properties = new ApexBotProperties();
        properties.getWeclaw().setEnabled(enabled);
        properties.getWeclaw().setBaseUrl(baseUrl);
        properties.getWeclaw().setRecipient(recipient);
        properties.getWeclaw().setTimeoutMs(3000);
        return properties;
    }
}
