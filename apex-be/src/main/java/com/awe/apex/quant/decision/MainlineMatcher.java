package com.awe.apex.quant.decision;

import com.awe.apex.common.util.StringUtils;

import java.util.List;

/**
 * 个股行业与主线题材匹配（可单测）
 */
public final class MainlineMatcher {

    private MainlineMatcher() {
    }

    /**
     * 匹配结果
     */
    public static final class Hit {
        public final boolean match;
        public final String name;

        public Hit(boolean match, String name) {
            this.match = match;
            this.name = name;
        }
    }

    /**
     * 行业是否命中主线列表
     *
     * @param industry      行业
     * @param mainlineNames 主线名称
     * @return 命中
     */
    public static Hit match(String industry, List<String> mainlineNames) {
        if (StringUtils.isBlank(industry) || mainlineNames == null || mainlineNames.isEmpty()) {
            return new Hit(false, null);
        }
        String industryTrim = industry.trim();
        for (String name : mainlineNames) {
            if (StringUtils.isBlank(name)) {
                continue;
            }
            String n = name.trim();
            if (industryTrim.equals(n)) {
                return new Hit(true, n);
            }
            // 行业包含主线名（主线至少 3 字，降低误伤）
            if (n.length() >= 3 && industryTrim.contains(n)) {
                return new Hit(true, n);
            }
            // 主线包含完整行业名（行业至少 4 字）
            if (industryTrim.length() >= 4 && n.contains(industryTrim)) {
                return new Hit(true, n);
            }
        }
        return new Hit(false, null);
    }
}
