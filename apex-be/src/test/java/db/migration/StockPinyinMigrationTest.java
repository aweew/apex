package db.migration;

import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 股票拼音缩写迁移测试
 */
class StockPinyinMigrationTest {

    @Test
    void backfillsAfterClosingStockQueryAndCreatesSearchIndex() throws Exception {
        Context context = mock(Context.class);
        Connection connection = mock(Connection.class);
        Statement ddlStatement = mock(Statement.class);
        PreparedStatement columnStatement = mock(PreparedStatement.class);
        PreparedStatement indexStatement = mock(PreparedStatement.class);
        PreparedStatement queryStatement = mock(PreparedStatement.class);
        PreparedStatement updateStatement = mock(PreparedStatement.class);
        ResultSet columnResult = countResult(0);
        ResultSet indexResult = countResult(0);
        ResultSet stockResult = mock(ResultSet.class);

        when(context.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(ddlStatement);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("information_schema.COLUMNS")) {
                return columnStatement;
            }
            if (sql.contains("information_schema.STATISTICS")) {
                return indexStatement;
            }
            if (sql.contains("SELECT id, name")) {
                return queryStatement;
            }
            return updateStatement;
        });
        when(columnStatement.executeQuery()).thenReturn(columnResult);
        when(indexStatement.executeQuery()).thenReturn(indexResult);
        when(queryStatement.executeQuery()).thenReturn(stockResult);
        when(stockResult.next()).thenReturn(true, false);
        when(stockResult.getLong("id")).thenReturn(1L);
        when(stockResult.getString("name")).thenReturn("贵州茅台");

        V55__Add_stock_pinyin_abbr migration = new V55__Add_stock_pinyin_abbr();
        migration.migrate(context);

        assertEquals("55", migration.getVersion().getVersion());
        verify(ddlStatement).execute(org.mockito.ArgumentMatchers.contains("ADD COLUMN pinyin_abbr"));
        verify(ddlStatement).execute(org.mockito.ArgumentMatchers.contains("idx_stock_basic_pinyin_abbr"));
        verify(updateStatement).setString(1, "gzmt");
        verify(updateStatement).setLong(2, 1L);
        verify(updateStatement).executeBatch();
        InOrder executionOrder = inOrder(stockResult, updateStatement);
        executionOrder.verify(stockResult).close();
        executionOrder.verify(updateStatement).executeBatch();
    }

    private ResultSet countResult(int count) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt(1)).thenReturn(count);
        return resultSet;
    }
}
