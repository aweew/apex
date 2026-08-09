package com.awe.apex.quant.holding;

import com.awe.apex.common.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 持仓题材桶匹配：每只股票只取一个最核心主营题材
 * （光模块(CPO) / 存储芯片 / 数据中心(IDC) / 算力 / 锂电）
 * <p>
 * 弱概念（如泛化「人工智能」）不足以打上核心标签；行业路径里的「含IDC」也不算数据中心主营。
 */
public final class ThemeBucketMatcher {

    /**
     * 低于该分不记为核心题材，避免软件/应用公司被弱 AI 概念误打成「算力」
     */
    private static final int MIN_CORE_SCORE = 55;

    /**
     * 固定题材桶：展示名 → 关键词及专指度权重（越高越像主营）
     */
    private static final Map<String, KeywordWeight[]> BUCKETS = new LinkedHashMap<>();

    static {
        // 专指度：细分关键词 > 泛化概念；「算力」最宽，冲突时让位给更具体桶
        BUCKETS.put("光模块(CPO)", new KeywordWeight[]{
                kw("光模块", 100), kw("CPO", 100), kw("cpo", 100),
                kw("光通信模块", 96), kw("光芯片", 92), kw("硅光", 90), kw("光互联", 88),
                kw("800G", 85), kw("1.6T", 85), kw("光通信", 75)
        });
        BUCKETS.put("存储芯片", new KeywordWeight[]{
                kw("存储芯片", 100), kw("HBM", 98), kw("DRAM", 92), kw("NAND", 92),
                kw("半导体存储", 90), kw("内存芯片", 88), kw("闪存", 82),
                kw("存储模组", 80), kw("NOR Flash", 78), kw("NORFLASH", 78),
                kw("DDR", 70), kw("eMMC", 68), kw("UFS", 68), kw("存储", 42)
        });
        BUCKETS.put("数据中心(IDC)", new KeywordWeight[]{
                kw("IDC概念", 100), kw("AIDC", 98), kw("数据中心", 96),
                kw("东数西算", 94), kw("智算中心", 90), kw("算力租赁", 86),
                kw("液冷概念", 78), kw("液冷", 72), kw("通算", 70),
                kw("UPS电源", 62), kw("机柜", 48),
                // 单独 IDC 需上下文校验，见 containsKeyword
                kw("IDC", 100), kw("idc", 100)
        });
        BUCKETS.put("算力", new KeywordWeight[]{
                kw("AI芯片", 74), kw("AI算力", 72), kw("算力概念", 60), kw("GPU", 65),
                kw("先进封装", 58), kw("算力", 50),
                kw("AI手机", 48), kw("服务器", 42),
                // 泛化 AI 概念权重低于门槛，单独出现不打核心算力
                kw("大模型", 45), kw("AIGC", 45), kw("AI应用", 40),
                kw("边缘计算", 38), kw("人工智能", 35), kw("半导体概念", 28)
        });
        BUCKETS.put("锂电", new KeywordWeight[]{
                kw("锂矿概念", 96), kw("锂电池概念", 92), kw("锂电", 88),
                kw("固态电池", 80), kw("刀片电池", 78), kw("能源金属", 70),
                kw("动力电池", 65), kw("电池技术", 55)
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
        return matchPrimary(texts, null);
    }

    /**
     * 主营/行业强证据优先；概念仅补充。单挂「存储芯片」等概念板不算主营。
     *
     * @param strongTexts 行业、主营、名称等
     * @param weakTexts   概念、题材板块名等（可空）
     * @return 最核心题材名；未命中返回 null
     */
    public static String matchPrimary(List<String> strongTexts, List<String> weakTexts) {
        String fromStrong = scoreBuckets(strongTexts, false);
        if (StringUtils.isNotBlank(fromStrong)) {
            return fromStrong;
        }
        String fromWeak = scoreBuckets(weakTexts, true);
        if (StringUtils.isBlank(fromWeak)) {
            return null;
        }
        if (contradictsIndustry(fromWeak, strongTexts)) {
            return null;
        }
        return fromWeak;
    }

    /**
     * 根据概念/板块/行业文本匹配题材（至多一个核心题材）
     *
     * @param texts 待匹配文本（概念、板块名、行业等）
     * @return 0 或 1 个题材桶名
     */
    public static List<String> match(List<String> texts) {
        return match(texts, null);
    }

    /**
     * 强/弱文本分层匹配题材
     *
     * @param strongTexts 行业、主营等
     * @param weakTexts   概念等
     * @return 0 或 1 个题材桶名
     */
    public static List<String> match(List<String> strongTexts, List<String> weakTexts) {
        String primary = matchPrimary(strongTexts, weakTexts);
        if (StringUtils.isBlank(primary)) {
            return List.of();
        }
        return List.of(primary);
    }

    /**
     * 对各题材桶打分取最高
     *
     * @param texts      文本
     * @param weakMode   true 时降权嘈杂概念词（如概念板「存储芯片」）
     * @return 题材名或 null
     */
    private static String scoreBuckets(List<String> texts, boolean weakMode) {
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
                if (containsKeyword(hay, keywordWeight.keyword)) {
                    int weight = keywordWeight.weight;
                    if (weakMode) {
                        weight = demoteWeakKeyword(keywordWeight.keyword, weight);
                    }
                    if (weight > score) {
                        score = weight;
                    }
                }
            }
            // 同权重时保留先定义的更细分桶（LinkedHashMap 顺序：光模块→存储→IDC→算力）
            if (score > bestScore) {
                bestScore = score;
                bestBucket = entry.getKey();
            }
        }
        if (bestScore < MIN_CORE_SCORE) {
            return null;
        }
        // 弱证据不得单靠概念板定锂电/IDC/算力（东财滥挂极常见）
        if (weakMode && ("锂电".equals(bestBucket) || "数据中心(IDC)".equals(bestBucket) || "算力".equals(bestBucket))) {
            return null;
        }
        // 弱证据打「存储芯片」还需专指词，避免测封/设备/材料公司误挂
        if (weakMode && "存储芯片".equals(bestBucket) && !hasStorageSpecialty(hay)) {
            return null;
        }
        return bestBucket;
    }

    /**
     * 概念板常见噪音词降权（单独挂板不足以定主营）
     */
    private static int demoteWeakKeyword(String keyword, int weight) {
        String key = keyword.toUpperCase(Locale.ROOT);
        if (key.contains("CPO") || key.contains("光模块") || key.contains("光通信模块")
                || key.contains("光芯片") || key.contains("硅光") || key.contains("光互联")
                || key.contains("光通信") || "800G".equals(key) || "1.6T".equals(key)) {
            return Math.min(weight, 40);
        }
        if ("存储芯片".equals(key) || "存储".equals(key)) {
            return Math.min(weight, 40);
        }
        if ("半导体概念".equals(key) || "服务器".equals(key)) {
            return Math.min(weight, 30);
        }
        if (key.contains("锂") || key.contains("电池") || "能源金属".equals(key)
                || "固态电池".equals(key) || "刀片电池".equals(key) || "动力电池".equals(key)) {
            return Math.min(weight, 40);
        }
        if (key.contains("IDC") || key.contains("数据中心") || key.contains("液冷")
                || key.contains("东数西算") || key.contains("智算") || key.contains("算力租赁")
                || "通算".equals(key) || "UPS电源".equals(key) || "机柜".equals(key)) {
            return Math.min(weight, 40);
        }
        if (key.contains("算力") || "GPU".equals(key) || key.contains("AI芯片") || "先进封装".equals(key)) {
            return Math.min(weight, 40);
        }
        return weight;
    }

    /**
     * 存储专指证据（HBM/DRAM 等），不含泛化「存储芯片」板名
     */
    private static boolean hasStorageSpecialty(String hayUpper) {
        String[] keys = {"HBM", "DRAM", "NAND", "NOR FLASH", "NORFLASH", "闪存", "内存芯片",
                "半导体存储", "高带宽内存", "DDR", "EMMC", "UFS"};
        for (String key : keys) {
            if (containsKeyword(hayUpper, key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 行业与题材明显冲突则丢弃弱匹配
     */
    private static boolean contradictsIndustry(String bucket, List<String> strongTexts) {
        if (StringUtils.isBlank(bucket) || strongTexts == null || strongTexts.isEmpty()) {
            return false;
        }
        StringBuilder sb = new StringBuilder();
        for (String text : strongTexts) {
            if (StringUtils.isNotBlank(text)) {
                sb.append(text.trim().toUpperCase(Locale.ROOT)).append('|');
            }
        }
        String hay = sb.toString();
        if (StringUtils.isBlank(hay)) {
            return false;
        }
        if ("存储芯片".equals(bucket) || "算力".equals(bucket) || "光模块(CPO)".equals(bucket)) {
            return containsAny(hay, "化工", "塑料", "树脂", "化学", "光伏", "白酒", "银行", "证券",
                    "保险", "地产", "房地产", "中药", "医药", "农业", "煤炭", "钢铁", "水泥");
        }
        if ("锂电".equals(bucket)) {
            return containsAny(hay, "白酒", "银行", "证券", "软件开发", "IT服务");
        }
        return false;
    }

    private static boolean containsAny(String hayUpper, String... keys) {
        for (String key : keys) {
            if (containsKeyword(hayUpper, key)) {
                return true;
            }
        }
        return false;
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

    /**
     * 关键词命中；对 IDC 跳过「含IDC / 与IDC / (IDC」等嵌套表述，避免云软件误判数据中心
     */
    static boolean containsKeyword(String hayUpper, String keyword) {
        if (StringUtils.isBlank(hayUpper) || StringUtils.isBlank(keyword)) {
            return false;
        }
        String key = keyword.toUpperCase(Locale.ROOT);
        int from = 0;
        while (from < hayUpper.length()) {
            int idx = hayUpper.indexOf(key, from);
            if (idx < 0) {
                return false;
            }
            if (isIdcToken(key) && isEmbeddedIdc(hayUpper, idx)) {
                from = idx + key.length();
                continue;
            }
            return true;
        }
        return false;
    }

    private static boolean isIdcToken(String key) {
        return "IDC".equals(key);
    }

    private static boolean isEmbeddedIdc(String hay, int idx) {
        if (idx <= 0) {
            return false;
        }
        char prev = hay.charAt(idx - 1);
        return prev == '含' || prev == '与' || prev == '(' || prev == '（' || prev == '/' || prev == '、';
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
