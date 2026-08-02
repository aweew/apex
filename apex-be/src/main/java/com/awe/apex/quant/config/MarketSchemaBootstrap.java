package com.awe.apex.quant.config;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 启动时补齐关键业务表（幂等）
 */
@Slf4j
@Component
public class MarketSchemaBootstrap implements ApplicationRunner {

    @Resource
    private JdbcTemplate jdbcTemplate;

    /**
     * 启动补表
     *
     * @param args 参数
     */
    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS market_briefing_snapshot (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        trade_date DATE NOT NULL,
                        stance VARCHAR(16) NULL,
                        stance_score INT NULL,
                        data_level VARCHAR(16) NULL,
                        payload_json MEDIUMTEXT NOT NULL,
                        create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        deleted TINYINT NOT NULL DEFAULT 0,
                        PRIMARY KEY (id),
                        UNIQUE KEY uk_market_briefing_date (trade_date)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            log.info("schema ready: market_briefing_snapshot");
            ensureColumn("daily_action", "mainline_match",
                    "ALTER TABLE daily_action ADD COLUMN mainline_match TINYINT NULL");
            ensureColumn("daily_action", "mainline_name",
                    "ALTER TABLE daily_action ADD COLUMN mainline_name VARCHAR(64) NULL");
            ensureColumn("daily_action", "score_explain",
                    "ALTER TABLE daily_action ADD COLUMN score_explain VARCHAR(512) NULL");
            ensureColumn("daily_action", "strategies_csv",
                    "ALTER TABLE daily_action ADD COLUMN strategies_csv VARCHAR(64) NULL");
            log.info("schema ready: daily_action attribution columns");
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS observe_pool (
                        id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                        code VARCHAR(16) NOT NULL COMMENT '证券代码',
                        name VARCHAR(64) NULL COMMENT '证券简称',
                        market VARCHAR(8) NULL COMMENT '市场',
                        side VARCHAR(8) NULL DEFAULT 'BUY' COMMENT '方向BUY/SELL',
                        reason VARCHAR(256) NULL COMMENT '关注原因',
                        guide_text TEXT NULL COMMENT '详细操作指导',
                        trigger_type VARCHAR(32) NOT NULL DEFAULT 'PRICE_ABOVE' COMMENT '触发类型',
                        trigger_expr VARCHAR(512) NULL COMMENT '补充触发条件',
                        trigger_price DECIMAL(16, 4) NULL COMMENT '触发价',
                        stop_loss DECIMAL(16, 4) NULL COMMENT '止损价',
                        target_price DECIMAL(16, 4) NULL COMMENT '目标价',
                        base_price DECIMAL(16, 4) NULL COMMENT '基准价',
                        priority INT NOT NULL DEFAULT 3 COMMENT '优先级1-5',
                        status VARCHAR(16) NOT NULL DEFAULT 'WATCHING' COMMENT '状态',
                        triggered_at DATETIME NULL COMMENT '触发时间',
                        note VARCHAR(512) NULL COMMENT '备注',
                        tags VARCHAR(256) NULL COMMENT '标签逗号分隔',
                        create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                        update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                        deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
                        PRIMARY KEY (id),
                        KEY idx_observe_code (code),
                        KEY idx_observe_status (status),
                        KEY idx_observe_priority (priority)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='观察池'
                    """);
            log.info("schema ready: observe_pool");
            ensureColumn("observe_pool", "side",
                    "ALTER TABLE observe_pool ADD COLUMN side VARCHAR(8) NULL DEFAULT 'BUY' COMMENT '方向BUY/SELL'");
        } catch (Exception ex) {
            log.warn("schema bootstrap skipped: {}", ex.getMessage());
        }
    }

    /**
     * 缺列则补齐
     *
     * @param table  表名
     * @param column 列名
     * @param ddl    ALTER 语句
     */
    private void ensureColumn(String table, String column, String ddl) {
        Integer cnt = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                        """,
                Integer.class, table, column);
        if (Objects.isNull(cnt) || cnt == 0) {
            jdbcTemplate.execute(ddl);
        }
    }
}
