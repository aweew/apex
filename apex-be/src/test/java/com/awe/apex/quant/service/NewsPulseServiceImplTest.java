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
}
