package com.awe.apex.quant.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DecisionPortfolioSnapshotMapperTest {

    @Test
    void historicalSnapshotUsesPublishedLiveRunOnly() throws Exception {
        Method method = DecisionPortfolioSnapshotMapper.class
                .getMethod("selectHistorical", java.time.LocalDate.class);
        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertTrue(sql.contains("t2.mode = 'LIVE'"));
        assertTrue(sql.contains("t2.status = 'SUCCESS'"));
        assertTrue(sql.contains("t2.published = 1"));
    }
}
