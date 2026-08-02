package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 可启动的同步任务定义
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncTaskDefResp {

    /**
     * 任务类型
     */
    private String taskType;

    /**
     * 显示名称
     */
    private String name;

    /**
     * 分组
     */
    private String groupName;

    /**
     * 说明
     */
    private String description;

    /**
     * 默认参数提示
     */
    private String defaultParamsHint;

    /**
     * 是否有运行中任务
     */
    private Boolean running;

    /**
     * 最近任务
     */
    private SyncJobResp latestJob;

    /**
     * 最近成功时间
     */
    private LocalDateTime lastSuccessAt;

    /**
     * 健康色 GREEN/YELLOW/RED
     */
    private String healthLevel;
}
