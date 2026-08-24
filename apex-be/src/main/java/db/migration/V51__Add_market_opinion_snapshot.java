package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

/**
 * 新增可追溯市场观点快照表。
 */
public class V51__Add_market_opinion_snapshot extends BaseJavaMigration {

    /**
     * 创建市场观点快照表。
     *
     * @param context Flyway 迁移上下文
     * @throws Exception SQL执行失败
     */
    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS market_opinion (
                        id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                        opinion_type VARCHAR(24) NOT NULL COMMENT '类型INSTITUTION/ACTIVE_SEAT/KOL',
                        source VARCHAR(32) NOT NULL COMMENT '数据来源',
                        external_id VARCHAR(128) NOT NULL COMMENT '来源内去重键',
                        subject_name VARCHAR(128) NOT NULL COMMENT '观点主体',
                        title VARCHAR(512) NOT NULL COMMENT '原始标题',
                        summary VARCHAR(1024) NULL COMMENT '原始摘要',
                        direction VARCHAR(24) NULL COMMENT '评级或行为方向',
                        related_code VARCHAR(16) NULL COMMENT '关联证券代码',
                        related_name VARCHAR(64) NULL COMMENT '关联证券名称',
                        topic VARCHAR(64) NULL COMMENT '行业或主题',
                        net_amount DECIMAL(20, 2) NULL COMMENT '净买卖额元',
                        url VARCHAR(1024) NULL COMMENT '原文链接',
                        published_at DATETIME NOT NULL COMMENT '公开发布时间',
                        snapshot_time DATETIME NOT NULL COMMENT '同步快照时间',
                        create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                        update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                        deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
                        PRIMARY KEY (id),
                        UNIQUE KEY uk_market_opinion_source_external (source, external_id),
                        KEY idx_market_opinion_type_published (opinion_type, published_at),
                        KEY idx_market_opinion_topic_published (topic, published_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='可追溯市场观点快照'
                    """);
        }
    }
}
