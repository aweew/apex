package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;
import java.util.List;

/**
 * 创建市场行为信号中心数据结构。
 */
public class V57__Create_signal_center_schema extends BaseJavaMigration {

    /**
     * 创建信号定义、事件、生命周期、查询投影和用户订阅表。
     *
     * @param context Flyway迁移上下文
     * @throws Exception SQL执行失败
     */
    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            for (String createTableSql : createTableSqlList()) {
                statement.execute(createTableSql);
            }
            seedSignalDefinitions(statement);
        }
    }

    private void seedSignalDefinitions(Statement statement) throws Exception {
        statement.executeUpdate("""
                INSERT IGNORE INTO signal_definition
                    (signal_code, signal_name, signal_category, signal_direction, description,
                     default_priority, supported_timeframes, valid_period_bars, enabled)
                VALUES
                    ('S001','放量突破','STRUCTURE','BULLISH','放量突破前序阻力价格带',90,'[\"DAY\",\"WEEK\"]',3,1),
                    ('S002','缩量突破','STRUCTURE','BULLISH','缩量突破且上方供给下降',70,'[\"DAY\",\"WEEK\"]',2,1),
                    ('S003','平台突破','STRUCTURE','BULLISH','波动收缩后的平台上沿突破',85,'[\"DAY\",\"WEEK\"]',5,1),
                    ('S004','突破后回踩不破','STRUCTURE','BULLISH','突破后缩量回踩仍守住价格带',88,'[\"DAY\",\"WEEK\"]',8,1),
                    ('S005','二次突破','STRUCTURE','BULLISH','已确认突破和回踩后的再次突破',86,'[\"DAY\",\"WEEK\"]',3,1),
                    ('S006','趋势加速','TREND','BULLISH','均线和量价效率同向加速',72,'[\"DAY\",\"WEEK\"]',3,1),
                    ('W001','多次突破失败','STRUCTURE','BEARISH','同一阻力带多次尝试未能站稳',72,'[\"DAY\",\"WEEK\"]',5,1),
                    ('W002','努力与结果背离','VOLUME','BEARISH','量能投入与价格推进效率背离',75,'[\"DAY\",\"WEEK\"]',3,1),
                    ('W003','阻力位放量大阴','CANDLE','BEARISH','强阻力附近放量长阴且收盘偏低',88,'[\"DAY\",\"WEEK\"]',3,1),
                    ('W004','高位放量滞涨','VOLUME','BEARISH','高位量能增加但价格推进减弱',78,'[\"DAY\",\"WEEK\"]',5,1),
                    ('W005','冲高回落','CANDLE','BEARISH','盘中突破后收回阻力下方',76,'[\"DAY\",\"WEEK\"]',3,1),
                    ('W006','假突破','STRUCTURE','BEARISH','突破后在有效窗口跌回价格带',92,'[\"DAY\",\"WEEK\"]',5,1),
                    ('R001','过度加速','RISK','RISK','价格相对均线和ATR偏离过高',90,'[\"DAY\",\"WEEK\"]',3,1),
                    ('R002','高位量价背离','RISK','RISK','价格创新高但量价效率未同步',82,'[\"DAY\",\"WEEK\"]',5,1),
                    ('R003','支撑破坏','RISK','RISK','收盘有效跌破强支撑价格带',95,'[\"DAY\",\"WEEK\"]',3,1),
                    ('R004','波动异常','RISK','RISK','ATR或日内振幅进入异常区间',84,'[\"DAY\",\"WEEK\"]',3,1),
                    ('R005','流动性不足','RISK','RISK','平均成交额不足或零成交频发',96,'[\"DAY\",\"WEEK\"]',10,1),
                    ('R006','环境逆风','CONTEXT','RISK','个股偏强但指数和板块同时走弱',80,'[\"DAY\",\"WEEK\"]',3,1),
                    ('R007','数据风险','RISK','RISK','关键行情或上下文数据不完整',100,'[\"DAY\",\"WEEK\"]',1,1)
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
                WHERE t1.deleted = 0
                """);
    }

    private List<String> createTableSqlList() {
        return List.of(
                """
                CREATE TABLE IF NOT EXISTS signal_definition (
                    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                    signal_code VARCHAR(32) NOT NULL COMMENT '稳定信号编码',
                    signal_name VARCHAR(64) NOT NULL COMMENT '信号名称',
                    signal_category VARCHAR(32) NOT NULL COMMENT '信号分类',
                    signal_direction VARCHAR(16) NOT NULL COMMENT '信号方向',
                    description VARCHAR(512) NOT NULL COMMENT '业务定义',
                    default_priority INT NOT NULL DEFAULT 50 COMMENT '默认优先级',
                    supported_timeframes VARCHAR(128) NOT NULL COMMENT '支持周期JSON数组',
                    valid_period_bars INT NOT NULL COMMENT '默认有效Bar数量',
                    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用0否1是',
                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_signal_definition_code (signal_code),
                    KEY idx_signal_definition_category (signal_category, enabled, deleted)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='市场行为信号定义'
                """,
                """
                CREATE TABLE IF NOT EXISTS signal_rule (
                    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                    definition_id BIGINT NOT NULL COMMENT '信号定义ID',
                    rule_version VARCHAR(32) NOT NULL COMMENT '规则版本',
                    schema_version VARCHAR(16) NOT NULL COMMENT 'DSL结构版本',
                    rule_json JSON NOT NULL COMMENT '规则AST',
                    parameter_json JSON NOT NULL COMMENT '参数定义与快照',
                    feature_version VARCHAR(32) NOT NULL COMMENT '特征版本',
                    rule_status VARCHAR(16) NOT NULL COMMENT '规则状态',
                    rollout_percent INT NOT NULL DEFAULT 0 COMMENT '灰度比例0到100',
                    checksum CHAR(64) NOT NULL COMMENT '规则内容SHA256',
                    effective_time DATETIME NULL COMMENT '生效时间',
                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_signal_rule_version (definition_id, rule_version),
                    UNIQUE KEY uk_signal_rule_checksum (definition_id, checksum),
                    KEY idx_signal_rule_status (rule_status, effective_time, deleted)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='信号规则版本'
                """,
                """
                CREATE TABLE IF NOT EXISTS signal_calculation_run (
                    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                    run_no VARCHAR(64) NOT NULL COMMENT '计算批次号',
                    trigger_type VARCHAR(16) NOT NULL COMMENT '触发类型',
                    timeframe VARCHAR(16) NOT NULL COMMENT '计算周期',
                    as_of_time DATETIME NOT NULL COMMENT '可见数据截止时间',
                    feature_version VARCHAR(32) NOT NULL COMMENT '特征版本',
                    rule_set_checksum CHAR(64) NOT NULL COMMENT '规则集合指纹',
                    run_status VARCHAR(16) NOT NULL COMMENT '批次状态',
                    total_count INT NOT NULL DEFAULT 0 COMMENT '计划证券数',
                    success_count INT NOT NULL DEFAULT 0 COMMENT '成功证券数',
                    failure_count INT NOT NULL DEFAULT 0 COMMENT '失败证券数',
                    started_at DATETIME NULL COMMENT '开始时间',
                    finished_at DATETIME NULL COMMENT '结束时间',
                    error_message VARCHAR(1024) NULL COMMENT '批次错误摘要',
                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_signal_calculation_run_no (run_no),
                    KEY idx_signal_calculation_run_query (timeframe, as_of_time, run_status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='信号计算批次'
                """,
                """
                CREATE TABLE IF NOT EXISTS signal_chain (
                    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                    chain_no VARCHAR(64) NOT NULL COMMENT '行为链编号',
                    instrument_type VARCHAR(16) NOT NULL COMMENT '证券类型',
                    symbol VARCHAR(32) NOT NULL COMMENT '证券代码',
                    timeframe VARCHAR(16) NOT NULL COMMENT '周期',
                    chain_direction VARCHAR(16) NOT NULL COMMENT '行为链方向',
                    structure_level_id BIGINT NULL COMMENT '结构价格带ID',
                    chain_type VARCHAR(32) NOT NULL COMMENT '行为链类型',
                    current_state VARCHAR(32) NOT NULL COMMENT '当前链状态',
                    start_time DATETIME NOT NULL COMMENT '开始时间',
                    last_event_time DATETIME NOT NULL COMMENT '最后事件时间',
                    end_time DATETIME NULL COMMENT '结束时间',
                    summary_json JSON NULL COMMENT '确定性摘要数据',
                    chain_version INT NOT NULL DEFAULT 1 COMMENT '链版本',
                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_signal_chain_no (chain_no),
                    KEY idx_signal_chain_symbol (instrument_type, symbol, timeframe, current_state, last_event_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='市场行为信号链'
                """,
                """
                CREATE TABLE IF NOT EXISTS signal_event (
                    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                    event_no VARCHAR(64) NOT NULL COMMENT '事件编号',
                    definition_id BIGINT NOT NULL COMMENT '信号定义ID',
                    rule_id BIGINT NOT NULL COMMENT '规则版本ID',
                    calculation_run_id BIGINT NOT NULL COMMENT '计算批次ID',
                    instrument_type VARCHAR(16) NOT NULL COMMENT '证券类型',
                    symbol VARCHAR(32) NOT NULL COMMENT '证券代码',
                    timeframe VARCHAR(16) NOT NULL COMMENT '周期',
                    trigger_time DATETIME NOT NULL COMMENT '首次触发时间',
                    as_of_time DATETIME NOT NULL COMMENT '可见数据截止时间',
                    signal_direction VARCHAR(16) NOT NULL COMMENT '信号方向',
                    lifecycle_state VARCHAR(24) NOT NULL COMMENT '生命周期状态',
                    strength_score DECIMAL(8, 4) NOT NULL COMMENT '行为强度0到100',
                    confidence_score DECIMAL(8, 4) NOT NULL COMMENT '置信度0到100',
                    probability_value DECIMAL(10, 6) NULL COMMENT '历史条件概率0到1',
                    risk_score DECIMAL(8, 4) NOT NULL DEFAULT 0 COMMENT '风险分0到100',
                    structure_level_id BIGINT NULL COMMENT '结构价格带ID',
                    parent_event_id BIGINT NULL COMMENT '直接父事件ID',
                    chain_id BIGINT NULL COMMENT '行为链ID',
                    valid_until DATETIME NULL COMMENT '有效截止时间',
                    evidence_json JSON NOT NULL COMMENT '结构化证据',
                    data_status VARCHAR(16) NOT NULL COMMENT '数据完整状态',
                    feature_version VARCHAR(32) NOT NULL COMMENT '特征版本',
                    published TINYINT NOT NULL DEFAULT 1 COMMENT '是否对查询发布0否1是',
                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_signal_event_no (event_no),
                    UNIQUE KEY uk_signal_event_idempotent (instrument_type, symbol, timeframe, definition_id, rule_id, trigger_time),
                    KEY idx_signal_event_rank (trigger_time, timeframe, signal_direction, lifecycle_state, published),
                    KEY idx_signal_event_symbol (symbol, timeframe, trigger_time),
                    KEY idx_signal_event_chain (chain_id, trigger_time),
                    KEY idx_signal_event_run (calculation_run_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='市场行为信号事件'
                """,
                """
                CREATE TABLE IF NOT EXISTS signal_snapshot (
                    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                    instrument_type VARCHAR(16) NOT NULL COMMENT '证券类型',
                    symbol VARCHAR(32) NOT NULL COMMENT '证券代码',
                    timeframe VARCHAR(16) NOT NULL COMMENT '周期',
                    definition_id BIGINT NOT NULL COMMENT '信号定义ID',
                    event_id BIGINT NOT NULL COMMENT '当前事件ID',
                    lifecycle_state VARCHAR(24) NOT NULL COMMENT '当前生命周期状态',
                    strength_score DECIMAL(8, 4) NOT NULL COMMENT '当前行为强度',
                    confidence_score DECIMAL(8, 4) NOT NULL COMMENT '当前置信度',
                    probability_value DECIMAL(10, 6) NULL COMMENT '历史条件概率',
                    risk_score DECIMAL(8, 4) NOT NULL DEFAULT 0 COMMENT '当前风险分',
                    market_state_id BIGINT NULL COMMENT '市场阶段快照ID',
                    snapshot_time DATETIME NOT NULL COMMENT '快照时间',
                    freshness_status VARCHAR(16) NOT NULL COMMENT '新鲜度状态',
                    evidence_summary_json JSON NOT NULL COMMENT '查询证据摘要',
                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_signal_snapshot_current (instrument_type, symbol, timeframe, definition_id),
                    KEY idx_signal_snapshot_rank (timeframe, lifecycle_state, strength_score, confidence_score),
                    KEY idx_signal_snapshot_event (event_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='信号当前查询快照'
                """,
                """
                CREATE TABLE IF NOT EXISTS signal_relation (
                    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                    source_event_id BIGINT NOT NULL COMMENT '源事件ID',
                    target_event_id BIGINT NOT NULL COMMENT '目标事件ID',
                    relation_type VARCHAR(16) NOT NULL COMMENT '事件关系类型',
                    relation_strength DECIMAL(8, 4) NULL COMMENT '关系强度0到100',
                    reason_json JSON NOT NULL COMMENT '关系证据',
                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_signal_relation (source_event_id, target_event_id, relation_type),
                    KEY idx_signal_relation_target (target_event_id, relation_type)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='信号事件关系'
                """,
                """
                CREATE TABLE IF NOT EXISTS signal_confirmation (
                    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                    event_id BIGINT NOT NULL COMMENT '被确认事件ID',
                    confirmation_code VARCHAR(32) NOT NULL COMMENT '确认类型编码',
                    confirmation_status VARCHAR(16) NOT NULL COMMENT '确认状态',
                    observation_start_time DATETIME NOT NULL COMMENT '观察开始时间',
                    observation_end_time DATETIME NULL COMMENT '计划观察结束时间',
                    confirmed_at DATETIME NULL COMMENT '确认时间',
                    evidence_json JSON NULL COMMENT '确认或失败证据',
                    invalid_reason VARCHAR(256) NULL COMMENT '失败原因',
                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_signal_confirmation (event_id, confirmation_code),
                    KEY idx_signal_confirmation_pending (confirmation_status, observation_end_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='信号确认记录'
                """,
                """
                CREATE TABLE IF NOT EXISTS signal_score (
                    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                    event_id BIGINT NOT NULL COMMENT '信号事件ID',
                    score_type VARCHAR(16) NOT NULL COMMENT '评分类型',
                    raw_score DECIMAL(10, 6) NOT NULL COMMENT '原始分值',
                    adjusted_score DECIMAL(10, 6) NOT NULL COMMENT '修正后分值',
                    components_json JSON NOT NULL COMMENT '评分贡献明细',
                    score_version VARCHAR(32) NOT NULL COMMENT '评分模型版本',
                    calculated_at DATETIME NOT NULL COMMENT '计算时间',
                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_signal_score (event_id, score_type, score_version),
                    KEY idx_signal_score_type (score_type, adjusted_score)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='信号评分明细'
                """,
                """
                CREATE TABLE IF NOT EXISTS signal_lifecycle (
                    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                    event_id BIGINT NOT NULL COMMENT '信号事件ID',
                    sequence_no INT NOT NULL COMMENT '事件内迁移序号',
                    from_state VARCHAR(24) NULL COMMENT '迁移前状态',
                    to_state VARCHAR(24) NOT NULL COMMENT '迁移后状态',
                    transition_time DATETIME NOT NULL COMMENT '状态迁移时间',
                    as_of_time DATETIME NOT NULL COMMENT '迁移可见数据截止时间',
                    reason_code VARCHAR(32) NOT NULL COMMENT '迁移原因编码',
                    evidence_json JSON NOT NULL COMMENT '迁移证据',
                    calculation_run_id BIGINT NOT NULL COMMENT '计算批次ID',
                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_signal_lifecycle_sequence (event_id, sequence_no),
                    UNIQUE KEY uk_signal_lifecycle_idempotent (event_id, to_state, as_of_time),
                    KEY idx_signal_lifecycle_time (transition_time, to_state)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='信号生命周期迁移'
                """,
                """
                CREATE TABLE IF NOT EXISTS signal_chain_event (
                    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                    chain_id BIGINT NOT NULL COMMENT '行为链ID',
                    event_id BIGINT NOT NULL COMMENT '信号事件ID',
                    sequence_no INT NOT NULL COMMENT '链内顺序',
                    event_role VARCHAR(24) NOT NULL COMMENT '事件角色',
                    event_time DATETIME NOT NULL COMMENT '事件时间',
                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_signal_chain_event (chain_id, event_id),
                    UNIQUE KEY uk_signal_chain_sequence (chain_id, sequence_no),
                    KEY idx_signal_chain_event_time (event_id, event_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行为链事件明细'
                """,
                """
                CREATE TABLE IF NOT EXISTS market_state (
                    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                    instrument_type VARCHAR(16) NOT NULL COMMENT '证券类型',
                    symbol VARCHAR(32) NOT NULL COMMENT '证券代码',
                    timeframe VARCHAR(16) NOT NULL COMMENT '周期',
                    state_code VARCHAR(32) NOT NULL COMMENT '市场阶段编码',
                    confidence_score DECIMAL(8, 4) NOT NULL COMMENT '阶段置信度0到100',
                    as_of_time DATETIME NOT NULL COMMENT '可见数据截止时间',
                    primary_event_ids JSON NOT NULL COMMENT '主要依据事件ID',
                    risk_event_ids JSON NOT NULL COMMENT '风险事件ID',
                    evidence_json JSON NOT NULL COMMENT '阶段证据',
                    calculation_run_id BIGINT NOT NULL COMMENT '计算批次ID',
                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_market_state_snapshot (instrument_type, symbol, timeframe, as_of_time),
                    KEY idx_market_state_query (symbol, timeframe, as_of_time),
                    KEY idx_market_state_rank (state_code, timeframe, confidence_score)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='证券市场阶段快照'
                """,
                """
                CREATE TABLE IF NOT EXISTS support_resistance (
                    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                    instrument_type VARCHAR(16) NOT NULL COMMENT '证券类型',
                    symbol VARCHAR(32) NOT NULL COMMENT '证券代码',
                    timeframe VARCHAR(16) NOT NULL COMMENT '周期',
                    level_type VARCHAR(16) NOT NULL COMMENT '价格带类型',
                    lower_price DECIMAL(16, 4) NOT NULL COMMENT '价格带下沿',
                    upper_price DECIMAL(16, 4) NOT NULL COMMENT '价格带上沿',
                    center_price DECIMAL(16, 4) NOT NULL COMMENT '价格带中心',
                    strength_score DECIMAL(8, 4) NOT NULL COMMENT '价格带强度0到100',
                    touch_count INT NOT NULL DEFAULT 0 COMMENT '有效触碰次数',
                    breakout_count INT NOT NULL DEFAULT 0 COMMENT '有效突破次数',
                    failure_count INT NOT NULL DEFAULT 0 COMMENT '失败次数',
                    source_types VARCHAR(256) NOT NULL COMMENT '来源类型JSON数组',
                    detection_start_time DATETIME NOT NULL COMMENT '识别窗口开始时间',
                    detection_end_time DATETIME NOT NULL COMMENT '识别窗口结束时间',
                    valid_from DATETIME NOT NULL COMMENT '生效时间',
                    valid_to DATETIME NULL COMMENT '失效时间',
                    level_state VARCHAR(16) NOT NULL COMMENT '价格带状态',
                    as_of_time DATETIME NOT NULL COMMENT '可见数据截止时间',
                    feature_version VARCHAR(32) NOT NULL COMMENT '特征版本',
                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    PRIMARY KEY (id),
                    KEY idx_support_resistance_current (instrument_type, symbol, timeframe, level_state, as_of_time),
                    KEY idx_support_resistance_price (symbol, timeframe, center_price)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支撑阻力价格带'
                """,
                """
                CREATE TABLE IF NOT EXISTS signal_backtest (
                    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                    job_no VARCHAR(64) NOT NULL COMMENT '回测任务编号',
                    user_id BIGINT NOT NULL COMMENT '所属用户ID',
                    definition_id BIGINT NOT NULL COMMENT '信号定义ID',
                    rule_id BIGINT NOT NULL COMMENT '规则版本ID',
                    timeframe VARCHAR(16) NOT NULL COMMENT '周期',
                    market_scope VARCHAR(32) NOT NULL COMMENT '市场范围',
                    universe_fingerprint CHAR(64) NOT NULL COMMENT '股票池指纹',
                    begin_date DATE NOT NULL COMMENT '统计开始日期',
                    end_date DATE NOT NULL COMMENT '统计结束日期',
                    hold_horizons_json JSON NOT NULL COMMENT '观察周期',
                    parameter_snapshot_json JSON NOT NULL COMMENT '参数快照',
                    regime_filter VARCHAR(32) NULL COMMENT '市场环境过滤',
                    job_status VARCHAR(16) NOT NULL COMMENT '任务状态',
                    sample_count INT NOT NULL DEFAULT 0 COMMENT '有效样本数',
                    confirmed_count INT NOT NULL DEFAULT 0 COMMENT '确认样本数',
                    invalidated_count INT NOT NULL DEFAULT 0 COMMENT '失效样本数',
                    leakage_check_status VARCHAR(16) NOT NULL COMMENT '未来函数检查状态',
                    started_at DATETIME NULL COMMENT '开始时间',
                    finished_at DATETIME NULL COMMENT '结束时间',
                    error_message VARCHAR(1024) NULL COMMENT '错误信息',
                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_signal_backtest_job_no (job_no),
                    KEY idx_signal_backtest_user (user_id, create_time, deleted),
                    KEY idx_signal_backtest_rule (rule_id, begin_date, end_date)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='信号事件有效性回测任务'
                """,
                """
                CREATE TABLE IF NOT EXISTS signal_statistics (
                    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                    definition_id BIGINT NOT NULL COMMENT '信号定义ID',
                    rule_id BIGINT NOT NULL COMMENT '规则版本ID',
                    timeframe VARCHAR(16) NOT NULL COMMENT '周期',
                    market_scope VARCHAR(32) NOT NULL COMMENT '市场范围',
                    regime_code VARCHAR(32) NOT NULL DEFAULT 'ALL' COMMENT '市场环境',
                    statistic_begin_date DATE NOT NULL COMMENT '统计开始日期',
                    statistic_end_date DATE NOT NULL COMMENT '统计结束日期',
                    horizon_days INT NOT NULL COMMENT '前瞻交易日数',
                    sample_count INT NOT NULL COMMENT '样本数',
                    confirmed_count INT NOT NULL COMMENT '确认次数',
                    invalidated_count INT NOT NULL COMMENT '失效次数',
                    win_count INT NOT NULL COMMENT '正收益次数',
                    win_rate DECIMAL(10, 6) NULL COMMENT '胜率',
                    average_return DECIMAL(12, 6) NULL COMMENT '平均收益率',
                    median_return DECIMAL(12, 6) NULL COMMENT '收益率中位数',
                    maximum_return DECIMAL(12, 6) NULL COMMENT '最大收益率',
                    maximum_drawdown DECIMAL(12, 6) NULL COMMENT '观察期最大回撤',
                    profit_loss_ratio DECIMAL(12, 6) NULL COMMENT '盈亏比',
                    confidence_lower DECIMAL(10, 6) NULL COMMENT '胜率置信区间下限',
                    confidence_upper DECIMAL(10, 6) NULL COMMENT '胜率置信区间上限',
                    backtest_id BIGINT NOT NULL COMMENT '来源回测任务ID',
                    calculated_at DATETIME NOT NULL COMMENT '统计计算时间',
                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_signal_statistics (rule_id, timeframe, market_scope, regime_code, statistic_begin_date, statistic_end_date, horizon_days),
                    KEY idx_signal_statistics_query (definition_id, timeframe, horizon_days, calculated_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='信号历史有效性统计'
                """,
                """
                CREATE TABLE IF NOT EXISTS signal_alert_subscription (
                    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                    user_id BIGINT NOT NULL COMMENT '所属用户ID',
                    definition_id BIGINT NULL COMMENT '指定信号定义ID',
                    signal_category VARCHAR(32) NULL COMMENT '指定信号分类',
                    symbol VARCHAR(32) NULL COMMENT '指定证券代码',
                    timeframe VARCHAR(16) NOT NULL COMMENT '周期',
                    lifecycle_states VARCHAR(256) NOT NULL COMMENT '订阅生命周期状态JSON数组',
                    min_strength DECIMAL(8, 4) NOT NULL DEFAULT 0 COMMENT '最低行为强度',
                    min_confidence DECIMAL(8, 4) NOT NULL DEFAULT 0 COMMENT '最低置信度',
                    channels_json JSON NOT NULL COMMENT '通知渠道配置',
                    cooldown_minutes INT NOT NULL DEFAULT 1440 COMMENT '相同事件冷却分钟数',
                    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用0否1是',
                    last_triggered_at DATETIME NULL COMMENT '最近触发时间',
                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
                    PRIMARY KEY (id),
                    KEY idx_signal_alert_user (user_id, enabled, deleted),
                    KEY idx_signal_alert_match (definition_id, signal_category, symbol, timeframe, enabled)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户信号预警订阅'
                """
        );
    }
}
