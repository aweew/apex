package com.awe.apex.quant.bot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Apex Bot 接入配置。
 */
@Data
@ConfigurationProperties(prefix = "apex.bot")
public class ApexBotProperties {

    /**
     * 是否开放 Bot API。
     */
    private boolean enabled = false;

    /**
     * HMAC 客户端标识。
     */
    private String clientKey = "";

    /**
     * HMAC 客户端密钥。
     */
    private String clientSecret = "";

    /**
     * 请求时间允许偏差秒数。
     */
    private int timestampToleranceSeconds = 300;

    /**
     * 相同通知冷却秒数。
     */
    private int notificationCooldownSeconds = 1800;

    /**
     * 自选异动阈值百分比。
     */
    private int moverThreshold = 5;

    /**
     * 默认自选分组。
     */
    private String watchlistGroup = "我的自选";

    /**
     * WeClaw 主动消息配置。
     */
    private WeClawProperties weclaw = new WeClawProperties();
}
