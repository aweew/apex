package com.awe.apex.quant.sync;

import com.awe.apex.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SyncJobLeaseServiceTest {

    @Test
    void acquiresLeaseWithOwnerAndTimeout() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("apex:sync:lease:DECISION:7", "owner-1", Duration.ofMinutes(35)))
                .thenReturn(true);
        SyncJobLeaseService leaseService = leaseService(redisTemplate);

        assertTrue(leaseService.tryAcquire(
                "apex:sync:lease:DECISION:7", "owner-1", Duration.ofMinutes(35)));
    }

    @Test
    void reportsLeaseHeldByAnotherInstance() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(false);
        SyncJobLeaseService leaseService = leaseService(redisTemplate);

        assertFalse(leaseService.tryAcquire("apex:sync:lease:CLOSE_BUNDLE", "owner-2", Duration.ofHours(1)));
    }

    @Test
    void redisFailureDoesNotAllowUnprotectedDuplicateTask() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.opsForValue()).thenThrow(new IllegalStateException("redis unavailable"));
        SyncJobLeaseService leaseService = leaseService(redisTemplate);

        assertThrows(BusinessException.class, () -> leaseService.tryAcquire(
                "apex:sync:lease:CLOSE_BUNDLE", "owner-3", Duration.ofHours(1)));
    }

    @Test
    void releasesOnlyTheLeaseOwnedByCurrentTask() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(), eq(java.util.List.of("apex:sync:lease:CLOSE_BUNDLE")), eq("owner-4")))
                .thenReturn(1L);
        SyncJobLeaseService leaseService = leaseService(redisTemplate);

        leaseService.release("apex:sync:lease:CLOSE_BUNDLE", "owner-4");

        verify(redisTemplate).execute(any(), eq(java.util.List.of("apex:sync:lease:CLOSE_BUNDLE")), eq("owner-4"));
    }

    private SyncJobLeaseService leaseService(StringRedisTemplate redisTemplate) {
        SyncJobLeaseService leaseService = new SyncJobLeaseService();
        ReflectionTestUtils.setField(leaseService, "stringRedisTemplate", redisTemplate);
        return leaseService;
    }
}
