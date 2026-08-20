package com.awe.apex.quant.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Apex AI 分析类型
 */
@Getter
@AllArgsConstructor
public enum ApexAiAnalysisTypeEnum {

    AUTO("AUTO", "自动识别"),
    PORTFOLIO("PORTFOLIO", "组合收益归因"),
    STRATEGY("STRATEGY", "策略失效诊断"),
    GENERAL("GENERAL", "通用投研问答");

    private final String code;

    private final String desc;

    /**
     * 按编码解析分析类型
     *
     * @param code 类型编码
     * @return 分析类型
     */
    public static ApexAiAnalysisTypeEnum of(String code) {
        for (ApexAiAnalysisTypeEnum type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return AUTO;
    }
}
