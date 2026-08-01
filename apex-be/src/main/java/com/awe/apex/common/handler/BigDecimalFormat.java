package com.awe.apex.common.handler;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.RoundingMode;

/**
 * BigDecimal格式化
 *
 * @author Awe
 * @date 2023/7/24 09:22
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@JacksonAnnotationsInside
@JsonSerialize(using = BigDecimalSerializer.class)
public @interface BigDecimalFormat {

    /**
     * bigDecimal 默认保留2位小数
     *
     * @return 保留位数
     */
    int scale() default 2;

    /**
     * 取整规则，默认4舍5入
     *
     * @return 取整规则
     */
    RoundingMode roundingMode() default RoundingMode.HALF_UP;

}
