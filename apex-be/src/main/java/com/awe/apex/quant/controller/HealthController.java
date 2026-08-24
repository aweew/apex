package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.quant.domain.dto.ApexHealthResp;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

/**
 * 健康检查
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 存活探针
     *
     * @return ok
     */
    @GetMapping
    public Result<ApexHealthResp> health() {
        return Result.success(ApexHealthResp.builder()
                .app("apex")
                .status("UP")
                .checkedAt(LocalDateTime.now())
                .build());
    }

    /**
     * 就绪探针，验证应用对核心持久化依赖的可用性。
     *
     * @return 核心依赖状态
     */
    @GetMapping("/ready")
    public ResponseEntity<Result<ApexHealthResp>> readiness() {
        boolean databaseUp = true;
        boolean redisUp = true;
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        } catch (Exception exception) {
            databaseUp = false;
        }
        try {
            RedisCallback<String> pingCallback = connection -> connection.ping();
            String pong = stringRedisTemplate.execute(pingCallback);
            redisUp = "PONG".equalsIgnoreCase(pong);
        } catch (Exception exception) {
            redisUp = false;
        }
        ApexHealthResp body = ApexHealthResp.builder()
                .app("apex")
                .status(databaseUp && redisUp ? "UP" : "DOWN")
                .checkedAt(LocalDateTime.now())
                .databaseStatus(databaseUp ? "UP" : "DOWN")
                .redisStatus(redisUp ? "UP" : "DOWN")
                .build();
        if (databaseUp && redisUp) {
            return ResponseEntity.ok(Result.success(body));
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Result.failure(HttpStatus.SERVICE_UNAVAILABLE.value(), "核心依赖不可用", body));
    }
}
