package com.awe.apex.quant.sync;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 同步任务规格（内部）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncTaskSpec {

    /**
     * 类型编码
     */
    private String taskType;

    /**
     * 名称
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
     * 脚本文件名
     */
    private String scriptFile;

    /**
     * 默认参数提示
     */
    private String defaultParamsHint;

    /**
     * 超时秒
     */
    private long timeoutSec;

    /**
     * 进度文件相对脚本目录（可空）
     */
    private String progressFile;
}
