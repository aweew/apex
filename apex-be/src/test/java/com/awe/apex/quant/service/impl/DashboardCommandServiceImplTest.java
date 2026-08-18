package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.domain.bo.DashboardCommandContextBO;
import com.awe.apex.quant.domain.dto.DashboardCommandResp;
import com.awe.apex.quant.domain.dto.DecisionItemResp;
import com.awe.apex.quant.domain.dto.DecisionTodayResp;
import com.awe.apex.quant.domain.dto.MarketBriefingResp;
import com.awe.apex.quant.domain.dto.MorningBriefingResp;
import com.awe.apex.quant.domain.dto.NewsPulseResp;
import com.awe.apex.quant.domain.dto.ObservePoolResp;
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
        assertTrue(command.getPreMarketSummary().getHeadline().contains("2 个卖出项"));
        assertTrue(command.getPreMarketSummary().getHeadline().contains("3 个可执行新仓候选"));
        assertFalse(command.getPreMarketSummary().getHeadline().contains("市场广度"));
        assertTrue(command.getPreMarketSummary().getOpportunityItems().size() <= 2);
        assertTrue(command.getPreMarketSummary().getRiskItems().size() <= 2);
        assertTrue(command.getPreMarketSummary().getEvidenceItems().size() <= 4);
        assertTrue(command.getPreMarketSummary().getWatchConditions().size() <= 2);
        assertEquals(3, command.getOperationGuide().getItems().size());
        assertGuideItem(command, 0, OperationGuideCodeEnum.RISK_FIRST,
                OperationGuideStatusEnum.REQUIRED, 2);
        assertGuideItem(command, 1, OperationGuideCodeEnum.BUY_CONDITIONALLY,
                OperationGuideStatusEnum.READY, 3);
        assertGuideItem(command, 2, OperationGuideCodeEnum.VIEW_CONTEXT,
                OperationGuideStatusEnum.WAIT, 0);
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
        assertEquals(OperationGuideStatusEnum.BLOCKED.getCode(),
                command.getOperationGuide().getItems().get(1).getStatus());
        assertTrue(command.getOperationGuide().getBlockedReason().contains("数据"));
        assertGuideItem(command, 2, OperationGuideCodeEnum.REFRESH_DATA,
                OperationGuideStatusEnum.BLOCKED, 0);
        assertEquals("刷新数据", command.getOperationGuide().getItems().get(2).getTitle());
    }

    @Test
    void shouldBlockWhenMarketBriefingIsMissing() {
        DashboardCommandResp command = service.build(DashboardCommandContextBO.builder()
                .currentTime(TRADE_DATE.atTime(8, 10))
                .decisionRunning(false)
                .build());

        assertEquals(DashboardCommandStatusEnum.BLOCKED.getCode(), command.getStatus());
        assertEquals(OperationGuideStatusEnum.BLOCKED.getCode(),
                command.getOperationGuide().getItems().get(1).getStatus());
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
        assertEquals(OperationGuideStatusEnum.BLOCKED.getCode(),
                command.getOperationGuide().getItems().get(1).getStatus());
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
        assertEquals(OperationGuideStatusEnum.WAIT.getCode(),
                command.getOperationGuide().getItems().get(1).getStatus());
    }

    @Test
    void shouldReturnPartialWhenMorningBriefingAndDecisionAreMissing() {
        DashboardCommandResp command = service.build(DashboardCommandContextBO.builder()
                .currentTime(TRADE_DATE.atTime(8, 10))
                .marketBriefing(readyMarket())
                .decisionRunning(false)
                .build());

        assertEquals(DashboardCommandStatusEnum.PARTIAL.getCode(), command.getStatus());
        assertGuideItem(command, 0, OperationGuideCodeEnum.RISK_FIRST,
                OperationGuideStatusEnum.WAIT, 0);
        assertGuideItem(command, 1, OperationGuideCodeEnum.BUY_CONDITIONALLY,
                OperationGuideStatusEnum.WAIT, 0);
        assertTrue(command.getPreMarketSummary().getHeadline().contains("目标交易日决策尚未生成"));
        assertTrue(command.getPreMarketSummary().getHeadline().contains(TRADE_DATE.toString()));
        assertFalse(command.getPreMarketSummary().getHeadline().contains("..."));
        assertFalse(command.getPreMarketSummary().getHeadline().contains("A股"));
        assertTrue(command.getPreMarketSummary().getEvidenceItems().isEmpty());
        assertTrue(command.getPreMarketSummary().getOpportunityItems().isEmpty());
        assertEquals(1, command.getPreMarketSummary().getWatchConditions().size());
        assertTrue(command.getOperationGuide().getSummary().contains("先复核持仓风险"));
    }

    @Test
    void shouldUseUserActionsInsteadOfBroadMarketAsSummaryEvidence() {
        DecisionItemResp buyItem = DecisionItemResp.builder()
                .code("300750")
                .name("宁德时代")
                .action("BUY")
                .score(new BigDecimal("88"))
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
        assertTrue(command.getPreMarketSummary().getHeadline().contains("1 个卖出项"));
        assertTrue(command.getPreMarketSummary().getHeadline().contains("1 个可执行新仓候选"));
        assertFalse(command.getPreMarketSummary().getHeadline().contains("市场广度"));
        assertTrue(command.getPreMarketSummary().getEvidenceItems().isEmpty());
        assertEquals("宁德时代", command.getPreMarketSummary().getOpportunityItems().get(0).getName());
        assertTrue(command.getPreMarketSummary().getOpportunityItems().get(0).getReason().contains("储能"));
        assertTrue(command.getPreMarketSummary().getOpportunityItems().get(0).getReason().contains("评分 88"));
        assertEquals("浦发银行", command.getPreMarketSummary().getRiskItems().get(0).getName());
        assertEquals("跌破止损价 9.80 元", command.getPreMarketSummary().getRiskItems().get(0).getReason());
        assertTrue(command.getPreMarketSummary().getWatchConditions().get(0).getCondition().contains("开仓门禁"));
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
        assertGuideItem(oldDecisionCommand, 0, OperationGuideCodeEnum.RISK_FIRST,
                OperationGuideStatusEnum.WAIT, 4);
        assertGuideItem(oldDecisionCommand, 1, OperationGuideCodeEnum.BUY_CONDITIONALLY,
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
        assertGuideItem(emptyDataDateCommand, 0, OperationGuideCodeEnum.RISK_FIRST,
                OperationGuideStatusEnum.WAIT, 1);
        assertGuideItem(emptyDataDateCommand, 1, OperationGuideCodeEnum.BUY_CONDITIONALLY,
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
        assertGuideItem(command, 0, OperationGuideCodeEnum.RISK_FIRST,
                OperationGuideStatusEnum.WAIT, 1);
        assertGuideItem(command, 1, OperationGuideCodeEnum.BUY_CONDITIONALLY,
                OperationGuideStatusEnum.WAIT, 1);
        assertTrue(command.getPreMarketSummary().getHeadline().contains("目标交易日决策尚未生成"));
        assertTrue(command.getOperationGuide().getSummary().contains("先复核持仓风险"));
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
