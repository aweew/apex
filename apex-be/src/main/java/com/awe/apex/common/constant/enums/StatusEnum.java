package com.awe.apex.common.constant.enums;

import com.awe.apex.common.constant.EnumDict;
import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 通用状态枚举
 *
 * @author Awe
 * @since 2025/12/9 15:08
 */
@Getter
@AllArgsConstructor
@EnumDict
public enum StatusEnum {

    ENABLE(1, "启用"),
    DISABLE(2, "禁用"),
    ;

    @EnumValue
    private final int value;
    private final String label;

}
