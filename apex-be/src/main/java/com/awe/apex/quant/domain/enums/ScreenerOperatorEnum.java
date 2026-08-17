package com.awe.apex.quant.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 选股规则操作符
 */
@Getter
@AllArgsConstructor
public enum ScreenerOperatorEnum {

    EQ("EQ", "等于"),
    GT("GT", "大于"),
    GTE("GTE", "大于等于"),
    LT("LT", "小于"),
    LTE("LTE", "小于等于"),
    BETWEEN("BETWEEN", "区间");

    private final String code;
    private final String desc;
}
