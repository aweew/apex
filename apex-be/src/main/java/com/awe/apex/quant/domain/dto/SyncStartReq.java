package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 启动同步任务请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncStartReq {

    /**
     * 任务类型
     */
    private String taskType;

    /**
     * 额外参数（覆盖默认），如 start/limit/sources/types/codes/sleep
     */
    private String start;

    /**
     * 条数/股票数限制
     */
    private Integer limit;

    /**
     * 来源列表
     */
    private String sources;

    /**
     * 板块类型
     */
    private String types;

    /**
     * 代码列表
     */
    private String codes;

    /**
     * 间隔秒
     */
    private Double sleep;

    /**
     * 模式
     */
    private String mode;

    /**
     * 批次
     */
    private Integer batch;

    /**
     * 轮数
     */
    private Integer rounds;

    /**
     * 智能决策是否纳入北交所
     */
    private Boolean includeBj;
}
