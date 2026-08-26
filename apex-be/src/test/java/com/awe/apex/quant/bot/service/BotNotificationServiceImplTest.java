package com.awe.apex.quant.bot.service;

import com.awe.apex.quant.bot.client.WeClawMessageClient;
import com.awe.apex.quant.bot.config.ApexBotProperties;
import com.awe.apex.quant.bot.service.impl.BotNotificationServiceImpl;
import com.awe.apex.quant.domain.dto.DecisionTodayResp;
import com.awe.apex.quant.domain.dto.DailyPreMarketReportResp;
import com.awe.apex.quant.domain.dto.MorningBriefingResp;
import com.awe.apex.quant.domain.dto.WatchlistMoverResp;
import com.awe.apex.quant.domain.dto.WatchlistResp;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class BotNotificationServiceImplTest {

    @Test
    void deduplicatesSameDecisionRun() {
        ApexBotProperties properties = new ApexBotProperties();
        properties.setNotificationCooldownSeconds(1800);
        WeClawMessageClient messageClient = mock(WeClawMessageClient.class);
        when(messageClient.sendText(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);

        BotNotificationServiceImpl service = new BotNotificationServiceImpl();
        ReflectionTestUtils.setField(service, "properties", properties);
        ReflectionTestUtils.setField(service, "messageClient", messageClient);
        DecisionTodayResp decision = DecisionTodayResp.builder()
                .runNo("RUN-20260813-1").asOfTime(LocalDateTime.of(2026, 8, 13, 16, 12))
                .buyCount(2).sellCount(1).holdCount(3).message("决策完成").build();

        service.notifyDecision(decision);
        service.notifyDecision(decision);

        verify(messageClient).sendText(org.mockito.ArgumentMatchers.contains("买入 2"));
        verifyNoMoreInteractions(messageClient);
    }

    @Test
    void sendsOnlyNewMarketEventsDuringCooldown() {
        ApexBotProperties properties = new ApexBotProperties();
        properties.setNotificationCooldownSeconds(1800);
        WeClawMessageClient messageClient = mock(WeClawMessageClient.class);
        when(messageClient.sendText(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        BotNotificationServiceImpl service = new BotNotificationServiceImpl();
        ReflectionTestUtils.setField(service, "properties", properties);
        ReflectionTestUtils.setField(service, "messageClient", messageClient);
        WatchlistResp first = WatchlistResp.builder()
                .code("600000").name("浦发银行").latestPrice(new BigDecimal("10.00"))
                .pctChg(new BigDecimal("5.20")).build();
        WatchlistResp second = WatchlistResp.builder()
                .code("300750").name("宁德时代").latestPrice(new BigDecimal("260.00"))
                .pctChg(new BigDecimal("6.10")).build();

        service.notifyMarketAlerts(WatchlistMoverResp.builder().gainers(List.of(first)).build(),
                List.of(), null);
        service.notifyMarketAlerts(WatchlistMoverResp.builder().gainers(List.of(first, second)).build(),
                List.of(), null);

        ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
        verify(messageClient, org.mockito.Mockito.times(2)).sendText(messages.capture());
        assertTrue(messages.getAllValues().get(0).contains("浦发银行"));
        assertTrue(messages.getAllValues().get(1).contains("宁德时代"));
        assertFalse(messages.getAllValues().get(1).contains("浦发银行"));
    }

    @Test
    void sendsMorningBriefingOnlyOncePerDay() {
        ApexBotProperties properties = new ApexBotProperties();
        properties.setNotificationCooldownSeconds(1800);
        WeClawMessageClient messageClient = mock(WeClawMessageClient.class);
        when(messageClient.sendText(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        BotNotificationServiceImpl service = new BotNotificationServiceImpl();
        ReflectionTestUtils.setField(service, "properties", properties);
        ReflectionTestUtils.setField(service, "messageClient", messageClient);
        MorningBriefingResp briefing = MorningBriefingResp.builder()
                .generatedAt(LocalDateTime.of(2026, 8, 14, 6, 40))
                .summary("隔夜美股：纳斯达克 +0.81%。\n夜间新闻：AI 方向偏暖。")
                .newsTitles(List.of("美联储公布最新经济数据"))
                .dataLevel("GREEN")
                .build();

        service.notifyMorningBriefing(briefing);
        service.notifyMorningBriefing(briefing);

        verify(messageClient).sendText(org.mockito.ArgumentMatchers.contains("纳斯达克 +0.81%"));
        verifyNoMoreInteractions(messageClient);
    }

    @Test
    void sendsDailyPreMarketReportWithExplicitDataGapsOnlyOnce() {
        ApexBotProperties properties = new ApexBotProperties();
        WeClawMessageClient messageClient = mock(WeClawMessageClient.class);
        when(messageClient.sendText(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        BotNotificationServiceImpl service = new BotNotificationServiceImpl();
        ReflectionTestUtils.setField(service, "properties", properties);
        ReflectionTestUtils.setField(service, "messageClient", messageClient);
        DailyPreMarketReportResp report = DailyPreMarketReportResp.builder()
                .tradeDate(LocalDate.of(2026, 8, 26))
                .content("Apex 每日盘前研报\n今日市场判断：中性")
                .missingData(List.of("两融数据"))
                .build();

        service.notifyDailyPreMarketReport(report);
        service.notifyDailyPreMarketReport(report);

        verify(messageClient).sendText(org.mockito.ArgumentMatchers.contains("数据缺口：两融数据"));
        verifyNoMoreInteractions(messageClient);
    }
}
