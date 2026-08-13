package com.awe.apex.quant.bot.config;

import lombok.Data;

/**
 * WeClaw 主动消息配置。
 */
@Data
public class WeClawProperties {

    /**
     * 是否启用主动消息。
     */
    private boolean enabled = false;

    /**
     * WeClaw HTTP 服务地址。
     */
    private String baseUrl = "";

    /**
     * 微信收件人标识。
     */
    private String recipient = "";

    /**
     * 可选的 WeClaw API Bearer Token。
     */
    private String apiToken = "";

    /**
     * HTTP 超时毫秒数。
     */
    private int timeoutMs = 5000;
}
