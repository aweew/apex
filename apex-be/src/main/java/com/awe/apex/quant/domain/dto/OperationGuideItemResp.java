package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 操作指引条目。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationGuideItemResp {

    /**
     * 操作优先级。
     */
    private Integer priority;

    /**
     * 稳定操作代码。
     */
    private String code;

    /**
     * 操作项状态。
     */
    private String status;

    /**
     * 操作项标题。
     */
    private String title;

    /**
     * 应执行的动作。
     */
    private String actionText;

    /**
     * 执行条件或阻断原因。
     */
    private String conditionText;

    /**
     * 关联目标数量。
     */
    private Integer targetCount;

    /**
     * 操作目标类型。
     */
    private String targetType;
}
