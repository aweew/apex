package com.awe.apex.quant.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户使用事件类型。
 */
@Getter
@AllArgsConstructor
public enum UserActivityTypeEnum {

    LOGIN("LOGIN", "登录"),
    PAGE_VIEW("PAGE_VIEW", "页面访问");

    private final String code;
    private final String desc;
}
