package com.awe.apex.quant.service;

import com.awe.apex.quant.ai.AiChatProperties;
import com.awe.apex.quant.ai.KimiChatClient;
import com.awe.apex.quant.domain.dto.NewsPulseResp;
import com.awe.apex.quant.domain.entity.MarketNews;
import com.awe.apex.quant.mapper.MarketNewsMapper;
import com.awe.apex.quant.service.impl.NewsPulseServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 今日消息面事件影响测试。
 */
class NewsPulseServiceImplTest {

    private NewsPulseServiceImpl service;
    private MarketNewsMapper marketNewsMapper;

    @BeforeEach
    void setUp() {
        service = new NewsPulseServiceImpl();
        marketNewsMapper = mock(MarketNewsMapper.class);
        IHotService hotService = mock(IHotService.class);
        IMarketBriefingService marketBriefingService = mock(IMarketBriefingService.class);
        KimiChatClient kimiChatClient = mock(KimiChatClient.class);
        ReflectionTestUtils.setField(service, "marketNewsMapper", marketNewsMapper);
        ReflectionTestUtils.setField(service, "hotService", hotService);
        ReflectionTestUtils.setField(service, "marketBriefingService", marketBriefingService);
        ReflectionTestUtils.setField(service, "kimiChatClient", kimiChatClient);
        ReflectionTestUtils.setField(service, "aiChatProperties", new AiChatProperties());
        when(kimiChatClient.available()).thenReturn(false);
    }

    @Test
    void returnsPrioritizedEventImpactsWithRelatedCodesAndVerificationStatus() {
        MarketNews policyNews = MarketNews.builder()
                .id(1L)
                .title("工信部发布人工智能产业支持政策")
                .summary("支持算力基础设施建设")
                .source("cls")
                .sentiment("利好")
                .publishedAt(LocalDateTime.now().minusMinutes(10))
                .build();
        MarketNews earningsNews = MarketNews.builder()
                .id(2L)
                .title("公司发布业绩预告，净利润同比增长")
                .summary("预计上半年业绩增长")
                .source("sse")
                .relatedCodes("600000")
                .sentiment("利好")
                .publishedAt(LocalDateTime.now())
                .build();
        when(marketNewsMapper.selectList(any())).thenReturn(List.of(policyNews, earningsNews));

        NewsPulseResp response = service.pulse(6, false);

        assertEquals(2, response.getEventImpacts().size());
        assertEquals("EARNINGS", response.getEventImpacts().get(0).getEventType());
        assertEquals(List.of("600000"), response.getEventImpacts().get(0).getRelatedCodes());
        assertTrue(response.getEventImpacts().get(0).isOfficialSource());
        assertEquals("媒体报道待核验", response.getEventImpacts().get(1).getVerificationStatus());
    }

    @Test
    void treatsNvidiaEarningsAsTopTierAiMarketVariable() {
        MarketNews nvidiaEarnings = MarketNews.builder()
                .id(3L)
                .title("英伟达发布最新季度财报")
                .summary("营收与下一季度业绩指引成为全球 AI 产业链定价焦点")
                .source("cls")
                .sentiment("中性")
                .publishedAt(LocalDateTime.now())
                .build();
        when(marketNewsMapper.selectList(any())).thenReturn(List.of(nvidiaEarnings));

        NewsPulseResp response = service.pulse(6, false);

        assertEquals(1, response.getEventImpacts().size());
        assertEquals("EARNINGS", response.getEventImpacts().get(0).getEventType());
        assertEquals(5, response.getEventImpacts().get(0).getPriority());
        assertTrue(response.getEventImpacts().get(0).getThemes().contains("算力"));
        assertTrue(response.getEventImpacts().get(0).getImpactExplanation().contains("全球 AI"));
    }

    @Test
    void excludesUnmappedCompanyEventsFromPreMarketImpacts() {
        MarketNews announcement = MarketNews.builder()
                .id(4L)
                .title("新能源车企业公告：财务总监拟减持不超过0.07%股份")
                .summary("公司披露高管减持计划")
                .source("eastmoney")
                .sentiment("利空")
                .publishedAt(LocalDateTime.now())
                .build();
        MarketNews earnings = MarketNews.builder()
                .id(5L)
                .title("广钢气体：半年度净利润同比增长17.73%")
                .summary("公司披露半年度业绩")
                .source("eastmoney")
                .sentiment("利好")
                .publishedAt(LocalDateTime.now().minusMinutes(1))
                .build();
        when(marketNewsMapper.selectList(any())).thenReturn(List.of(announcement, earnings));

        NewsPulseResp response = service.pulse(6, false);

        assertTrue(response.getEventImpacts().isEmpty());
    }

    @Test
    void keepsHighPriorityMarketPolicyWithoutThemeMapping() {
        MarketNews policy = MarketNews.builder()
                .id(6L)
                .title("国务院发布资本市场改革方案")
                .summary("【要闻】明确市场改革重点与落地安排")
                .source("cctv")
                .sentiment("中性")
                .publishedAt(LocalDateTime.now())
                .build();
        when(marketNewsMapper.selectList(any())).thenReturn(List.of(policy));

        NewsPulseResp response = service.pulse(6, false);

        assertEquals(1, response.getEventImpacts().size());
        assertEquals("MARKET", response.getEventImpacts().get(0).getImpactScope());
    }

    @Test
    void keepsOnlyTopThreeDistinctEventsWithExplicitAsharesTransmission() {
        MarketNews industryPolicy = MarketNews.builder()
                .id(7L)
                .title("工信部印发算力基础设施行动计划")
                .summary("明确支持算力网络和数据中心建设")
                .source("cctv")
                .sentiment("利好")
                .publishedAt(LocalDateTime.now().minusMinutes(5))
                .build();
        MarketNews repeatedIndustryPolicy = MarketNews.builder()
                .id(8L)
                .title("工信部发布算力基础设施政策")
                .summary("算力网络建设获得政策支持")
                .source("cls")
                .sentiment("利好")
                .publishedAt(LocalDateTime.now().minusMinutes(4))
                .build();
        MarketNews companyEarnings = MarketNews.builder()
                .id(9L)
                .title("公司发布业绩预告，净利润同比增长80%")
                .summary("交易所公告披露上半年业绩")
                .source("sse")
                .relatedCodes("600000")
                .sentiment("利好")
                .publishedAt(LocalDateTime.now().minusMinutes(3))
                .build();
        MarketNews nvidiaEarnings = MarketNews.builder()
                .id(10L)
                .title("英伟达发布季度财报和下一季度业绩指引")
                .summary("全球 AI 产业链关注收入增速与指引")
                .source("cls")
                .sentiment("中性")
                .publishedAt(LocalDateTime.now().minusMinutes(2))
                .build();
        MarketNews fedCommentary = MarketNews.builder()
                .id(11L)
                .title("分析师解读美联储官员讲话")
                .summary("认为货币政策路径仍需由市场自行判断")
                .source("eastmoney")
                .sentiment("中性")
                .publishedAt(LocalDateTime.now().minusMinutes(1))
                .build();
        MarketNews remoteEvent = MarketNews.builder()
                .id(12L)
                .title("战争与极端天气推高海外农产品价格")
                .summary("海外农产品创多年最大月涨幅")
                .source("cls")
                .sentiment("利好")
                .publishedAt(LocalDateTime.now())
                .build();
        when(marketNewsMapper.selectList(any())).thenReturn(List.of(
                industryPolicy, repeatedIndustryPolicy, companyEarnings,
                nvidiaEarnings, fedCommentary, remoteEvent));

        NewsPulseResp response = service.pulse(6, false);

        assertEquals(3, response.getEventImpacts().size());
        assertEquals(1, response.getEventImpacts().stream()
                .filter(item -> item.getThemes().contains("算力") && "POLICY".equals(item.getEventType()))
                .count());
        assertTrue(response.getEventImpacts().stream()
                .anyMatch(item -> item.getRelatedCodes().contains("600000")));
        assertTrue(response.getEventImpacts().stream()
                .anyMatch(item -> item.getTitle().contains("英伟达")));
        assertFalse(response.getEventImpacts().stream()
                .anyMatch(item -> item.getTitle().contains("分析师解读") || item.getTitle().contains("农产品")));
    }

    @Test
    void summarizesTopEventsAsVariableTransmissionAndOpeningValidation() {
        MarketNews policy = MarketNews.builder()
                .id(13L)
                .title("工信部印发人工智能算力基础设施行动计划")
                .summary("明确支持算力网络和数据中心建设")
                .source("cctv")
                .sentiment("利好")
                .publishedAt(LocalDateTime.now())
                .build();
        MarketNews commentary = MarketNews.builder()
                .id(14L)
                .title("经济学家评论美联储政策路径")
                .summary("市场应自行判断未来利率方向")
                .source("eastmoney")
                .sentiment("中性")
                .publishedAt(LocalDateTime.now().minusMinutes(1))
                .build();
        when(marketNewsMapper.selectList(any())).thenReturn(List.of(policy, commentary));

        NewsPulseResp response = service.pulse(6, false);

        assertEquals(1, response.getBullCount());
        assertEquals(0, response.getBearCount());
        assertEquals(0, response.getNeutralCount());
        assertEquals("消息偏多", response.getBiasLabel());
        assertTrue(response.getExecutiveSummary().contains("首要变量："));
        assertTrue(response.getExecutiveSummary().contains("A股映射："));
        assertTrue(response.getExecutiveSummary().contains("开盘验证："));
        assertFalse(response.getExecutiveSummary().contains("利好方向："));
        assertFalse(response.getExecutiveSummary().contains("行情立场"));
    }

    @Test
    void excludesForeignPolicyCommentaryAndLocalPublicityWithoutAsharesMapping() {
        MarketNews foreignPolicy = MarketNews.builder()
                .id(15L)
                .title("美国财长：相信日本将出台举措推动日元升值")
                .summary("日本央行政策与金融市场预期受到关注")
                .source("eastmoney")
                .sentiment("中性")
                .publishedAt(LocalDateTime.now())
                .build();
        MarketNews localPublicity = MarketNews.builder()
                .id(16L)
                .title("北京自贸试验区联动发展探新路")
                .summary("地方发布产业区域发展政策")
                .source("eastmoney")
                .sentiment("中性")
                .publishedAt(LocalDateTime.now().minusMinutes(1))
                .build();
        MarketNews nationalPolicy = MarketNews.builder()
                .id(17L)
                .title("国务院发布资本市场改革方案")
                .summary("明确改革重点与落地安排")
                .source("cctv")
                .sentiment("利好")
                .publishedAt(LocalDateTime.now().minusMinutes(2))
                .build();
        when(marketNewsMapper.selectList(any())).thenReturn(List.of(
                foreignPolicy, localPublicity, nationalPolicy));

        NewsPulseResp response = service.pulse(6, false);

        assertEquals(1, response.getEventImpacts().size());
        assertTrue(response.getEventImpacts().get(0).getTitle().contains("资本市场改革"));
    }
}
