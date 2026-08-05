package com.awe.apex.quant.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI 配置装配
 */
@Configuration
@EnableConfigurationProperties(AiChatProperties.class)
public class AiConfig {
}
