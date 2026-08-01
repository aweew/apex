package com.awe.apex.common.constant;

import cn.hutool.core.util.ClassUtil;
import cn.hutool.core.util.ReflectUtil;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 枚举收集器
 *
 * @author Awe
 * @since 2025/12/9 15:17
 */
@Component
public class EnumCollector implements InitializingBean {

    private final Map<String, List<Map<String, Object>>> enumMap = new HashMap<>();

    @Override
    public void afterPropertiesSet() {
        scanEnums("com.awe.apex.common.constant.enums");
    }

    private void scanEnums(String basePackage) {
        Set<Class<?>> classes = ClassUtil.scanPackage(basePackage, clazz ->
                clazz.isEnum() && clazz.isAnnotationPresent(EnumDict.class));

        for (Class<?> enumClass : classes) {
            List<Map<String, Object>> list = new ArrayList<>();

            Object[] constants = enumClass.getEnumConstants();
            for (Object constant : constants) {
                Map<String, Object> item = new HashMap<>();
                item.put("value", ReflectUtil.getFieldValue(constant, "value"));
                item.put("label", ReflectUtil.getFieldValue(constant, "label"));
                list.add(item);
            }

            enumMap.put(enumClass.getSimpleName(), list);
        }
    }

    public Map<String, List<Map<String, Object>>> getAllEnums() {
        return enumMap;
    }
}
