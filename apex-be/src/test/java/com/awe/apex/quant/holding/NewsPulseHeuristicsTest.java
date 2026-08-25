package com.awe.apex.quant.holding;

import org.junit.jupiter.api.Test;
import com.awe.apex.quant.domain.dto.NewsPulseCardResp;
import com.awe.apex.quant.domain.dto.PreMarketEventImpactResp;

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

    @Test
    void classifiesPolicyEventWithThemeImpactAndMediaVerification() {
        NewsPulseCardResp card = NewsPulseCardResp.builder()
                .title("工信部发布人工智能产业支持政策")
                .summary("鼓励算力基础设施建设")
                .sentiment("利好")
                .stars(4)
                .themes(List.of("算力"))
                .source("cls")
                .build();

        PreMarketEventImpactResp impact = PreMarketEventHeuristics.toImpact(card);

        assertEquals("POLICY", impact.getEventType());
        assertEquals("THEME", impact.getImpactScope());
        assertEquals("利好", impact.getDirection());
        assertEquals("媒体报道待核验", impact.getVerificationStatus());
        assertTrue(impact.getImpactExplanation().contains("正式文件"));
    }

    @Test
    void classifiesEarningsEventWithStockImpactAndExpectationCaveat() {
        NewsPulseCardResp card = NewsPulseCardResp.builder()
                .title("某公司发布业绩预告，净利润同比增长")
                .summary("预计上半年归母净利润增长 80%")
                .sentiment("利好")
                .stars(4)
                .relatedCodes(List.of("600000"))
                .source("sse")
                .build();

        PreMarketEventImpactResp impact = PreMarketEventHeuristics.toImpact(card);

        assertEquals("EARNINGS", impact.getEventType());
        assertEquals("STOCK", impact.getImpactScope());
        assertTrue(impact.isOfficialSource());
        assertEquals("已核验", impact.getVerificationStatus());
        assertTrue(impact.getImpactExplanation().contains("市场预期"));
    }

    @Test
    void classifiesEmergencyAsPendingVerification() {
        NewsPulseCardResp card = NewsPulseCardResp.builder()
                .title("海外突发冲突升级，原油供应或受扰动")
                .summary("市场正在评估影响")
                .sentiment("中性")
                .stars(3)
                .themes(List.of("油气"))
                .source("sina")
                .build();

        PreMarketEventImpactResp impact = PreMarketEventHeuristics.toImpact(card);

        assertEquals("EMERGENCY", impact.getEventType());
        assertEquals("待验证", impact.getDirection());
        assertTrue(impact.getImpactExplanation().contains("权威来源"));
    }
}
