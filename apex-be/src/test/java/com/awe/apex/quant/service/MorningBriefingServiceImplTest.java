package com.awe.apex.quant.service;

import com.awe.apex.quant.bot.config.ApexBotProperties;
import com.awe.apex.quant.cache.RedisCacheService;
import com.awe.apex.quant.domain.dto.MorningBriefingResp;
import com.awe.apex.quant.domain.dto.NewsPulseCardResp;
import com.awe.apex.quant.domain.dto.NewsPulseResp;
import com.awe.apex.quant.domain.dto.OvernightMarketQuote;
import com.awe.apex.quant.market.TradingCalendar;
import com.awe.apex.quant.market.UsMarketQuoteClient;
import com.awe.apex.quant.service.impl.MorningBriefingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MorningBriefingServiceImplTest {

    private MorningBriefingServiceImpl service;
    private UsMarketQuoteClient usMarketQuoteClient;
    private INewsPulseService newsPulseService;
    private IMarketOpinionService marketOpinionService;
    private RedisCacheService redisCacheService;
    private ApexBotProperties properties;

    @BeforeEach
    void setUp() {
        service = new MorningBriefingServiceImpl();
        usMarketQuoteClient = mock(UsMarketQuoteClient.class);
        newsPulseService = mock(INewsPulseService.class);
        marketOpinionService = mock(IMarketOpinionService.class);
        redisCacheService = mock(RedisCacheService.class);
        properties = new ApexBotProperties();
        ReflectionTestUtils.setField(service, "properties", properties);
        ReflectionTestUtils.setField(service, "marketQuoteClient", usMarketQuoteClient);
        ReflectionTestUtils.setField(service, "newsPulseService", newsPulseService);
        ReflectionTestUtils.setField(service, "marketOpinionService", marketOpinionService);
        ReflectionTestUtils.setField(service, "redisCacheService", redisCacheService);
    }

    @Test
    void requestsDefaultTechnologyAndSemiconductorWatchPool() {
        when(usMarketQuoteClient.fetch(anyList())).thenReturn(List.of());

        service.generate();

        ArgumentCaptor<List<String>> symbolsCaptor = ArgumentCaptor.forClass(List.class);
        verify(usMarketQuoteClient).fetch(symbolsCaptor.capture());
        List<String> requestedSymbols = symbolsCaptor.getValue();
        assertTrue(requestedSymbols.containsAll(List.of(
                "usIXIC", "usDJI", "usINX",
                "hkHSI", "hkHSTECH",
                "usMSFT", "usAAPL", "usAMZN", "usGOOG", "usMETA", "usTSLA", "usSPCX",
                "usNVDA", "usAVGO", "usARM", "usMRVL", "usMU",
                "usSNDK", "usSKHY", "usAMD", "usWDC", "usSTX",
                "usTSM", "usGFS", "usASML", "usAMAT", "usLRCX", "usKLAC",
                "usSNPS", "usCDNS", "usQCOM", "usINTC", "usTXN", "usADI",
                "usNXPI", "usON", "usBABA", "usPDD"
        )));
    }

    @Test
    void assignsCurrentOrNextTradingDate() {
        when(usMarketQuoteClient.fetch(anyList())).thenReturn(List.of());

        MorningBriefingResp response = service.generate();

        LocalDateTime generatedAt = response.getGeneratedAt();
        assertEquals(TradingCalendar.isTradingDay(generatedAt.toLocalDate())
                        ? generatedAt.toLocalDate() : TradingCalendar.nextTradingDay(generatedAt.toLocalDate()),
                response.getTradeDate());
    }

    @Test
    void requestsSymbolsAddedThroughIndexStarAndThemeConfiguration() {
        properties.getMorningBriefing().setSymbols("usIXIC");
        properties.getMorningBriefing().setIndexSymbols("usINX");
        properties.getMorningBriefing().setStarSymbols("usAMD");
        properties.getMorningBriefing().setStorageSymbols("usSNDK");
        when(usMarketQuoteClient.fetch(anyList())).thenReturn(List.of());

        service.generate();

        ArgumentCaptor<List<String>> symbolsCaptor = ArgumentCaptor.forClass(List.class);
        verify(usMarketQuoteClient).fetch(symbolsCaptor.capture());
        assertTrue(symbolsCaptor.getValue().containsAll(List.of("usIXIC", "usINX", "usAMD", "usSNDK")));
    }

    @Test
    void summarizesUsMarketAndOvernightNews() {
        when(usMarketQuoteClient.fetch(anyList())).thenReturn(List.of(
                OvernightMarketQuote.builder().symbol("usIXIC").name("纳斯达克")
                        .pctChg(new BigDecimal("0.81")).quoteTime(LocalDateTime.of(2026, 8, 14, 5, 15)).build(),
                OvernightMarketQuote.builder().symbol("usNVDA").name("英伟达")
                        .pctChg(new BigDecimal("-1.20")).quoteTime(LocalDateTime.of(2026, 8, 14, 4, 0)).build()
        ));
        NewsPulseResp newsPulse = NewsPulseResp.builder()
                .executiveSummary("利好方向：AI；承压方向：原油；整体偏中性。")
                .cards(List.of(NewsPulseCardResp.builder().title("美联储公布最新经济数据").build()))
                .build();
        when(newsPulseService.pulse(6, true)).thenReturn(newsPulse);

        MorningBriefingResp response = service.generate();

        assertEquals("YELLOW", response.getDataLevel());
        assertEquals(2, response.getMarketQuotes().size());
        assertTrue(response.getSummary().contains("纳斯达克 +0.81%"));
        assertTrue(response.getSummary().contains("英伟达 -1.20%"));
        assertTrue(response.getSummary().contains("AI"));
        assertEquals(List.of("美联储公布最新经济数据"), response.getNewsTitles());
        assertSame(newsPulse, response.getNewsPulse());
        verify(redisCacheService).put(eq("apex:morning-briefing:latest"), eq(response), any());
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

    @Test
    void groupsMarketIndexesSeparatelyFromIndividualStocks() {
        when(usMarketQuoteClient.fetch(anyList())).thenReturn(List.of(
                quote("usNVDA", "英伟达", "3.20"),
                quote("usIXIC", "纳斯达克", "0.81"),
                quote("usDJI", "道琼斯", "-0.22"),
                quote("usAMD", "AMD", "-2.10"),
                quote("usINX", "标普500", "0.35")
        ));

        MorningBriefingResp response = service.generate();

        assertEquals(List.of("usIXIC", "usDJI", "usINX"), response.getIndexQuotes().stream()
                .map(OvernightMarketQuote::getSymbol)
                .toList());
        assertEquals(5, response.getMarketQuotes().size());
    }

    @Test
    void groupsAsiaIndexesSeparatelyAndIncludesThemInSummary() {
        when(usMarketQuoteClient.fetch(anyList())).thenReturn(List.of(
                quote("usIXIC", "纳斯达克", "0.81"),
                quote("hkHSI", "恒生指数", "-1.20"),
                quote("hkHSTECH", "恒生科技", "-2.10")
        ));

        MorningBriefingResp response = service.generate();

        assertEquals(List.of("hkHSI", "hkHSTECH"), response.getAsiaQuotes().stream()
                .map(OvernightMarketQuote::getSymbol)
                .toList());
        assertTrue(response.getSummary().contains("亚太市场：恒生指数 -1.20%"));
    }

    @Test
    void calculatesStorageThemeBreadthMedianAndLeaders() {
        when(usMarketQuoteClient.fetch(anyList())).thenReturn(List.of(
                quote("usSNDK", "闪迪", "6.50"),
                quote("usSKHY", "SK海力士", "2.00"),
                quote("usMU", "美光", "0.00"),
                quote("usWDC", "西部数据", "-1.00"),
                quote("usSTX", "希捷", "-4.00")
        ));

        MorningBriefingResp response = service.generate();

        var storageTheme = response.getMarketThemes().stream()
                .filter(theme -> "STORAGE".equals(theme.getCode()))
                .findFirst()
                .orElseThrow();
        assertEquals("存储", storageTheme.getName());
        assertEquals(5, storageTheme.getQuoteCount());
        assertEquals(2, storageTheme.getUpCount());
        assertEquals(2, storageTheme.getDownCount());
        assertEquals(1, storageTheme.getFlatCount());
        assertEquals(new BigDecimal("0.00"), storageTheme.getMedianPctChg());
        assertEquals("usSNDK", storageTheme.getLeaderQuote().getSymbol());
        assertEquals("usSTX", storageTheme.getLaggardQuote().getSymbol());
    }

    @Test
    void sortsThemesByMedianChangeFromStrongToWeak() {
        when(usMarketQuoteClient.fetch(anyList())).thenReturn(List.of(
                quote("usNVDA", "英伟达", "4.00"),
                quote("usAMD", "AMD", "2.00"),
                quote("usSNDK", "闪迪", "-1.00"),
                quote("usSTX", "希捷", "-3.00")
        ));

        MorningBriefingResp response = service.generate();

        assertEquals(List.of("AI_CHIP", "STORAGE"), response.getMarketThemes().subList(0, 2).stream()
                .map(theme -> theme.getCode())
                .toList());
        assertEquals(new BigDecimal("3.00"), response.getMarketThemes().get(0).getMedianPctChg());
        assertEquals(new BigDecimal("-2.00"), response.getMarketThemes().get(1).getMedianPctChg());
        assertTrue(response.getSummary().contains("最强AI芯片 +3.00%"));
        assertTrue(response.getSummary().contains("最弱存储 -2.00%"));
    }

    @Test
    void sortsStarQuotesByAbsoluteChangeAndAppliesConfiguredLimit() {
        properties.getMorningBriefing().setStarQuoteLimit(3);
        when(usMarketQuoteClient.fetch(anyList())).thenReturn(List.of(
                quote("usINX", "标普500", "20.00"),
                quote("usNVDA", "英伟达", "-7.00"),
                quote("usAMD", "AMD", "8.00"),
                quote("usSNDK", "闪迪", "-9.00"),
                quote("usWDC", "西部数据", "4.00"),
                quote("usSTX", "希捷", "-3.00")
        ));

        MorningBriefingResp response = service.generate();

        assertEquals(List.of("usSNDK", "usAMD", "usNVDA"), response.getStarQuotes().stream()
                .map(OvernightMarketQuote::getSymbol)
                .toList());
    }

    @Test
    void summaryIncludesThemeSentimentAndStarMovers() {
        when(usMarketQuoteClient.fetch(anyList())).thenReturn(List.of(
                quote("usIXIC", "纳斯达克", "0.81"),
                quote("usSNDK", "闪迪", "6.50"),
                quote("usSKHY", "SK海力士", "2.00"),
                quote("usMU", "美光", "0.00"),
                quote("usWDC", "西部数据", "-1.00"),
                quote("usSTX", "希捷", "-4.00")
        ));

        MorningBriefingResp response = service.generate();

        assertTrue(response.getSummary().contains("主题情绪"));
        assertTrue(response.getSummary().contains("存储"));
        assertTrue(response.getSummary().contains("明星异动"));
        assertTrue(response.getSummary().contains("闪迪 +6.50%"));
        assertFalse(response.getSummary().contains("西部数据 -1.00%"));
    }

    @Test
    void marksReportYellowWhenIndexOrStarQuotesAreMissing() {
        when(usMarketQuoteClient.fetch(anyList()))
                .thenReturn(List.of(quote("usIXIC", "纳斯达克", "0.81")))
                .thenReturn(List.of(quote("usNVDA", "英伟达", "3.20")));

        MorningBriefingResp indexOnlyResponse = service.generate();
        MorningBriefingResp starOnlyResponse = service.generate();

        assertEquals("YELLOW", indexOnlyResponse.getDataLevel());
        assertEquals("YELLOW", starOnlyResponse.getDataLevel());
    }

    @Test
    void marksReportGreenWhenEveryConfiguredQuoteIsAvailable() {
        properties.getMorningBriefing().setSymbols("usIXIC,usAMD");
        properties.getMorningBriefing().setIndexSymbols("usIXIC");
        properties.getMorningBriefing().setAsiaIndexSymbols("");
        properties.getMorningBriefing().setStarSymbols("usAMD");
        properties.getMorningBriefing().setTechnologyGiantsSymbols("usAMD");
        properties.getMorningBriefing().setAiChipSymbols("usAMD");
        properties.getMorningBriefing().setStorageSymbols("usAMD");
        properties.getMorningBriefing().setWaferManufacturingSymbols("usAMD");
        properties.getMorningBriefing().setSemiconductorEquipmentSymbols("usAMD");
        properties.getMorningBriefing().setEdaIpSymbols("usAMD");
        properties.getMorningBriefing().setAnalogAutomotiveChipSymbols("usAMD");
        properties.getMorningBriefing().setChinaConceptSymbols("usAMD");
        when(usMarketQuoteClient.fetch(anyList())).thenReturn(List.of(
                quote("usIXIC", "纳斯达克", "0.81"),
                quote("usAMD", "AMD", "3.20")
        ));

        MorningBriefingResp response = service.generate();

        assertEquals("GREEN", response.getDataLevel());
    }

    @Test
    void marksReportYellowWhenNoQuoteSymbolsAreConfigured() {
        properties.getMorningBriefing().setSymbols("");
        properties.getMorningBriefing().setIndexSymbols("");
        properties.getMorningBriefing().setStarSymbols("");
        properties.getMorningBriefing().setTechnologyGiantsSymbols("");
        properties.getMorningBriefing().setAiChipSymbols("");
        properties.getMorningBriefing().setStorageSymbols("");
        properties.getMorningBriefing().setWaferManufacturingSymbols("");
        properties.getMorningBriefing().setSemiconductorEquipmentSymbols("");
        properties.getMorningBriefing().setEdaIpSymbols("");
        properties.getMorningBriefing().setAnalogAutomotiveChipSymbols("");
        properties.getMorningBriefing().setChinaConceptSymbols("");
        when(usMarketQuoteClient.fetch(anyList())).thenReturn(List.of());

        MorningBriefingResp response = service.generate();

        assertEquals("YELLOW", response.getDataLevel());
    }

    @Test
    void latestUsesCachedMorningBriefingWithoutExternalRequests() {
        MorningBriefingResp cached = MorningBriefingResp.builder()
                .generatedAt(LocalDateTime.of(2026, 8, 18, 6, 35))
                .dataLevel("GREEN")
                .build();
        when(redisCacheService.get("apex:morning-briefing:latest", MorningBriefingResp.class))
                .thenReturn(cached);

        MorningBriefingResp response = service.latest();

        assertSame(cached, response);
        verify(usMarketQuoteClient, never()).fetch(anyList());
        verify(newsPulseService, never()).pulse(6, true);
    }

    private OvernightMarketQuote quote(String symbol, String name, String pctChg) {
        return OvernightMarketQuote.builder()
                .symbol(symbol)
                .name(name)
                .pctChg(new BigDecimal(pctChg))
                .quoteTime(LocalDateTime.of(2026, 8, 18, 4, 0))
                .build();
    }
}
