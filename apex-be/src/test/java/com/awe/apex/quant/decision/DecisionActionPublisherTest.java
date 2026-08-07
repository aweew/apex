package com.awe.apex.quant.decision;

import com.awe.apex.quant.domain.dto.DecisionItemResp;
import com.awe.apex.quant.domain.entity.DailyAction;
import com.awe.apex.quant.domain.entity.DecisionRun;
import com.awe.apex.quant.mapper.DailyActionMapper;
import com.awe.apex.quant.mapper.DecisionRunMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DecisionActionPublisherTest {

    private final DailyActionMapper mapper = mock(DailyActionMapper.class);
    private final DecisionRunMapper runMapper = mock(DecisionRunMapper.class);
    private final DecisionActionPublisher publisher = new DecisionActionPublisher();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(publisher, "dailyActionMapper", mapper);
        ReflectionTestUtils.setField(publisher, "decisionRunMapper", runMapper);
        when(runMapper.updateById(org.mockito.ArgumentMatchers.any(DecisionRun.class))).thenReturn(1);
    }

    @Test
    void publishesRankedActionsForRun() {
        List<DecisionItemResp> items = List.of(
                DecisionItemResp.builder().code("000001").action("BUY")
                        .score(new BigDecimal("90")).riskFlags(List.of("估值偏高")).build(),
                DecisionItemResp.builder().code("600000").action("HOLD").build());

        DecisionRun run = DecisionRun.builder()
                .id(12L)
                .actionDate(LocalDate.of(2026, 8, 7))
                .build();
        publisher.publish(run, items, "GREEN", "完成");

        ArgumentCaptor<DailyAction> captor = ArgumentCaptor.forClass(DailyAction.class);
        verify(mapper, times(2)).insert(captor.capture());
        assertEquals(12L, captor.getAllValues().get(0).getRunId());
        assertEquals(1, captor.getAllValues().get(0).getRankNo());
        assertEquals(2, captor.getAllValues().get(1).getRankNo());
        assertEquals("PUBLISHED", captor.getAllValues().get(0).getDecisionStatus());
        assertEquals("SUCCESS", run.getStatus());
        assertEquals(1, run.getPublished());
        verify(runMapper).selectList(org.mockito.ArgumentMatchers.any());
        verify(runMapper).updateById(run);
    }
}
