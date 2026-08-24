package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 应用健康状态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApexHealthResp {

    /**
     * 应用标识。
     */
    private String app;

    /**
     * 应用状态。
     */
    private String status;

    /**
     * 检查时间。
     */
    private LocalDateTime checkedAt;

    /**
     * MySQL 状态。
     */
    private String databaseStatus;

    /**
     * Redis 状态。
     */
    private String redisStatus;
}
