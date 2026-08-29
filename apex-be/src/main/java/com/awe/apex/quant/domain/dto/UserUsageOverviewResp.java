package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理员用户使用情况总览。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserUsageOverviewResp {

    /** 统计周期天数 */
    private Integer periodDays;

    /** 生成时间 */
    private LocalDateTime generatedAt;

    /** 用户总数 */
    private Long totalUsers;

    /** 启用用户数 */
    private Long enabledUsers;

    /** 活跃用户数 */
    private Long activeUsers;

    /** 新增用户数 */
    private Long newUsers;

    /** 访问次数 */
    private Long totalVisits;

    /** 活跃率百分比 */
    private Double activeRate;

    /** 每日趋势 */
    private List<UserUsageTrendResp> trend;

    /** 功能模块排行 */
    private List<UserUsageModuleResp> modules;

    /** 用户使用明细 */
    private List<UserUsageDetailResp> users;
}
