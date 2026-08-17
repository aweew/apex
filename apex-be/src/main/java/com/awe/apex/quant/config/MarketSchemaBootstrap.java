package com.awe.apex.quant.config;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 启动时补齐关键业务表（幂等）
 */
@Slf4j
@Component
public class MarketSchemaBootstrap implements ApplicationRunner {

    @Resource
    private JdbcTemplate jdbcTemplate;

    /**
     * 启动补表
     *
     * @param args 参数
     */
    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS market_briefing_snapshot (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        trade_date DATE NOT NULL,
                        stance VARCHAR(16) NULL,
                        stance_score INT NULL,
                        data_level VARCHAR(16) NULL,
                        payload_json MEDIUMTEXT NOT NULL,
                        create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        deleted TINYINT NOT NULL DEFAULT 0,
                        PRIMARY KEY (id),
                        UNIQUE KEY uk_market_briefing_date (trade_date)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            log.info("schema ready: market_briefing_snapshot");
            ensureDecisionTables();
            ensureCompoundDecisionColumns();
            ensureColumn("daily_action", "run_id",
                    "ALTER TABLE daily_action ADD COLUMN run_id BIGINT NULL COMMENT '决策运行ID'");
            ensureColumn("daily_action", "rank_no",
                    "ALTER TABLE daily_action ADD COLUMN rank_no INT NULL COMMENT '运行内排序'");
            ensureColumn("daily_action", "confidence",
                    "ALTER TABLE daily_action ADD COLUMN confidence DECIMAL(10, 4) NULL COMMENT '决策置信度'");
            ensureColumn("daily_action", "uncertainty",
                    "ALTER TABLE daily_action ADD COLUMN uncertainty DECIMAL(10, 4) NULL COMMENT '决策不确定性'");
            ensureColumn("daily_action", "decision_status",
                    "ALTER TABLE daily_action ADD COLUMN decision_status VARCHAR(16) NULL COMMENT '决策状态'");
            ensureColumn("daily_action", "reference_price",
                    "ALTER TABLE daily_action ADD COLUMN reference_price DECIMAL(16, 4) NULL COMMENT '决策时参考价'");
            ensureColumn("daily_action", "stop_loss_price",
                    "ALTER TABLE daily_action ADD COLUMN stop_loss_price DECIMAL(16, 4) NULL COMMENT '决策止损价'");
            ensureColumn("daily_action", "take_profit_price",
                    "ALTER TABLE daily_action ADD COLUMN take_profit_price DECIMAL(16, 4) NULL COMMENT '决策止盈价'");
            ensureColumn("daily_action", "mainline_match",
                    "ALTER TABLE daily_action ADD COLUMN mainline_match TINYINT NULL");
            ensureColumn("daily_action", "mainline_name",
                    "ALTER TABLE daily_action ADD COLUMN mainline_name VARCHAR(64) NULL");
            ensureColumn("daily_action", "score_explain",
                    "ALTER TABLE daily_action ADD COLUMN score_explain VARCHAR(512) NULL");
            ensureColumn("daily_action", "strategies_csv",
                    "ALTER TABLE daily_action ADD COLUMN strategies_csv VARCHAR(64) NULL");
            ensureColumn("daily_action", "valuation_level",
                    "ALTER TABLE daily_action ADD COLUMN valuation_level VARCHAR(32) NULL");
            ensureColumn("daily_action", "valuation_label",
                    "ALTER TABLE daily_action ADD COLUMN valuation_label VARCHAR(32) NULL");
            ensureColumn("daily_action", "valuation_score",
                    "ALTER TABLE daily_action ADD COLUMN valuation_score DECIMAL(10, 2) NULL");
            ensureColumn("daily_action", "valuation_summary",
                    "ALTER TABLE daily_action ADD COLUMN valuation_summary VARCHAR(256) NULL");
            ensureColumn("daily_action", "link_hint",
                    "ALTER TABLE daily_action ADD COLUMN link_hint VARCHAR(64) NULL");
            ensureColumn("daily_action", "risk_flags",
                    "ALTER TABLE daily_action ADD COLUMN risk_flags VARCHAR(256) NULL");
            ensureColumn("daily_action", "executable_hint",
                    "ALTER TABLE daily_action ADD COLUMN executable_hint TINYINT NULL");
            log.info("schema ready: daily_action attribution/valuation columns");
            ensureStrategyLabSchema();
            ensureUserIsolationSchema();
            ensureUserScopedAssetIndexes();
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS observe_pool (
                        id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                        code VARCHAR(16) NOT NULL COMMENT '证券代码',
                        name VARCHAR(64) NULL COMMENT '证券简称',
                        market VARCHAR(8) NULL COMMENT '市场',
                        side VARCHAR(8) NULL DEFAULT 'BUY' COMMENT '方向BUY/SELL',
                        reason VARCHAR(256) NULL COMMENT '关注原因',
                        guide_text TEXT NULL COMMENT '详细操作指导',
                        trigger_type VARCHAR(32) NOT NULL DEFAULT 'PRICE_ABOVE' COMMENT '触发类型',
                        trigger_expr VARCHAR(512) NULL COMMENT '补充触发条件',
                        trigger_price DECIMAL(16, 4) NULL COMMENT '触发价',
                        stop_loss DECIMAL(16, 4) NULL COMMENT '止损价',
                        target_price DECIMAL(16, 4) NULL COMMENT '目标价',
                        base_price DECIMAL(16, 4) NULL COMMENT '基准价',
                        priority INT NOT NULL DEFAULT 3 COMMENT '优先级1-5',
                        status VARCHAR(16) NOT NULL DEFAULT 'WATCHING' COMMENT '状态',
                        triggered_at DATETIME NULL COMMENT '触发时间',
                        note VARCHAR(512) NULL COMMENT '备注',
                        tags VARCHAR(256) NULL COMMENT '标签逗号分隔',
                        decision_updated_at DATETIME NULL COMMENT '最近一次智能决策写入时间',
                        create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                        update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                        deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
                        PRIMARY KEY (id),
                        KEY idx_observe_code (code),
                        KEY idx_observe_status (status),
                        KEY idx_observe_priority (priority)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='观察池'
                    """);
            log.info("schema ready: observe_pool");
            ensureColumn("observe_pool", "side",
                    "ALTER TABLE observe_pool ADD COLUMN side VARCHAR(8) NULL DEFAULT 'BUY' COMMENT '方向BUY/SELL'");
            ensureColumn("observe_pool", "decision_updated_at",
                    "ALTER TABLE observe_pool ADD COLUMN decision_updated_at DATETIME NULL COMMENT '最近一次智能决策写入时间'");
            jdbcTemplate.update("""
                    UPDATE observe_pool
                    SET decision_updated_at = update_time
                    WHERE decision_updated_at IS NULL
                      AND tags LIKE '%自动%'
                    """);
            ensurePortfolioTables();
            ensurePortfolioDecisionColumns();
            ensureBotTables();
            ensureSmartTraderTables();
            ensureCompanyProfileRevenueColumns();
            ensureStockBasicPeColumns();
        } catch (Exception ex) {
            log.error("schema bootstrap failed", ex);
            throw new IllegalStateException("关键数据库结构初始化失败", ex);
        }
    }

    /**
     * 策略实验室用户隔离、时点股票池和实验历史结构
     */
    private void ensureStrategyLabSchema() {
        boolean backtestUserAdded = ensureColumn("backtest_job", "user_id",
                "ALTER TABLE backtest_job ADD COLUMN user_id BIGINT NULL COMMENT '所属用户ID' AFTER id");
        jdbcTemplate.update("""
                UPDATE backtest_job t1
                JOIN (
                    SELECT MIN(user_id) AS user_id
                    FROM apex_user_profile
                    WHERE role = 'ADMIN'
                ) t2 ON 1 = 1
                SET t1.user_id = t2.user_id
                WHERE t1.user_id IS NULL
                """);
        ensureRequiredColumn("backtest_job", "user_id", backtestUserAdded,
                "ALTER TABLE backtest_job MODIFY COLUMN user_id BIGINT NOT NULL COMMENT '所属用户ID'");
        ensureIndex("backtest_job", "idx_backtest_user_status_id",
                "ALTER TABLE backtest_job ADD KEY idx_backtest_user_status_id (user_id, status, id)");
        ensureColumn("backtest_job", "comparison_batch_id",
                "ALTER TABLE backtest_job ADD COLUMN comparison_batch_id VARCHAR(32) NULL COMMENT '策略对比批次ID' AFTER strategy_id");
        ensureColumn("backtest_job", "comparison_strategy_ids",
                "ALTER TABLE backtest_job ADD COLUMN comparison_strategy_ids VARCHAR(256) NULL COMMENT '策略对比集合' AFTER comparison_batch_id");
        ensureColumn("backtest_job", "strategy_parameters",
                "ALTER TABLE backtest_job ADD COLUMN strategy_parameters VARCHAR(512) NULL COMMENT '当前策略参数快照' AFTER comparison_strategy_ids");
        ensureColumn("backtest_job", "comparison_config_fingerprint",
                "ALTER TABLE backtest_job ADD COLUMN comparison_config_fingerprint CHAR(64) NULL COMMENT '对比策略配置SHA-256指纹' AFTER strategy_parameters");
        ensureColumn("backtest_job", "commission_rate",
                "ALTER TABLE backtest_job ADD COLUMN commission_rate DECIMAL(12, 8) NULL COMMENT '单边佣金比例' AFTER init_cash");
        ensureColumn("backtest_job", "stamp_tax_rate",
                "ALTER TABLE backtest_job ADD COLUMN stamp_tax_rate DECIMAL(12, 8) NULL COMMENT '卖出印花税比例' AFTER commission_rate");
        ensureColumn("backtest_job", "buy_slippage",
                "ALTER TABLE backtest_job ADD COLUMN buy_slippage DECIMAL(12, 8) NULL COMMENT '买入滑点比例' AFTER stamp_tax_rate");
        ensureColumn("backtest_job", "sell_slippage",
                "ALTER TABLE backtest_job ADD COLUMN sell_slippage DECIMAL(12, 8) NULL COMMENT '卖出滑点比例' AFTER buy_slippage");
        ensureColumn("backtest_job", "execution_model_version",
                "ALTER TABLE backtest_job ADD COLUMN execution_model_version VARCHAR(32) NULL COMMENT '成交语义版本' AFTER sell_slippage");
        ensureColumn("backtest_job", "price_adjustment",
                "ALTER TABLE backtest_job ADD COLUMN price_adjustment VARCHAR(16) NULL COMMENT '行情复权口径' AFTER execution_model_version");
        ensureColumn("backtest_job", "data_fingerprint",
                "ALTER TABLE backtest_job ADD COLUMN data_fingerprint CHAR(64) NULL COMMENT '行情数据SHA-256指纹' AFTER price_adjustment");
        ensureIndex("backtest_job", "idx_backtest_user_comparison_batch",
                "ALTER TABLE backtest_job ADD KEY idx_backtest_user_comparison_batch (user_id, comparison_batch_id)");

        if (columnExists("universe_snapshot", "user_id")
                && !columnExists("universe_snapshot", "creator_user_id")) {
            jdbcTemplate.execute("ALTER TABLE universe_snapshot CHANGE COLUMN user_id creator_user_id BIGINT NULL COMMENT '创建用户ID'");
        }
        boolean universeCreatorAdded = ensureColumn("universe_snapshot", "creator_user_id",
                "ALTER TABLE universe_snapshot ADD COLUMN creator_user_id BIGINT NULL COMMENT '创建用户ID' AFTER id");
        boolean universeDateAdded = ensureColumn("universe_snapshot", "as_of_date",
                "ALTER TABLE universe_snapshot ADD COLUMN as_of_date DATE NULL COMMENT '数据截止日' AFTER batch_no");
        jdbcTemplate.update("""
                UPDATE universe_snapshot t1
                JOIN (
                    SELECT MIN(user_id) AS creator_user_id
                    FROM apex_user_profile
                    WHERE role = 'ADMIN'
                ) t2 ON 1 = 1
                SET t1.creator_user_id = t2.creator_user_id
                WHERE t1.creator_user_id IS NULL
                """);
        jdbcTemplate.update("""
                UPDATE universe_snapshot t1
                SET t1.as_of_date = DATE(t1.create_time)
                WHERE t1.as_of_date IS NULL
                """);
        ensureRequiredColumn("universe_snapshot", "creator_user_id", universeCreatorAdded,
                "ALTER TABLE universe_snapshot MODIFY COLUMN creator_user_id BIGINT NOT NULL COMMENT '创建用户ID'");
        ensureRequiredColumn("universe_snapshot", "as_of_date", universeDateAdded,
                "ALTER TABLE universe_snapshot MODIFY COLUMN as_of_date DATE NOT NULL COMMENT '数据截止日'");
        dropIndex("universe_snapshot", "idx_universe_user_batch");
        dropIndex("universe_snapshot", "idx_universe_user_as_of_id");
        ensureIndex("universe_snapshot", "idx_universe_batch",
                "ALTER TABLE universe_snapshot ADD KEY idx_universe_batch (batch_no)");
        ensureIndex("universe_snapshot", "idx_universe_as_of_id",
                "ALTER TABLE universe_snapshot ADD KEY idx_universe_as_of_id (as_of_date, id)");
        ensureIndex("universe_snapshot", "idx_universe_creator_id",
                "ALTER TABLE universe_snapshot ADD KEY idx_universe_creator_id (creator_user_id, id)");

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS backtest_experiment (
                    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                    user_id BIGINT NOT NULL COMMENT '所属用户ID',
                    code VARCHAR(16) NOT NULL COMMENT '证券代码',
                    strategy_id VARCHAR(64) NOT NULL COMMENT '策略ID',
                    strategy_name VARCHAR(128) NOT NULL COMMENT '策略名称',
                    strategy_parameters VARCHAR(512) NOT NULL COMMENT '实际策略参数',
                    benchmark_code VARCHAR(16) NOT NULL COMMENT '基准代码',
                    window_mode VARCHAR(16) NOT NULL COMMENT '窗口模式',
                    data_begin_date DATE NOT NULL COMMENT '实际数据开始日',
                    data_end_date DATE NOT NULL COMMENT '实际数据结束日',
                    out_sample_begin_date DATE NOT NULL COMMENT '首个样本外窗口开始日',
                    out_sample_end_date DATE NOT NULL COMMENT '最后样本外窗口结束日',
                    train_days INT NOT NULL COMMENT '样本内交易日数',
                    test_days INT NOT NULL COMMENT '样本外交易日数',
                    step_days INT NOT NULL COMMENT '相邻样本外窗口步长',
                    init_cash DECIMAL(20, 2) NULL COMMENT '初始资金',
                    fold_count INT NOT NULL COMMENT '样本外窗口数量',
                    compounded_out_sample_return DECIMAL(18, 8) NOT NULL COMMENT '样本外复合收益',
                    compounded_benchmark_return DECIMAL(18, 8) NOT NULL COMMENT '基准复合收益',
                    compounded_excess_return DECIMAL(18, 8) NOT NULL COMMENT '复合超额收益',
                    out_sample_sharpe DECIMAL(18, 8) NOT NULL COMMENT '样本外整体夏普',
                    worst_out_sample_drawdown DECIMAL(18, 8) NOT NULL COMMENT '样本外最差最大回撤',
                    commission_rate DECIMAL(12, 8) NOT NULL COMMENT '单边佣金比例',
                    stamp_tax_rate DECIMAL(12, 8) NOT NULL COMMENT '卖出印花税比例',
                    buy_slippage DECIMAL(12, 8) NOT NULL COMMENT '买入滑点比例',
                    sell_slippage DECIMAL(12, 8) NOT NULL COMMENT '卖出滑点比例',
                    execution_model_version VARCHAR(32) NULL COMMENT '成交语义版本',
                    price_adjustment VARCHAR(16) NULL COMMENT '行情复权口径',
                    data_fingerprint CHAR(64) NOT NULL COMMENT '行情数据SHA-256指纹',
                    request_json MEDIUMTEXT NOT NULL COMMENT '实际请求JSON',
                    result_json MEDIUMTEXT NOT NULL COMMENT '完整结果JSON',
                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
                    PRIMARY KEY (id),
                    KEY idx_backtest_experiment_user_id (user_id, id),
                    KEY idx_backtest_experiment_user_code (user_id, code, id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回测实验历史'
                """);
        ensureColumn("backtest_experiment", "init_cash",
                "ALTER TABLE backtest_experiment ADD COLUMN init_cash DECIMAL(20, 2) NULL COMMENT '初始资金' AFTER step_days");
        ensureColumn("backtest_experiment", "execution_model_version",
                "ALTER TABLE backtest_experiment ADD COLUMN execution_model_version VARCHAR(32) NULL COMMENT '成交语义版本' AFTER sell_slippage");
        ensureColumn("backtest_experiment", "price_adjustment",
                "ALTER TABLE backtest_experiment ADD COLUMN price_adjustment VARCHAR(16) NULL COMMENT '行情复权口径' AFTER execution_model_version");
        log.info("schema ready: strategy lab");
    }

    /**
     * 后台任务、决策、日记和策略信号的用户隔离结构
     */
    private void ensureUserIsolationSchema() {
        dropIndex("sync_job", "idx_sync_job_user_type_status");
        if (columnExists("sync_job", "user_id")) {
            jdbcTemplate.execute("ALTER TABLE sync_job DROP COLUMN user_id");
        }

        ensureColumn("decision_run", "user_id",
                "ALTER TABLE decision_run ADD COLUMN user_id BIGINT NULL COMMENT '所属用户ID' AFTER id");
        ensureIndex("decision_run", "idx_decision_run_user_publish",
                "ALTER TABLE decision_run ADD KEY idx_decision_run_user_publish (user_id, action_date, published, status)");

        ensureColumn("daily_action", "user_id",
                "ALTER TABLE daily_action ADD COLUMN user_id BIGINT NULL COMMENT '所属用户ID' AFTER id");
        ensureIndex("daily_action", "idx_daily_action_user_date",
                "ALTER TABLE daily_action ADD KEY idx_daily_action_user_date (user_id, action_date, rank_no)");

        ensureColumn("journal_trade", "user_id",
                "ALTER TABLE journal_trade ADD COLUMN user_id BIGINT NULL COMMENT '所属用户ID' AFTER id");
        ensureIndex("journal_trade", "idx_journal_trade_user_date",
                "ALTER TABLE journal_trade ADD KEY idx_journal_trade_user_date (user_id, trade_date, id)");

        ensureColumn("strategy_signal", "user_id",
                "ALTER TABLE strategy_signal ADD COLUMN user_id BIGINT NULL COMMENT '所属用户ID' AFTER id");
        ensureIndex("strategy_signal", "idx_strategy_signal_user_date",
                "ALTER TABLE strategy_signal ADD KEY idx_strategy_signal_user_date (user_id, signal_date, id)");
        log.info("schema ready: user isolation");
    }

    /**
     * 自选和兼容持仓表的唯一约束按用户分区
     */
    private void ensureUserScopedAssetIndexes() {
        ensureIndex("watchlist", "uk_watchlist_user_code_group",
                "ALTER TABLE watchlist ADD UNIQUE KEY uk_watchlist_user_code_group (user_id, code, group_name)");
        ensureIndex("my_holding", "uk_my_holding_user_code",
                "ALTER TABLE my_holding ADD UNIQUE KEY uk_my_holding_user_code (user_id, code)");
        dropIndex("watchlist", "uk_watchlist_code_group");
        dropIndex("my_holding", "uk_my_holding_code");
        log.info("schema ready: user scoped asset unique indexes");
    }

    /**
     * 个股三种市盈率口径
     */
    private void ensureStockBasicPeColumns() {
        boolean introduced = ensureColumn("stock_basic", "pe_dynamic",
                "ALTER TABLE stock_basic ADD COLUMN pe_dynamic DECIMAL(16, 4) NULL COMMENT '动态市盈率' AFTER st_flag");
        introduced |= ensureColumn("stock_basic", "pe_static",
                "ALTER TABLE stock_basic ADD COLUMN pe_static DECIMAL(16, 4) NULL COMMENT '静态市盈率' AFTER pe_dynamic");
        if (introduced) {
            // 历史代码把动态 PE 写进 pe_ttm；新增口径时清空，等待 f164 正确回填。
            jdbcTemplate.execute("UPDATE stock_basic SET pe_ttm = NULL WHERE pe_ttm IS NOT NULL");
        }
        log.info("schema ready: stock_basic PE variants");
    }

    /**
     * 智能决策运行、特征和结果表
     */
    private void ensureDecisionTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS decision_run (
                    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                    run_no VARCHAR(64) NOT NULL COMMENT '运行编号',
                    action_date DATE NOT NULL COMMENT '决策交易日',
                    as_of_time DATETIME NOT NULL COMMENT '数据可见截止时间',
                    group_name VARCHAR(64) NULL COMMENT '决策分组',
                    mode VARCHAR(16) NOT NULL DEFAULT 'LIVE' COMMENT 'LIVE/REPLAY/SHADOW',
                    rule_version VARCHAR(64) NULL COMMENT '规则版本',
                    model_version VARCHAR(64) NULL COMMENT '模型版本',
                    feature_version VARCHAR(64) NULL COMMENT '特征版本',
                    data_level VARCHAR(16) NULL COMMENT '数据质量等级',
                    data_cutoff_json MEDIUMTEXT NULL COMMENT '各数据源截止时间JSON',
                    config_snapshot_json MEDIUMTEXT NULL COMMENT '配置快照JSON',
                    status VARCHAR(16) NOT NULL COMMENT 'RUNNING/SUCCESS/FAILED/PUBLISHED',
                    message VARCHAR(512) NULL COMMENT '运行消息',
                    started_at DATETIME NOT NULL COMMENT '开始时间',
                    finished_at DATETIME NULL COMMENT '结束时间',
                    published TINYINT NOT NULL DEFAULT 0 COMMENT '是否发布',
                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_decision_run_no (run_no),
                    KEY idx_decision_run_date (action_date),
                    KEY idx_decision_run_publish (action_date, published, status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能决策运行'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS decision_feature_snapshot (
                    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                    run_id BIGINT NOT NULL COMMENT '决策运行ID',
                    code VARCHAR(16) NOT NULL COMMENT '证券代码',
                    action VARCHAR(16) NULL COMMENT 'BUY/REDUCE/SELL/HOLD/AVOID',
                    feature_version VARCHAR(64) NOT NULL COMMENT '特征版本',
                    feature_hash VARCHAR(64) NOT NULL COMMENT '特征SHA-256',
                    signal_score DECIMAL(10, 4) NULL COMMENT '策略信号分',
                    confluence_count INT NULL COMMENT '共振策略数',
                    hot_source_count INT NULL COMMENT '热点来源数',
                    mainline_match TINYINT NULL COMMENT '是否匹配主线',
                    valuation_level VARCHAR(32) NULL COMMENT '估值档位',
                    market_stance VARCHAR(16) NULL COMMENT '市场状态',
                    data_quality VARCHAR(16) NULL COMMENT '特征数据质量',
                    selection_status VARCHAR(16) NOT NULL DEFAULT 'SELECTED',
                    reject_reason VARCHAR(256) NULL,
                    rank_no INT NULL,
                    feature_json MEDIUMTEXT NOT NULL COMMENT '完整特征JSON',
                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_decision_feature_run_code_action (run_id, code, action),
                    KEY idx_decision_feature_code (code),
                    KEY idx_decision_feature_hash (feature_hash)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能决策特征快照'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS decision_outcome (
                    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                    feature_snapshot_id BIGINT NOT NULL,
                    action_id BIGINT NULL COMMENT '操作清单ID',
                    run_id BIGINT NULL COMMENT '决策运行ID',
                    code VARCHAR(16) NOT NULL COMMENT '证券代码',
                    action_date DATE NOT NULL COMMENT '决策交易日',
                    entry_date DATE NULL,
                    entry_price DECIMAL(16, 4) NULL,
                    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PARTIAL/COMPLETE/INVALID',
                    return_1d DECIMAL(12, 6) NULL COMMENT '1交易日收益率',
                    return_3d DECIMAL(12, 6) NULL COMMENT '3交易日收益率',
                    return_5d DECIMAL(12, 6) NULL COMMENT '5交易日收益率',
                    return_10d DECIMAL(12, 6) NULL COMMENT '10交易日收益率',
                    return_20d DECIMAL(12, 6) NULL COMMENT '20交易日收益率',
                    excess_1d DECIMAL(12, 6) NULL COMMENT '1交易日超额收益率',
                    excess_3d DECIMAL(12, 6) NULL COMMENT '3交易日超额收益率',
                    excess_5d DECIMAL(12, 6) NULL COMMENT '5交易日超额收益率',
                    excess_10d DECIMAL(12, 6) NULL COMMENT '10交易日超额收益率',
                    excess_20d DECIMAL(12, 6) NULL COMMENT '20交易日超额收益率',
                    mfe DECIMAL(12, 6) NULL COMMENT '最大有利变动',
                    mae DECIMAL(12, 6) NULL COMMENT '最大不利变动',
                    stop_hit TINYINT NULL COMMENT '是否触及止损',
                    stop_hit_date DATE NULL COMMENT '首次止损日期',
                    target_hit TINYINT NULL COMMENT '是否触及止盈',
                    target_hit_date DATE NULL COMMENT '首次止盈日期',
                    net_return DECIMAL(12, 6) NULL COMMENT '成本后理论收益率',
                    adoption_status VARCHAR(16) NULL COMMENT '采纳状态',
                    actual_price DECIMAL(16, 4) NULL COMMENT '实际成交价',
                    actual_weight DECIMAL(10, 4) NULL COMMENT '实际仓位',
                    slippage DECIMAL(12, 6) NULL COMMENT '成交滑点',
                    actual_pnl DECIMAL(18, 2) NULL COMMENT '实际盈亏',
                    quality_status VARCHAR(32) NULL COMMENT '结果质量状态',
                    calculated_at DATETIME NULL COMMENT '最后计算时间',
                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_decision_outcome_feature (feature_snapshot_id),
                    UNIQUE KEY uk_decision_outcome_action (action_id),
                    KEY idx_decision_outcome_run_code (run_id, code),
                    KEY idx_decision_outcome_date (action_date)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能决策结果归因'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS decision_portfolio_snapshot (
                    id BIGINT NOT NULL AUTO_INCREMENT,
                    run_id BIGINT NOT NULL,
                    portfolio_id BIGINT NOT NULL,
                    action_date DATE NOT NULL,
                    cash DECIMAL(18, 2) NOT NULL DEFAULT 0,
                    market_value DECIMAL(18, 2) NOT NULL DEFAULT 0,
                    total_equity DECIMAL(18, 2) NOT NULL DEFAULT 0,
                    peak_equity DECIMAL(18, 2) NOT NULL DEFAULT 0,
                    drawdown DECIMAL(12, 6) NOT NULL DEFAULT 0,
                    exposure_ratio DECIMAL(12, 6) NOT NULL DEFAULT 0,
                    market_regime VARCHAR(16) NULL,
                    exposure_limit DECIMAL(12, 6) NULL,
                    single_stock_limit DECIMAL(12, 6) NULL,
                    industry_limit DECIMAL(12, 6) NULL,
                    atr_stop_multiplier DECIMAL(12, 6) NULL,
                    atr_take_multiplier DECIMAL(12, 6) NULL,
                    regime_reason VARCHAR(256) NULL,
                    industry_exposure_json MEDIUMTEXT NULL,
                    holding_payload MEDIUMTEXT NULL,
                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    deleted TINYINT NOT NULL DEFAULT 0,
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_decision_portfolio_run (run_id),
                    KEY idx_decision_portfolio_date (portfolio_id, action_date)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        log.info("schema ready: compound decision tables");
    }

    /**
     * 复利决策候选与结果字段
     */
    private void ensureCompoundDecisionColumns() {
        ensureColumn("decision_feature_snapshot", "selection_status",
                "ALTER TABLE decision_feature_snapshot ADD COLUMN selection_status VARCHAR(16) NOT NULL DEFAULT 'SELECTED'");
        ensureColumn("decision_feature_snapshot", "reject_reason",
                "ALTER TABLE decision_feature_snapshot ADD COLUMN reject_reason VARCHAR(256) NULL");
        ensureColumn("decision_feature_snapshot", "rank_no",
                "ALTER TABLE decision_feature_snapshot ADD COLUMN rank_no INT NULL");
        ensureColumn("decision_outcome", "feature_snapshot_id",
                "ALTER TABLE decision_outcome ADD COLUMN feature_snapshot_id BIGINT NULL");
        ensureColumn("decision_outcome", "entry_date",
                "ALTER TABLE decision_outcome ADD COLUMN entry_date DATE NULL");
        ensureColumn("decision_outcome", "entry_price",
                "ALTER TABLE decision_outcome ADD COLUMN entry_price DECIMAL(16, 4) NULL");
        ensureNullableColumn("decision_outcome", "action_id",
                "ALTER TABLE decision_outcome MODIFY COLUMN action_id BIGINT NULL");
        ensureColumn("decision_portfolio_snapshot", "market_regime",
                "ALTER TABLE decision_portfolio_snapshot ADD COLUMN market_regime VARCHAR(16) NULL");
        ensureColumn("decision_portfolio_snapshot", "exposure_limit",
                "ALTER TABLE decision_portfolio_snapshot ADD COLUMN exposure_limit DECIMAL(12, 6) NULL");
        ensureColumn("decision_portfolio_snapshot", "single_stock_limit",
                "ALTER TABLE decision_portfolio_snapshot ADD COLUMN single_stock_limit DECIMAL(12, 6) NULL");
        ensureColumn("decision_portfolio_snapshot", "industry_limit",
                "ALTER TABLE decision_portfolio_snapshot ADD COLUMN industry_limit DECIMAL(12, 6) NULL");
        ensureColumn("decision_portfolio_snapshot", "atr_stop_multiplier",
                "ALTER TABLE decision_portfolio_snapshot ADD COLUMN atr_stop_multiplier DECIMAL(12, 6) NULL");
        ensureColumn("decision_portfolio_snapshot", "atr_take_multiplier",
                "ALTER TABLE decision_portfolio_snapshot ADD COLUMN atr_take_multiplier DECIMAL(12, 6) NULL");
        ensureColumn("decision_portfolio_snapshot", "regime_reason",
                "ALTER TABLE decision_portfolio_snapshot ADD COLUMN regime_reason VARCHAR(256) NULL");
        if (!indexExists("decision_outcome", "uk_decision_outcome_feature")) {
            jdbcTemplate.execute("ALTER TABLE decision_outcome ADD UNIQUE KEY uk_decision_outcome_feature (feature_snapshot_id)");
        }
    }

    /**
     * 公司概况主营收入构成列
     */
    private void ensureCompanyProfileRevenueColumns() {
        ensureColumn("stock_company_profile", "revenue_report_date",
                "ALTER TABLE stock_company_profile ADD COLUMN revenue_report_date DATE NULL");
        ensureColumn("stock_company_profile", "revenue_items",
                "ALTER TABLE stock_company_profile ADD COLUMN revenue_items TEXT NULL");
        ensureColumn("stock_company_profile", "top_profit_business",
                "ALTER TABLE stock_company_profile ADD COLUMN top_profit_business VARCHAR(128) NULL");
        ensureColumn("stock_company_profile", "top_profit_ratio",
                "ALTER TABLE stock_company_profile ADD COLUMN top_profit_ratio DECIMAL(10, 4) NULL");
        // 主营业务原文可能较长，放宽为 TEXT
        try {
            jdbcTemplate.execute("ALTER TABLE stock_company_profile MODIFY COLUMN main_business TEXT NULL");
        } catch (Exception ignored) {
            // 已是 TEXT 或无权限时忽略
        }
        log.info("schema ready: stock_company_profile revenue columns");
    }

    /**
     * 实盘组合三表
     */
    private void ensurePortfolioTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS portfolio (
                    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                    name VARCHAR(64) NOT NULL COMMENT '组合名称',
                    note VARCHAR(512) NULL COMMENT '备注',
                    owner_label VARCHAR(64) NULL COMMENT '实盘归属人标签',
                    cash_balance DECIMAL(18, 2) NOT NULL DEFAULT 0,
                    is_default TINYINT NOT NULL DEFAULT 0 COMMENT '是否默认组合',
                    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/ARCHIVED',
                    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序',
                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
                    PRIMARY KEY (id),
                    KEY idx_portfolio_status (status),
                    KEY idx_portfolio_default (is_default)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实盘组合'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS portfolio_holding (
                    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                    portfolio_id BIGINT NOT NULL COMMENT '组合ID',
                    code VARCHAR(16) NOT NULL COMMENT '证券代码',
                    name VARCHAR(64) NULL COMMENT '证券简称',
                    quantity INT NOT NULL DEFAULT 0 COMMENT '持仓数量',
                    cost_price DECIMAL(16, 4) NULL COMMENT '成本价',
                    stop_loss DECIMAL(16, 4) NULL COMMENT '止损价',
                    take_profit DECIMAL(16, 4) NULL COMMENT '止盈价',
                    note VARCHAR(256) NULL COMMENT '备注',
                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_portfolio_holding_code (portfolio_id, code),
                    KEY idx_portfolio_holding_pf (portfolio_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组合持仓'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS portfolio_daily (
                    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                    portfolio_id BIGINT NOT NULL COMMENT '组合ID',
                    trade_date DATE NOT NULL COMMENT '交易日',
                    market_value DECIMAL(18, 2) NULL COMMENT '市值',
                    cost_value DECIMAL(18, 2) NULL COMMENT '成本市值',
                    total_pnl DECIMAL(18, 2) NULL COMMENT '累计浮盈',
                    today_pnl DECIMAL(18, 2) NULL COMMENT '当日浮盈',
                    today_pct DECIMAL(10, 4) NULL COMMENT '当日涨跌幅%',
                    position_count INT NOT NULL DEFAULT 0 COMMENT '持仓只数',
                    cash DECIMAL(18, 2) NOT NULL DEFAULT 0 COMMENT '现金',
                    total_equity DECIMAL(18, 2) NULL,
                    peak_equity DECIMAL(18, 2) NULL,
                    drawdown DECIMAL(12, 6) NULL,
                    payload MEDIUMTEXT NULL COMMENT '持仓明细JSON',
                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_portfolio_daily (portfolio_id, trade_date),
                    KEY idx_portfolio_daily_date (trade_date)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组合每日快照'
                """);
        log.info("schema ready: portfolio / portfolio_holding / portfolio_daily");
    }

    /**
     * 组合现金与权益字段
     */
    private void ensurePortfolioDecisionColumns() {
        ensureColumn("portfolio", "cash_balance",
                "ALTER TABLE portfolio ADD COLUMN cash_balance DECIMAL(18, 2) NOT NULL DEFAULT 0");
        ensureColumn("portfolio_daily", "total_equity",
                "ALTER TABLE portfolio_daily ADD COLUMN total_equity DECIMAL(18, 2) NULL");
        ensureColumn("portfolio_daily", "peak_equity",
                "ALTER TABLE portfolio_daily ADD COLUMN peak_equity DECIMAL(18, 2) NULL");
        ensureColumn("portfolio_daily", "drawdown",
                "ALTER TABLE portfolio_daily ADD COLUMN drawdown DECIMAL(12, 6) NULL");
    }

    /**
     * Bot 待确认操作与调用审计表。
     */
    private void ensureBotTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS bot_call_audit (
                    id BIGINT NOT NULL AUTO_INCREMENT,
                    request_id VARCHAR(80) NOT NULL,
                    operation VARCHAR(64) NOT NULL,
                    user_id VARCHAR(128) NULL,
                    conversation_id VARCHAR(128) NULL,
                    data_level VARCHAR(16) NOT NULL,
                    error_message VARCHAR(512) NULL,
                    duration_ms BIGINT NOT NULL,
                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (id),
                    KEY idx_bot_audit_request (request_id),
                    KEY idx_bot_audit_created (create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Bot调用审计'
                """);
        log.info("schema ready: bot_call_audit");
    }

    /**
     * Smart Trader 第一阶段事件、证据和正式交易流水表。
     */
    private void ensureSmartTraderTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS trader (
                    id BIGINT NOT NULL AUTO_INCREMENT, name VARCHAR(64) NOT NULL, nickname VARCHAR(64) NULL, wechat_peer_id VARCHAR(128) NULL, avatar VARCHAR(512) NULL, initial_capital DECIMAL(18, 2) NOT NULL DEFAULT 1000000.00, status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, deleted TINYINT NOT NULL DEFAULT 0,
                    PRIMARY KEY (id), UNIQUE KEY uk_trader_wechat_peer_id (wechat_peer_id), KEY idx_trader_name (name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        ensureColumn("trader", "initial_capital", "ALTER TABLE trader ADD COLUMN initial_capital DECIMAL(18, 2) NOT NULL DEFAULT 1000000.00");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS trade_event (
                    id BIGINT NOT NULL AUTO_INCREMENT, trader_id BIGINT NOT NULL, event_type VARCHAR(24) NOT NULL, symbol VARCHAR(16) NULL, stock_name VARCHAR(64) NULL, side VARCHAR(16) NULL, quantity INT NULL, price DECIMAL(16, 4) NULL, trade_time DATETIME NULL, confidence DECIMAL(6, 4) NOT NULL, source VARCHAR(24) NOT NULL, raw_text TEXT NOT NULL, idempotency_key VARCHAR(128) NULL, status VARCHAR(24) NOT NULL, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, deleted TINYINT NOT NULL DEFAULT 0,
                    PRIMARY KEY (id), UNIQUE KEY uk_trade_event_idempotency (idempotency_key), KEY idx_trade_event_trader_created (trader_id, create_time), KEY idx_trade_event_status (status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS trade_evidence (
                    id BIGINT NOT NULL AUTO_INCREMENT, trade_event_id BIGINT NOT NULL, trader_id BIGINT NOT NULL, source VARCHAR(24) NOT NULL, raw_text TEXT NULL, image_url VARCHAR(1024) NULL, parsed_result MEDIUMTEXT NOT NULL, confidence DECIMAL(6, 4) NOT NULL, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (id), KEY idx_trade_evidence_event (trade_event_id), KEY idx_trade_evidence_trader (trader_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS trader_trade (
                    id BIGINT NOT NULL AUTO_INCREMENT, trader_id BIGINT NOT NULL, symbol VARCHAR(16) NOT NULL, stock_name VARCHAR(64) NULL, side VARCHAR(8) NOT NULL, quantity INT NOT NULL, price DECIMAL(16, 4) NOT NULL, amount DECIMAL(18, 2) NOT NULL, trade_time DATETIME NULL, evidence_id BIGINT NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'VALID', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, deleted TINYINT NOT NULL DEFAULT 0,
                    PRIMARY KEY (id), UNIQUE KEY uk_trader_trade_evidence (evidence_id), KEY idx_trader_trade_trader_time (trader_id, trade_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS trader_position (id BIGINT NOT NULL AUTO_INCREMENT, trader_id BIGINT NOT NULL, symbol VARCHAR(16) NOT NULL, stock_name VARCHAR(64) NULL, quantity INT NOT NULL DEFAULT 0, avg_cost DECIMAL(16, 4) NOT NULL DEFAULT 0, market_price DECIMAL(16, 4) NULL, market_value DECIMAL(18, 2) NULL, profit DECIMAL(18, 2) NULL, profit_rate DECIMAL(12, 6) NULL, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, deleted TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (id), UNIQUE KEY uk_trader_position (trader_id, symbol)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS trader_portfolio_daily (id BIGINT NOT NULL AUTO_INCREMENT, trader_id BIGINT NOT NULL, trade_date DATE NOT NULL, cash DECIMAL(18, 2) NOT NULL, market_value DECIMAL(18, 2) NOT NULL, total_asset DECIMAL(18, 2) NOT NULL, daily_profit DECIMAL(18, 2) NOT NULL, daily_profit_rate DECIMAL(12, 6) NOT NULL, total_profit DECIMAL(18, 2) NOT NULL, total_profit_rate DECIMAL(12, 6) NOT NULL, max_drawdown DECIMAL(12, 6) NOT NULL, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, deleted TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (id), UNIQUE KEY uk_trader_portfolio_daily (trader_id, trade_date)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS trader_ranking_daily (id BIGINT NOT NULL AUTO_INCREMENT, trade_date DATE NOT NULL, trader_id BIGINT NOT NULL, total_return DECIMAL(12, 6) NOT NULL, daily_return DECIMAL(12, 6) NOT NULL, max_drawdown DECIMAL(12, 6) NOT NULL, win_rate DECIMAL(12, 6) NOT NULL, profit_loss_ratio DECIMAL(12, 6) NOT NULL, sharpe DECIMAL(12, 6) NULL, trader_score DECIMAL(12, 6) NOT NULL, return_ranking INT NULL, daily_ranking INT NULL, steady_ranking INT NULL, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, deleted TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (id), UNIQUE KEY uk_trader_ranking_daily (trade_date, trader_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS trader_profile (id BIGINT NOT NULL AUTO_INCREMENT, trader_id BIGINT NOT NULL, style VARCHAR(32) NOT NULL, preferred_industries MEDIUMTEXT NULL, average_holding_days DECIMAL(12, 4) NULL, win_rate DECIMAL(12, 6) NULL, profit_loss_ratio DECIMAL(12, 6) NULL, max_drawdown DECIMAL(12, 6) NULL, turnover_rate DECIMAL(12, 6) NULL, volatility DECIMAL(12, 6) NULL, concentration DECIMAL(12, 6) NULL, summary VARCHAR(512) NULL, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, deleted TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (id), UNIQUE KEY uk_trader_profile (trader_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS smart_money_factor (id BIGINT NOT NULL AUTO_INCREMENT, trade_date DATE NOT NULL, symbol VARCHAR(16) NOT NULL, stock_name VARCHAR(64) NULL, net_buy_amount DECIMAL(18, 2) NOT NULL, trader_total_asset DECIMAL(18, 2) NOT NULL, factor_value DECIMAL(12, 6) NOT NULL, trader_count INT NOT NULL, consensus DECIMAL(12, 6) NOT NULL, return_1d DECIMAL(12, 6) NULL, return_5d DECIMAL(12, 6) NULL, return_10d DECIMAL(12, 6) NULL, return_20d DECIMAL(12, 6) NULL, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, deleted TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (id), UNIQUE KEY uk_smart_money_factor (trade_date, symbol)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        ensureColumn("smart_money_factor", "return_1d", "ALTER TABLE smart_money_factor ADD COLUMN return_1d DECIMAL(12, 6) NULL");
        ensureColumn("smart_money_factor", "return_5d", "ALTER TABLE smart_money_factor ADD COLUMN return_5d DECIMAL(12, 6) NULL");
        ensureColumn("smart_money_factor", "return_10d", "ALTER TABLE smart_money_factor ADD COLUMN return_10d DECIMAL(12, 6) NULL");
        ensureColumn("smart_money_factor", "return_20d", "ALTER TABLE smart_money_factor ADD COLUMN return_20d DECIMAL(12, 6) NULL");
        log.info("schema ready: smart trader tables");
    }

    /**
     * 缺列则补齐
     *
     * @param table  表名
     * @param column 列名
     * @param ddl    ALTER 语句
     */
    private boolean ensureColumn(String table, String column, String ddl) {
        if (!columnExists(table, column)) {
            jdbcTemplate.execute(ddl);
            return true;
        }
        return false;
    }

    /**
     * 判断列是否存在
     *
     * @param table  表名
     * @param column 列名
     * @return 是否存在
     */
    private boolean columnExists(String table, String column) {
        Integer cnt = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                        """,
                Integer.class, table, column);
        return Objects.nonNull(cnt) && cnt > 0;
    }

    /**
     * 判断索引是否存在
     *
     * @param table     表名
     * @param indexName 索引名
     * @return 是否存在
     */
    private boolean indexExists(String table, String indexName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM information_schema.STATISTICS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?
                        """,
                Integer.class, table, indexName);
        return Objects.nonNull(count) && count > 0;
    }

    /**
     * 缺索引则补齐
     *
     * @param table     表名
     * @param indexName 索引名
     * @param ddl       ALTER 语句
     */
    private void ensureIndex(String table, String indexName, String ddl) {
        if (!indexExists(table, indexName)) {
            jdbcTemplate.execute(ddl);
        }
    }

    /**
     * 删除不再适用的索引
     *
     * @param table     表名
     * @param indexName 索引名
     */
    private void dropIndex(String table, String indexName) {
        if (indexExists(table, indexName)) {
            jdbcTemplate.execute("ALTER TABLE " + table + " DROP INDEX " + indexName);
        }
    }

    /**
     * 将完成回填的业务列收紧为非空
     *
     * @param table       表名
     * @param column      列名
     * @param columnAdded 本次是否新增列
     * @param ddl         ALTER 语句
     */
    private void ensureRequiredColumn(String table, String column, boolean columnAdded, String ddl) {
        if (columnAdded) {
            jdbcTemplate.execute(ddl);
            return;
        }
        String nullable = jdbcTemplate.queryForObject(
                """
                        SELECT IS_NULLABLE FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                        """,
                String.class, table, column);
        if (!"NO".equalsIgnoreCase(nullable)) {
            jdbcTemplate.execute(ddl);
        }
    }

    /**
     * 列存在但仍为非空约束时改为可空
     *
     * @param table  表名
     * @param column 列名
     * @param ddl    ALTER 语句
     */
    private void ensureNullableColumn(String table, String column, String ddl) {
        String nullable = jdbcTemplate.queryForObject(
                """
                        SELECT IS_NULLABLE FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                        """,
                String.class, table, column);
        if (!"YES".equalsIgnoreCase(nullable)) {
            jdbcTemplate.execute(ddl);
        }
    }
}
