package com.awe.apex.quant.migration;

import db.migration.V48__Add_capital_flow_snapshot;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CapitalFlowMigrationTest {

    @Test
    void createsThreeCommentedCapitalFlowTables() throws Exception {
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

        new V48__Add_capital_flow_snapshot().migrate(context);

        assertEquals(3, executedSql.size());
        assertTable(executedSql.get(0), "northbound_flow", "uk_northbound_flow_date",
                "COMMENT='北向资金日快照'");
        assertTable(executedSql.get(1), "stock_fund_flow", "uk_stock_fund_flow_code_date",
                "COMMENT='个股资金流日快照'");
        assertTable(executedSql.get(2), "dragon_tiger_item", "uk_dragon_tiger_code_date_reason",
                "COMMENT='龙虎榜日明细'");
        for (String sql : executedSql) {
            assertTrue(sql.contains("id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键'"));
            assertTrue(sql.contains("trade_date DATE NOT NULL COMMENT '交易日'"));
            assertTrue(sql.contains("synced_at DATETIME NOT NULL COMMENT '同步时间'"));
            assertTrue(sql.contains("create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'"));
            assertTrue(sql.contains("update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'"));
            assertTrue(sql.contains("deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除'"));
        }
        verify(statement).close();
    }

    private void assertTable(String sql, String tableName, String uniqueKey, String tableComment) {
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS " + tableName));
        assertTrue(sql.contains("UNIQUE KEY " + uniqueKey));
        assertTrue(sql.contains(tableComment));
    }
}
