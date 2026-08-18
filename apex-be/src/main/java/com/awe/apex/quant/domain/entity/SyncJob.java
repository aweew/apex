package com.awe.apex.quant.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 数据同步任务运行记录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sync_job")
public class SyncJob implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 任务类型编码
     */
    private String taskType;

    /**
     * 任务名称
     */
    private String taskName;

    /**
     * 状态 PENDING/RUNNING/SUCCESS/PARTIAL/FAILED/CANCELLED
     */
    private String status;

    /**
     * 启动参数 JSON
     */
    private String paramsJson;

    /**
     * 进度 0-100
     */
    private Integer progressPct;

    /**
     * 已完成条目
     */
    private Integer doneItems;

    /**
     * 总条目
     */
    private Integer totalItems;

    /**
     * 状态说明
     */
    private String message;

    /**
     * 日志尾部
     */
    private String logTail;

    /**
     * 进程退出码
     */
    private Integer exitCode;

    /**
     * 操作系统进程号
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

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 逻辑删除
     */
    @TableLogic
    private Integer deleted;
}
