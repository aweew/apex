package com.awe.apex.quant.signal.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 生效信号定义与规则标识。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalDefinitionRuleBO {

    /** 信号定义ID */
    private Long definitionId;

    /** 规则ID */
    private Long ruleId;

    /** 信号编码 */
    private String signalCode;

    /** 规则版本 */
    private String ruleVersion;
}
