package com.awe.apex.quant.bot.auth;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.bot.config.ApexBotProperties;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bot API HMAC 鉴权服务。
 */
@Service
public class BotHmacAuthService {

    @Resource
    private ApexBotProperties properties;

    private Clock clock = Clock.systemUTC();

    private final Map<String, Long> nonceExpireAt = new ConcurrentHashMap<>();

    /**
     * 校验 Bot API 请求签名。
     *
     * @param method        HTTP 方法
     * @param requestPath   请求路径
     * @param clientKey     客户端标识
     * @param timestampText Unix 秒文本
     * @param nonce         随机值
     * @param contentDigest 请求体摘要
     * @param signature     HMAC 签名
     * @param body          原始请求体
     */
    public void validate(String method,
                         String requestPath,
                         String clientKey,
                         String timestampText,
                         String nonce,
                         String contentDigest,
                         String signature,
                         byte[] body) {
        if (!properties.isEnabled()) {
            throw new BusinessException("Bot API 未启用");
        }
        if (StringUtils.isBlank(properties.getClientKey()) || StringUtils.isBlank(properties.getClientSecret())) {
            throw new BusinessException("Bot API 鉴权未配置");
        }
        if (!properties.getClientKey().equals(clientKey)) {
            throw new BusinessException("Bot API 客户端无效");
        }
        if (StringUtils.isBlank(timestampText) || StringUtils.isBlank(nonce)
                || StringUtils.isBlank(contentDigest) || StringUtils.isBlank(signature)) {
            throw new BusinessException("Bot API 签名头不完整");
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampText);
        } catch (NumberFormatException ex) {
            throw new BusinessException("Bot API 时间戳无效");
        }
        long now = clock.instant().getEpochSecond();
        int tolerance = Math.max(30, properties.getTimestampToleranceSeconds());
        long earliestTimestamp = now - tolerance;
        long latestTimestamp = now + tolerance;
        if (timestamp < earliestTimestamp || timestamp > latestTimestamp) {
            throw new BusinessException("Bot API 请求已过期");
        }

        byte[] requestBody = Objects.isNull(body) ? new byte[0] : body;
        String actualDigest = sha256(requestBody);
        if (!secureEquals(actualDigest, contentDigest.toLowerCase())) {
            throw new BusinessException("Bot API 请求体摘要无效");
        }

        String canonical = method.toUpperCase() + "\n" + requestPath + "\n" + timestampText
                + "\n" + nonce + "\n" + actualDigest;
        String expectedSignature = hmac(canonical, properties.getClientSecret());
        if (!secureEquals(expectedSignature, signature.toLowerCase())) {
            throw new BusinessException("Bot API 签名无效");
        }

        nonceExpireAt.entrySet().removeIf(entry -> entry.getValue() < now);
        Long existingExpireAt = nonceExpireAt.putIfAbsent(clientKey + ":" + nonce, now + tolerance);
        if (Objects.nonNull(existingExpireAt)) {
            throw new BusinessException("Bot API 请求已重放");
        }
    }

    private String sha256(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (Exception ex) {
            throw new BusinessException("Bot API 摘要计算失败", ex);
        }
    }

    private String hmac(String content, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new BusinessException("Bot API 签名计算失败", ex);
        }
    }

    private boolean secureEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII));
    }
}
