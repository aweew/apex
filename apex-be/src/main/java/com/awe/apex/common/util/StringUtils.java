package com.awe.apex.common.util;

import cn.hutool.core.util.StrUtil;

import java.util.List;

/**
 * 字符串工具类
 *
 * @author Awe
 * @since 2025/9/9 14:34
 */
public class StringUtils extends StrUtil {

    public static final String SEPARATOR = ",";

    /**
     * 判断 url 是否匹配排除列表
     * 支持:
     * 1. 完整匹配
     * 2. 末尾 * 前缀匹配，如 /api/user/*
     * 3. 正则匹配
     */
    public static boolean matches(String url, List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return false;
        }

        for (String pattern : patterns) {
            if (pattern == null || pattern.isEmpty()) {
                continue;
            }

            // 1. 完整匹配
            if (pattern.equals(url)) {
                return true;
            }

            // 2. 前缀匹配 /api/user/*
            if (pattern.endsWith("/*")) {
                String prefix = pattern.substring(0, pattern.length() - 2);
                if (url.startsWith(prefix)) {
                    return true;
                }
            }

            // 3. 正则匹配
            if (url.matches(pattern)) {
                return true;
            }
        }
        return false;
    }

}
