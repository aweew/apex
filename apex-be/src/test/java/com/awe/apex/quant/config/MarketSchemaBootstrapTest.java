package com.awe.apex.quant.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketSchemaBootstrapTest {

    @Test
    void createsDecisionTablesAndAddsDailyActionTraceColumns() {
        List<String> executed = runBootstrapWithColumnCount(null);

        assertContains(executed, "CREATE TABLE IF NOT EXISTS decision_run");
        assertContains(executed, "UNIQUE KEY uk_decision_run_no (run_no)");
        assertContains(executed, "CREATE TABLE IF NOT EXISTS decision_feature_snapshot");
        assertContains(executed, "UNIQUE KEY uk_decision_feature_run_code_action (run_id, code, action)");
        assertContains(executed, "CREATE TABLE IF NOT EXISTS decision_outcome");
        assertContains(executed, "UNIQUE KEY uk_decision_outcome_action (action_id)");
        assertContains(executed, "ADD COLUMN run_id BIGINT NULL");
        assertContains(executed, "ADD COLUMN rank_no INT NULL");
        assertContains(executed, "ADD COLUMN confidence DECIMAL(10, 4) NULL");
        assertContains(executed, "ADD COLUMN uncertainty DECIMAL(10, 4) NULL");
        assertContains(executed, "ADD COLUMN decision_status VARCHAR(16) NULL");
    }

    @Test
    void doesNotAlterDailyActionWhenColumnsAlreadyExist() {
        List<String> executed = runBootstrapWithColumnCount(1);

        assertFalse(executed.stream().anyMatch(sql -> sql.startsWith("ALTER TABLE daily_action")));
        assertContains(executed, "CREATE TABLE IF NOT EXISTS decision_run");
        assertContains(executed, "CREATE TABLE IF NOT EXISTS decision_feature_snapshot");
        assertContains(executed, "CREATE TABLE IF NOT EXISTS decision_outcome");
    }

    private List<String> runBootstrapWithColumnCount(Integer columnCount) {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        List<String> executed = new ArrayList<>();
        doAnswer(invocation -> {
            executed.add(invocation.getArgument(0));
            return null;
        }).when(jdbcTemplate).execute(anyString());
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(), any()))
                .thenReturn(columnCount);

        MarketSchemaBootstrap bootstrap = new MarketSchemaBootstrap();
        ReflectionTestUtils.setField(bootstrap, "jdbcTemplate", jdbcTemplate);
        bootstrap.run(null);
        return executed;
    }

    private void assertContains(List<String> statements, String fragment) {
        assertTrue(statements.stream().anyMatch(sql -> sql.contains(fragment)),
                () -> "Missing DDL fragment: " + fragment);
    }
}
