package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    /**
     * 存活探针
     *
     * @return ok
     */
    @GetMapping
    public Result<Map<String, Object>> health() {
        Map<String, Object> body = new HashMap<>();
        body.put("status", "UP");
        body.put("time", LocalDateTime.now().toString());
        body.put("app", "apex");
        return Result.success(body);
    }
}
