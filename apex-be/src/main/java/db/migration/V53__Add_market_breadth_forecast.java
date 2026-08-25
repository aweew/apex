package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

/**
 * 新增盘前涨跌比预测与收盘回测快照表。
 */
public class V53__Add_market_breadth_forecast extends BaseJavaMigration {

    /**
     * 创建预测快照表。
     *
     * @param context Flyway 迁移上下文
     * @throws Exception SQL执行失败
     */
    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS market_breadth_forecast (
                        id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                        trade_date DATE NOT NULL COMMENT '预测对应交易日',
                        generated_at DATETIME NOT NULL COMMENT '预测生成时间',
                        model_version VARCHAR(32) NOT NULL COMMENT '规则模型版本',
                        source_as_of DATETIME NULL COMMENT '盘前输入数据截至时间',
                        predicted_up_ratio DECIMAL(6, 2) NOT NULL COMMENT '预测上涨占比百分比平盘剔除',
                        predicted_down_ratio DECIMAL(6, 2) NOT NULL COMMENT '预测下跌占比百分比平盘剔除',
                        calibration_adjustment DECIMAL(6, 2) NOT NULL DEFAULT 0 COMMENT '历史偏差校准百分点',
                        confidence VARCHAR(8) NOT NULL COMMENT '预测置信度高中新低',
                        factor_summary VARCHAR(1024) NOT NULL COMMENT '盘前依据摘要',
                        actual_up_count INT NULL COMMENT '实际上涨家数',
                        actual_down_count INT NULL COMMENT '实际下跌家数',
                        actual_up_ratio DECIMAL(6, 2) NULL COMMENT '实际上涨占比百分比平盘剔除',
                        actual_down_ratio DECIMAL(6, 2) NULL COMMENT '实际下跌占比百分比平盘剔除',
                        absolute_error DECIMAL(6, 2) NULL COMMENT '预测绝对误差百分点',
                        direction_hit TINYINT NULL COMMENT '涨跌方向是否命中',
                        analysis_summary VARCHAR(1024) NULL COMMENT '收盘回测结论',
                        settled_at DATETIME NULL COMMENT '收盘回测结算时间',
                        create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                        update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                        deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
                        PRIMARY KEY (id),
                        UNIQUE KEY uk_market_breadth_forecast_trade_date (trade_date),
                        KEY idx_market_breadth_forecast_settled (settled_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='盘前涨跌比预测与收盘回测快照'
                    """);
        }
    }
}
