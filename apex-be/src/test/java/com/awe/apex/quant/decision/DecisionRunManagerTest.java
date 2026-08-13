package com.awe.apex.quant.decision;

import cn.hutool.extra.spring.SpringUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.domain.entity.DecisionFeatureSnapshot;
import com.awe.apex.quant.domain.entity.DecisionRun;
import com.awe.apex.quant.mapper.DecisionFeatureSnapshotMapper;
import com.awe.apex.quant.mapper.DecisionRunMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DecisionRunManagerTest {

    private static GenericApplicationContext applicationContext;
    private static ApplicationContext originalApplicationContext;

    private final DecisionRunMapper runMapper = mock(DecisionRunMapper.class);
    private final DecisionFeatureSnapshotMapper featureMapper = mock(DecisionFeatureSnapshotMapper.class);
    private final DecisionRunManager manager = new DecisionRunManager();

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
        ReflectionTestUtils.setField(manager, "decisionRunMapper", runMapper);
        ReflectionTestUtils.setField(manager, "featureSnapshotMapper", featureMapper);
        when(runMapper.insert(any(DecisionRun.class))).thenReturn(1);
        when(runMapper.updateById(any(DecisionRun.class))).thenReturn(1);
        when(featureMapper.insert(any(DecisionFeatureSnapshot.class))).thenReturn(1);
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
        DecisionFeature feature = DecisionFeature.builder()
                .code("000001")
                .action("BUY")
                .strategyId("S2")
                .signalScore(new BigDecimal("80"))
                .finalScore(new BigDecimal("90"))
                .suggestedWeight(new BigDecimal("0.1"))
                .confluenceCount(2)
                .hotSourceCount(3)
                .mainlineMatch(true)
                .valuationLevel("FAIR")
                .valuationScore(new BigDecimal("70"))
                .marketStance("均衡")
                .dataQuality("GREEN")
                .executableHint(true)
                .valuationScoreDelta(0)
                .buyWeightFactor(BigDecimal.ONE)
                .singleLimit(new BigDecimal("0.15"))
                .selectionStatus("SELECTED")
                .rankNo(1)
                .riskFlags(List.of("估值偏高"))
                .featureHash("hash")
                .build();

        manager.saveFeatures(run, List.of(feature));

        ArgumentCaptor<DecisionFeatureSnapshot> captor = ArgumentCaptor.forClass(DecisionFeatureSnapshot.class);
        verify(featureMapper).insert(captor.capture());
        DecisionFeatureSnapshot saved = captor.getValue();
        assertEquals(3L, saved.getRunId());
        assertEquals("000001", saved.getCode());
        assertEquals("hash", saved.getFeatureHash());
        assertEquals("SELECTED", saved.getSelectionStatus());
        assertEquals(1, saved.getRankNo());
        assertNotNull(saved.getFeatureJson());
    }

    @Test
    void failsWhenFeatureSnapshotCannotBePersisted() {
        DecisionRun run = DecisionRun.builder().id(3L).featureVersion("FEATURE_V1").build();
        DecisionFeature feature = DecisionFeature.builder()
                .code("000001").action("BUY").featureHash("hash").build();
        when(featureMapper.insert(any(DecisionFeatureSnapshot.class))).thenReturn(0);

        assertThrows(BusinessException.class, () -> manager.saveFeatures(run, List.of(feature)));
    }
}
