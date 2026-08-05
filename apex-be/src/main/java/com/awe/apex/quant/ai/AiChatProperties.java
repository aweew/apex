package com.awe.apex.quant.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenAI 兼容大模型配置（默认 Kimi / Moonshot）
 */
@Data
@ConfigurationProperties(prefix = "apex.ai")
public class AiChatProperties {

    /**
     * 是否启用
     */
    private boolean enabled = true;

    /**
     * API Base，如 https://api.moonshot.cn/v1
     */
    private String baseUrl = "https://api.moonshot.cn/v1";

    /**
     * API Key（仅放 application-local.yml / 环境变量）
     */
    private String apiKey = "";

    /**
     * 模型名
     */
    private String model = "kimi-k2.6";

    /**
     * 超时毫秒
     */
    private int timeoutMs = 45000;

    /**
     * 摘要缓存秒数
     */
    private int summaryCacheSeconds = 600;
}
