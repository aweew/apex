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
            ensurePortfolioTables();
            ensureCompanyProfileRevenueColumns();
            ensureStockBasicPeColumns();
        } catch (Exception ex) {
            log.warn("schema bootstrap skipped: {}", ex.getMessage());
        }
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
                    action VARCHAR(16) NULL COMMENT 'BUY/SELL/HOLD/AVOID',
                    feature_version VARCHAR(64) NOT NULL COMMENT '特征版本',
                    feature_hash VARCHAR(64) NOT NULL COMMENT '特征SHA-256',
                    signal_score DECIMAL(10, 4) NULL COMMENT '策略信号分',
                    confluence_count INT NULL COMMENT '共振策略数',
                    hot_source_count INT NULL COMMENT '热点来源数',
                    mainline_match TINYINT NULL COMMENT '是否匹配主线',
                    valuation_level VARCHAR(32) NULL COMMENT '估值档位',
                    market_stance VARCHAR(16) NULL COMMENT '市场状态',
                    data_quality VARCHAR(16) NULL COMMENT '特征数据质量',
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
                    action_id BIGINT NOT NULL COMMENT '操作清单ID',
                    run_id BIGINT NULL COMMENT '决策运行ID',
                    code VARCHAR(16) NOT NULL COMMENT '证券代码',
                    action_date DATE NOT NULL COMMENT '决策交易日',
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
                    UNIQUE KEY uk_decision_outcome_action (action_id),
                    KEY idx_decision_outcome_run_code (run_id, code),
                    KEY idx_decision_outcome_date (action_date)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能决策结果归因'
                """);
        log.info("schema ready: decision_run / decision_feature_snapshot / decision_outcome");
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
     * 缺列则补齐
     *
     * @param table  表名
     * @param column 列名
     * @param ddl    ALTER 语句
     */
    private boolean ensureColumn(String table, String column, String ddl) {
        Integer cnt = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                        """,
                Integer.class, table, column);
        if (Objects.isNull(cnt) || cnt == 0) {
            jdbcTemplate.execute(ddl);
            return true;
        }
        return false;
    }
}
