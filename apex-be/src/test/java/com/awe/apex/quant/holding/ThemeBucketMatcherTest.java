package com.awe.apex.quant.holding;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 题材桶匹配：只取一个最核心主营题材；弱概念不误判
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
    void embeddedIdcInCloudSoftwareDoesNotCount() {
        assertNull(ThemeBucketMatcher.matchPrimary(List.of("计算机-计算机应用-云服务(含IDC与CDN)", "软件开发")));
        assertFalse(ThemeBucketMatcher.containsKeyword("云服务(含IDC与CDN)|软件开发".toUpperCase(), "IDC"));
    }

    @Test
    void weakAiDoesNotBecomeComputeCore() {
        // 软件公司常见弱标签，不应打成核心「算力」
        assertNull(ThemeBucketMatcher.matchPrimary(List.of("人工智能", "大模型", "软件开发")));
        assertNull(ThemeBucketMatcher.matchPrimary(List.of("AI应用", "DeepSeek概念", "企业应用软件")));
    }

    @Test
    void computeWhenStrongKeyword() {
        assertEquals("算力", ThemeBucketMatcher.matchPrimary(List.of("AI芯片", "消费电子概念")));
        assertEquals("算力", ThemeBucketMatcher.matchPrimary(List.of("算力概念", "物联网")));
    }

    @Test
    void noHitReturnsEmpty() {
        assertTrue(ThemeBucketMatcher.match(List.of("银行", "白酒")).isEmpty());
        assertNull(ThemeBucketMatcher.matchPrimary(List.of("银行", "白酒")));
    }

    @Test
    void lithiumWhenEnergyMetal() {
        assertEquals("锂电", ThemeBucketMatcher.matchPrimary(List.of("锂电池概念", "能源金属")));
    }

    @Test
    void eastDataComputeAndStorageKeywords() {
        assertEquals("数据中心(IDC)", ThemeBucketMatcher.matchPrimary(List.of("东数西算", "储能概念")));
        assertEquals("存储芯片", ThemeBucketMatcher.matchPrimary(List.of("NOR Flash", "物联网")));
    }

    @Test
    void conceptOnlyStorageDoesNotOverrideIndustry() {
        // 长川类：概念板挂了「存储芯片」，但主营是半导体设备 → 不打存储
        assertNull(ThemeBucketMatcher.matchPrimary(
                List.of("半导体", "集成电路专用设备的研发、生产和销售"),
                List.of("国产芯片", "半导体概念", "存储芯片")));
        // 圣泉类：化工+滥挂存储板 → 绝不能是存储芯片（锂电概念可另议）
        String chemical = ThemeBucketMatcher.matchPrimary(
                List.of("化工", "化学新材料和生物质新材料"),
                List.of("锂电池概念", "存储芯片", "先进封装"));
        assertFalse("存储芯片".equals(chemical));
    }

    @Test
    void conceptOnlyCpoDoesNotOverrideSemiconductorIndustry() {
        // 立昂微类：主营是半导体材料/功率器件，仅概念板挂 CPO，不应展示成光模块。
        assertNull(ThemeBucketMatcher.matchPrimary(
                List.of("半导体", "半导体硅片、功率器件芯片及化合物半导体射频芯片"),
                List.of("CPO概念", "半导体概念", "第三代半导体")));
    }

    @Test
    void specialtyStorageStillMatchesFromStrong() {
        assertEquals("存储芯片", ThemeBucketMatcher.matchPrimary(
                List.of("半导体", "HBM存储器研发销售"),
                List.of("存储芯片", "人工智能")));
    }

    @Test
    void runzeIdcFromMainBusiness() {
        // 润泽科技：行业是通信服务，但主营明确 IDC/AIDC → 核心题材应为数据中心
        assertEquals("数据中心(IDC)", ThemeBucketMatcher.matchPrimary(
                List.of("通信服务", "IDC业务和AIDC业务。IDC业务:公司与基础电信运营商合作", "润泽科技"),
                List.of("数据中心", "东数西算", "算力概念", "液冷概念")));
    }
}
