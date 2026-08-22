package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

/**
 * 新增 Apex AI 会话与消息表。
 */
public class V49__Add_apex_ai_conversation extends BaseJavaMigration {

    /**
     * 创建用户隔离的会话与消息表。
     *
     * @param context Flyway迁移上下文
     * @throws Exception SQL执行失败
     */
    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS apex_ai_conversation (
                        id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                        user_id BIGINT NOT NULL COMMENT '所属用户ID',
                        title VARCHAR(120) NOT NULL COMMENT '会话标题',
                        summary VARCHAR(1000) NULL COMMENT '会话摘要',
                        last_analysis_type VARCHAR(16) NULL COMMENT '最近分析类型',
                        message_count INT NOT NULL DEFAULT 0 COMMENT '消息数量',
                        create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                        update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                        deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
                        PRIMARY KEY (id),
                        KEY idx_apex_ai_conversation_user_time (user_id, deleted, update_time)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Apex AI用户会话'
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS apex_ai_message (
                        id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                        conversation_id BIGINT NOT NULL COMMENT '会话ID',
                        user_id BIGINT NOT NULL COMMENT '所属用户ID',
                        role VARCHAR(16) NOT NULL COMMENT '消息角色USER或ASSISTANT',
                        content TEXT NOT NULL COMMENT '消息文本内容',
                        analysis_type VARCHAR(16) NULL COMMENT '分析类型',
                        portfolio_id BIGINT NULL COMMENT '关联组合ID',
                        strategy_id VARCHAR(40) NULL COMMENT '关联策略ID',
                        request_id VARCHAR(64) NOT NULL COMMENT '分析请求编号',
                        analysis_json MEDIUMTEXT NULL COMMENT '结构化分析结果JSON',
                        ai_enhanced TINYINT NOT NULL DEFAULT 0 COMMENT '是否经过大模型增强',
                        latency_ms BIGINT NULL COMMENT '本阶段处理耗时毫秒',
                        create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                        update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                        deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
                        PRIMARY KEY (id),
                        UNIQUE KEY uk_apex_ai_message_request_role (request_id, role),
                        KEY idx_apex_ai_message_conversation_time (conversation_id, deleted, create_time),
                        KEY idx_apex_ai_message_user_time (user_id, deleted, create_time)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Apex AI会话消息'
                    """);
        }
    }
}
