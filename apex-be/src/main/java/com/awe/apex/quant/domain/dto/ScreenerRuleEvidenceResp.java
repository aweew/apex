package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 选股规则命中依据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenerRuleEvidenceResp {

    /** 规则类型 */
    private String ruleType;

    /** 规则名称 */
    private String ruleName;

    /** 实际值 */
    private String actualValue;

    /** 目标条件 */
    private String expectedValue;

    /** 数据来源阶段 */
    private String stage;
}
