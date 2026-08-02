package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 数据同步总览
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncOverviewResp {

    /**
     * 任务定义列表
     */
    private List<SyncTaskDefResp> tasks;

    /**
     * 运行中任务数
     */
    private Integer runningCount;

    /**
     * 最近任务
     */
    private List<SyncJobResp> recentJobs;

    /**
     * 说明
     */
    private String message;
}
