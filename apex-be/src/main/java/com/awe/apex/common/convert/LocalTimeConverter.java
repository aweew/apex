package com.awe.apex.common.convert;

import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.Objects;

/**
 * 本地时间转换器
 *
 * @author Awe
 * @date 2023/4/7 16:03
 */
@Component
@ConfigurationPropertiesBinding
public class LocalTimeConverter implements Converter<String, LocalTime> {

    @Override
    public LocalTime convert(String source) {
        Objects.requireNonNull(source);
        return LocalTime.parse(source);
    }

}
