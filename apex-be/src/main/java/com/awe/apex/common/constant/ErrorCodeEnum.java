package com.awe.apex.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 错误编码枚举
 *
 * @author Awe
 * @since 2025/9/9 11:21
 */
@Getter
@AllArgsConstructor
public enum ErrorCodeEnum {

    // 成功
    SUCCESS(0, "成功"),

    // 失败
    FAILURE(9000, "失败"),

    // 异常
    ERROR(9999, "异常"),
    ;

    /**
     * 错误编码
     */
    private final Integer code;

    /**
     * 错误信息
     */
    private final String msg;

    // 防止code码重复
    static {
        long distinctCount = Arrays.stream(values()).map(ErrorCodeEnum::getCode).distinct().count();
        if (distinctCount != (long) values().length) {
            throw new IllegalArgumentException("duplicate code in BizCodeEnum");
        }
    }

}
