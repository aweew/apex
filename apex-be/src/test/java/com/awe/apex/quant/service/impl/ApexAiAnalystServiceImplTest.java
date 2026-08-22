package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.ai.KimiChatClient;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.dto.ApexAiAnalysisResp;
import com.awe.apex.quant.domain.dto.ApexAiAnalyzeReq;
import com.awe.apex.quant.domain.dto.ApexAiContextResp;
import com.awe.apex.quant.domain.dto.ApexAiEnhanceReq;
import com.awe.apex.quant.domain.dto.ApexAiPortfolioOption;
import com.awe.apex.quant.domain.dto.ApexAiStrategyOption;
import com.awe.apex.quant.domain.dto.DecisionAttrBucket;
import com.awe.apex.quant.domain.dto.DecisionAttributionResp;
import com.awe.apex.quant.domain.dto.DecisionStrategyPerformance;
import com.awe.apex.quant.domain.dto.PortfolioSummaryResp;
import com.awe.apex.quant.domain.entity.PortfolioHolding;
import com.awe.apex.quant.mapper.ApexAiQueryMapper;
import com.awe.apex.quant.service.ApexUserAuthService;
import com.awe.apex.quant.service.ApexAiConversationService;
import com.awe.apex.quant.service.IDecisionService;
import com.awe.apex.quant.service.IPortfolioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApexAiAnalystServiceImplTest {

    private final IPortfolioService portfolioService = mock(IPortfolioService.class);
    private final IDecisionService decisionService = mock(IDecisionService.class);
    private final KimiChatClient kimiChatClient = mock(KimiChatClient.class);
    private final ApexAiQueryMapper apexAiQueryMapper = mock(ApexAiQueryMapper.class);
    private final ApexUserContext userContext = mock(ApexUserContext.class);
    private final ApexUserAuthService userAuthService = mock(ApexUserAuthService.class);
    private final ApexAiConversationService conversationService = mock(ApexAiConversationService.class);
    private final ApexAiAnalystServiceImpl service = new ApexAiAnalystServiceImpl();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "portfolioService", portfolioService);
        ReflectionTestUtils.setField(service, "decisionService", decisionService);
        ReflectionTestUtils.setField(service, "kimiChatClient", kimiChatClient);
        ReflectionTestUtils.setField(service, "apexAiQueryMapper", apexAiQueryMapper);
        ReflectionTestUtils.setField(service, "userContext", userContext);
        ReflectionTestUtils.setField(service, "userAuthService", userAuthService);
        ReflectionTestUtils.setField(service, "conversationService", conversationService);
        when(kimiChatClient.available()).thenReturn(false);
        when(conversationService.openConversation(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(21L);
    }

    @Test
    void attributesPortfolioTodayPnlByIndustryWithoutInventingResidual() {
        PortfolioSummaryResp option = PortfolioSummaryResp.builder()
                .id(8L).name("我的组合").isDefault(true).editable(true).build();
        when(portfolioService.listPortfolios(false)).thenReturn(List.of(option));
        when(portfolioService.detail(8L)).thenReturn(PortfolioSummaryResp.builder()
                .id(8L)
                .name("我的组合")
                .marketValue(new BigDecimal("97000"))
                .totalEquity(new BigDecimal("107000"))
                .todayPnl(new BigDecimal("-3000"))
                .todayPct(new BigDecimal("-3.0000"))
                .positionCount(3)
                .missingQuoteCount(0)
                .quoteTime(LocalDateTime.of(2026, 8, 21, 14, 55))
                .holdings(List.of(
                        holding("688001", "芯片一号", "半导体", "-1800"),
                        holding("688002", "芯片二号", "半导体", "-700"),
                        holding("600000", "银行一号", "银行", "-500")))
                .build());

        ApexAiAnalysisResp response = service.analyze(ApexAiAnalyzeReq.builder()
                .question("为什么今天收益跌了？")
                .analysisType("PORTFOLIO")
                .portfolioId(8L)
                .build());

        assertEquals("PORTFOLIO", response.getAnalysisType());
        assertEquals(new BigDecimal("-3000.00"), response.getTotalValue());
        assertEquals("半导体", response.getContributors().get(0).getName());
        assertEquals(new BigDecimal("-2500.00"), response.getContributors().get(0).getValue());
        assertEquals(new BigDecimal("-2.5000"), response.getContributors().get(0).getContributionPct());
        assertEquals(0, response.getResidualValue().compareTo(BigDecimal.ZERO));
        assertEquals("GREEN", response.getDataLevel());
        assertFalse(response.getAiEnhanced());
        assertEquals(21L, response.getConversationId());
        verifyNoInteractions(kimiChatClient);
    }

    @Test
    void enhancementFailureKeepsPersistedRuleAnalysis() {
        ApexAiAnalysisResp ruleAnalysis = ApexAiAnalysisResp.builder()
                .conversationId(21L)
                .requestId("req-1")
                .analysisType("PORTFOLIO")
                .summary("规则结论")
                .aiEnhanced(false)
                .build();
        when(conversationService.loadAnalysis(21L, "req-1")).thenReturn(ruleAnalysis);
        when(conversationService.history(21L, 10)).thenReturn(List.of());
        when(kimiChatClient.available()).thenReturn(true);
        when(kimiChatClient.chatMessages(org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.eq(500))).thenReturn(null);

        ApexAiAnalysisResp response = service.enhance(ApexAiEnhanceReq.builder()
                .conversationId(21L)
                .requestId("req-1")
                .build());

        assertEquals("规则结论", response.getSummary());
        assertFalse(response.getAiEnhanced());
        verify(conversationService, org.mockito.Mockito.never())
                .saveEnhancement(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void enhancementWithUnsupportedNumbersKeepsRuleAnalysis() {
        ApexAiAnalysisResp ruleAnalysis = ApexAiAnalysisResp.builder()
                .conversationId(21L)
                .requestId("req-2")
                .analysisType("STRATEGY")
                .title("S1 · 策略有效性诊断")
                .summary("S1 次日平均 -0.42%。")
                .dataLevel("GREEN")
                .aiEnhanced(false)
                .build();
        when(conversationService.loadAnalysis(21L, "req-2")).thenReturn(ruleAnalysis);
        when(conversationService.history(21L, 10)).thenReturn(List.of());
        when(kimiChatClient.available()).thenReturn(true);
        when(kimiChatClient.chatMessages(org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.eq(500))).thenReturn("趋势因子 IC 已下降到 0.021。");

        ApexAiAnalysisResp response = service.enhance(ApexAiEnhanceReq.builder()
                .conversationId(21L)
                .requestId("req-2")
                .build());

        assertEquals("S1 次日平均 -0.42%。", response.getSummary());
        assertFalse(response.getAiEnhanced());
        verify(conversationService, org.mockito.Mockito.never())
                .saveEnhancement(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void diagnosesWeakStrategyFromMeasuredDecisionEvidence() {
        when(portfolioService.listPortfolios(false)).thenReturn(List.of());
        when(decisionService.attribution(60)).thenReturn(DecisionAttributionResp.builder()
                .days(60)
                .byStrategy(List.of(
                        bucket("S1", 31, 28, "-0.42", "35.71"),
                        bucket("S2", 24, 22, "0.18", "54.55")))
                .byConfluence(List.of(
                        bucket("共振", 20, 19, "0.36", "63.16"),
                        bucket("非共振", 35, 31, "-0.31", "35.48")))
                .byMainline(List.of(
                        bucket("主线同向", 21, 20, "0.28", "60.00"),
                        bucket("非主线", 34, 30, "-0.27", "36.67")))
                .byStance(List.of(bucket("震荡", 26, 24, "-0.38", "33.33")))
                .matureStrategyPerformance(List.of(DecisionStrategyPerformance.builder()
                        .strategyId("S1").sampleCount(18)
                        .avgExcess5d(new BigDecimal("-0.0210"))
                        .winRate5d(new BigDecimal("0.3333"))
                        .build()))
                .message("近 60 个决策日")
                .build());

        ApexAiAnalysisResp response = service.analyze(ApexAiAnalyzeReq.builder()
                .question("S1 最近为什么失效？")
                .analysisType("STRATEGY")
                .strategyId("S1")
                .days(60)
                .build());

        assertEquals("STRATEGY", response.getAnalysisType());
        assertTrue(response.getTitle().contains("S1"));
        assertTrue(response.getSummary().contains("28"));
        assertTrue(response.getContributors().stream().anyMatch(item -> item.getName().contains("非共振")));
        assertTrue(response.getSuggestions().stream().anyMatch(item -> item.contains("共振")));
        assertFalse(response.getAiEnhanced());
    }

    @Test
    void contextUsesLightweightQueriesWithoutBuildingFullAnalysis() {
        when(userContext.currentUserId()).thenReturn(3L);
        when(userAuthService.isAdmin(3L)).thenReturn(false);
        when(apexAiQueryMapper.selectPortfolioOptions(3L, false)).thenReturn(List.of(
                ApexAiPortfolioOption.builder()
                        .id(8L).name("我的组合").defaultPortfolio(true).positionCount(3).build()));
        when(apexAiQueryMapper.selectStrategyOptions(3L, 60)).thenReturn(List.of(
                ApexAiStrategyOption.builder()
                        .strategyId("S1").strategyName("S1").measuredCount(12)
                        .avgNextPct(new BigDecimal("-0.21")).winRate(new BigDecimal("41.67")).build()));

        ApexAiContextResp response = service.context();

        assertEquals(1, response.getPortfolios().size());
        assertEquals(8L, response.getPortfolios().get(0).getId());
        assertEquals("我的组合", response.getPortfolios().get(0).getName());
        assertEquals("S1", response.getStrategies().get(0).getStrategyId());
        verifyNoInteractions(portfolioService, decisionService);
    }

    @Test
    void portfolioAnalysisUsesDefaultPortfolioWithoutDependingOnListOrder() {
        when(portfolioService.listPortfolios(false)).thenReturn(List.of(
                PortfolioSummaryResp.builder().id(7L).name("普通组合").editable(true).isDefault(false).build(),
                PortfolioSummaryResp.builder().id(8L).name("默认组合").editable(true).isDefault(true).build()));
        when(portfolioService.detail(8L)).thenReturn(PortfolioSummaryResp.builder()
                .id(8L).name("默认组合").marketValue(BigDecimal.ZERO).totalEquity(BigDecimal.ZERO)
                .todayPnl(BigDecimal.ZERO).positionCount(0).missingQuoteCount(0).holdings(List.of()).build());

        ApexAiAnalysisResp response = service.analyze(ApexAiAnalyzeReq.builder()
                .question("为什么今天收益下跌？")
                .analysisType("PORTFOLIO")
                .build());

        assertEquals(8L, response.getPortfolioId());
        assertEquals("默认组合 · 今日收益归因", response.getTitle());
    }

    private PortfolioHolding holding(String code, String name, String industry, String todayPnl) {
        return PortfolioHolding.builder()
                .code(code)
                .name(name)
                .industry(industry)
                .todayPnl(new BigDecimal(todayPnl))
                .build();
    }

    private DecisionAttrBucket bucket(String key, int sampleCount, int measuredCount,
                                      String avgNextPct, String winRate) {
        return DecisionAttrBucket.builder()
                .key(key)
                .label(key)
                .sampleCount(sampleCount)
                .measuredCount(measuredCount)
                .avgNextPct(new BigDecimal(avgNextPct))
                .winRate(new BigDecimal(winRate))
                .build();
    }
}
