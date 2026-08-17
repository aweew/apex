package com.awe.apex.quant.service.impl;

import cn.hutool.extra.spring.SpringUtil;
import com.awe.apex.quant.ai.KimiChatClient;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.dto.DecisionAdviceResp;
import com.awe.apex.quant.domain.dto.DecisionPortfolioHolding;
import com.awe.apex.quant.domain.entity.DailyAction;
import com.awe.apex.quant.domain.entity.DecisionPortfolioSnapshot;
import com.awe.apex.quant.domain.entity.DecisionRun;
import com.awe.apex.quant.mapper.DailyActionMapper;
import com.awe.apex.quant.mapper.DecisionPortfolioSnapshotMapper;
import com.awe.apex.quant.mapper.DecisionRunMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DecisionAdviceServiceTest {

    private static final LocalDate ACTION_DATE = LocalDate.of(2026, 8, 7);
    private static GenericApplicationContext applicationContext;
    private static ApplicationContext originalApplicationContext;

    private DecisionServiceImpl service;
    private DailyActionMapper dailyActionMapper;
    private DecisionRunMapper decisionRunMapper;
    private DecisionPortfolioSnapshotMapper snapshotMapper;

    @BeforeAll
    static void initJsonUtilsContext() {
        originalApplicationContext = SpringUtil.getApplicationContext();
        applicationContext = new GenericApplicationContext();
        applicationContext.registerBean(ObjectMapper.class, () -> new ObjectMapper().findAndRegisterModules());
        applicationContext.refresh();
        new SpringUtil().setApplicationContext(applicationContext);
    }

    @AfterAll
    static void closeJsonUtilsContext() {
        new SpringUtil().setApplicationContext(originalApplicationContext);
        applicationContext.close();
    }

    @BeforeEach
    void setUp() {
        service = new DecisionServiceImpl();
        dailyActionMapper = mock(DailyActionMapper.class);
        decisionRunMapper = mock(DecisionRunMapper.class);
        snapshotMapper = mock(DecisionPortfolioSnapshotMapper.class);
        KimiChatClient kimiChatClient = mock(KimiChatClient.class);
        ReflectionTestUtils.setField(service, "dailyActionMapper", dailyActionMapper);
        ReflectionTestUtils.setField(service, "decisionRunMapper", decisionRunMapper);
        ReflectionTestUtils.setField(service, "decisionPortfolioSnapshotMapper", snapshotMapper);
        ReflectionTestUtils.setField(service, "kimiChatClient", kimiChatClient);
        ApexUserContext userContext = mock(ApexUserContext.class);
        ReflectionTestUtils.setField(service, "userContext", userContext);
        when(userContext.currentUserId()).thenReturn(7L);
        when(kimiChatClient.available()).thenReturn(false);
    }

    @Test
    void returnsExecutableLotsAndZeroTargetForSellWithoutAi() throws Exception {
        DailyAction buy = DailyAction.builder()
                .runId(9L).rankNo(1).actionDate(ACTION_DATE).code("000001").name("平安银行")
                .action("BUY").suggestedWeight(new BigDecimal("0.10"))
                .referencePrice(new BigDecimal("10")).stopLossPrice(new BigDecimal("9.2"))
                .takeProfitPrice(new BigDecimal("12"))
                .executableHint(1).reason("风险预算通过").build();
        DailyAction sell = DailyAction.builder()
                .runId(9L).rankNo(2).actionDate(ACTION_DATE).code("600000").name("浦发银行")
                .action("SELL").executableHint(0).reason("触及止损").build();
        when(dailyActionMapper.selectList(any())).thenReturn(List.of(buy, sell));
        when(decisionRunMapper.selectById(9L)).thenReturn(DecisionRun.builder()
                .id(9L).userId(7L).runNo("RUN-9").actionDate(ACTION_DATE).build());
        List<DecisionPortfolioHolding> holdings = List.of(DecisionPortfolioHolding.builder()
                .code("600000").quantity(500).marketPrice(new BigDecimal("8"))
                .marketValue(new BigDecimal("4000")).stopLoss(new BigDecimal("7.50"))
                .takeProfit(new BigDecimal("9.60")).build());
        when(snapshotMapper.selectOne(any())).thenReturn(DecisionPortfolioSnapshot.builder()
                .runId(9L).actionDate(ACTION_DATE).cash(new BigDecimal("60000"))
                .marketValue(new BigDecimal("40000")).totalEquity(new BigDecimal("100000"))
                .drawdown(new BigDecimal("0.04")).exposureRatio(new BigDecimal("0.40"))
                .marketRegime("BALANCE").exposureLimit(new BigDecimal("0.50"))
                .regimeReason("市场均衡")
                .holdingPayload(new ObjectMapper().writeValueAsString(holdings))
                .build());

        DecisionAdviceResp advice = service.advice(ACTION_DATE);

        assertFalse(advice.getAiEnhanced());
        assertEquals(0, advice.getTargetExposure().compareTo(new BigDecimal("0.46")));
        assertEquals("BUY", advice.getActions().get(0).getAction());
        assertEquals(1000, advice.getActions().get(0).getQuantity());
        assertEquals("SELL", advice.getActions().get(1).getAction());
        assertEquals(BigDecimal.ZERO.setScale(4), advice.getActions().get(1).getTargetWeight());
        assertEquals(500, advice.getActions().get(1).getQuantity());
        assertEquals(new BigDecimal("8"), advice.getActions().get(1).getReferencePrice());
        assertEquals(new BigDecimal("7.50"), advice.getActions().get(1).getStopLossPrice());
        assertEquals(new BigDecimal("9.60"), advice.getActions().get(1).getTakeProfitPrice());
    }

    @Test
    void returnsWholeLotQuantityForDrawdownReduction() throws Exception {
        DailyAction reduce = DailyAction.builder()
                .runId(9L).rankNo(1).actionDate(ACTION_DATE).code("600000").name("浦发银行")
                .action("REDUCE").suggestedWeight(new BigDecimal("0.12"))
                .referencePrice(new BigDecimal("10")).executableHint(1).reason("组合回撤降仓").build();
        when(dailyActionMapper.selectList(any())).thenReturn(List.of(reduce));
        when(decisionRunMapper.selectById(9L)).thenReturn(DecisionRun.builder()
                .id(9L).userId(7L).runNo("RUN-9").actionDate(ACTION_DATE).build());
        List<DecisionPortfolioHolding> holdings = List.of(DecisionPortfolioHolding.builder()
                .code("600000").quantity(2000).marketPrice(new BigDecimal("10"))
                .marketValue(new BigDecimal("20000")).build());
        when(snapshotMapper.selectOne(any())).thenReturn(DecisionPortfolioSnapshot.builder()
                .runId(9L).actionDate(ACTION_DATE).cash(new BigDecimal("80000"))
                .marketValue(new BigDecimal("20000")).totalEquity(new BigDecimal("100000"))
                .drawdown(new BigDecimal("0.08")).exposureRatio(new BigDecimal("0.20"))
                .marketRegime("BALANCE").exposureLimit(new BigDecimal("0.25"))
                .regimeReason("组合回撤收紧")
                .holdingPayload(new ObjectMapper().writeValueAsString(holdings))
                .build());

        DecisionAdviceResp advice = service.advice(ACTION_DATE);

        assertEquals("REDUCE", advice.getActions().get(0).getAction());
        assertEquals(new BigDecimal("0.1200"), advice.getActions().get(0).getTargetWeight());
        assertEquals(800, advice.getActions().get(0).getQuantity());
        assertEquals(new BigDecimal("0.1200"), advice.getTargetExposure());
    }

    @Test
    void turnsSellWithoutHoldingIntoNonExecutableWatch() {
        DailyAction sell = DailyAction.builder()
                .runId(9L).rankNo(1).actionDate(ACTION_DATE).code("600000").name("浦发银行")
                .action("SELL").executableHint(1).reason("触及止损").build();
        when(dailyActionMapper.selectList(any())).thenReturn(List.of(sell));
        when(decisionRunMapper.selectById(9L)).thenReturn(DecisionRun.builder()
                .id(9L).userId(7L).runNo("RUN-9").actionDate(ACTION_DATE).build());
        when(snapshotMapper.selectOne(any())).thenReturn(DecisionPortfolioSnapshot.builder()
                .runId(9L).actionDate(ACTION_DATE).cash(new BigDecimal("100000"))
                .marketValue(BigDecimal.ZERO).totalEquity(new BigDecimal("100000"))
                .drawdown(BigDecimal.ZERO).exposureRatio(BigDecimal.ZERO)
                .marketRegime("BALANCE").exposureLimit(new BigDecimal("0.50"))
                .regimeReason("市场均衡").holdingPayload("[]")
                .build());

        DecisionAdviceResp advice = service.advice(ACTION_DATE);

        assertEquals("WATCH", advice.getActions().get(0).getAction());
        assertEquals(0, advice.getActions().get(0).getQuantity());
        assertFalse(advice.getActions().get(0).getExecutable());
        assertEquals(new BigDecimal("0.0000"), advice.getTargetExposure());
    }

    @Test
    void alignsTargetWeightWithExecutableWholeLotQuantity() {
        DailyAction buy = DailyAction.builder()
                .runId(9L).rankNo(1).actionDate(ACTION_DATE).code("000001").name("平安银行")
                .action("BUY").suggestedWeight(new BigDecimal("0.105"))
                .referencePrice(new BigDecimal("10")).executableHint(1).reason("风险预算通过").build();
        when(dailyActionMapper.selectList(any())).thenReturn(List.of(buy));
        when(decisionRunMapper.selectById(9L)).thenReturn(DecisionRun.builder()
                .id(9L).userId(7L).runNo("RUN-9").actionDate(ACTION_DATE).build());
        when(snapshotMapper.selectOne(any())).thenReturn(DecisionPortfolioSnapshot.builder()
                .runId(9L).actionDate(ACTION_DATE).cash(new BigDecimal("100000"))
                .marketValue(BigDecimal.ZERO).totalEquity(new BigDecimal("100000"))
                .drawdown(BigDecimal.ZERO).exposureRatio(BigDecimal.ZERO)
                .marketRegime("ATTACK").exposureLimit(new BigDecimal("0.70"))
                .regimeReason("市场进攻").holdingPayload("[]")
                .build());

        DecisionAdviceResp advice = service.advice(ACTION_DATE);

        assertEquals(1000, advice.getActions().get(0).getQuantity());
        assertEquals(new BigDecimal("0.1000"), advice.getActions().get(0).getTargetWeight());
        assertEquals(new BigDecimal("0.1000"), advice.getTargetExposure());
    }

    @Test
    void keepsActualExposureWhenNoExecutableReductionExists() throws Exception {
        DailyAction hold = DailyAction.builder()
                .runId(9L).rankNo(1).actionDate(ACTION_DATE).code("600000").name("浦发银行")
                .action("HOLD").suggestedWeight(new BigDecimal("0.60"))
                .executableHint(0).reason("继续持有").build();
        when(dailyActionMapper.selectList(any())).thenReturn(List.of(hold));
        when(decisionRunMapper.selectById(9L)).thenReturn(DecisionRun.builder()
                .id(9L).userId(7L).runNo("RUN-9").actionDate(ACTION_DATE).build());
        List<DecisionPortfolioHolding> holdings = List.of(DecisionPortfolioHolding.builder()
                .code("600000").quantity(6000).marketPrice(new BigDecimal("10"))
                .marketValue(new BigDecimal("60000")).build());
        when(snapshotMapper.selectOne(any())).thenReturn(DecisionPortfolioSnapshot.builder()
                .runId(9L).actionDate(ACTION_DATE).cash(new BigDecimal("40000"))
                .marketValue(new BigDecimal("60000")).totalEquity(new BigDecimal("100000"))
                .drawdown(BigDecimal.ZERO).exposureRatio(new BigDecimal("0.60"))
                .marketRegime("BALANCE").exposureLimit(new BigDecimal("0.50"))
                .regimeReason("市场均衡")
                .holdingPayload(new ObjectMapper().writeValueAsString(holdings))
                .build());

        DecisionAdviceResp advice = service.advice(ACTION_DATE);

        assertEquals("HOLD", advice.getActions().get(0).getAction());
        assertEquals(new BigDecimal("0.6000"), advice.getActions().get(0).getTargetWeight());
        assertEquals(new BigDecimal("0.6000"), advice.getTargetExposure());
    }
}
