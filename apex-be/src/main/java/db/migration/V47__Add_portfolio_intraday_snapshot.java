package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

/**
 * 新增实盘组合五分钟盘中收益快照表。
 */
public class V47__Add_portfolio_intraday_snapshot extends BaseJavaMigration {

    /**
     * 创建组合盘中收益快照表。
     *
     * @param context Flyway迁移上下文
     * @throws Exception SQL执行失败
     */
    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS portfolio_intraday_snapshot (
                        id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                        portfolio_id BIGINT NOT NULL COMMENT '组合ID',
                        trade_date DATE NOT NULL COMMENT '交易日',
                        snapshot_time DATETIME NOT NULL COMMENT '五分钟快照时间',
                        total_equity DECIMAL(18, 2) NULL COMMENT '组合总权益',
                        today_pnl DECIMAL(18, 2) NULL COMMENT '当日盈亏',
                        today_pct DECIMAL(10, 4) NULL COMMENT '当日收益率百分比',
                        position_count INT NOT NULL DEFAULT 0 COMMENT '持仓只数',
                        create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                        update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                        deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
                        PRIMARY KEY (id),
                        UNIQUE KEY uk_portfolio_intraday_time (portfolio_id, snapshot_time),
                        KEY idx_portfolio_intraday_date (portfolio_id, trade_date, snapshot_time)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实盘组合五分钟盘中收益快照'
                    """);
        }
    }
}
