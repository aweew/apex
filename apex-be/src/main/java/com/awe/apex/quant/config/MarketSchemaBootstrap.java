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
