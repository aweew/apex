package com.awe.apex.quant.bot.client;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.bot.config.ApexBotProperties;
import com.awe.apex.quant.bot.config.WeClawProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * WeClaw 主动消息客户端。
 */
@Slf4j
@Component
public class WeClawMessageClient {

    @Resource
    private ApexBotProperties properties;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 发送微信文本消息。
     *
     * @param text 消息正文
     * @return true=发送成功
     */
    public boolean sendText(String text) {
        WeClawProperties channel = properties.getWeclaw();
        if (Objects.isNull(channel) || !channel.isEnabled()
                || StringUtils.isBlank(channel.getBaseUrl())
                || StringUtils.isBlank(channel.getRecipient())
                || StringUtils.isBlank(text)) {
            return false;
        }

        String url = channel.getBaseUrl().trim().replaceAll("/+$", "") + "/api/send";
        WeClawSendReq request = WeClawSendReq.builder()
                .to(channel.getRecipient().trim())
                .text(text.trim())
                .build();
        try {
            HttpRequest httpRequest = HttpRequest.post(url)
                    .header("Content-Type", "application/json")
                    .timeout(Math.max(1000, channel.getTimeoutMs()))
                    .body(objectMapper.writeValueAsString(request));
            if (StringUtils.isNotBlank(channel.getApiToken())) {
                httpRequest.header("Authorization", "Bearer " + channel.getApiToken().trim());
            }
            try (HttpResponse response = httpRequest.execute()) {
                if (!response.isOk()) {
                    log.warn("WeClaw 消息发送失败，状态={}，响应内容={}", response.getStatus(), clip(response.body()));
                    return false;
                }
                return true;
            }
        } catch (Exception ex) {
            log.warn("WeClaw 消息发送异常，接收方={}，原因={}", channel.getRecipient(), ex.getMessage());
            return false;
        }
    }

    private String clip(String text) {
        if (StringUtils.isBlank(text)) {
            return "";
        }
        String content = text.trim();
        return content.length() <= 300 ? content : content.substring(0, 300);
    }
}
