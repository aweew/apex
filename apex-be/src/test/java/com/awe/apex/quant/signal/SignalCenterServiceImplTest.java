package com.awe.apex.quant.signal;

import com.awe.apex.quant.domain.dto.ExternalMarketItemResp;
import com.awe.apex.quant.domain.dto.MarketBriefingResp;
import com.awe.apex.quant.domain.dto.MarketIndexItem;
import com.awe.apex.quant.market.ExternalMarketIndicatorEnum;
import com.awe.apex.quant.market.ExternalMarketQuoteClient;
import com.awe.apex.quant.service.IMarketBriefingService;
import com.awe.apex.quant.signal.event.MarketBehaviorDetector;
import com.awe.apex.quant.signal.mapper.SignalCenterMapper;
import com.awe.apex.quant.signal.query.SignalCenterServiceImpl;
import com.awe.apex.quant.signal.query.ShortTermSignalResp;
import com.awe.apex.quant.signal.query.ShortTermVixResp;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 短线信号中心聚合服务测试。
 */
class SignalCenterServiceImplTest {

    @Test
    void buildsShortTermContextWithCompleteFields() {
        SignalCenterMapper mapper = mock(SignalCenterMapper.class);
        IMarketBriefingService briefingService = mock(IMarketBriefingService.class);
        ExternalMarketQuoteClient quoteClient = mock(ExternalMarketQuoteClient.class);
        SignalCenterServiceImpl service = service(mapper, briefingService, quoteClient);
        LocalDate dataAsOf = LocalDate.of(2026, 8, 25);
        when(briefingService.briefing()).thenReturn(briefing(dataAsOf, 72));
        when(quoteClient.fetch()).thenReturn(completeMacroItems());
        when(quoteClient.fetchVixProxy()).thenReturn(availableVix("18.0"));
        when(mapper.selectUniverseItems()).thenReturn(List.of());
        when(mapper.selectShortTermBars(dataAsOf, 80)).thenReturn(List.of());

        ShortTermSignalResp response = service.shortTerm();

        assertEquals(dataAsOf, response.getDataAsOf());
        assertEquals(72, response.getAshareEmotion().getScore());
        assertEquals("贪婪", response.getAshareEmotion().getLabel());
        assertEquals(new BigDecimal("18.0"), response.getExternalEmotion().getValue());
        assertEquals(73, response.getExternalEmotion().getScore());
        assertEquals("贪婪", response.getExternalEmotion().getLabel());
        assertEquals("COMPLETE", response.getDataStatus());
        assertEquals("进攻", response.getStrategy().getState());
        assertEquals(new BigDecimal("0.00"), response.getResistanceDistancePct());
    }

    @Test
    void keepsLocalContextWhenMacroOrVixIsMissing() {
        SignalCenterMapper mapper = mock(SignalCenterMapper.class);
        IMarketBriefingService briefingService = mock(IMarketBriefingService.class);
        ExternalMarketQuoteClient quoteClient = mock(ExternalMarketQuoteClient.class);
        SignalCenterServiceImpl service = service(mapper, briefingService, quoteClient);
        LocalDate dataAsOf = LocalDate.of(2026, 8, 25);
        when(briefingService.briefing()).thenReturn(briefing(dataAsOf, 50));
        when(quoteClient.fetch()).thenReturn(List.of(unavailable(ExternalMarketIndicatorEnum.GOLD)));
        when(quoteClient.fetchVixProxy()).thenReturn(unavailable(ExternalMarketIndicatorEnum.VIX));
        when(mapper.selectUniverseItems()).thenReturn(List.of());
        when(mapper.selectShortTermBars(dataAsOf, 80)).thenReturn(List.of());

        ShortTermSignalResp response = service.shortTerm();

        assertEquals("中性", response.getAshareEmotion().getLabel());
        assertEquals("PARTIAL", response.getDataStatus());
        assertTrue(response.getMissingData().contains("部分外围宏观指标"));
        assertTrue(response.getMissingData().contains("VIX外部情绪代理"));
        assertFalse(response.getExternalEmotion().isAvailable());
        verify(mapper, never()).insertEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void mapsVixProxyBoundaryValues() {
        SignalCenterServiceImpl service = service(mock(SignalCenterMapper.class),
                mock(IMarketBriefingService.class), mock(ExternalMarketQuoteClient.class));

        ShortTermVixResp panic = ReflectionTestUtils.invokeMethod(service, "buildVixEmotion",
                availableVix("29.5"), new java.util.ArrayList<String>());
        ShortTermVixResp greed = ReflectionTestUtils.invokeMethod(service, "buildVixEmotion",
                availableVix("19.5"), new java.util.ArrayList<String>());

        assertEquals(35, panic.getScore());
        assertEquals("恐慌", panic.getLabel());
        assertEquals(68, greed.getScore());
        assertEquals("贪婪", greed.getLabel());
    }

    private SignalCenterServiceImpl service(SignalCenterMapper mapper,
                                             IMarketBriefingService briefingService,
                                             ExternalMarketQuoteClient quoteClient) {
        SignalCenterServiceImpl service = new SignalCenterServiceImpl();
        ReflectionTestUtils.setField(service, "signalCenterMapper", mapper);
        ReflectionTestUtils.setField(service, "marketBriefingService", briefingService);
        ReflectionTestUtils.setField(service, "externalMarketQuoteClient", quoteClient);
        ReflectionTestUtils.setField(service, "marketBehaviorDetector", new MarketBehaviorDetector());
        return service;
    }

    private MarketBriefingResp briefing(LocalDate dataAsOf, int stanceScore) {
        return MarketBriefingResp.builder()
                .asOf(dataAsOf)
                .stanceScore(stanceScore)
                .stanceReason("指数与量能保持配合")
                .indexes(List.of(
                        MarketIndexItem.builder().name("上证指数").close(new BigDecimal("3270")).build(),
                        MarketIndexItem.builder().name("深证成指").close(new BigDecimal("10000")).build()))
                .shanghaiKeyResistance(new BigDecimal("3270"))
                .indexVolume(new BigDecimal("900000000000"))
                .indexVolumeText("9000.00亿")
                .volumeTrend("放量")
                .volumeVsMa5Pct(new BigDecimal("6.20"))
                .breadthUp(2500)
                .breadthDown(1800)
                .breadthFlat(300)
                .build();
    }

    private List<ExternalMarketItemResp> completeMacroItems() {
        return List.of(
                available(ExternalMarketIndicatorEnum.GOLD),
                available(ExternalMarketIndicatorEnum.CRUDE_OIL),
                available(ExternalMarketIndicatorEnum.DOLLAR_INDEX),
                available(ExternalMarketIndicatorEnum.OFFSHORE_RENMINBI),
                available(ExternalMarketIndicatorEnum.US_TREASURY_10Y));
    }

    private ExternalMarketItemResp available(ExternalMarketIndicatorEnum indicator) {
        return available(indicator, "0.10");
    }

    private ExternalMarketItemResp available(ExternalMarketIndicatorEnum indicator, String price) {
        return ExternalMarketItemResp.builder()
                .code(indicator.getCode())
                .name(indicator.getDesc())
                .available(true)
                .latestPrice(new BigDecimal(price))
                .quoteTime(LocalDateTime.of(2026, 8, 25, 11, 30))
                .source("test")
                .build();
    }

    private ExternalMarketItemResp unavailable(ExternalMarketIndicatorEnum indicator) {
        return ExternalMarketItemResp.builder()
                .code(indicator.getCode())
                .name(indicator.getDesc())
                .available(false)
                .build();
    }

    private ExternalMarketItemResp availableVix(String price) {
        return available(ExternalMarketIndicatorEnum.VIX, price);
    }
}
