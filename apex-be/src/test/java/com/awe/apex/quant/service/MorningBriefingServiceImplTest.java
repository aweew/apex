package com.awe.apex.quant.service;

import com.awe.apex.quant.bot.config.ApexBotProperties;
import com.awe.apex.quant.domain.dto.MorningBriefingResp;
import com.awe.apex.quant.domain.dto.NewsPulseCardResp;
import com.awe.apex.quant.domain.dto.NewsPulseResp;
import com.awe.apex.quant.domain.dto.OvernightMarketQuote;
import com.awe.apex.quant.market.UsMarketQuoteClient;
import com.awe.apex.quant.service.impl.MorningBriefingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MorningBriefingServiceImplTest {

    private MorningBriefingServiceImpl service;
    private UsMarketQuoteClient usMarketQuoteClient;
    private INewsPulseService newsPulseService;

    @BeforeEach
    void setUp() {
        service = new MorningBriefingServiceImpl();
        usMarketQuoteClient = mock(UsMarketQuoteClient.class);
        newsPulseService = mock(INewsPulseService.class);
        ApexBotProperties properties = new ApexBotProperties();
        properties.getMorningBriefing().setSymbols("usIXIC,usDJI,usINX,usNVDA,usBABA");
        ReflectionTestUtils.setField(service, "properties", properties);
        ReflectionTestUtils.setField(service, "usMarketQuoteClient", usMarketQuoteClient);
        ReflectionTestUtils.setField(service, "newsPulseService", newsPulseService);
    }

    @Test
    void summarizesUsMarketAndOvernightNews() {
        when(usMarketQuoteClient.fetch(anyList())).thenReturn(List.of(
                OvernightMarketQuote.builder().symbol("usIXIC").name("纳斯达克")
                        .pctChg(new BigDecimal("0.81")).quoteTime(LocalDateTime.of(2026, 8, 14, 5, 15)).build(),
                OvernightMarketQuote.builder().symbol("usNVDA").name("英伟达")
                        .pctChg(new BigDecimal("-1.20")).quoteTime(LocalDateTime.of(2026, 8, 14, 4, 0)).build()
        ));
        when(newsPulseService.pulse(6, true)).thenReturn(NewsPulseResp.builder()
                .executiveSummary("利好方向：AI；承压方向：原油；整体偏中性。")
                .cards(List.of(NewsPulseCardResp.builder().title("美联储公布最新经济数据").build()))
                .build());

        MorningBriefingResp response = service.generate();

        assertEquals("GREEN", response.getDataLevel());
        assertEquals(2, response.getMarketQuotes().size());
        assertTrue(response.getSummary().contains("纳斯达克 +0.81%"));
        assertTrue(response.getSummary().contains("英伟达 -1.20%"));
        assertTrue(response.getSummary().contains("AI"));
        assertEquals(List.of("美联储公布最新经济数据"), response.getNewsTitles());
    }

    @Test
    void marksReportYellowWhenUsQuotesAreUnavailable() {
        when(usMarketQuoteClient.fetch(anyList())).thenReturn(List.of());
        when(newsPulseService.pulse(6, true)).thenReturn(NewsPulseResp.builder()
                .executiveSummary("夜间新闻暂无有效条目。")
                .cards(List.of())
                .build());

        MorningBriefingResp response = service.generate();

        assertEquals("YELLOW", response.getDataLevel());
        assertTrue(response.getSummary().contains("美股行情暂未获取"));
    }
}
