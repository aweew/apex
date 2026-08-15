package com.awe.apex.quant.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class RedisTemplateConfigTest {

    @Test
    void shouldCreateStringRedisTemplate() {
        RedisConnectionFactory redisConnectionFactory = mock(RedisConnectionFactory.class);

        StringRedisTemplate redisTemplate = new RedisTemplateConfig().stringRedisTemplate(redisConnectionFactory);

        assertNotNull(redisTemplate);
    }
}
