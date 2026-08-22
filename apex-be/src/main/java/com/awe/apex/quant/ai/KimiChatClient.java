package com.awe.apex.quant.ai;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.awe.apex.common.util.StringUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Kimi / Moonshot OpenAI 兼容 Chat Completions
 */
@Slf4j
@Component
public class KimiChatClient {

    private static final int RATE_LIMIT_RETRY_COUNT = 2;
    private static final long[] RATE_LIMIT_BACKOFF_MS = {250L, 750L};
    private final Semaphore requestSemaphore = new Semaphore(1, true);

    @Resource
    private AiChatProperties properties;

    /**
     * 是否可用
     *
     * @return true=已配置 Key
     */
    public boolean available() {
        return properties.isEnabled() && StringUtils.isNotBlank(properties.getApiKey());
    }

    /**
     * 单轮对话，返回助手文本；失败返回 null
     *
     * @param systemPrompt 系统提示
     * @param userPrompt   用户提示
     * @param maxTokens    最大输出
     * @return 文本或 null
     */
    public String chat(String systemPrompt, String userPrompt, int maxTokens) {
        if (!available()) {
            return null;
        }
        String base = StringUtils.isBlank(properties.getBaseUrl())
                ? "https://api.moonshot.cn/v1"
                : properties.getBaseUrl().trim().replaceAll("/+$", "");
        String url = base + "/chat/completions";

        JSONArray messages = new JSONArray();
        if (StringUtils.isNotBlank(systemPrompt)) {
            messages.add(new JSONObject().set("role", "system").set("content", systemPrompt));
        }
        messages.add(new JSONObject().set("role", "user").set("content", userPrompt));

        JSONObject body = new JSONObject();
        body.set("model", properties.getModel());
        body.set("messages", messages);
        body.set("max_tokens", Math.max(256, maxTokens));
        // k2.6：关闭 thinking，加快消息面摘要
        body.set("thinking", new JSONObject().set("type", "disabled"));

        return execute(url, body.toString(), false);
    }

    /**
     * 发送单张图片与文本提示，返回助手文本；失败返回 null
     *
     * @param systemPrompt 系统提示
     * @param userPrompt   用户提示
     * @param contentType  图片 MIME 类型
     * @param imageBytes   图片内容
     * @param maxTokens    最大输出
     * @return 文本或 null
     */
    public String chatImage(String systemPrompt, String userPrompt, String contentType,
                            byte[] imageBytes, int maxTokens) {
        if (!available() || StringUtils.isBlank(contentType)
                || Objects.isNull(imageBytes) || imageBytes.length == 0) {
            return null;
        }
        String base = StringUtils.isBlank(properties.getBaseUrl())
                ? "https://api.moonshot.cn/v1"
                : properties.getBaseUrl().trim().replaceAll("/+$", "");
        String url = base + "/chat/completions";

        JSONArray messages = new JSONArray();
        if (StringUtils.isNotBlank(systemPrompt)) {
            messages.add(new JSONObject().set("role", "system").set("content", systemPrompt));
        }
        JSONArray content = new JSONArray();
        content.add(new JSONObject().set("type", "text").set("text", userPrompt));
        String imageUrl = "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(imageBytes);
        content.add(new JSONObject().set("type", "image_url")
                .set("image_url", new JSONObject().set("url", imageUrl)));
        messages.add(new JSONObject().set("role", "user").set("content", content));

        JSONObject body = new JSONObject();
        body.set("model", properties.getModel());
        body.set("messages", messages);
        body.set("max_tokens", Math.max(256, maxTokens));
        body.set("thinking", new JSONObject().set("type", "disabled"));
        return execute(url, body.toString(), true);
    }

    private String execute(String url, String requestBody, boolean sensitive) {
        boolean acquired = false;
        try {
            acquired = requestSemaphore.tryAcquire(Math.max(1000, properties.getTimeoutMs()), TimeUnit.MILLISECONDS);
            if (!acquired) {
                log.warn("Kimi 调用排队超时");
                return null;
            }
            for (int attempt = 0; attempt <= RATE_LIMIT_RETRY_COUNT; attempt++) {
                try (HttpResponse response = HttpRequest.post(url)
                        .header("Authorization", "Bearer " + properties.getApiKey().trim())
                        .header("Content-Type", "application/json")
                        .timeout(Math.max(5000, properties.getTimeoutMs()))
                        .body(requestBody)
                        .execute()) {
                    String raw = response.body();
                    if (response.getStatus() == 429 && attempt < RATE_LIMIT_RETRY_COUNT) {
                        Thread.sleep(RATE_LIMIT_BACKOFF_MS[attempt]);
                        continue;
                    }
                    if (!response.isOk()) {
                        if (sensitive) {
                            log.warn("Kimi 图片识别调用失败，状态={}", response.getStatus());
                        } else {
                            log.warn("Kimi 调用失败，状态={}，响应内容={}", response.getStatus(), trim(raw));
                        }
                        return null;
                    }
                    JSONObject root = JSONUtil.parseObj(raw);
                    JSONArray choices = root.getJSONArray("choices");
                    if (Objects.isNull(choices) || choices.isEmpty()) {
                        if (sensitive) {
                            log.warn("Kimi 图片识别无 choices");
                        } else {
                            log.warn("Kimi 无 choices: {}", trim(raw));
                        }
                        return null;
                    }
                    JSONObject message = choices.getJSONObject(0).getJSONObject("message");
                    if (Objects.isNull(message)) {
                        return null;
                    }
                    String content = message.getStr("content");
                    return StringUtils.isBlank(content) ? null : content.trim();
                }
            }
            return null;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Kimi 调用被中断");
            return null;
        } catch (Exception ex) {
            if (sensitive) {
                log.warn("Kimi 图片识别调用异常，类型={}", ex.getClass().getSimpleName());
            } else {
                log.warn("Kimi 调用异常: {}", ex.getMessage());
            }
            return null;
        } finally {
            if (acquired) {
                requestSemaphore.release();
            }
        }
    }

    /**
     * 多轮（预留）
     *
     * @param messages 带真实角色的历史消息
     * @param maxTokens 最大输出
     * @return 文本或 null
     */
    public String chatMessages(List<KimiChatMessage> messages, int maxTokens) {
        if (!available() || Objects.isNull(messages) || messages.isEmpty()) {
            return null;
        }
        String base = StringUtils.isBlank(properties.getBaseUrl())
                ? "https://api.moonshot.cn/v1"
                : properties.getBaseUrl().trim().replaceAll("/+$", "");
        JSONArray requestMessages = new JSONArray();
        for (KimiChatMessage message : messages) {
            if (Objects.isNull(message) || StringUtils.isBlank(message.getRole())
                    || StringUtils.isBlank(message.getContent())) {
                continue;
            }
            String role = message.getRole().trim().toLowerCase(Locale.ROOT);
            if (!"system".equals(role) && !"user".equals(role) && !"assistant".equals(role)) {
                continue;
            }
            requestMessages.add(new JSONObject()
                    .set("role", role)
                    .set("content", message.getContent().trim()));
        }
        if (requestMessages.isEmpty()) {
            return null;
        }
        JSONObject body = new JSONObject();
        body.set("model", properties.getModel());
        body.set("messages", requestMessages);
        body.set("max_tokens", Math.max(256, maxTokens));
        body.set("thinking", new JSONObject().set("type", "disabled"));
        return execute(base + "/chat/completions", body.toString(), false);
    }

    private String trim(String raw) {
        if (StringUtils.isBlank(raw)) {
            return "";
        }
        String t = raw.trim();
        return t.length() > 400 ? t.substring(0, 400) + "…" : t;
    }
}
