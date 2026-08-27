package db.migration;

import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.util.StockPinyinUtils;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 新增证券简称拼音缩写索引。
 */
public class V55__Add_stock_pinyin_abbr extends BaseJavaMigration {

    /**
     * 新增拼音缩写列并回填历史股票。
     *
     * @param context Flyway 迁移上下文
     * @throws Exception SQL执行失败
     */
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        try (Statement statement = connection.createStatement()) {
            if (!columnExists(connection)) {
                statement.execute("""
                        ALTER TABLE stock_basic
                        ADD COLUMN pinyin_abbr VARCHAR(64) NULL COMMENT '证券简称拼音首字母缩写' AFTER name
                        """);
            }
            if (!indexExists(connection)) {
                statement.execute("""
                        CREATE INDEX idx_stock_basic_pinyin_abbr ON stock_basic (pinyin_abbr)
                        """);
            }
        }

        List<StockBasic> stocks = new ArrayList<>();
        try (PreparedStatement queryStatement = connection.prepareStatement("""
                SELECT id, name
                FROM stock_basic
                WHERE deleted = 0
                  AND name IS NOT NULL
                  AND name <> ''
                  AND (pinyin_abbr IS NULL OR pinyin_abbr = '')
                """);
             ResultSet resultSet = queryStatement.executeQuery()) {
            while (resultSet.next()) {
                stocks.add(StockBasic.builder()
                        .id(resultSet.getLong("id"))
                        .name(resultSet.getString("name"))
                        .build());
            }
        }

        try (PreparedStatement updateStatement = connection.prepareStatement("""
                UPDATE stock_basic
                SET pinyin_abbr = ?
                WHERE id = ?
                """)) {
            for (StockBasic stock : stocks) {
                updateStatement.setString(1, StockPinyinUtils.buildAbbr(stock.getName()));
                updateStatement.setLong(2, stock.getId());
                updateStatement.addBatch();
            }
            updateStatement.executeBatch();
        }
    }

    private boolean columnExists(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'stock_basic'
                  AND COLUMN_NAME = 'pinyin_abbr'
                """);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() && resultSet.getInt(1) > 0;
        }
    }

    private boolean indexExists(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'stock_basic'
                  AND INDEX_NAME = 'idx_stock_basic_pinyin_abbr'
                """);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() && resultSet.getInt(1) > 0;
        }
    }
}
