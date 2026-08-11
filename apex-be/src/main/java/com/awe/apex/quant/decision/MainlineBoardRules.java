package com.awe.apex.quant.decision;

import com.awe.apex.common.util.StringUtils;

/**
 * 主线板块规则：识别纯概念板块，并提供板块评分规则
 */
public final class MainlineBoardRules {

    /**
     * 结果型/统计型板块关键词（涨停结果复盘，不宜作前瞻主线）
     */
    private static final String[] OUTCOME_KEYWORDS = {
            "昨日连板",
            "昨日涨停",
            "昨日高振幅",
            "昨日振幅",
            "今日连板",
            "今日涨停",
            "昨日首板",
            "今日首板",
            "昨日打板",
            "今日打板",
            "最近多板",
            "近期多板",
            "多次涨停",
            "反复涨停",
            "曾涨停",
            "曾连板",
            "含一字",
            "炸板",
            "跌停",
            "连板天梯",
            "涨停天梯",
            "自然涨停",
            "回封板",
            "回封",
            "高标股",
            "晋级股",
            "空间板",
            "卡位板",
            "弱转强",
            "反包",
            "多板",
            "高振幅",
            "振幅",
            "一字板",
            "连板",
            "涨停",
            "首板",
            "打板",
    };

    /**
     * 风格、持仓与统计标签，不属于产业概念板块
     */
    private static final String[] STYLE_KEYWORDS = {
            "风格", "ST股", "重仓", "成份", "成分", "新高", "新低",
            "低价股", "高价股", "百元股", "超跌股", "热股", "密集调研", "摘帽",
            "融资融券", "沪股通", "深股通", "北向资金", "机构持股", "社保持股",
            "养老金", "QFII", "MSCI", "富时罗素", "破净股", "高股息", "绩优股",
            "蓝筹股", "大盘股", "中盘股", "小盘股", "预增", "预亏", "高送转",
            "股权激励", "AH股",
    };

    private MainlineBoardRules() {
    }

    /**
     * 是否为结果型情绪板（应从主线池排除）
     *
     * @param boardName 板块名称
     * @return true=排除
     */
    public static boolean isOutcomeBoard(String boardName) {
        if (StringUtils.isBlank(boardName)) {
            return true;
        }
        String name = boardName.trim();
        // 「昨日*」几乎全是结果统计板（昨日连板/高振幅/涨停…）
        if (name.startsWith("昨日")) {
            return true;
        }
        // 「最近/近期 + 板/涨停」：最近多板、近期连板等
        if ((name.startsWith("最近") || name.startsWith("近期"))
                && (name.contains("板") || name.contains("涨停") || name.contains("振幅"))) {
            return true;
        }
        // 「今日 + 情绪词」
        if (name.startsWith("今日") && containsEmotionToken(name)) {
            return true;
        }
        for (String keyword : OUTCOME_KEYWORDS) {
            if (name.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否为可用于概念涨幅和主线识别的纯概念板块
     *
     * @param boardType 板块类型
     * @param boardName 板块名称
     * @return true=纯概念板块
     */
    public static boolean isConceptBoard(String boardType, String boardName) {
        if (!"CONCEPT".equals(boardType) || isOutcomeBoard(boardName)) {
            return false;
        }
        String name = boardName.trim();
        for (String keyword : STYLE_KEYWORDS) {
            if (name.contains(keyword)) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsEmotionToken(String name) {
        return name.contains("涨停")
                || name.contains("连板")
                || name.contains("首板")
                || name.contains("打板")
                || name.contains("跌停")
                || name.contains("振幅")
                || name.contains("多板")
                || name.contains("炸板")
                || name.contains("一字");
    }

    /**
     * 板块类型加分：行业 > 概念 > 题材（降低纯情绪题材权重）
     *
     * @param boardType INDUSTRY/CONCEPT/THEME
     * @return 加分（0~0.08）
     */
    public static double typeBonus(String boardType) {
        if ("INDUSTRY".equals(boardType)) {
            return 0.08;
        }
        if ("CONCEPT".equals(boardType)) {
            return 0.05;
        }
        if ("THEME".equals(boardType)) {
            return 0.02;
        }
        return 0;
    }

    /**
     * 同名去重时的类型优先级（越小越优先）
     *
     * @param boardType 类型
     * @return 排序键
     */
    public static int typeRank(String boardType) {
        if ("INDUSTRY".equals(boardType)) {
            return 0;
        }
        if ("CONCEPT".equals(boardType)) {
            return 1;
        }
        if ("THEME".equals(boardType)) {
            return 2;
        }
        return 9;
    }
}
