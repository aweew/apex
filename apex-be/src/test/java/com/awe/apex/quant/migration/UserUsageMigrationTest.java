package com.awe.apex.quant.migration;

import db.migration.V56__Add_user_usage_activity;
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

/**
 * 用户使用情况表迁移测试
 */
class UserUsageMigrationTest {

    @Test
    void createsCommentedActivityTableWithStatisticsIndexes() throws Exception {
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

        V56__Add_user_usage_activity migration = new V56__Add_user_usage_activity();
        migration.migrate(context);

        assertEquals("56", migration.getVersion().getVersion());
        assertEquals(1, executedSql.size());
        String activitySql = executedSql.get(0);
        assertTrue(activitySql.contains("CREATE TABLE IF NOT EXISTS apex_user_activity"));
        assertTrue(activitySql.contains("user_id BIGINT NOT NULL COMMENT '用户ID'"));
        assertTrue(activitySql.contains("activity_type VARCHAR(24) NOT NULL COMMENT '事件类型'"));
        assertTrue(activitySql.contains("module_code VARCHAR(64) NOT NULL COMMENT '功能模块编码'"));
        assertTrue(activitySql.contains("occurred_at DATETIME NOT NULL COMMENT '发生时间'"));
        assertTrue(activitySql.contains("KEY idx_apex_user_activity_time"));
        assertTrue(activitySql.contains("KEY idx_apex_user_activity_user_time"));
        assertTrue(activitySql.contains("COMMENT='用户功能使用事件'"));
    }
}
