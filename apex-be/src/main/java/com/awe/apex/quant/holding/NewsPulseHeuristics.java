package com.awe.apex.quant.holding;

import com.awe.apex.common.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 消息面题材标签与星级（规则估算，不入库）
 */
public final class NewsPulseHeuristics {

    private NewsPulseHeuristics() {
    }

    private static final String[][] THEME_RULES = {
            {"光模块", "光模块", "CPO", "光通信", "光电"},
            {"算力", "算力", "AI算力", "英伟达", "NVIDIA", "GPU", "液冷"},
            {"半导体", "半导体", "芯片", "晶圆", "存储芯片", "ARM", "英特尔"},
            {"锂电", "锂电", "锂电池", "正极", "负极", "电解液"},
            {"新能源车", "新能源车", "电动车", "智能驾驶", "自动驾驶"},
            {"光伏", "光伏", "硅料", "组件", "逆变器"},
            {"贵金属", "黄金", "白银", "贵金属"},
            {"油气", "原油", "石油", "天然气", "油气"},
            {"军工", "军工", "航天", "导弹", "国防"},
            {"消费", "消费", "白酒", "免税", "旅游"},
            {"金融", "银行", "券商", "保险", "央行", "降准", "降息"},
            {"地产", "地产", "房地产", "物业"},
            {"医药", "医药", "创新药", "中药", "医疗"},
            {"机器人", "机器人", "人形机器人", "减速器"},
            {"存储", "存储", "HBM", "DDR", "闪存"},
            {"IDC", "IDC", "数据中心", "AIDC"},
    };

    /**
     * 从标题/摘要提取题材（最多 3 个）
     *
     * @param title   标题
     * @param summary 摘要
     * @return 题材列表
     */
    public static List<String> extractThemes(String title, String summary) {
        String body = ((title == null ? "" : title) + " " + (summary == null ? "" : summary)).toUpperCase(Locale.ROOT);
        Set<String> hit = new LinkedHashSet<>();
        for (String[] rule : THEME_RULES) {
            String label = rule[0];
            for (int i = 1; i < rule.length; i++) {
                if (body.contains(rule[i].toUpperCase(Locale.ROOT))) {
                    hit.add(label);
                    break;
                }
            }
            if (hit.size() >= 3) {
                break;
            }
        }
        return new ArrayList<>(hit);
    }

    /**
     * 估算重要度 1-5
     *
     * @param title     标题
     * @param summary   摘要
     * @param sentiment 情感
     * @param source    来源
     * @param yaowen    是否要闻
     * @return 1-5
     */
    public static int estimateStars(String title, String summary, String sentiment, String source, boolean yaowen) {
        int score = 2;
        if (yaowen) {
            score += 2;
        }
        if ("利好".equals(sentiment) || "利空".equals(sentiment)) {
            score += 1;
        }
        if ("cls".equalsIgnoreCase(source) || "eastmoney".equalsIgnoreCase(source)) {
            score += 1;
        }
        String body = (title == null ? "" : title) + (summary == null ? "" : summary);
        if (StringUtils.isNotBlank(body)) {
            if (body.contains("央行") || body.contains("证监会") || body.contains("国务院")
                    || body.contains("美联储") || body.contains("降准") || body.contains("降息")) {
                score += 1;
            }
            if (body.contains("暴涨") || body.contains("暴跌") || body.contains("涨停") || body.contains("跌停")) {
                score += 1;
            }
        }
        return Math.max(1, Math.min(5, score));
    }
}
