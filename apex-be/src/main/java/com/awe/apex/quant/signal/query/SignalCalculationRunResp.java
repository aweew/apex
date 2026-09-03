package com.awe.apex.quant.signal.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 信号计算批次响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalCalculationRunResp {

    /** 批次号 */
    private String runNo;

    /** 批次状态 */
    private String status;

    /** 计划证券数 */
    private Integer totalCount;

    /** 成功证券数 */
    private Integer successCount;

    /** 失败证券数 */
    private Integer failureCount;

    /** 数据截止时间 */
    private LocalDateTime asOfTime;

    /** 错误摘要 */
    private String errorMessage;
}
