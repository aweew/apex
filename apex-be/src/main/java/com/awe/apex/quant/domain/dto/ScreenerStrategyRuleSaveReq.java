package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 选股策略规则保存请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenerStrategyRuleSaveReq {

    /** 规则类型 */
    private String ruleType;

    /** 操作符 */
    private String operatorCode;

    /** 最小值或单值 */
    private BigDecimal minValue;

    /** 最大值 */
    private BigDecimal maxValue;

    /** 整数参数 */
    private Integer intValue;

    /** 文本参数 */
    private String textValue;

    /** 布尔参数 */
    private Boolean boolValue;

    /** 回看交易日数 */
    private Integer lookbackDays;

    /** 容错阈值 */
    private BigDecimal toleranceValue;

    /** 排序号 */
    private Integer sortNo;
}
