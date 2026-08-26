package com.awe.apex.quant.job;

import com.awe.apex.quant.bot.config.ApexBotProperties;
import com.awe.apex.quant.bot.service.IBotNotificationService;
import com.awe.apex.quant.domain.dto.MorningBriefingResp;
import com.awe.apex.quant.domain.dto.DailyPreMarketReportResp;
import com.awe.apex.quant.domain.dto.NewsRefreshResp;
import com.awe.apex.quant.service.IDailyPreMarketReportService;
import com.awe.apex.quant.service.IMorningBriefingService;
import com.awe.apex.quant.service.IMarketOpinionService;
import com.awe.apex.quant.service.INewsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MorningBriefingSchedulerTest {

    private final ApexBotProperties properties = new ApexBotProperties();
    private final INewsService newsService = mock(INewsService.class);
    private final IMorningBriefingService morningBriefingService = mock(IMorningBriefingService.class);
    private final IDailyPreMarketReportService dailyPreMarketReportService = mock(IDailyPreMarketReportService.class);
    private final IMarketOpinionService marketOpinionService = mock(IMarketOpinionService.class);
    private final IBotNotificationService notificationService = mock(IBotNotificationService.class);
    private final MorningBriefingScheduler scheduler = new MorningBriefingScheduler();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "properties", properties);
        ReflectionTestUtils.setField(scheduler, "newsService", newsService);
        ReflectionTestUtils.setField(scheduler, "morningBriefingService", morningBriefingService);
        ReflectionTestUtils.setField(scheduler, "dailyPreMarketReportService", dailyPreMarketReportService);
        ReflectionTestUtils.setField(scheduler, "marketOpinionService", marketOpinionService);
        ReflectionTestUtils.setField(scheduler, "notificationService", notificationService);
        when(newsService.refresh("eastmoney,cls,ths,sina", 80))
                .thenReturn(NewsRefreshResp.builder().message("完成").build());
        when(morningBriefingService.generate()).thenReturn(MorningBriefingResp.builder()
                .dataLevel("GREEN")
                .marketQuotes(List.of())
                .newsTitles(List.of())
                .build());
    }

    @Test
    void generatesWebBriefingWhenBotNotificationIsDisabled() {
        properties.getMorningBriefing().setEnabled(false);

        scheduler.generateMorningBriefing();

        verify(newsService).refresh("eastmoney,cls,ths,sina", 80);
        verify(marketOpinionService).refresh();
        verify(morningBriefingService).generate();
        verify(notificationService, never()).notifyMorningBriefing(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void notifiesBotWhenMorningBriefingNotificationIsEnabled() {
        properties.getMorningBriefing().setEnabled(true);

        scheduler.generateMorningBriefing();

        verify(notificationService).notifyMorningBriefing(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void generatesUserScopedReportWhenBotUserIsConfigured() {
        properties.setApexUserId(7L);
        properties.getMorningBriefing().setEnabled(true);
        DailyPreMarketReportResp report = DailyPreMarketReportResp.builder()
                .content("Apex 每日盘前研报")
                .build();
        when(dailyPreMarketReportService.generateForUser(7L)).thenReturn(report);

        scheduler.generateMorningBriefing();

        verify(dailyPreMarketReportService).generateForUser(7L);
        verify(notificationService).notifyDailyPreMarketReport(report);
        verify(notificationService, never()).notifyMorningBriefing(org.mockito.ArgumentMatchers.any());
    }
}
