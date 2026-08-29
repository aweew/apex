package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

/**
 * 新增用户功能使用事件表。
 */
public class V56__Add_user_usage_activity extends BaseJavaMigration {

    /**
     * 创建用户功能使用事件表与统计索引。
     *
     * @param context Flyway迁移上下文
     * @throws Exception SQL执行失败
     */
    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS apex_user_activity (
                        id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                        user_id BIGINT NOT NULL COMMENT '用户ID',
                        activity_type VARCHAR(24) NOT NULL COMMENT '事件类型',
                        module_code VARCHAR(64) NOT NULL COMMENT '功能模块编码',
                        module_name VARCHAR(64) NOT NULL COMMENT '功能模块名称',
                        occurred_at DATETIME NOT NULL COMMENT '发生时间',
                        create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                        PRIMARY KEY (id),
                        KEY idx_apex_user_activity_time (occurred_at, activity_type),
                        KEY idx_apex_user_activity_user_time (user_id, occurred_at),
                        KEY idx_apex_user_activity_module_time (module_code, occurred_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户功能使用事件'
                    """);
        }
    }
}
