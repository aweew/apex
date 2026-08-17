package com.awe.apex.quant.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 选股策略来源
 */
@Getter
@AllArgsConstructor
public enum ScreenerStrategySourceEnum {

    USER("USER", "用户创建"),
    TEMPLATE_COPY("TEMPLATE_COPY", "系统模板副本");

    private final String code;
    private final String desc;
}
