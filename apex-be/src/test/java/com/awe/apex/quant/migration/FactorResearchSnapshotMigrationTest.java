package com.awe.apex.quant.migration;

import db.migration.V50__Add_factor_research_snapshot;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FactorResearchSnapshotMigrationTest {

    @Test
    void createsImmutableResearchSnapshotTable() throws Exception {
        Context context = mock(Context.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        List<String> executedSql = new ArrayList<>();
        when(context.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.execute(anyString())).thenAnswer(invocation -> {
            executedSql.add(invocation.getArgument(0));
            return true;
        });

        new V50__Add_factor_research_snapshot().migrate(context);

        assertEquals(1, executedSql.size());
        String sql = executedSql.get(0);
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS factor_research_snapshot"));
        assertTrue(sql.contains("UNIQUE KEY uk_factor_research_snapshot_date_model_code"));
        assertTrue(sql.contains("research_score DECIMAL(8, 2) NULL"));
        assertTrue(sql.contains("quality_percentile DECIMAL(8, 2) NULL"));
        assertTrue(sql.contains("COMMENT='个股横截面因子研究快照'"));
    }
}
