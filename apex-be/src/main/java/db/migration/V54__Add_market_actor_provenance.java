package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

/**
 * 增加市场主体白名单与席位映射证据。
 */
public class V54__Add_market_actor_provenance extends BaseJavaMigration {

    /**
     * 创建市场主体与席位映射审计表。
     *
     * @param context Flyway 迁移上下文
     * @throws Exception SQL执行失败
     */
    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS market_actor (
                        id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                        actor_code VARCHAR(64) NOT NULL COMMENT '主体唯一编码',
                        actor_name VARCHAR(128) NOT NULL COMMENT '主体名称',
                        actor_type VARCHAR(24) NOT NULL COMMENT '主体类型SEAT/KOL',
                        platform VARCHAR(64) NULL COMMENT '公开平台',
                        account_url VARCHAR(1024) NULL COMMENT '已核验公开账号主页',
                        feed_url VARCHAR(1024) NULL COMMENT '已授权订阅源地址',
                        source_status VARCHAR(32) NOT NULL COMMENT '来源状态READY/PENDING_VERIFICATION',
                        source_note VARCHAR(512) NULL COMMENT '核验或限制说明',
                        create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                        update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                        deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
                        PRIMARY KEY (id),
                        UNIQUE KEY uk_market_actor_code (actor_code),
                        KEY idx_market_actor_type_status (actor_type, source_status)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='市场主体白名单'
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS market_actor_seat (
                        id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                        actor_code VARCHAR(64) NOT NULL COMMENT '主体唯一编码',
                        seat_keyword VARCHAR(128) NOT NULL COMMENT '营业部匹配关键词',
                        confidence VARCHAR(32) NOT NULL COMMENT '映射置信度',
                        evidence_url VARCHAR(1024) NOT NULL COMMENT '映射证据链接',
                        source_note VARCHAR(512) NULL COMMENT '映射限制说明',
                        create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                        update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                        deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
                        PRIMARY KEY (id),
                        UNIQUE KEY uk_market_actor_seat (actor_code, seat_keyword),
                        KEY idx_market_actor_seat_keyword (seat_keyword)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='市场主体席位映射证据'
                    """);
            statement.execute("""
                    ALTER TABLE market_opinion
                        ADD COLUMN actor_name VARCHAR(128) NULL COMMENT '关联市场主体名称' AFTER subject_name,
                        ADD COLUMN actor_type VARCHAR(24) NULL COMMENT '关联市场主体类型' AFTER actor_name,
                        ADD COLUMN actor_confidence VARCHAR(32) NULL COMMENT '主体关联置信度' AFTER actor_type,
                        ADD COLUMN actor_evidence_url VARCHAR(1024) NULL COMMENT '主体关联证据链接' AFTER actor_confidence
                    """);
        }
    }
}
