package com.awe.apex.quant.signal.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 信号定义响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalDefinitionResp {

    /** 信号编码 */
    private String signalCode;

    /** 信号名称 */
    private String signalName;

    /** 信号分类 */
    private String category;

    /** 信号方向 */
    private String direction;

    /** 业务解释 */
    private String description;

    /** 当前规则版本 */
    private String ruleVersion;

    /** 特征版本 */
    private String featureVersion;
}
