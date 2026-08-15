package com.awe.apex.quant.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 模板配置
 */
@Configuration
public class RedisTemplateConfig {

    /**
     * 提供缓存 JSON 读写所需的字符串 Redis 模板
     *
     * @param redisConnectionFactory Redis 连接工厂
     * @return 字符串 Redis 模板
     */
    @Bean
    @ConditionalOnMissingBean(StringRedisTemplate.class)
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        return new StringRedisTemplate(redisConnectionFactory);
    }
}
