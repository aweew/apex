package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

/**
 * 增加放量回踩不破市场行为信号。
 */
public class V58__Add_volume_pullback_signal extends BaseJavaMigration {

    /**
     * 注册S007定义和当前规则。
     *
     * @param context Flyway迁移上下文
     * @throws Exception SQL执行失败
     */
    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.executeUpdate("""
                    INSERT IGNORE INTO signal_definition
                        (signal_code, signal_name, signal_category, signal_direction, description,
                         default_priority, supported_timeframes, valid_period_bars, enabled)
                    VALUES
                        ('S007','放量回踩不破','STRUCTURE','BULLISH',
                         '有效突破后回踩突破价位，收盘重新站稳且成交量放大',90,'["DAY"]',5,1)
                    """);
            statement.executeUpdate("""
                    INSERT IGNORE INTO signal_rule
                        (definition_id, rule_version, schema_version, rule_json, parameter_json,
                         feature_version, rule_status, rollout_percent, checksum, effective_time)
                    SELECT t1.id,
                           'mvp-1',
                           '1.0',
                           JSON_OBJECT('op','DETECTOR','signalCode',t1.signal_code),
                           JSON_OBJECT('implementation','daily-v1','requiresCompleteBar',TRUE),
                           'daily-v1',
                           'ACTIVE',
                           100,
                           SHA2(CONCAT(t1.signal_code,':mvp-1:daily-v1'),256),
                           CURRENT_TIMESTAMP
                    FROM signal_definition t1
                    WHERE t1.signal_code = 'S007'
                      AND t1.deleted = 0
                    """);
        }
    }
}
