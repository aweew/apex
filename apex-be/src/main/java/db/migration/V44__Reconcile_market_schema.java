package db.migration;

import com.awe.apex.quant.migration.MarketSchemaMigration;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * 将历史启动补表逻辑收敛为一次性 Flyway 迁移。
 */
public class V44__Reconcile_market_schema extends BaseJavaMigration {

    /**
     * 使用 Flyway 当前连接执行结构兼容迁移。
     *
     * @param context Flyway 迁移上下文
     */
    @Override
    public void migrate(Context context) {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(context.getConnection(), true);
        new MarketSchemaMigration(new JdbcTemplate(dataSource)).migrate();
    }
}
