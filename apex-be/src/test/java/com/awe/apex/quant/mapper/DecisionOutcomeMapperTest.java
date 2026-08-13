package com.awe.apex.quant.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DecisionOutcomeMapperTest {

    @Test
    void outcomeLearningUsesSelectedBuyActionsFromPublishedLiveRunsOnly() throws Exception {
        Method pendingMethod = DecisionOutcomeMapper.class.getMethod("selectPendingOutcomes");
        String pendingSql = sqlOf(pendingMethod);
        Method performanceMethod = DecisionOutcomeMapper.class.getMethod("selectStrategyPerformance");
        String performanceSql = sqlOf(performanceMethod);

        assertTrue(pendingSql.contains("t1.selection_status = 'SELECTED'"));
        assertTrue(pendingSql.contains("t3.mode = 'LIVE'"));
        assertTrue(pendingSql.contains("t3.status = 'SUCCESS'"));
        assertTrue(pendingSql.contains("t3.published = 1"));
        assertTrue(performanceSql.contains("t1.selection_status = 'SELECTED'"));
        assertTrue(performanceSql.contains("t3.mode = 'LIVE'"));
        assertTrue(performanceSql.contains("t3.status = 'SUCCESS'"));
        assertTrue(performanceSql.contains("t3.published = 1"));
    }

    private String sqlOf(Method method) {
        return String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");
    }
}
