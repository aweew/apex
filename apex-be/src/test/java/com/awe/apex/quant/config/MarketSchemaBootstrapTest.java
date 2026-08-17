package com.awe.apex.quant.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketSchemaBootstrapTest {

    @Test
    void createsDecisionTablesAndAddsDailyActionTraceColumns() {
        List<String> executed = runBootstrap(null, "NO", 0);

        assertContains(executed, "CREATE TABLE IF NOT EXISTS decision_run");
        assertContains(executed, "UNIQUE KEY uk_decision_run_no (run_no)");
        assertContains(executed, "CREATE TABLE IF NOT EXISTS decision_feature_snapshot");
        assertContains(executed, "UNIQUE KEY uk_decision_feature_run_code_action (run_id, code, action)");
        assertContains(executed, "CREATE TABLE IF NOT EXISTS decision_outcome");
        assertContains(executed, "UNIQUE KEY uk_decision_outcome_feature (feature_snapshot_id)");
        assertContains(executed, "CREATE TABLE IF NOT EXISTS decision_portfolio_snapshot");
        assertContains(executed, "UNIQUE KEY uk_decision_portfolio_run (run_id)");
        assertContains(executed, "ALTER TABLE portfolio ADD COLUMN cash_balance");
        assertContains(executed, "ALTER TABLE portfolio_daily ADD COLUMN total_equity");
        assertContains(executed, "ALTER TABLE portfolio_daily ADD COLUMN peak_equity");
        assertContains(executed, "ALTER TABLE portfolio_daily ADD COLUMN drawdown");
        assertContains(executed, "ALTER TABLE decision_feature_snapshot ADD COLUMN selection_status");
        assertContains(executed, "ALTER TABLE decision_feature_snapshot ADD COLUMN reject_reason");
        assertContains(executed, "ALTER TABLE decision_feature_snapshot ADD COLUMN rank_no");
        assertContains(executed, "ALTER TABLE decision_outcome ADD COLUMN feature_snapshot_id");
        assertContains(executed, "ALTER TABLE decision_outcome ADD COLUMN entry_date");
        assertContains(executed, "ALTER TABLE decision_outcome ADD COLUMN entry_price");
        assertContains(executed, "ALTER TABLE decision_outcome MODIFY COLUMN action_id BIGINT NULL");
        assertContains(executed, "ALTER TABLE decision_portfolio_snapshot ADD COLUMN market_regime");
        assertContains(executed, "ALTER TABLE decision_portfolio_snapshot ADD COLUMN exposure_limit");
        assertContains(executed, "ALTER TABLE decision_portfolio_snapshot ADD COLUMN single_stock_limit");
        assertContains(executed, "ALTER TABLE decision_portfolio_snapshot ADD COLUMN industry_limit");
        assertContains(executed, "ALTER TABLE decision_portfolio_snapshot ADD COLUMN atr_stop_multiplier");
        assertContains(executed, "ALTER TABLE decision_portfolio_snapshot ADD COLUMN atr_take_multiplier");
        assertContains(executed, "ALTER TABLE decision_portfolio_snapshot ADD COLUMN regime_reason");
        assertContains(executed, "ADD COLUMN run_id BIGINT NULL");
        assertContains(executed, "ADD COLUMN rank_no INT NULL");
        assertContains(executed, "ADD COLUMN confidence DECIMAL(10, 4) NULL");
        assertContains(executed, "ADD COLUMN uncertainty DECIMAL(10, 4) NULL");
        assertContains(executed, "ADD COLUMN decision_status VARCHAR(16) NULL");
        assertContains(executed, "ADD COLUMN reference_price DECIMAL(16, 4) NULL");
        assertContains(executed, "ADD COLUMN stop_loss_price DECIMAL(16, 4) NULL");
        assertContains(executed, "ADD COLUMN take_profit_price DECIMAL(16, 4) NULL");
        assertContains(executed, "ALTER TABLE stock_basic ADD COLUMN pe_dynamic");
        assertContains(executed, "ALTER TABLE stock_basic ADD COLUMN pe_static");
        assertContains(executed, "ALTER TABLE observe_pool ADD COLUMN decision_updated_at");
        assertContains(executed, "UPDATE observe_pool");
        assertContains(executed, "UPDATE stock_basic SET pe_ttm = NULL");
        assertContains(executed, "ALTER TABLE backtest_job ADD COLUMN user_id");
        assertContains(executed, "UPDATE backtest_job t1");
        assertContains(executed, "ALTER TABLE backtest_job MODIFY COLUMN user_id BIGINT NOT NULL");
        assertContains(executed, "ADD KEY idx_backtest_user_status_id");
        assertContains(executed, "ALTER TABLE backtest_job ADD COLUMN comparison_batch_id");
        assertContains(executed, "ALTER TABLE backtest_job ADD COLUMN comparison_strategy_ids");
        assertContains(executed, "ALTER TABLE backtest_job ADD COLUMN strategy_parameters");
        assertContains(executed, "ALTER TABLE backtest_job ADD COLUMN comparison_config_fingerprint");
        assertContains(executed, "ALTER TABLE backtest_job ADD COLUMN commission_rate");
        assertContains(executed, "ALTER TABLE backtest_job ADD COLUMN stamp_tax_rate");
        assertContains(executed, "ALTER TABLE backtest_job ADD COLUMN buy_slippage");
        assertContains(executed, "ALTER TABLE backtest_job ADD COLUMN sell_slippage");
        assertContains(executed, "ALTER TABLE backtest_job ADD COLUMN execution_model_version");
        assertContains(executed, "ALTER TABLE backtest_job ADD COLUMN price_adjustment");
        assertContains(executed, "ALTER TABLE backtest_job ADD COLUMN data_fingerprint");
        assertContains(executed, "ADD KEY idx_backtest_user_comparison_batch");
        assertContains(executed, "ALTER TABLE universe_snapshot ADD COLUMN user_id");
        assertContains(executed, "ALTER TABLE universe_snapshot ADD COLUMN as_of_date");
        assertContains(executed, "UPDATE universe_snapshot t1");
        assertContains(executed, "SET t1.as_of_date = DATE(t1.create_time)");
        assertContains(executed, "ALTER TABLE universe_snapshot MODIFY COLUMN user_id BIGINT NOT NULL");
        assertContains(executed, "ALTER TABLE universe_snapshot MODIFY COLUMN as_of_date DATE NOT NULL");
        assertContains(executed, "ADD KEY idx_universe_user_batch");
        assertContains(executed, "ADD KEY idx_universe_user_as_of_id");
        assertContains(executed, "CREATE TABLE IF NOT EXISTS backtest_experiment");
        assertContains(executed, "init_cash DECIMAL(20, 2) NULL COMMENT '初始资金'");
        assertContains(executed, "execution_model_version VARCHAR(32) NULL COMMENT '成交语义版本'");
        assertContains(executed, "price_adjustment VARCHAR(16) NULL COMMENT '行情复权口径'");
        assertContains(executed, "ALTER TABLE backtest_experiment ADD COLUMN execution_model_version");
        assertContains(executed, "ALTER TABLE backtest_experiment ADD COLUMN price_adjustment");
        assertContains(executed, "ALTER TABLE backtest_experiment ADD COLUMN init_cash");
        assertContains(executed, "KEY idx_backtest_experiment_user_id (user_id, id)");
        assertContains(executed, "ALTER TABLE sync_job ADD COLUMN user_id");
        assertContains(executed, "ADD KEY idx_sync_job_user_type_status");
        assertContains(executed, "ALTER TABLE decision_run ADD COLUMN user_id");
        assertContains(executed, "ADD KEY idx_decision_run_user_publish");
        assertContains(executed, "ALTER TABLE daily_action ADD COLUMN user_id");
        assertContains(executed, "ADD KEY idx_daily_action_user_date");
        assertContains(executed, "ALTER TABLE journal_trade ADD COLUMN user_id");
        assertContains(executed, "ADD KEY idx_journal_trade_user_date");
        assertContains(executed, "ALTER TABLE strategy_signal ADD COLUMN user_id");
        assertContains(executed, "ADD KEY idx_strategy_signal_user_date");
        assertContains(executed, "ADD UNIQUE KEY uk_watchlist_user_code_group");
        assertContains(executed, "ADD UNIQUE KEY uk_my_holding_user_code");
    }

    @Test
    void doesNotAlterDailyActionWhenColumnsAlreadyExist() {
        List<String> executed = runBootstrap(1, "YES", 1);

        assertFalse(executed.stream().anyMatch(sql -> sql.startsWith("ALTER TABLE daily_action")));
        assertFalse(executed.stream().anyMatch(sql -> sql.contains("MODIFY COLUMN action_id")));
        assertFalse(executed.stream().anyMatch(sql -> sql.contains("ADD UNIQUE KEY uk_decision_outcome_feature")));
        assertContains(executed, "CREATE TABLE IF NOT EXISTS decision_run");
        assertContains(executed, "CREATE TABLE IF NOT EXISTS decision_feature_snapshot");
        assertContains(executed, "CREATE TABLE IF NOT EXISTS decision_outcome");
        assertContains(executed, "CREATE TABLE IF NOT EXISTS decision_portfolio_snapshot");
        assertContains(executed, "CREATE TABLE IF NOT EXISTS backtest_experiment");
        assertContains(executed, "DROP INDEX uk_watchlist_code_group");
        assertContains(executed, "DROP INDEX uk_my_holding_code");
    }

    @Test
    void failsApplicationStartupWhenSchemaBootstrapFails() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        doAnswer(invocation -> {
            throw new IllegalStateException("database unavailable");
        }).when(jdbcTemplate).execute(anyString());
        MarketSchemaBootstrap bootstrap = new MarketSchemaBootstrap();
        ReflectionTestUtils.setField(bootstrap, "jdbcTemplate", jdbcTemplate);

        assertThrows(IllegalStateException.class, () -> bootstrap.run(null));
    }

    private List<String> runBootstrap(Integer columnCount, String actionIdNullable, Integer indexCount) {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        List<String> executed = new ArrayList<>();
        doAnswer(invocation -> {
            executed.add(invocation.getArgument(0));
            return null;
        }).when(jdbcTemplate).execute(anyString());
        doAnswer(invocation -> {
            executed.add(invocation.getArgument(0));
            return 0;
        }).when(jdbcTemplate).update(anyString());
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0, String.class).contains("STATISTICS")
                        ? indexCount : columnCount);
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), any(), any()))
                .thenReturn(actionIdNullable);

        MarketSchemaBootstrap bootstrap = new MarketSchemaBootstrap();
        ReflectionTestUtils.setField(bootstrap, "jdbcTemplate", jdbcTemplate);
        bootstrap.run(null);
        return executed;
    }

    private void assertContains(List<String> statements, String fragment) {
        assertTrue(statements.stream().anyMatch(sql -> sql.contains(fragment)),
                () -> "Missing DDL fragment: " + fragment);
    }
}
