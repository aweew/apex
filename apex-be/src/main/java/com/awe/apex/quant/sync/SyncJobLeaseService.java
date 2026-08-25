package com.awe.apex.quant.sync;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * 同步任务跨实例运行租约。
 */
@Slf4j
@Service
public class SyncJobLeaseService {

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end", Long.class);
    private static final DefaultRedisScript<Long> RENEW_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end", Long.class);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 尝试获取任务运行租约。
     *
     * @param leaseKey 租约键
     * @param owner    当前任务唯一标识
     * @param ttl      最长占用时间
     * @return true=获取成功，false=已有其他实例持有
     */
    public boolean tryAcquire(String leaseKey, String owner, Duration ttl) {
        if (StringUtils.isBlank(leaseKey) || StringUtils.isBlank(owner)
                || Objects.isNull(ttl) || ttl.isZero() || ttl.isNegative()) {
            throw new BusinessException("同步任务租约参数不完整");
        }
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(leaseKey, owner, ttl));
        } catch (Exception ex) {
            log.warn("获取同步任务租约失败，租约键={}，原因={}", leaseKey, ex.getMessage());
            throw new BusinessException("同步任务去重锁不可用，请检查 Redis", ex);
        }
    }

    /**
     * 仅释放当前任务持有的运行租约。
     *
     * @param leaseKey 租约键
     * @param owner    当前任务唯一标识
     */
    public void release(String leaseKey, String owner) {
        if (StringUtils.isBlank(leaseKey) || StringUtils.isBlank(owner)) {
            return;
        }
        try {
            stringRedisTemplate.execute(RELEASE_SCRIPT, List.of(leaseKey), owner);
        } catch (Exception ex) {
            log.warn("释放同步任务租约失败，租约键={}，原因={}", leaseKey, ex.getMessage());
        }
    }

    /**
     * 延长当前任务持有的运行租约。
     *
     * @param leaseKey 租约键
     * @param owner 当前任务唯一标识
     * @param ttl 续期后的最长占用时间
     * @return true=续期成功，false=租约已不属于当前任务
     */
    public boolean renew(String leaseKey, String owner, Duration ttl) {
        if (StringUtils.isBlank(leaseKey) || StringUtils.isBlank(owner)
                || Objects.isNull(ttl) || ttl.isZero() || ttl.isNegative()) {
            throw new BusinessException("同步任务租约续期参数不完整");
        }
        try {
            Long renewed = stringRedisTemplate.execute(
                    RENEW_SCRIPT, List.of(leaseKey), owner, String.valueOf(ttl.toMillis()));
            return Long.valueOf(1L).equals(renewed);
        } catch (Exception ex) {
            log.warn("同步任务租约续期失败，租约键={}，原因={}", leaseKey, ex.getMessage());
            throw new BusinessException("同步任务租约续期失败，请检查 Redis", ex);
        }
    }
}
