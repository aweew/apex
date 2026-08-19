package db.migration;

import com.awe.apex.quant.migration.MarketSchemaMigration;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * 为已完成用户隔离迁移的策略信号表补齐写入索引。
 */
public class V45__Add_strategy_signal_write_index extends BaseJavaMigration {

    /**
     * 使用 Flyway 当前连接补齐策略信号写入索引。
     *
     * @param context Flyway 迁移上下文
     */
    @Override
    public void migrate(Context context) {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(context.getConnection(), true);
        new MarketSchemaMigration(new JdbcTemplate(dataSource)).ensureStrategySignalWriteIndex();
    }
}
