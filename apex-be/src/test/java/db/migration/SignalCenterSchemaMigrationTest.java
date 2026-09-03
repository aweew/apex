package db.migration;

import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 市场行为信号中心数据库迁移测试。
 */
class SignalCenterSchemaMigrationTest {

    /**
     * 验证完整创建信号中心表及关键审计约束。
     *
     * @throws Exception SQL执行失败
     */
    @Test
    void createsCompleteSignalCenterSchemaWithAuditMetadata() throws Exception {
        Context context = mock(Context.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(context.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.execute(anyString())).thenReturn(true);

        V57__Create_signal_center_schema migration = new V57__Create_signal_center_schema();
        migration.migrate(context);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(statement, times(16)).execute(sqlCaptor.capture());
        List<String> statements = sqlCaptor.getAllValues();
        String completeSql = String.join("\n", statements);

        assertEquals("57", migration.getVersion().getVersion());
        assertTrue(completeSql.contains("CREATE TABLE IF NOT EXISTS signal_definition"));
        assertTrue(completeSql.contains("CREATE TABLE IF NOT EXISTS signal_event"));
        assertTrue(completeSql.contains("CREATE TABLE IF NOT EXISTS signal_snapshot"));
        assertTrue(completeSql.contains("CREATE TABLE IF NOT EXISTS signal_alert_subscription"));
        assertTrue(completeSql.contains("uk_signal_event_idempotent"));
        assertTrue(completeSql.contains("evidence_json JSON NOT NULL COMMENT '结构化证据'"));
        for (String sql : statements) {
            assertTrue(sql.contains("COMMENT='"), "每张表必须声明数据库COMMENT");
            assertTrue(sql.contains("COMMENT '主键'"), "每张表主键必须声明数据库COMMENT");
        }
    }
}
