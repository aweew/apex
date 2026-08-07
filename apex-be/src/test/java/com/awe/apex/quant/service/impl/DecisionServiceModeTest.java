package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.decision.DecisionActionPublisher;
import com.awe.apex.quant.decision.DecisionContext;
import com.awe.apex.quant.decision.DecisionMode;
import com.awe.apex.quant.decision.DecisionRunManager;
import com.awe.apex.quant.domain.dto.DecisionTodayResp;
import com.awe.apex.quant.domain.entity.DecisionRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

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
}
