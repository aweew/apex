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

import java.util.List;
import java.util.Objects;

/**
 * Kimi / Moonshot OpenAI 兼容 Chat Completions
 */
@Slf4j
@Component
public class KimiChatClient {

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

        try (HttpResponse response = HttpRequest.post(url)
                .header("Authorization", "Bearer " + properties.getApiKey().trim())
                .header("Content-Type", "application/json")
                .timeout(Math.max(5000, properties.getTimeoutMs()))
                .body(body.toString())
                .execute()) {
            String raw = response.body();
            if (!response.isOk()) {
                log.warn("Kimi 调用失败 status={} body={}", response.getStatus(), trim(raw));
                return null;
            }
            JSONObject root = JSONUtil.parseObj(raw);
            JSONArray choices = root.getJSONArray("choices");
            if (Objects.isNull(choices) || choices.isEmpty()) {
                log.warn("Kimi 无 choices: {}", trim(raw));
                return null;
            }
            JSONObject message = choices.getJSONObject(0).getJSONObject("message");
            if (Objects.isNull(message)) {
                return null;
            }
            String content = message.getStr("content");
            return StringUtils.isBlank(content) ? null : content.trim();
        } catch (Exception ex) {
            log.warn("Kimi 调用异常: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * 多轮（预留）
     *
     * @param messages role/content 对
     * @param maxTokens 最大输出
     * @return 文本或 null
     */
    public String chatMessages(List<String[]> messages, int maxTokens) {
        if (!available() || Objects.isNull(messages) || messages.isEmpty()) {
            return null;
        }
        StringBuilder user = new StringBuilder();
        String system = null;
        for (String[] pair : messages) {
            if (pair == null || pair.length < 2) {
                continue;
            }
            if ("system".equalsIgnoreCase(pair[0])) {
                system = pair[1];
            } else {
                user.append(pair[1]).append('\n');
            }
        }
        return chat(system, user.toString(), maxTokens);
    }

    private String trim(String raw) {
        if (StringUtils.isBlank(raw)) {
            return "";
        }
        String t = raw.trim();
        return t.length() > 400 ? t.substring(0, 400) + "…" : t;
    }
}
