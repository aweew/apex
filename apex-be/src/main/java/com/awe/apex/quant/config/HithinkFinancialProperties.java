package com.awe.apex.quant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 同花顺金融数据服务配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "apex.hithink")
public class HithinkFinancialProperties {

    /**
     * 是否启用同花顺金融数据服务。
     */
    private boolean enabled;

    /**
     * 服务地址。
     */
    private String baseUrl = "https://fuyao.aicubes.cn";

    /**
     * API Key，仅从环境变量或本地配置读取。
     */
    private String apiKey = "";

    /**
     * 单次 HTTP 请求超时时间，单位毫秒。
     */
    private int timeoutMs = 10000;

    /**
     * 批量行情请求的最大股票数。
     */
    private int batchSize = 50;
}
