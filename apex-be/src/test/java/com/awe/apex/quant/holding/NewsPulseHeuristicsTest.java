package com.awe.apex.quant.holding;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 消息面题材/星级启发式
 */
class NewsPulseHeuristicsTest {

    @Test
    void extractThemes_hitsSemiconductor() {
        List<String> themes = NewsPulseHeuristics.extractThemes(
                "费城半导体指数暴涨，英伟达带动算力",
                "芯片板块走强");
        assertTrue(themes.contains("半导体") || themes.contains("算力"));
    }

    @Test
    void estimateStars_yaowenHigher() {
        int normal = NewsPulseHeuristics.estimateStars("普通新闻", "内容", "中性", "sina", false);
        int yaowen = NewsPulseHeuristics.estimateStars("央行降准", "【要闻】流动性", "利好", "eastmoney", true);
        assertTrue(yaowen > normal);
        assertEquals(5, Math.min(5, yaowen));
    }
}
