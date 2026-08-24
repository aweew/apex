package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

/**
 * 新增不可变的因子研究快照表。
 */
public class V50__Add_factor_research_snapshot extends BaseJavaMigration {

    /**
     * 创建因子研究快照表。
     *
     * @param context Flyway迁移上下文
     * @throws Exception SQL执行失败
     */
    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS factor_research_snapshot (
                        id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                        trade_date DATE NOT NULL COMMENT '快照交易日',
                        model_version VARCHAR(32) NOT NULL COMMENT '模型版本',
                        code VARCHAR(16) NOT NULL COMMENT '证券代码',
                        industry VARCHAR(128) NULL COMMENT '所属行业',
                        quality_raw DECIMAL(18, 6) NULL COMMENT '质量原始值ROE',
                        quality_percentile DECIMAL(8, 2) NULL COMMENT '质量可比组分位',
                        growth_raw DECIMAL(18, 6) NULL COMMENT '成长原始值净利润同比',
                        growth_percentile DECIMAL(8, 2) NULL COMMENT '成长可比组分位',
                        valuation_raw DECIMAL(18, 6) NULL COMMENT '估值原始值盈利收益率',
                        valuation_percentile DECIMAL(8, 2) NULL COMMENT '估值可比组分位',
                        momentum_raw DECIMAL(18, 6) NULL COMMENT '动量原始值相对基准强度',
                        momentum_percentile DECIMAL(8, 2) NULL COMMENT '动量可比组分位',
                        capital_raw DECIMAL(18, 6) NULL COMMENT '资金原始值成交额强度',
                        capital_percentile DECIMAL(8, 2) NULL COMMENT '资金可比组分位',
                        research_score DECIMAL(8, 2) NULL COMMENT '研究评分',
                        coverage DECIMAL(8, 2) NOT NULL COMMENT '可用权重覆盖率',
                        universe_size INT NOT NULL COMMENT '全市场候选样本数',
                        captured_at DATETIME NOT NULL COMMENT '快照生成时间',
                        create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                        update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                        deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
                        PRIMARY KEY (id),
                        UNIQUE KEY uk_factor_research_snapshot_date_model_code (trade_date, model_version, code),
                        KEY idx_factor_research_snapshot_code_date (code, trade_date),
                        KEY idx_factor_research_snapshot_date_score (trade_date, research_score)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='个股横截面因子研究快照'
                    """);
        }
    }
}
