package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 当前用户最近一次实时决策尝试的执行状态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionRunStatusResp {

    /** 决策运行号 */
    private String runNo;

    /** 决策日期 */
    private LocalDate actionDate;

    /** 执行状态：RUNNING、SUCCESS、FAILED */
    private String status;

    /** 本次运行是否已发布正式决策 */
    private Boolean published;

    /** 市场数据质量等级 */
    private String dataLevel;

    /** 执行说明或未发布、失败原因 */
    private String message;

    /** 本次运行开始时间 */
    private LocalDateTime startedAt;

    /** 本次运行完成时间 */
    private LocalDateTime finishedAt;
}
