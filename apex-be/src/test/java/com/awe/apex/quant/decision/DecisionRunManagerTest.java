package com.awe.apex.quant.decision;

import com.awe.apex.quant.domain.entity.DecisionFeatureSnapshot;
import com.awe.apex.quant.domain.entity.DecisionRun;
import com.awe.apex.quant.mapper.DecisionFeatureSnapshotMapper;
import com.awe.apex.quant.mapper.DecisionRunMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DecisionRunManagerTest {

    private final DecisionRunMapper runMapper = mock(DecisionRunMapper.class);
    private final DecisionFeatureSnapshotMapper featureMapper = mock(DecisionFeatureSnapshotMapper.class);
    private final DecisionRunManager manager = new DecisionRunManager();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(manager, "decisionRunMapper", runMapper);
        ReflectionTestUtils.setField(manager, "featureSnapshotMapper", featureMapper);
    }

    @Test
    void startsAndCompletesUnpublishedRun() {
        DecisionContext context = DecisionContext.builder()
                .actionDate(LocalDate.of(2026, 8, 7))
                .asOfTime(LocalDateTime.of(2026, 8, 7, 15, 5))
                .mode(DecisionMode.LIVE)
                .dataPolicy(DecisionDataPolicy.LATEST_AVAILABLE)
                .build();

        DecisionRun run = manager.start(context, "我的自选");
        run.setId(9L);
        manager.completeUnpublished(run, "GREEN", "完成");

        assertNotNull(run.getRunNo());
        assertEquals("RULE_V1", run.getRuleVersion());
        assertEquals("SUCCESS", run.getStatus());
        assertEquals(0, run.getPublished());
        verify(runMapper).insert(run);
        verify(runMapper).updateById(run);
    }

    @Test
    void persistsCanonicalFeatureSnapshot() {
        DecisionRun run = DecisionRun.builder().id(3L).featureVersion("FEATURE_V1").build();
        DecisionFeature feature = new DecisionFeature(
                "000001", "BUY", "S2", new BigDecimal("80"), new BigDecimal("90"), new BigDecimal("0.1"),
                2, 3, true, "FAIR", new BigDecimal("70"), "均衡", "GREEN", true,
                false, false, false, 0, BigDecimal.ONE, new BigDecimal("0.15"), false,
                List.of("估值偏高"), "hash");

        manager.saveFeatures(run, List.of(feature));

        ArgumentCaptor<DecisionFeatureSnapshot> captor = ArgumentCaptor.forClass(DecisionFeatureSnapshot.class);
        verify(featureMapper).insert(captor.capture());
        DecisionFeatureSnapshot saved = captor.getValue();
        assertEquals(3L, saved.getRunId());
        assertEquals("000001", saved.getCode());
        assertEquals("hash", saved.getFeatureHash());
        assertNotNull(saved.getFeatureJson());
    }
}
