package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.domain.bo.DashboardCommandContextBO;
import com.awe.apex.quant.domain.dto.DashboardCommandResp;
import com.awe.apex.quant.domain.dto.DecisionItemResp;
import com.awe.apex.quant.domain.dto.DecisionTodayResp;
import com.awe.apex.quant.domain.dto.MarketBriefingResp;
import com.awe.apex.quant.domain.dto.MarketHotThemeItem;
import com.awe.apex.quant.domain.dto.MorningBriefingResp;
import com.awe.apex.quant.domain.dto.NewsPulseResp;
import com.awe.apex.quant.domain.dto.ObservePoolResp;
import com.awe.apex.quant.domain.dto.OvernightMarketQuote;
import com.awe.apex.quant.domain.dto.OperationGuideItemResp;
import com.awe.apex.quant.domain.enums.DashboardCommandPhaseEnum;
import com.awe.apex.quant.domain.enums.DashboardCommandStatusEnum;
import com.awe.apex.quant.domain.enums.OperationGuideCodeEnum;
import com.awe.apex.quant.domain.enums.OperationGuideStatusEnum;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardCommandServiceImplTest {

    private static final LocalDate TRADE_DATE = LocalDate.of(2026, 8, 18);
    private static final LocalDate PREVIOUS_TRADE_DATE = LocalDate.of(2026, 8, 17);

    private final DashboardCommandServiceImpl service = new DashboardCommandServiceImpl();

    @Test
    void shouldKeepAshareStanceAndBuildRiskFirstReadyGuide() {
        NewsPulseResp newsPulse = NewsPulseResp.builder()
                .bullCount(5)
                .bearCount(1)
                .hotThemes(List.of("机器人", "半导体", "消费电子"))
                .build();
        MorningBriefingResp morningBriefing = MorningBriefingResp.builder()
                .tradeDate(TRADE_DATE)
                .summary("隔夜科技股普涨，消息面偏暖")
                .dataLevel("GREEN")
                .newsPulse(newsPulse)
                .build();
        DecisionTodayResp decision = DecisionTodayResp.builder()
                .actionDate(TRADE_DATE)
                .asOfTime(TRADE_DATE.atTime(6, 50))
                .dataAsOf(PREVIOUS_TRADE_DATE)
                .generated(true)
                .sellCount(2)
                .executableCount(3)
                .mainlineMatchCount(2)
                .build();
        MarketBriefingResp marketBriefing = market(PREVIOUS_TRADE_DATE, "GREEN",
                List.of("半导体", "机器人", "算力"));

        DashboardCommandResp command = service.build(DashboardCommandContextBO.builder()
                .currentTime(TRADE_DATE.atTime(8, 10))
                .marketBriefing(marketBriefing)
                .morningBriefing(morningBriefing)
                .decision(decision)
                .observeAlerts(List.of())
                .decisionRunning(false)
                .build());

        assertEquals(DashboardCommandStatusEnum.READY.getCode(), command.getStatus());
        assertEquals(DashboardCommandPhaseEnum.PRE_MARKET.getCode(), command.getPhase());
        assertEquals(TRADE_DATE, command.getTradeDate());
        assertEquals(PREVIOUS_TRADE_DATE, command.getMarketDataAsOf());
        assertEquals(PREVIOUS_TRADE_DATE, command.getDecisionDataAsOf());
        assertEquals("今日操作已生成，请按执行清单处理。",
                command.getPreMarketSummary().getHeadline());
        assertFalse(command.getPreMarketSummary().getHeadline().contains("先处理"));
        assertFalse(command.getPreMarketSummary().getHeadline().contains("市场广度"));
        assertTrue(command.getPreMarketSummary().getOpportunityItems().size() <= 2);
        assertTrue(command.getPreMarketSummary().getRiskItems().size() <= 2);
        assertTrue(command.getPreMarketSummary().getEvidenceItems().size() <= 4);
        assertTrue(command.getPreMarketSummary().getWatchConditions().size() <= 2);
        assertEquals(2, command.getOperationGuide().getItems().size());
        assertGuideItem(command, 0, OperationGuideCodeEnum.RISK_FIRST,
                OperationGuideStatusEnum.REQUIRED, 2);
        assertGuideItem(command, 1, OperationGuideCodeEnum.BUY_CONDITIONALLY,
                OperationGuideStatusEnum.READY, 3);
    }

    @Test
    void shouldBuildConcreteForecastFromOvernightAsiaAndPreviousCloseThemes() {
        DecisionItemResp buyItem = DecisionItemResp.builder()
                .code("601088")
                .name("中国神华")
                .mainlineMatch(true)
                .mainlineName("煤炭开采")
                .executableHint(true)
                .build();
        MarketBriefingResp marketBriefing = market(PREVIOUS_TRADE_DATE, "GREEN", List.of("煤炭开采", "保险Ⅱ"));
        marketBriefing.setHotThemeItems(List.of(
                MarketHotThemeItem.builder().name("煤炭开采").pctChg(new BigDecimal("2.17"))
                        .pctChg3d(new BigDecimal("3.40")).pctChg5d(new BigDecimal("4.12"))
                        .tradeDate(PREVIOUS_TRADE_DATE).build(),
                MarketHotThemeItem.builder().name("保险Ⅱ").pctChg(new BigDecimal("1.23"))
                        .pctChg3d(new BigDecimal("2.11")).pctChg5d(new BigDecimal("2.80"))
                        .tradeDate(PREVIOUS_TRADE_DATE).build(),
                MarketHotThemeItem.builder().name("半导体").pctChg(new BigDecimal("-1.10"))
                        .pctChg3d(new BigDecimal("-2.40")).pctChg5d(new BigDecimal("-3.20"))
                        .tradeDate(PREVIOUS_TRADE_DATE).build()
        ));
        DashboardCommandResp command = service.build(DashboardCommandContextBO.builder()
                .currentTime(TRADE_DATE.atTime(8, 10))
                .marketBriefing(marketBriefing)
                .morningBriefing(MorningBriefingResp.builder()
                        .tradeDate(TRADE_DATE)
                        .dataLevel("GREEN")
                        .indexQuotes(List.of(quote("usIXIC", "纳斯达克", "-0.30")))
                        .asiaQuotes(List.of(quote("hkHSTECH", "恒生科技", "-3.61")))
                        .marketThemes(List.of())
                        .build())
                .decision(DecisionTodayResp.builder()
                        .actionDate(TRADE_DATE)
                        .dataAsOf(PREVIOUS_TRADE_DATE)
                        .generated(true)
                        .buys(List.of(buyItem))
                        .executableCount(1)
                        .build())
                .build());

        assertTrue(command.getPreMarketSummary().getForecast().getMarketOutlook().contains("承压后分化"));
        assertEquals("煤炭开采", command.getPreMarketSummary().getForecast().getFocusItems().get(0).getName());
        assertEquals(List.of("中国神华"),
                command.getPreMarketSummary().getForecast().getFocusItems().get(0).getWatchStocks());
        assertEquals("科技成长", command.getPreMarketSummary().getForecast().getRiskItems().get(0).getName());
        assertTrue(command.getPreMarketSummary().getForecast().getWatchConditions().get(0)
                .getCondition().contains("上涨家数"));
    }

    @Test
    void shouldNotTurnSingleDayThemeMovesIntoFocusOrRiskDirection() {
        MarketBriefingResp marketBriefing = market(PREVIOUS_TRADE_DATE, "GREEN",
                List.of("粮食概念", "科技成长"));
        marketBriefing.setHotThemeItems(List.of(
                MarketHotThemeItem.builder().name("粮食概念").pctChg(new BigDecimal("3.07"))
                        .tradeDate(PREVIOUS_TRADE_DATE).build(),
                MarketHotThemeItem.builder().name("科技成长").pctChg(new BigDecimal("-2.10"))
                        .tradeDate(PREVIOUS_TRADE_DATE).build()
        ));

        DashboardCommandResp command = service.build(DashboardCommandContextBO.builder()
                .currentTime(TRADE_DATE.atTime(8, 10))
                .marketBriefing(marketBriefing)
                .morningBriefing(MorningBriefingResp.builder()
                        .tradeDate(TRADE_DATE)
                        .dataLevel("GREEN")
                        .indexQuotes(List.of(quote("usIXIC", "纳斯达克", "-0.30")))
                        .asiaQuotes(List.of(quote("hkHSTECH", "恒生科技", "-3.61")))
                        .build())
                .decision(DecisionTodayResp.builder()
                        .actionDate(TRADE_DATE)
                        .dataAsOf(PREVIOUS_TRADE_DATE)
                        .generated(true)
                        .build())
                .build());

        assertTrue(command.getPreMarketSummary().getForecast().getFocusItems().isEmpty());
        assertTrue(command.getPreMarketSummary().getForecast().getRiskItems().stream()
                .noneMatch(item -> "科技成长".equals(item.getName())));
        assertTrue(command.getPreMarketSummary().getForecast().getWatchConditions().stream()
                .anyMatch(item -> "方向持续性".equals(item.getTitle())));
        assertTrue(command.getPreMarketSummary().getForecast().getWatchConditions().stream()
                .anyMatch(item -> item.getCondition().contains("近3日转强")));
        assertFalse(command.getPreMarketSummary().getForecast().getMarketOutlook().contains("昨日强势"));
    }

    @Test
    void shouldUseFreshIntradayMarketDataInsteadOfPreviousCloseForecast() {
        MarketBriefingResp marketBriefing = market(TRADE_DATE, "GREEN", List.of("液冷服务器"));
        marketBriefing.setMarketDataUpdatedAt(TRADE_DATE.atTime(13, 42));
        marketBriefing.setHotThemeItems(List.of(
                MarketHotThemeItem.builder().name("液冷服务器").pctChg(new BigDecimal("2.23"))
                        .pctChg3d(new BigDecimal("3.10")).pctChg5d(new BigDecimal("4.20"))
                        .tradeDate(TRADE_DATE).syncedAt(TRADE_DATE.atTime(13, 42)).build()
        ));

        DashboardCommandResp command = service.build(DashboardCommandContextBO.builder()
                .currentTime(TRADE_DATE.atTime(13, 44))
                .marketBriefing(marketBriefing)
                .morningBriefing(MorningBriefingResp.builder()
                        .tradeDate(TRADE_DATE)
                        .dataLevel("GREEN")
                        .build())
                .decision(DecisionTodayResp.builder()
                        .actionDate(TRADE_DATE)
                        .dataAsOf(PREVIOUS_TRADE_DATE)
                        .generated(true)
                        .build())
                .build());

        assertEquals(DashboardCommandStatusEnum.READY.getCode(), command.getStatus());
        assertEquals(TRADE_DATE.atTime(13, 42), command.getMarketDataUpdatedAt());
        assertTrue(command.getPreMarketSummary().getForecast().getMarketOutlook().contains("盘中截至 13:42"));
        assertTrue(command.getPreMarketSummary().getForecast().getMarketOutlook().contains("当前 A 股行情为准"));
        assertFalse(command.getPreMarketSummary().getForecast().getMarketOutlook().contains("收盘结构"));
        assertTrue(command.getPreMarketSummary().getForecast().getFocusItems().get(0)
                .getReason().contains("盘中截至 13:42 涨幅 +2.23%"));
        assertFalse(command.getPreMarketSummary().getForecast().getFocusItems().get(0)
                .getReason().contains("昨日收盘"));
    }

    @Test
    void shouldUseMultiDayTrendInsteadOfLatestSessionAsFocusGate() {
        MarketBriefingResp marketBriefing = market(PREVIOUS_TRADE_DATE, "GREEN", List.of("机器人"));
        marketBriefing.setHotThemeItems(List.of(
                MarketHotThemeItem.builder().name("机器人").pctChg(new BigDecimal("-0.65"))
                        .pctChg3d(new BigDecimal("2.40")).pctChg5d(new BigDecimal("5.10"))
                        .tradeDate(PREVIOUS_TRADE_DATE).build()
        ));

        DashboardCommandResp command = service.build(DashboardCommandContextBO.builder()
                .currentTime(TRADE_DATE.atTime(8, 10))
                .marketBriefing(marketBriefing)
                .morningBriefing(MorningBriefingResp.builder()
                        .tradeDate(TRADE_DATE)
                        .dataLevel("GREEN")
                        .build())
                .decision(DecisionTodayResp.builder()
                        .actionDate(TRADE_DATE)
                        .dataAsOf(PREVIOUS_TRADE_DATE)
                        .generated(true)
                        .build())
                .build());

        assertEquals("机器人", command.getPreMarketSummary().getForecast().getFocusItems().get(0).getName());
        assertTrue(command.getPreMarketSummary().getForecast().getFocusItems().get(0)
                .getReason().contains("趋势延续"));
    }

    @Test
    void shouldIdentifyThreeDayReversalAsNewStrengtheningDirection() {
        MarketBriefingResp marketBriefing = market(PREVIOUS_TRADE_DATE, "GREEN", List.of("低空经济"));
        marketBriefing.setHotThemeItems(List.of(
                MarketHotThemeItem.builder().name("低空经济").pctChg(new BigDecimal("-0.20"))
                        .pctChg3d(new BigDecimal("1.80")).pctChg5d(new BigDecimal("-1.30"))
                        .tradeDate(PREVIOUS_TRADE_DATE).build()
        ));

        DashboardCommandResp command = service.build(DashboardCommandContextBO.builder()
                .currentTime(TRADE_DATE.atTime(8, 10))
                .marketBriefing(marketBriefing)
                .morningBriefing(MorningBriefingResp.builder()
                        .tradeDate(TRADE_DATE)
                        .dataLevel("GREEN")
                        .build())
                .decision(DecisionTodayResp.builder()
                        .actionDate(TRADE_DATE)
                        .dataAsOf(PREVIOUS_TRADE_DATE)
                        .generated(true)
                        .build())
                .build());

        assertEquals("低空经济", command.getPreMarketSummary().getForecast().getFocusItems().get(0).getName());
        assertTrue(command.getPreMarketSummary().getForecast().getFocusItems().get(0)
                .getReason().contains("新转强"));
    }

    @Test
    void shouldNotUseStaleThemeAsIntradayDirection() {
        MarketBriefingResp marketBriefing = market(TRADE_DATE, "GREEN", List.of("液冷服务器"));
        marketBriefing.setMarketDataUpdatedAt(TRADE_DATE.atTime(13, 42));
        marketBriefing.setHotThemeItems(List.of(
                MarketHotThemeItem.builder().name("液冷服务器").pctChg(new BigDecimal("2.23"))
                        .pctChg3d(new BigDecimal("3.10")).pctChg5d(new BigDecimal("4.20"))
                        .tradeDate(PREVIOUS_TRADE_DATE).syncedAt(PREVIOUS_TRADE_DATE.atTime(15, 5)).build(),
                MarketHotThemeItem.builder().name("机器人").pctChg(new BigDecimal("1.50"))
                        .pctChg3d(new BigDecimal("2.80")).pctChg5d(new BigDecimal("3.60"))
                        .tradeDate(TRADE_DATE).syncedAt(TRADE_DATE.atTime(13, 20)).build()
        ));

        DashboardCommandResp command = service.build(DashboardCommandContextBO.builder()
                .currentTime(TRADE_DATE.atTime(13, 44))
                .marketBriefing(marketBriefing)
                .morningBriefing(MorningBriefingResp.builder()
                        .tradeDate(TRADE_DATE)
                        .dataLevel("GREEN")
                        .build())
                .decision(DecisionTodayResp.builder()
                        .actionDate(TRADE_DATE)
                        .dataAsOf(PREVIOUS_TRADE_DATE)
                        .generated(true)
                        .build())
                .build());

        assertTrue(command.getPreMarketSummary().getForecast().getFocusItems().isEmpty());
        assertTrue(command.getPreMarketSummary().getForecast().getWatchConditions().stream()
                .anyMatch(item -> "板块行情".equals(item.getTitle())));
    }

    @Test
    void shouldWaitForRefreshWhenIntradayMarketDataIsExpired() {
        MarketBriefingResp marketBriefing = market(TRADE_DATE, "GREEN", List.of("液冷服务器"));
        marketBriefing.setMarketDataUpdatedAt(TRADE_DATE.atTime(13, 30));

        DashboardCommandResp command = service.build(DashboardCommandContextBO.builder()
                .currentTime(TRADE_DATE.atTime(13, 44))
                .marketBriefing(marketBriefing)
                .morningBriefing(MorningBriefingResp.builder()
                        .tradeDate(TRADE_DATE)
                        .dataLevel("GREEN")
                        .build())
                .decision(DecisionTodayResp.builder()
                        .actionDate(TRADE_DATE)
                        .dataAsOf(PREVIOUS_TRADE_DATE)
                        .generated(true)
                        .build())
                .build());

        assertEquals(DashboardCommandStatusEnum.PARTIAL.getCode(), command.getStatus());
        assertTrue(command.getPreMarketSummary().getWatchConditions().get(0)
                .getCondition().contains("刷新盘中行情"));
    }

    @Test
    void shouldPrioritizeBlockedOverStaleAndGenerating() {
        DashboardCommandResp command = service.build(DashboardCommandContextBO.builder()
                .currentTime(TRADE_DATE.atTime(8, 10))
                .marketBriefing(market(LocalDate.of(2026, 8, 14), "RED", List.of("半导体")))
                .morningBriefing(MorningBriefingResp.builder()
                        .tradeDate(LocalDate.of(2026, 8, 19))
                        .dataLevel("GREEN")
                        .build())
                .decisionRunning(true)
                .build());

        assertEquals(DashboardCommandStatusEnum.BLOCKED.getCode(), command.getStatus());
        assertEquals(1, command.getOperationGuide().getItems().size());
        assertTrue(command.getOperationGuide().getBlockedReason().contains("数据"));
        assertGuideItem(command, 0, OperationGuideCodeEnum.REFRESH_DATA,
                OperationGuideStatusEnum.BLOCKED, 0);
        assertEquals("刷新数据", command.getOperationGuide().getItems().get(0).getTitle());
    }

    @Test
    void shouldBlockWhenMarketBriefingIsMissing() {
        DashboardCommandResp command = service.build(DashboardCommandContextBO.builder()
                .currentTime(TRADE_DATE.atTime(8, 10))
                .decisionRunning(false)
                .build());

        assertEquals(DashboardCommandStatusEnum.BLOCKED.getCode(), command.getStatus());
        assertGuideItem(command, 0, OperationGuideCodeEnum.REFRESH_DATA,
                OperationGuideStatusEnum.BLOCKED, 0);
    }

    @Test
    void shouldPrioritizeStaleOverGenerating() {
        DashboardCommandResp command = service.build(DashboardCommandContextBO.builder()
                .currentTime(TRADE_DATE.atTime(8, 10))
                .marketBriefing(market(LocalDate.of(2026, 8, 14), "GREEN", List.of("半导体")))
                .morningBriefing(MorningBriefingResp.builder()
                        .tradeDate(TRADE_DATE)
                        .dataLevel("GREEN")
                        .build())
                .decisionRunning(true)
                .build());

        assertEquals(DashboardCommandStatusEnum.STALE.getCode(), command.getStatus());
        assertGuideItem(command, 0, OperationGuideCodeEnum.REFRESH_DATA,
                OperationGuideStatusEnum.BLOCKED, 0);
    }

    @Test
    void shouldTreatMismatchedMorningTradeDateAsStale() {
        DashboardCommandResp command = service.build(DashboardCommandContextBO.builder()
                .currentTime(TRADE_DATE.atTime(8, 10))
                .marketBriefing(readyMarket())
                .morningBriefing(MorningBriefingResp.builder()
                        .tradeDate(LocalDate.of(2026, 8, 19))
                        .dataLevel("GREEN")
                        .build())
                .decisionRunning(false)
                .build());

        assertEquals(DashboardCommandStatusEnum.STALE.getCode(), command.getStatus());
    }

    @Test
    void shouldReportGeneratingBeforePartial() {
        DashboardCommandResp command = service.build(DashboardCommandContextBO.builder()
                .currentTime(TRADE_DATE.atTime(8, 10))
                .marketBriefing(readyMarket())
                .decision(DecisionTodayResp.builder()
                        .actionDate(TRADE_DATE)
                        .asOfTime(TRADE_DATE.atTime(6, 50))
                        .dataAsOf(PREVIOUS_TRADE_DATE)
                        .generated(true)
                        .sellCount(0)
                        .executableCount(2)
                        .build())
                .decisionRunning(true)
                .build());

        assertEquals(DashboardCommandStatusEnum.GENERATING.getCode(), command.getStatus());
        assertGuideItem(command, 0, OperationGuideCodeEnum.BUY_CONDITIONALLY,
                OperationGuideStatusEnum.WAIT, 2);
    }

    @Test
    void shouldReturnPartialWhenMorningBriefingAndDecisionAreMissing() {
        DashboardCommandResp command = service.build(DashboardCommandContextBO.builder()
                .currentTime(TRADE_DATE.atTime(8, 10))
                .marketBriefing(readyMarket())
                .decisionRunning(false)
                .build());

        assertEquals(DashboardCommandStatusEnum.PARTIAL.getCode(), command.getStatus());
        assertGuideItem(command, 0, OperationGuideCodeEnum.BUY_CONDITIONALLY,
                OperationGuideStatusEnum.WAIT, 0);
        assertTrue(command.getPreMarketSummary().getHeadline().contains("目标交易日决策尚未生成"));
        assertTrue(command.getPreMarketSummary().getHeadline().contains(TRADE_DATE.toString()));
        assertFalse(command.getPreMarketSummary().getHeadline().contains("..."));
        assertFalse(command.getPreMarketSummary().getHeadline().contains("A股"));
        assertTrue(command.getPreMarketSummary().getEvidenceItems().isEmpty());
        assertTrue(command.getPreMarketSummary().getOpportunityItems().isEmpty());
        assertEquals(1, command.getPreMarketSummary().getWatchConditions().size());
        assertTrue(command.getOperationGuide().getSummary().contains("重算决策"));
    }

    @Test
    void shouldUseUserActionsInsteadOfBroadMarketAsSummaryEvidence() {
        DecisionItemResp buyItem = DecisionItemResp.builder()
                .code("300750")
                .name("宁德时代")
                .action("BUY")
                .score(new BigDecimal("88"))
                .suggestedWeight(new BigDecimal("0.08"))
                .referencePrice(new BigDecimal("180.50"))
                .stopLossPrice(new BigDecimal("168.00"))
                .takeProfitPrice(new BigDecimal("210.00"))
                .mainlineMatch(true)
                .mainlineName("储能")
                .executableHint(true)
                .entryGatePassed(true)
                .build();
        DecisionItemResp sellItem = DecisionItemResp.builder()
                .code("600000")
                .name("浦发银行")
                .action("SELL")
                .exitRule("跌破止损价 9.80 元")
                .build();
        DecisionTodayResp decision = DecisionTodayResp.builder()
                .actionDate(TRADE_DATE)
                .asOfTime(TRADE_DATE.atTime(6, 50))
                .dataAsOf(PREVIOUS_TRADE_DATE)
                .generated(true)
                .buys(List.of(buyItem))
                .sells(List.of(sellItem))
                .sellCount(1)
                .executableCount(1)
                .mainlineMatchCount(1)
                .build();

        DashboardCommandResp command = service.build(DashboardCommandContextBO.builder()
                .currentTime(TRADE_DATE.atTime(8, 10))
                .marketBriefing(readyMarket())
                .morningBriefing(MorningBriefingResp.builder()
                        .tradeDate(TRADE_DATE)
                        .summary("隔夜科技股普涨")
                        .dataLevel("GREEN")
                        .build())
                .decision(decision)
                .observeAlerts(List.of())
                .build());

        assertEquals(DashboardCommandStatusEnum.READY.getCode(), command.getStatus());
        assertEquals("今日操作已生成，请按执行清单处理。",
                command.getPreMarketSummary().getHeadline());
        assertFalse(command.getPreMarketSummary().getHeadline().contains("市场广度"));
        assertTrue(command.getPreMarketSummary().getEvidenceItems().isEmpty());
        assertEquals("宁德时代", command.getPreMarketSummary().getOpportunityItems().get(0).getName());
        assertEquals("买至8%仓位；参考180.5；止损168；止盈210",
                command.getPreMarketSummary().getOpportunityItems().get(0).getReason());
        assertEquals("浦发银行", command.getPreMarketSummary().getRiskItems().get(0).getName());
        assertEquals("清仓；跌破止损价 9.80 元",
                command.getPreMarketSummary().getRiskItems().get(0).getReason());
        assertTrue(command.getPreMarketSummary().getWatchConditions().get(0).getCondition().contains("跌破止损价"));
        assertEquals(2, command.getOperationGuide().getItems().size());
        assertTrue(command.getOperationGuide().getItems().get(0).getActionText().contains("清仓浦发银行"));
        assertTrue(command.getOperationGuide().getItems().get(1).getActionText().contains("买宁德时代至8%仓位"));
        assertTrue(command.getOperationGuide().getItems().get(1).getConditionText().contains("参考180.5"));
    }

    @Test
    void shouldKeepOnlyConcreteRiskActionWhenNoExecutableBuyExists() {
        DecisionItemResp firstSell = DecisionItemResp.builder()
                .code("603986")
                .name("立昂微")
                .action("SELL")
                .exitRule("RSI>70或跌破MA60离场；止损70.38/止盈91.80")
                .build();
        DecisionItemResp secondSell = DecisionItemResp.builder()
                .code("002335")
                .name("科华数据")
                .action("REDUCE")
                .suggestedWeight(new BigDecimal("0.05"))
                .exitRule("跌破MA20离场；止损27.35/止盈35.67")
                .build();
        DecisionItemResp thirdSell = DecisionItemResp.builder()
                .code("600000")
                .name("浦发银行")
                .action("SELL")
                .build();
        DecisionTodayResp decision = DecisionTodayResp.builder()
                .actionDate(TRADE_DATE)
                .asOfTime(TRADE_DATE.atTime(6, 50))
                .dataAsOf(PREVIOUS_TRADE_DATE)
                .generated(true)
                .buys(List.of())
                .sells(List.of(firstSell, secondSell, thirdSell))
                .sellCount(3)
                .executableCount(0)
                .build();

        DashboardCommandResp command = service.build(DashboardCommandContextBO.builder()
                .currentTime(TRADE_DATE.atTime(8, 10))
                .marketBriefing(readyMarket())
                .morningBriefing(MorningBriefingResp.builder()
                        .tradeDate(TRADE_DATE)
                        .dataLevel("GREEN")
                        .build())
                .decision(decision)
                .observeAlerts(List.of())
                .build());

        assertEquals("今日有3项卖出/减仓，按清单处理。",
                command.getPreMarketSummary().getHeadline());
        assertTrue(command.getPreMarketSummary().getWatchConditions().isEmpty());
        assertEquals("先处理3项卖出/减仓。",
                command.getOperationGuide().getSummary());
        assertEquals(1, command.getOperationGuide().getItems().size());
        assertGuideItem(command, 0, OperationGuideCodeEnum.RISK_FIRST,
                OperationGuideStatusEnum.REQUIRED, 3);
        assertTrue(command.getOperationGuide().getItems().get(0).getActionText().contains("清仓立昂微"));
        assertTrue(command.getOperationGuide().getItems().get(0).getActionText().contains("科华数据减至5%"));
    }

    @Test
    void shouldUseNextTradeDateAndNonTradingPhaseOnWeekend() {
        LocalDate saturday = LocalDate.of(2026, 8, 22);
        DashboardCommandResp command = service.build(DashboardCommandContextBO.builder()
                .currentTime(saturday.atTime(10, 0))
                .marketBriefing(market(TRADE_DATE.plusDays(3), "GREEN", List.of("半导体")))
                .morningBriefing(MorningBriefingResp.builder()
                        .tradeDate(LocalDate.of(2026, 8, 24))
                        .dataLevel("GREEN")
                        .build())
                .decision(DecisionTodayResp.builder()
                        .actionDate(LocalDate.of(2026, 8, 24))
                        .asOfTime(LocalDate.of(2026, 8, 24).atTime(6, 50))
                        .dataAsOf(LocalDate.of(2026, 8, 21))
                        .generated(true)
                        .sellCount(0)
                        .executableCount(0)
                        .build())
                .build());

        assertEquals(LocalDate.of(2026, 8, 24), command.getTradeDate());
        assertEquals(DashboardCommandPhaseEnum.NON_TRADING_DAY.getCode(), command.getPhase());
    }

    @Test
    void shouldDowngradeSessionWithPreviousCloseMarketDataToPartial() {
        DashboardCommandResp command = service.build(DashboardCommandContextBO.builder()
                .currentTime(TRADE_DATE.atTime(10, 0))
                .marketBriefing(readyMarket())
                .morningBriefing(MorningBriefingResp.builder()
                        .tradeDate(TRADE_DATE)
                        .dataLevel("GREEN")
                        .build())
                .decision(DecisionTodayResp.builder()
                        .actionDate(TRADE_DATE)
                        .asOfTime(TRADE_DATE.atTime(6, 50))
                        .dataAsOf(PREVIOUS_TRADE_DATE)
                        .generated(true)
                        .sellCount(0)
                        .executableCount(1)
                        .build())
                .build());

        assertEquals(DashboardCommandPhaseEnum.IN_SESSION.getCode(), command.getPhase());
        assertEquals(DashboardCommandStatusEnum.PARTIAL.getCode(), command.getStatus());
        assertGuideItem(command, 0, OperationGuideCodeEnum.BUY_CONDITIONALLY,
                OperationGuideStatusEnum.WAIT, 1);
        assertEquals("重算决策", command.getOperationGuide().getItems().get(0).getTitle());
    }

    @Test
    void shouldRequireFreshTriggeredAlertAndWaitForOldDecisionRisk() {
        ObservePoolResp alert = ObservePoolResp.builder()
                .status("TRIGGERED")
                .triggeredAt(TRADE_DATE.atTime(8, 5))
                .build();
        DashboardCommandResp freshAlertCommand = service.build(DashboardCommandContextBO.builder()
                .currentTime(TRADE_DATE.atTime(8, 10))
                .marketBriefing(readyMarket())
                .morningBriefing(MorningBriefingResp.builder()
                        .tradeDate(TRADE_DATE)
                        .dataLevel("GREEN")
                        .build())
                .decision(DecisionTodayResp.builder()
                        .actionDate(TRADE_DATE)
                        .asOfTime(TRADE_DATE.atTime(6, 50))
                        .dataAsOf(PREVIOUS_TRADE_DATE)
                        .generated(true)
                        .sellCount(0)
                        .executableCount(0)
                        .build())
                .observeAlerts(List.of(alert))
                .build());

        assertGuideItem(freshAlertCommand, 0, OperationGuideCodeEnum.RISK_FIRST,
                OperationGuideStatusEnum.REQUIRED, 1);

        DashboardCommandResp oldDecisionCommand = service.build(DashboardCommandContextBO.builder()
                .currentTime(TRADE_DATE.atTime(8, 10))
                .marketBriefing(readyMarket())
                .morningBriefing(MorningBriefingResp.builder()
                        .tradeDate(TRADE_DATE)
                        .dataLevel("GREEN")
                        .build())
                .decision(DecisionTodayResp.builder()
                        .actionDate(TRADE_DATE)
                        .asOfTime(TRADE_DATE.atTime(6, 50))
                        .dataAsOf(LocalDate.of(2026, 8, 14))
                        .generated(true)
                        .sellCount(4)
                        .executableCount(2)
                        .build())
                .observeAlerts(List.of())
                .build());

        assertEquals(DashboardCommandStatusEnum.PARTIAL.getCode(), oldDecisionCommand.getStatus());
        assertGuideItem(oldDecisionCommand, 0, OperationGuideCodeEnum.BUY_CONDITIONALLY,
                OperationGuideStatusEnum.WAIT, 2);

        DashboardCommandResp emptyDataDateCommand = service.build(DashboardCommandContextBO.builder()
                .currentTime(TRADE_DATE.atTime(8, 10))
                .marketBriefing(readyMarket())
                .morningBriefing(MorningBriefingResp.builder()
                        .tradeDate(TRADE_DATE)
                        .dataLevel("GREEN")
                        .build())
                .decision(DecisionTodayResp.builder()
                        .actionDate(TRADE_DATE)
                        .asOfTime(TRADE_DATE.atTime(6, 50))
                        .generated(true)
                        .sellCount(1)
                        .executableCount(1)
                        .build())
                .observeAlerts(List.of())
                .build());

        assertEquals(DashboardCommandStatusEnum.PARTIAL.getCode(), emptyDataDateCommand.getStatus());
        assertGuideItem(emptyDataDateCommand, 0, OperationGuideCodeEnum.BUY_CONDITIONALLY,
                OperationGuideStatusEnum.WAIT, 1);
    }

    @Test
    void shouldNotTreatUnpublishedDecisionAsFresh() {
        DashboardCommandResp command = service.build(DashboardCommandContextBO.builder()
                .currentTime(TRADE_DATE.atTime(8, 10))
                .marketBriefing(readyMarket())
                .morningBriefing(MorningBriefingResp.builder()
                        .tradeDate(TRADE_DATE)
                        .dataLevel("GREEN")
                        .build())
                .decision(DecisionTodayResp.builder()
                        .actionDate(TRADE_DATE)
                        .asOfTime(TRADE_DATE.atTime(6, 50))
                        .dataAsOf(PREVIOUS_TRADE_DATE)
                        .generated(false)
                        .sellCount(1)
                        .executableCount(1)
                        .build())
                .build());

        assertEquals(DashboardCommandStatusEnum.PARTIAL.getCode(), command.getStatus());
        assertGuideItem(command, 0, OperationGuideCodeEnum.BUY_CONDITIONALLY,
                OperationGuideStatusEnum.WAIT, 1);
        assertTrue(command.getPreMarketSummary().getHeadline().contains("目标交易日决策尚未生成"));
        assertTrue(command.getOperationGuide().getSummary().contains("重算决策"));
    }

    private MarketBriefingResp readyMarket() {
        return market(PREVIOUS_TRADE_DATE, "GREEN", List.of("半导体", "机器人"));
    }

    private MarketBriefingResp market(LocalDate asOf, String dataLevel, List<String> hotThemes) {
        return MarketBriefingResp.builder()
                .asOf(asOf)
                .stance("防守")
                .stanceScore(35)
                .stanceReason("市场广度偏弱")
                .positionAdvice("总仓控制在2-4成")
                .dataLevel(dataLevel)
                .breadthUp(1200)
                .breadthDown(3300)
                .limitUpCount(35)
                .limitDownCount(8)
                .hotThemes(hotThemes)
                .build();
    }

    private OvernightMarketQuote quote(String symbol, String name, String pctChg) {
        return OvernightMarketQuote.builder()
                .symbol(symbol)
                .name(name)
                .pctChg(new BigDecimal(pctChg))
                .build();
    }

    private void assertGuideItem(DashboardCommandResp command,
                                 int index,
                                 OperationGuideCodeEnum code,
                                 OperationGuideStatusEnum status,
                                 int targetCount) {
        OperationGuideItemResp item = command.getOperationGuide().getItems().get(index);
        assertEquals(index + 1, item.getPriority());
        assertEquals(code.getCode(), item.getCode());
        assertEquals(status.getCode(), item.getStatus());
        assertEquals(targetCount, item.getTargetCount());
    }
}
