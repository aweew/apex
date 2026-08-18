package com.awe.apex.quant.domain.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单条选股规则评估结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenerRuleEvaluationBO {

    /** 是否通过 */
    private Boolean passed;

    /** 是否因数据缺失未通过 */
    private Boolean missing;

    /** 实际值 */
    private String actualValue;

    /** 目标条件 */
    private String expectedValue;

    /** 数据缺失说明 */
    private String missingMessage;
}
