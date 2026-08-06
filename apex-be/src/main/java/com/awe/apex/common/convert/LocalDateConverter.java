package com.awe.apex.common.convert;

import com.awe.apex.common.util.StringUtils;
import org.springframework.core.convert.converter.Converter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;

/**
 * 本地日期转换器：兼容 yyyy-MM-dd 与 yyyy-MM-dd HH:mm:ss
 *
 * @author Awe
 * @date 2023/4/7 16:03
 */
public class LocalDateConverter implements Converter<String, LocalDate> {

    public static final String MONTH = "yyyy-MM";
    public static final String DATE = "yyyy-MM-dd";
    public static final String TIME = "yyyy-MM-dd HH:mm:ss";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern(DATE);
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern(TIME);

    @Override
    public LocalDate convert(String s) {
        if (StringUtils.isBlank(s)) {
            return null;
        }
        String text = s.trim();
        try {
            return LocalDate.parse(text, DATE_FMT);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDateTime.parse(text, DATETIME_FMT).toLocalDate();
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        // ISO_LOCAL_DATE 兜底（如 2026-08-06）
        return LocalDate.parse(text);
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
