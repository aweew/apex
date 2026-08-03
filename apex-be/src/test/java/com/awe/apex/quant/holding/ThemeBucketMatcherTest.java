package com.awe.apex.quant.holding;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 题材桶匹配：只取一个最核心主营题材
 */
class ThemeBucketMatcherTest {

    @Test
    void opticalBeatsBroadCompute() {
        List<String> hits = ThemeBucketMatcher.match(List.of("光模块概念", "CPO", "算力概念"));
        assertEquals(1, hits.size());
        assertEquals("光模块(CPO)", hits.get(0));
    }

    @Test
    void storageBeatsIdcWhenHbmStronger() {
        assertEquals("存储芯片", ThemeBucketMatcher.matchPrimary(List.of("HBM", "数据中心")));
    }

    @Test
    void idcWhenPrimaryConcept() {
        assertEquals("数据中心(IDC)", ThemeBucketMatcher.matchPrimary(List.of("IDC概念", "机柜")));
    }

    @Test
    void computeWhenOnlyBroadAi() {
        assertEquals("算力", ThemeBucketMatcher.matchPrimary(List.of("人工智能", "大模型")));
    }

    @Test
    void noHitReturnsEmpty() {
        assertTrue(ThemeBucketMatcher.match(List.of("银行", "白酒")).isEmpty());
        assertNull(ThemeBucketMatcher.matchPrimary(List.of("银行", "白酒")));
    }
}
