package com.awe.apex.quant.migration;

import db.migration.V49__Add_apex_ai_conversation;
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

class ApexAiConversationMigrationTest {

    @Test
    void createsCommentedConversationAndMessageTables() throws Exception {
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

        new V49__Add_apex_ai_conversation().migrate(context);

        assertEquals(2, executedSql.size());
        String conversationSql = executedSql.get(0);
        assertTrue(conversationSql.contains("CREATE TABLE IF NOT EXISTS apex_ai_conversation"));
        assertTrue(conversationSql.contains("user_id BIGINT NOT NULL COMMENT '所属用户ID'"));
        assertTrue(conversationSql.contains("message_count INT NOT NULL DEFAULT 0 COMMENT '消息数量'"));
        assertTrue(conversationSql.contains("KEY idx_apex_ai_conversation_user_time"));
        assertTrue(conversationSql.contains("COMMENT='Apex AI用户会话'"));

        String messageSql = executedSql.get(1);
        assertTrue(messageSql.contains("CREATE TABLE IF NOT EXISTS apex_ai_message"));
        assertTrue(messageSql.contains("conversation_id BIGINT NOT NULL COMMENT '会话ID'"));
        assertTrue(messageSql.contains("analysis_json MEDIUMTEXT NULL COMMENT '结构化分析结果JSON'"));
        assertTrue(messageSql.contains("UNIQUE KEY uk_apex_ai_message_request_role"));
        assertTrue(messageSql.contains("KEY idx_apex_ai_message_conversation_time"));
        assertTrue(messageSql.contains("COMMENT='Apex AI会话消息'"));
    }
}
