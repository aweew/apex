package com.awe.apex.quant.cache;

import com.awe.apex.common.util.JsonUtils;
import com.awe.apex.common.util.StringUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;

/**
 * Redis JSON 缓存访问，Redis 不可用时自动降级到调用方原始链路
 */
@Slf4j
@Service
public class RedisCacheService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 读取缓存对象
     *
     * @param cacheKey 缓存键
     * @param valueType 缓存对象类型
     * @param <T> 缓存对象类型
     * @return 缓存对象，未命中或 Redis 异常时返回 null
     */
    public <T> T get(String cacheKey, Class<T> valueType) {
        try {
            String payload = stringRedisTemplate.opsForValue().get(cacheKey);
            if (StringUtils.isBlank(payload)) {
                return null;
            }
            return JsonUtils.parseObject(payload, valueType);
        } catch (Exception ex) {
            log.debug("Redis 缓存读取失败 key={}: {}", cacheKey, ex.getMessage());
            return null;
        }
    }

    /**
     * 写入带过期时间的缓存对象
     *
     * @param cacheKey 缓存键
     * @param value 缓存对象
     * @param ttl 缓存时长
     */
    public void put(String cacheKey, Object value, Duration ttl) {
        if (Objects.isNull(value) || Objects.isNull(ttl) || ttl.isNegative() || ttl.isZero()) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(cacheKey, JsonUtils.toJsonString(value), ttl);
        } catch (Exception ex) {
            log.debug("Redis 缓存写入失败 key={}: {}", cacheKey, ex.getMessage());
        }
    }

    /**
     * 删除缓存对象
     *
     * @param cacheKey 缓存键
     */
    public void evict(String cacheKey) {
        try {
            stringRedisTemplate.delete(cacheKey);
        } catch (Exception ex) {
            log.debug("Redis 缓存删除失败 key={}: {}", cacheKey, ex.getMessage());
        }
    }
}
