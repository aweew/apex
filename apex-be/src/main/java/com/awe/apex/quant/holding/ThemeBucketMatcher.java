package com.awe.apex.quant.holding;

import com.awe.apex.common.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 持仓题材桶匹配：每只股票只取一个最核心主营题材
 * （光模块(CPO) / 存储芯片 / 数据中心(IDC) / 算力）
 */
public final class ThemeBucketMatcher {

    /**
     * 固定题材桶：展示名 → 关键词及专指度权重（越高越像主营）
     */
    private static final Map<String, KeywordWeight[]> BUCKETS = new LinkedHashMap<>();

    static {
        // 专指度：细分关键词 > 泛化概念；「算力」最宽，冲突时让位给更具体桶
        BUCKETS.put("光模块(CPO)", new KeywordWeight[]{
                kw("光模块", 100), kw("CPO", 100), kw("cpo", 100),
                kw("光芯片", 92), kw("硅光", 90), kw("光互联", 88),
                kw("800G", 85), kw("1.6T", 85), kw("光通信", 75)
        });
        BUCKETS.put("存储芯片", new KeywordWeight[]{
                kw("存储芯片", 100), kw("HBM", 98), kw("DRAM", 92), kw("NAND", 92),
                kw("半导体存储", 90), kw("内存芯片", 88), kw("闪存", 82),
                kw("存储模组", 80), kw("存储", 42)
        });
        BUCKETS.put("数据中心(IDC)", new KeywordWeight[]{
                kw("IDC", 100), kw("idc", 100), kw("数据中心", 96),
                kw("智算中心", 90), kw("算力租赁", 86), kw("液冷", 72),
                kw("通算", 70), kw("机柜", 48)
        });
        BUCKETS.put("算力", new KeywordWeight[]{
                kw("AI算力", 72), kw("算力概念", 60), kw("GPU", 65),
                kw("先进封装", 58), kw("大模型", 55), kw("算力", 50),
                kw("服务器", 42), kw("AI应用", 40), kw("人工智能", 35)
        });
    }

    private ThemeBucketMatcher() {
    }

    /**
     * 全部题材桶名称（固定顺序）
     *
     * @return 名称列表
     */
    public static List<String> bucketNames() {
        return new ArrayList<>(BUCKETS.keySet());
    }

    /**
     * 匹配唯一核心题材（可空）
     *
     * @param texts 待匹配文本（概念、板块名、行业等）
     * @return 最核心题材名；未命中返回 null
     */
    public static String matchPrimary(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return null;
        }
        StringBuilder joined = new StringBuilder();
        for (String text : texts) {
            if (StringUtils.isBlank(text)) {
                continue;
            }
            joined.append(text.trim().toUpperCase(Locale.ROOT)).append('|');
        }
        String hay = joined.toString();
        if (StringUtils.isBlank(hay)) {
            return null;
        }

        String bestBucket = null;
        int bestScore = 0;
        for (Map.Entry<String, KeywordWeight[]> entry : BUCKETS.entrySet()) {
            int score = 0;
            for (KeywordWeight keywordWeight : entry.getValue()) {
                if (StringUtils.isBlank(keywordWeight.keyword)) {
                    continue;
                }
                if (hay.contains(keywordWeight.keyword.toUpperCase(Locale.ROOT))) {
                    if (keywordWeight.weight > score) {
                        score = keywordWeight.weight;
                    }
                }
            }
            // 同权重时保留先定义的更细分桶（LinkedHashMap 顺序：光模块→存储→IDC→算力）
            if (score > bestScore) {
                bestScore = score;
                bestBucket = entry.getKey();
            }
        }
        return bestBucket;
    }

    /**
     * 根据概念/板块/行业文本匹配题材（至多一个核心题材）
     *
     * @param texts 待匹配文本（概念、板块名、行业等）
     * @return 0 或 1 个题材桶名
     */
    public static List<String> match(List<String> texts) {
        String primary = matchPrimary(texts);
        if (StringUtils.isBlank(primary)) {
            return List.of();
        }
        return List.of(primary);
    }

    /**
     * 逗号/顿号分隔概念串 → 列表
     *
     * @param conceptsCsv 概念串
     * @return 概念列表
     */
    public static List<String> splitConcepts(String conceptsCsv) {
        List<String> list = new ArrayList<>();
        if (StringUtils.isBlank(conceptsCsv)) {
            return list;
        }
        String[] parts = conceptsCsv.split("[,，、;；|/]");
        for (String part : parts) {
            if (StringUtils.isNotBlank(part)) {
                list.add(part.trim());
            }
        }
        return list;
    }

    private static KeywordWeight kw(String keyword, int weight) {
        return new KeywordWeight(keyword, weight);
    }

    private static final class KeywordWeight {
        private final String keyword;
        private final int weight;

        private KeywordWeight(String keyword, int weight) {
            this.keyword = keyword;
            this.weight = weight;
        }
    }
}
