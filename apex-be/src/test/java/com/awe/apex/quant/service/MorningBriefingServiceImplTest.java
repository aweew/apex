package com.awe.apex.quant.service;

import com.awe.apex.quant.bot.config.ApexBotProperties;
import com.awe.apex.quant.cache.RedisCacheService;
import com.awe.apex.quant.domain.dto.MorningBriefingResp;
import com.awe.apex.quant.domain.dto.ExternalMarketItemResp;
import com.awe.apex.quant.domain.dto.NewsPulseCardResp;
import com.awe.apex.quant.domain.dto.NewsPulseResp;
import com.awe.apex.quant.domain.dto.OvernightMarketQuote;
import com.awe.apex.quant.domain.dto.GlobalMarketIntradayResp;
import com.awe.apex.quant.domain.dto.IntradayKlineBar;
import com.awe.apex.quant.market.GlobalMarketIntradayClient;
import com.awe.apex.quant.market.TradingCalendar;
import com.awe.apex.quant.market.GlobalFuturesQuoteClient;
import com.awe.apex.quant.market.ExternalMarketIndicatorEnum;
import com.awe.apex.quant.market.ExternalMarketQuoteClient;
import com.awe.apex.quant.market.UsMarketQuoteClient;
import com.awe.apex.quant.service.impl.MorningBriefingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private GlobalFuturesQuoteClient globalFuturesQuoteClient;
    private GlobalMarketIntradayClient globalMarketIntradayClient;
    private ExternalMarketQuoteClient externalMarketQuoteClient;
    private INewsPulseService newsPulseService;
    private IMarketOpinionService marketOpinionService;
    private RedisCacheService redisCacheService;
    private ApexBotProperties properties;

    @BeforeEach
    void setUp() {
        service = new MorningBriefingServiceImpl();
        usMarketQuoteClient = mock(UsMarketQuoteClient.class);
        globalFuturesQuoteClient = mock(GlobalFuturesQuoteClient.class);
        globalMarketIntradayClient = mock(GlobalMarketIntradayClient.class);
        externalMarketQuoteClient = mock(ExternalMarketQuoteClient.class);
        newsPulseService = mock(INewsPulseService.class);
        marketOpinionService = mock(IMarketOpinionService.class);
        redisCacheService = mock(RedisCacheService.class);
        properties = new ApexBotProperties();
        ReflectionTestUtils.setField(service, "properties", properties);
        ReflectionTestUtils.setField(service, "marketQuoteClient", usMarketQuoteClient);
        ReflectionTestUtils.setField(service, "globalFuturesQuoteClient", globalFuturesQuoteClient);
        ReflectionTestUtils.setField(service, "globalMarketIntradayClient", globalMarketIntradayClient);
        ReflectionTestUtils.setField(service, "externalMarketQuoteClient", externalMarketQuoteClient);
        ReflectionTestUtils.setField(service, "newsPulseService", newsPulseService);
        ReflectionTestUtils.setField(service, "marketOpinionService", marketOpinionService);
        ReflectionTestUtils.setField(service, "redisCacheService", redisCacheService);
        when(externalMarketQuoteClient.fetch()).thenReturn(completeExternalMarketItems());
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
                "usHXC",
                "hkHSI", "hkHSTECH",
                "usMSFT", "usAAPL", "usAMZN", "usGOOG", "usMETA", "usTSLA", "usSPCX",
                "usNVDA", "usAVGO", "usARM", "usMRVL", "usMU",
                "usSNDK", "usSKHY", "usAMD", "usWDC", "usSTX",
                "usTSM", "usGFS", "usASML", "usAMAT", "usLRCX", "usKLAC",
                "usSNPS", "usCDNS", "usQCOM", "usINTC", "usTXN", "usADI",
                "usNXPI", "usON", "usBABA", "usPDD", "usJD", "usBIDU",
                "usNTES", "usTCOM", "usNIO", "usLI", "usXPEV", "usBILI", "usFUTU", "usTME"
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
    void attachesIntradayBarsToUsIndexesAndFtseA50() {
        OvernightMarketQuote nasdaqQuote = quote("usIXIC", "纳斯达克", "-0.52");
        OvernightMarketQuote ftseA50Quote = quote("hf_CHA50CFD", "富时中国A50期货", "0.13");
        IntradayKlineBar intradayBar = IntradayKlineBar.builder()
                .datetime("2026-08-31 21:30")
                .openPrice(new BigDecimal("100"))
                .closePrice(new BigDecimal("101"))
                .highPrice(new BigDecimal("102"))
                .lowPrice(new BigDecimal("99"))
                .build();
        when(usMarketQuoteClient.fetch(anyList())).thenReturn(List.of(nasdaqQuote));
        when(globalFuturesQuoteClient.fetch("hf_CHA50CFD")).thenReturn(ftseA50Quote);
        when(globalMarketIntradayClient.fetch("usIXIC")).thenReturn(GlobalMarketIntradayResp.builder()
                .previousClose(new BigDecimal("102"))
                .bars(List.of(intradayBar))
                .build());
        when(globalMarketIntradayClient.fetch("hf_CHA50CFD")).thenReturn(GlobalMarketIntradayResp.builder()
                .previousClose(new BigDecimal("98"))
                .bars(List.of(intradayBar))
                .build());

        MorningBriefingResp response = service.generate();

        assertEquals(new BigDecimal("102"), response.getIndexQuotes().get(0).getPreviousClose());
        assertEquals(List.of(intradayBar), response.getIndexQuotes().get(0).getIntradayBars());
        assertEquals(new BigDecimal("98"), response.getFtseA50Future().getPreviousClose());
        assertEquals(List.of(intradayBar), response.getFtseA50Future().getIntradayBars());
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
        verify(redisCacheService).put(eq("apex:morning-briefing:latest:v7"), eq(response), any());
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
    void groupsChinaGoldenDragonAndChinaConceptStocksSeparately() {
        when(usMarketQuoteClient.fetch(anyList())).thenReturn(List.of(
                quote("usHXC", "纳斯达克中国金龙指数", "-0.74"),
                quote("usBABA", "阿里巴巴", "-2.94"),
                quote("usPDD", "拼多多", "-2.36"),
                quote("usJD", "京东", "1.20")
        ));

        MorningBriefingResp response = service.generate();

        assertEquals("usHXC", response.getChinaGoldenDragon().getSymbol());
        assertEquals(List.of("usBABA", "usPDD", "usJD"), response.getChinaConceptQuotes().stream()
                .map(OvernightMarketQuote::getSymbol)
                .toList());
        assertTrue(response.getSummary().contains("中概风向：纳斯达克中国金龙指数 -0.74%"));
        assertTrue(response.getSummary().contains("代表股中位数 -2.36%"));
    }

    @Test
    void includesExternalMarketEnvironmentAndMarksMissingItemsAsIncomplete() {
        when(usMarketQuoteClient.fetch(anyList())).thenReturn(List.of(quote("usIXIC", "纳斯达克", "0.81")));
        when(externalMarketQuoteClient.fetch()).thenReturn(List.of(
                externalItem(ExternalMarketIndicatorEnum.GOLD, "0.60"),
                externalItem(ExternalMarketIndicatorEnum.CRUDE_OIL, "1.20")
        ));

        MorningBriefingResp response = service.generate();

        assertEquals(5, response.getExternalMarketItems().size());
        assertTrue(response.getExternalMarketItems().get(0).isAvailable());
        assertFalse(response.getExternalMarketItems().get(2).isAvailable());
        assertTrue(response.getSummary().contains("外围环境：黄金 +0.60%；原油 +1.20%；其余指标暂未获取。"));
        assertEquals("YELLOW", response.getDataLevel());
    }

    @Test
    void returnsLastBriefingAsRefreshingAfterCacheInvalidation() {
        MorningBriefingResp cached = MorningBriefingResp.builder()
                .generatedAt(LocalDateTime.of(2026, 8, 18, 6, 35))
                .dataLevel("GREEN")
                .build();
        ReflectionTestUtils.setField(service, "cachedBriefing", cached);
        ReflectionTestUtils.setField(service, "cachedAtMs", System.currentTimeMillis());

        service.invalidateCache();
        MorningBriefingResp response = service.latest();

        assertTrue(response.isStale());
        assertTrue(response.isRefreshing());
        assertEquals("YELLOW", response.getDataLevel());
        assertSame(cached.getGeneratedAt(), response.getGeneratedAt());
    }

    @Test
    void includesFtseA50FutureAsAnAsharesPreMarketReference() {
        properties.getMorningBriefing().setFtseA50FutureSymbol("hf_CHA50CFD");
        when(usMarketQuoteClient.fetch(anyList())).thenReturn(List.of());
        when(globalFuturesQuoteClient.fetch("hf_CHA50CFD"))
                .thenReturn(quote("hf_CHA50CFD", "富时 A50 期指连续", "0.65"));

        MorningBriefingResp response = service.generate();

        assertEquals("hf_CHA50CFD", response.getFtseA50Future().getSymbol());
        assertTrue(response.getSummary().contains("A股盘前：富时 A50 期指连续 +0.65%"));
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
        properties.getMorningBriefing().setChinaGoldenDragonSymbol("");
        properties.getMorningBriefing().setFtseA50FutureSymbol("");
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
        when(redisCacheService.get("apex:morning-briefing:latest:v7", MorningBriefingResp.class))
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

    private List<ExternalMarketItemResp> completeExternalMarketItems() {
        List<ExternalMarketItemResp> items = new ArrayList<>();
        for (ExternalMarketIndicatorEnum indicator : ExternalMarketIndicatorEnum.values()) {
            items.add(externalItem(indicator, "0.10"));
        }
        return items;
    }

    private ExternalMarketItemResp externalItem(ExternalMarketIndicatorEnum indicator, String pctChg) {
        return ExternalMarketItemResp.builder()
                .code(indicator.getCode())
                .name(indicator.getDesc())
                .pctChg(new BigDecimal(pctChg))
                .available(true)
                .aShareImpact(indicator.getAShareImpact())
                .build();
    }
}
