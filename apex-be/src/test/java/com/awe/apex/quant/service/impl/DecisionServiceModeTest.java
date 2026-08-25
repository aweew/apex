package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.decision.DecisionActionPublisher;
import com.awe.apex.quant.decision.DecisionContext;
import com.awe.apex.quant.decision.DecisionMode;
import com.awe.apex.quant.decision.DecisionRunManager;
import com.awe.apex.quant.domain.dto.DecisionTodayResp;
import com.awe.apex.quant.domain.entity.DecisionRun;
import com.awe.apex.quant.domain.entity.StrategySignalEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DecisionServiceModeTest {

    private final DecisionRunManager runManager = mock(DecisionRunManager.class);
    private final DecisionActionPublisher publisher = mock(DecisionActionPublisher.class);
    private final DecisionServiceImpl service = new DecisionServiceImpl();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "decisionRunManager", runManager);
        ReflectionTestUtils.setField(service, "decisionActionPublisher", publisher);
    }

    @Test
    void shadowCompletesWithoutPublishingActions() {
        DecisionContext context = DecisionContext.builder().mode(DecisionMode.SHADOW).build();
        DecisionRun run = DecisionRun.builder().id(1L).build();
        DecisionTodayResp response = DecisionTodayResp.builder().items(List.of()).message("完成").build();

        service.finishRun(context, run, response, "GREEN");

        verify(runManager).completeUnpublished(run, "GREEN", "完成");
        verify(publisher, never()).publish(run, List.of(), "GREEN", "完成");
    }

    @Test
    void redDataCompletesWithoutPublishingActions() {
        DecisionContext context = DecisionContext.builder().mode(DecisionMode.LIVE).build();
        DecisionRun run = DecisionRun.builder().id(1L).build();
        DecisionTodayResp response = DecisionTodayResp.builder().items(List.of()).message("完成").build();

        service.finishRun(context, run, response, "RED");

        verify(runManager).completeUnpublished(run, "RED", response.getMessage());
        verify(publisher, never()).publish(run, List.of(), "RED", response.getMessage());
    }

    @Test
    void describesS3HoldingSellAsBreakoutFailure() {
        StrategySignalEntity signal = StrategySignalEntity.builder()
                .strategyId("S3")
                .reasonJson("{\"rule\":\"跌破突破日低点\"}")
                .build();

        String reason = ReflectionTestUtils.invokeMethod(service, "humanReason", signal, null, null, "持仓卖出", null);

        assertEquals("持仓卖出：S3突破失败（跌破突破日低点）", reason);
    }
}
