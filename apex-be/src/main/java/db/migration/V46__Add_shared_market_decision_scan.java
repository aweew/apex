package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

/**
 * 新增全用户共享的市场决策扫描与信号快照。
 */
public class V46__Add_shared_market_decision_scan extends BaseJavaMigration {

    /**
     * 创建共享市场决策表。
     *
     * @param context Flyway迁移上下文
     * @throws Exception SQL执行失败
     */
    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS decision_market_scan (
                        id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                        action_date DATE NOT NULL COMMENT '决策日期',
                        universe_batch_no VARCHAR(64) NOT NULL COMMENT '股票池批次号',
                        include_bj TINYINT NOT NULL DEFAULT 0 COMMENT '是否包含北交所0否1是',
                        status VARCHAR(16) NOT NULL COMMENT '扫描状态RUNNING SUCCESS FAILED',
                        universe_count INT NOT NULL DEFAULT 0 COMMENT '股票池数量',
                        hot_scan_count INT NOT NULL DEFAULT 0 COMMENT '热点扩扫数量',
                        scan_code_count INT NOT NULL DEFAULT 0 COMMENT '实际扫描证券数量',
                        signal_count INT NOT NULL DEFAULT 0 COMMENT '买入信号数量',
                        error_message VARCHAR(512) NULL COMMENT '失败原因',
                        started_at DATETIME NOT NULL COMMENT '扫描开始时间',
                        finished_at DATETIME NULL COMMENT '扫描完成时间',
                        create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                        update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                        PRIMARY KEY (id),
                        UNIQUE KEY uk_decision_market_scan_scope (action_date, universe_batch_no, include_bj),
                        KEY idx_decision_market_scan_status_date (status, action_date)
                    ) COMMENT='共享市场决策扫描快照'
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS decision_market_signal (
                        id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                        scan_id BIGINT NOT NULL COMMENT '共享扫描ID',
                        action_date DATE NOT NULL COMMENT '决策日期',
                        code VARCHAR(16) NOT NULL COMMENT '证券代码',
                        strategy_id VARCHAR(64) NOT NULL COMMENT '策略ID',
                        signal_date DATE NOT NULL COMMENT '信号日期',
                        side VARCHAR(8) NOT NULL COMMENT '信号方向',
                        score DECIMAL(16, 6) NULL COMMENT '信号评分',
                        reason_json TEXT NULL COMMENT '信号理由JSON',
                        create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                        update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                        PRIMARY KEY (id),
                        UNIQUE KEY uk_decision_market_signal_scope (scan_id, code, side),
                        KEY idx_decision_market_signal_date_code (action_date, code),
                        KEY idx_decision_market_signal_scan (scan_id)
                    ) COMMENT='共享市场决策买入信号'
                    """);
        }
    }
}
