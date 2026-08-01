package com.awe.apex.common.convert;

import org.springframework.core.convert.converter.Converter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;

/**
 * 本地日期转换器
 *
 * @author Awe
 * @date 2023/4/7 16:03
 */
public class LocalDateConverter implements Converter<String, LocalDate> {

    public static final String MONTH = "yyyy-MM";
    public static final String DATE = "yyyy-MM-dd";
    public static final String TIME = "yyyy-MM-dd HH:mm:ss";

    @Override
    public LocalDate convert(String s) {
        return LocalDate.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * 当月第一天
     */
    public static LocalDate firstDayOfMonth(LocalDate date) {
        if (null == date) {
            return null;
        }
        return date.with(TemporalAdjusters.firstDayOfMonth());
    }

    /**
     * LocalDate 转为 String
     */
    public static String dateToString(LocalDate date, String pattern) {
        if (null == date) {
            return "";
        }
        return date.format(DateTimeFormatter.ofPattern(pattern));
    }

}
