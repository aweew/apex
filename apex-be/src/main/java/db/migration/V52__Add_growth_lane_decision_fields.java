package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

/**
 * 为每日决策记录增加科技成长线归因字段。
 */
public class V52__Add_growth_lane_decision_fields extends BaseJavaMigration {

    /**
     * 增加决策通道与成长线未准入原因字段。
     *
     * @param context Flyway 迁移上下文
     * @throws Exception SQL执行失败
     */
    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("""
                    ALTER TABLE daily_action
                    ADD COLUMN decision_lane VARCHAR(16) NULL COMMENT '决策通道CORE核心防守线或GROWTH科技成长线'
                    """);
            statement.execute("""
                    ALTER TABLE daily_action
                    ADD COLUMN growth_lane_reject_reason VARCHAR(256) NULL COMMENT '科技候选未进入成长线的原因'
                    """);
        }
    }
}
