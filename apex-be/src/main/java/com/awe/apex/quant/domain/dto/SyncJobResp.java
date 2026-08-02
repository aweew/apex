package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 同步任务状态
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncJobResp {

    /**
     * 任务 ID
     */
    private Long id;

    /**
     * 任务类型
     */
    private String taskType;

    /**
     * 任务名称
     */
    private String taskName;

    /**
     * 状态
     */
    private String status;

    /**
     * 参数
     */
    private String paramsJson;

    /**
     * 进度百分比
     */
    private Integer progressPct;

    /**
     * 已完成
     */
    private Integer doneItems;

    /**
     * 总数
     */
    private Integer totalItems;

    /**
     * 说明
     */
    private String message;

    /**
     * 日志尾部
     */
    private String logTail;

    /**
     * 退出码
     */
    private Integer exitCode;

    /**
     * 进程号
     */
    private Long pid;

    /**
     * 开始时间
     */
    private LocalDateTime startedAt;

    /**
     * 结束时间
     */
    private LocalDateTime finishedAt;
}
