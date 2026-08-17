package com.awe.apex.quant.bot.auth;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.bot.config.ApexBotProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BotHmacAuthServiceTest {

    private static final String CLIENT_KEY = "clawbot";
    private static final String CLIENT_SECRET = "test-secret";
    private static final long NOW = 1786579200L;

    private BotHmacAuthService authService;
    private ApexBotProperties properties;

    @BeforeEach
    void setUp() {
        properties = new ApexBotProperties();
        properties.setEnabled(true);
        properties.setClientKey(CLIENT_KEY);
        properties.setClientSecret(CLIENT_SECRET);
        properties.setApexUserId(7L);
        properties.setExternalUserId("wechat-user");
        properties.setTimestampToleranceSeconds(300);

        authService = new BotHmacAuthService();
        ReflectionTestUtils.setField(authService, "properties", properties);
        ReflectionTestUtils.setField(authService, "clock", Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC));
    }

    @Test
    void acceptsValidSignature() {
        byte[] body = "{\"question\":\"宁德时代能买吗\"}".getBytes(StandardCharsets.UTF_8);
        String digest = sha256(body);
        String signature = sign("POST", "/apex/bot/v1/ask", NOW, "nonce-1", digest);

        assertDoesNotThrow(() -> authService.validate(
                "POST", "/apex/bot/v1/ask", CLIENT_KEY, String.valueOf(NOW),
                "nonce-1", digest, signature, body));
    }

    @Test
    void rejectsTamperedBody() {
        byte[] original = "{\"question\":\"宁德时代能买吗\"}".getBytes(StandardCharsets.UTF_8);
        byte[] tampered = "{\"question\":\"平安银行能买吗\"}".getBytes(StandardCharsets.UTF_8);
        String digest = sha256(original);
        String signature = sign("POST", "/apex/bot/v1/ask", NOW, "nonce-2", digest);

        assertThrows(BusinessException.class, () -> authService.validate(
                "POST", "/apex/bot/v1/ask", CLIENT_KEY, String.valueOf(NOW),
                "nonce-2", digest, signature, tampered));
    }

    @Test
    void rejectsExpiredTimestampAndNonceReplay() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String digest = sha256(body);
        String expiredSignature = sign("POST", "/apex/bot/v1/ask", NOW - 301, "nonce-3", digest);
        assertThrows(BusinessException.class, () -> authService.validate(
                "POST", "/apex/bot/v1/ask", CLIENT_KEY, String.valueOf(NOW - 301),
                "nonce-3", digest, expiredSignature, body));

        String signature = sign("POST", "/apex/bot/v1/ask", NOW, "nonce-4", digest);
        authService.validate("POST", "/apex/bot/v1/ask", CLIENT_KEY, String.valueOf(NOW),
                "nonce-4", digest, signature, body);
        assertThrows(BusinessException.class, () -> authService.validate(
                "POST", "/apex/bot/v1/ask", CLIENT_KEY, String.valueOf(NOW),
                "nonce-4", digest, signature, body));
    }

    @Test
    void rejectsTimestampOverflow() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String digest = sha256(body);
        String pastSignature = sign("POST", "/apex/bot/v1/ask", Long.MIN_VALUE, "nonce-5", digest);
        String futureSignature = sign("POST", "/apex/bot/v1/ask", Long.MAX_VALUE, "nonce-6", digest);

        assertThrows(BusinessException.class, () -> authService.validate(
                "POST", "/apex/bot/v1/ask", CLIENT_KEY, String.valueOf(Long.MIN_VALUE),
                "nonce-5", digest, pastSignature, body));
        assertThrows(BusinessException.class, () -> authService.validate(
                "POST", "/apex/bot/v1/ask", CLIENT_KEY, String.valueOf(Long.MAX_VALUE),
                "nonce-6", digest, futureSignature, body));
    }

    @Test
    void rejectsClientWithoutTrustedApexUserBinding() {
        properties.setApexUserId(null);
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String digest = sha256(body);
        String signature = sign("POST", "/apex/bot/v1/ask", NOW, "nonce-7", digest);

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.validate(
                "POST", "/apex/bot/v1/ask", CLIENT_KEY, String.valueOf(NOW),
                "nonce-7", digest, signature, body));

        assertEquals("Bot API 未绑定 Apex 用户", exception.getMessage());
    }

    @Test
    void rejectsClientWithoutTrustedExternalUserBinding() {
        properties.setExternalUserId("");
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String digest = sha256(body);
        String signature = sign("POST", "/apex/bot/v1/ask", NOW, "nonce-8", digest);

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.validate(
                "POST", "/apex/bot/v1/ask", CLIENT_KEY, String.valueOf(NOW),
                "nonce-8", digest, signature, body));

        assertEquals("Bot API 未绑定外部用户", exception.getMessage());
    }

    private String sign(String method, String path, long timestamp, String nonce, String contentDigest) {
        try {
            String canonical = method + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + contentDigest;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(CLIENT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String sha256(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
