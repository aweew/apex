package com.awe.apex.quant.cache;

import com.awe.apex.common.util.SpringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisCacheServiceTest {

    @BeforeEach
    void setUpJsonUtils() {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBean(ObjectMapper.class)).thenReturn(new ObjectMapper());
        new SpringUtils().setApplicationContext(applicationContext);
    }

    @Test
    void getShouldDeserializeCachedValue() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("apex:test")).thenReturn("{\"name\":\"Apex\"}");
        RedisCacheService cacheService = cacheService(redisTemplate);

        CachePayload payload = cacheService.get("apex:test", CachePayload.class);

        assertEquals("Apex", payload.getName());
    }

    @Test
    void putShouldWriteJsonWithTtl() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        RedisCacheService cacheService = cacheService(redisTemplate);

        cacheService.put("apex:test", new CachePayload("Apex"), Duration.ofMinutes(10));

        verify(valueOperations).set(eq("apex:test"), eq("{\"name\":\"Apex\"}"), eq(Duration.ofMinutes(10)));
    }

    @Test
    void redisFailureShouldFallbackWithoutThrowing() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.opsForValue()).thenThrow(new IllegalStateException("redis unavailable"));
        RedisCacheService cacheService = cacheService(redisTemplate);

        assertNull(cacheService.get("apex:test", CachePayload.class));
        cacheService.evict("apex:test");

        verify(redisTemplate).delete("apex:test");
    }

    private RedisCacheService cacheService(StringRedisTemplate redisTemplate) {
        RedisCacheService cacheService = new RedisCacheService();
        ReflectionTestUtils.setField(cacheService, "stringRedisTemplate", redisTemplate);
        return cacheService;
    }

    public static class CachePayload {

        public String name;

        public CachePayload() {
        }

        public CachePayload(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
