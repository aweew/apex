CREATE TABLE IF NOT EXISTS trader (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    name VARCHAR(64) NOT NULL COMMENT '交易者名称',
    nickname VARCHAR(64) NULL COMMENT '交易者昵称',
    wechat_peer_id VARCHAR(128) NULL COMMENT '微信会话标识',
    avatar VARCHAR(512) NULL COMMENT '头像地址',
    initial_capital DECIMAL(18, 2) NOT NULL DEFAULT 1000000.00 COMMENT '初始资金',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_trader_wechat_peer_id (wechat_peer_id),
    KEY idx_trader_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Smart Trader交易者';

CREATE TABLE IF NOT EXISTS trade_event (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    trader_id BIGINT NOT NULL COMMENT '交易者ID',
    event_type VARCHAR(24) NOT NULL COMMENT '事件类型',
    symbol VARCHAR(16) NULL COMMENT '证券代码',
    stock_name VARCHAR(64) NULL COMMENT '证券名称',
    side VARCHAR(16) NULL COMMENT '交易方向',
    quantity INT NULL COMMENT '交易数量',
    price DECIMAL(16, 4) NULL COMMENT '交易价格',
    trade_time DATETIME NULL COMMENT '交易时间',
    confidence DECIMAL(6, 4) NOT NULL COMMENT '解析置信度',
    source VARCHAR(24) NOT NULL COMMENT '事件来源',
    raw_text TEXT NOT NULL COMMENT '原始文本',
    idempotency_key VARCHAR(128) NULL COMMENT '幂等键',
    status VARCHAR(24) NOT NULL COMMENT '处理状态',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_trade_event_idempotency (idempotency_key),
    KEY idx_trade_event_trader_created (trader_id, create_time),
    KEY idx_trade_event_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Smart Trader交易事件';

CREATE TABLE IF NOT EXISTS trade_evidence (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    trade_event_id BIGINT NOT NULL COMMENT '交易事件ID',
    trader_id BIGINT NOT NULL COMMENT '交易者ID',
    source VARCHAR(24) NOT NULL COMMENT '证据来源',
    raw_text TEXT NULL COMMENT '原始文本',
    image_url VARCHAR(1024) NULL COMMENT '证据图片地址',
    parsed_result MEDIUMTEXT NOT NULL COMMENT '解析结果',
    confidence DECIMAL(6, 4) NOT NULL COMMENT '解析置信度',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_trade_evidence_event (trade_event_id),
    KEY idx_trade_evidence_trader (trader_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Smart Trader交易证据';

CREATE TABLE IF NOT EXISTS trader_trade (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    trader_id BIGINT NOT NULL COMMENT '交易者ID',
    symbol VARCHAR(16) NOT NULL COMMENT '证券代码',
    stock_name VARCHAR(64) NULL COMMENT '证券名称',
    side VARCHAR(8) NOT NULL COMMENT '交易方向',
    quantity INT NOT NULL COMMENT '成交数量',
    price DECIMAL(16, 4) NOT NULL COMMENT '成交价格',
    amount DECIMAL(18, 2) NOT NULL COMMENT '成交金额',
    trade_time DATETIME NULL COMMENT '成交时间',
    evidence_id BIGINT NOT NULL COMMENT '交易证据ID',
    status VARCHAR(16) NOT NULL DEFAULT 'VALID' COMMENT '交易状态',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_trader_trade_evidence (evidence_id),
    KEY idx_trader_trade_trader_time (trader_id, trade_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Smart Trader正式交易流水';

CREATE TABLE IF NOT EXISTS trader_position (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    trader_id BIGINT NOT NULL COMMENT '交易者ID',
    symbol VARCHAR(16) NOT NULL COMMENT '证券代码',
    stock_name VARCHAR(64) NULL COMMENT '证券名称',
    quantity INT NOT NULL DEFAULT 0 COMMENT '持仓数量',
    avg_cost DECIMAL(16, 4) NOT NULL DEFAULT 0 COMMENT '持仓成本价',
    market_price DECIMAL(16, 4) NULL COMMENT '最新市场价',
    market_value DECIMAL(18, 2) NULL COMMENT '持仓市值',
    profit DECIMAL(18, 2) NULL COMMENT '持仓盈亏',
    profit_rate DECIMAL(12, 6) NULL COMMENT '持仓收益率',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_trader_position (trader_id, symbol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Smart Trader当前持仓';

CREATE TABLE IF NOT EXISTS trader_portfolio_daily (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    trader_id BIGINT NOT NULL COMMENT '交易者ID',
    trade_date DATE NOT NULL COMMENT '交易日期',
    cash DECIMAL(18, 2) NOT NULL COMMENT '现金余额',
    market_value DECIMAL(18, 2) NOT NULL COMMENT '持仓市值',
    total_asset DECIMAL(18, 2) NOT NULL COMMENT '总资产',
    daily_profit DECIMAL(18, 2) NOT NULL COMMENT '当日盈亏',
    daily_profit_rate DECIMAL(12, 6) NOT NULL COMMENT '当日收益率',
    total_profit DECIMAL(18, 2) NOT NULL COMMENT '累计盈亏',
    total_profit_rate DECIMAL(12, 6) NOT NULL COMMENT '累计收益率',
    max_drawdown DECIMAL(12, 6) NOT NULL COMMENT '最大回撤',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_trader_portfolio_daily (trader_id, trade_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Smart Trader每日账户';

CREATE TABLE IF NOT EXISTS trader_ranking_daily (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    trade_date DATE NOT NULL COMMENT '交易日期',
    trader_id BIGINT NOT NULL COMMENT '交易者ID',
    total_return DECIMAL(12, 6) NOT NULL COMMENT '累计收益率',
    daily_return DECIMAL(12, 6) NOT NULL COMMENT '当日收益率',
    max_drawdown DECIMAL(12, 6) NOT NULL COMMENT '最大回撤',
    win_rate DECIMAL(12, 6) NOT NULL COMMENT '胜率',
    profit_loss_ratio DECIMAL(12, 6) NOT NULL COMMENT '盈亏比',
    sharpe DECIMAL(12, 6) NULL COMMENT '夏普比率',
    trader_score DECIMAL(12, 6) NOT NULL COMMENT '综合评分',
    return_ranking INT NULL COMMENT '累计收益排名',
    daily_ranking INT NULL COMMENT '当日收益排名',
    steady_ranking INT NULL COMMENT '稳健性排名',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_trader_ranking_daily (trade_date, trader_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Smart Trader每日排名';

CREATE TABLE IF NOT EXISTS trader_profile (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    trader_id BIGINT NOT NULL COMMENT '交易者ID',
    style VARCHAR(32) NOT NULL COMMENT '交易风格',
    preferred_industries MEDIUMTEXT NULL COMMENT '偏好行业',
    average_holding_days DECIMAL(12, 4) NULL COMMENT '平均持有天数',
    win_rate DECIMAL(12, 6) NULL COMMENT '胜率',
    profit_loss_ratio DECIMAL(12, 6) NULL COMMENT '盈亏比',
    max_drawdown DECIMAL(12, 6) NULL COMMENT '最大回撤',
    turnover_rate DECIMAL(12, 6) NULL COMMENT '换手率',
    volatility DECIMAL(12, 6) NULL COMMENT '收益波动率',
    concentration DECIMAL(12, 6) NULL COMMENT '持仓集中度',
    summary VARCHAR(512) NULL COMMENT '画像摘要',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_trader_profile (trader_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Smart Trader画像';

CREATE TABLE IF NOT EXISTS smart_money_factor (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    trade_date DATE NOT NULL COMMENT '交易日期',
    symbol VARCHAR(16) NOT NULL COMMENT '证券代码',
    stock_name VARCHAR(64) NULL COMMENT '证券名称',
    net_buy_amount DECIMAL(18, 2) NOT NULL COMMENT '净买入金额',
    trader_total_asset DECIMAL(18, 2) NOT NULL COMMENT '交易者总资产',
    factor_value DECIMAL(12, 6) NOT NULL COMMENT '因子值',
    trader_count INT NOT NULL COMMENT '参与交易者数量',
    consensus DECIMAL(12, 6) NOT NULL COMMENT '共识度',
    return_1d DECIMAL(12, 6) NULL COMMENT '1日收益率',
    return_5d DECIMAL(12, 6) NULL COMMENT '5日收益率',
    return_10d DECIMAL(12, 6) NULL COMMENT '10日收益率',
    return_20d DECIMAL(12, 6) NULL COMMENT '20日收益率',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_smart_money_factor (trade_date, symbol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Smart Money因子';
