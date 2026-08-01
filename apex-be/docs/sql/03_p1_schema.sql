USE apex;

CREATE TABLE IF NOT EXISTS universe_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    batch_no VARCHAR(64) NOT NULL COMMENT '批次号',
    code VARCHAR(16) NOT NULL COMMENT '证券代码',
    name VARCHAR(64) NULL COMMENT '证券简称',
    reason_tags VARCHAR(512) NULL COMMENT '过滤标记，逗号分隔',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_universe_batch (batch_no),
    KEY idx_universe_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='股票池快照';

CREATE TABLE IF NOT EXISTS strategy_signal (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    code VARCHAR(16) NOT NULL COMMENT '证券代码',
    strategy_id VARCHAR(16) NOT NULL COMMENT '策略ID',
    signal_date DATE NOT NULL COMMENT '信号日',
    side VARCHAR(8) NOT NULL COMMENT 'BUY/SELL/HOLD',
    score DECIMAL(10, 4) NULL COMMENT '评分',
    reason_json VARCHAR(1024) NULL COMMENT '理由JSON',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_signal_date (signal_date),
    KEY idx_signal_code_strategy (code, strategy_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='策略信号';

CREATE TABLE IF NOT EXISTS backtest_job (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    code VARCHAR(16) NOT NULL COMMENT '证券代码',
    strategy_id VARCHAR(16) NOT NULL COMMENT '策略ID',
    begin_date DATE NOT NULL COMMENT '开始日期',
    end_date DATE NOT NULL COMMENT '结束日期',
    init_cash DECIMAL(18, 2) NOT NULL COMMENT '初始资金',
    final_cash DECIMAL(18, 2) NULL COMMENT '期末权益',
    total_return DECIMAL(12, 6) NULL COMMENT '累计收益',
    annual_return DECIMAL(12, 6) NULL COMMENT '年化收益',
    max_drawdown DECIMAL(12, 6) NULL COMMENT '最大回撤',
    sharpe DECIMAL(12, 6) NULL COMMENT '夏普',
    sortino DECIMAL(12, 6) NULL COMMENT 'Sortino',
    win_rate DECIMAL(12, 6) NULL COMMENT '胜率',
    profit_factor DECIMAL(12, 6) NULL COMMENT '盈亏比',
    avg_hold_days DECIMAL(12, 4) NULL COMMENT '平均持仓天数',
    trade_count INT NULL COMMENT '成交笔数',
    status VARCHAR(16) NOT NULL COMMENT 'SUCCESS/FAIL',
    disclaimer VARCHAR(256) NOT NULL COMMENT '免责声明',
    message VARCHAR(1024) NULL COMMENT '消息',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_backtest_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回测任务';

CREATE TABLE IF NOT EXISTS backtest_trade (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    job_id BIGINT NOT NULL COMMENT '回测任务ID',
    trade_date DATE NOT NULL COMMENT '成交日',
    side VARCHAR(8) NOT NULL COMMENT 'BUY/SELL',
    price DECIMAL(16, 4) NOT NULL COMMENT '成交价',
    quantity INT NOT NULL COMMENT '数量(股)',
    amount DECIMAL(18, 2) NOT NULL COMMENT '成交额',
    fee DECIMAL(18, 4) NOT NULL COMMENT '费用',
    reason VARCHAR(256) NULL COMMENT '原因',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_bt_trade_job (job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回测成交';

CREATE TABLE IF NOT EXISTS backtest_equity (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    job_id BIGINT NOT NULL COMMENT '回测任务ID',
    trade_date DATE NOT NULL COMMENT '交易日',
    equity DECIMAL(18, 2) NOT NULL COMMENT '权益',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_bt_equity_job_date (job_id, trade_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回测资金曲线';
