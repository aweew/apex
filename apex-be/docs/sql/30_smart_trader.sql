CREATE TABLE IF NOT EXISTS trader (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL,
    nickname VARCHAR(64) NULL,
    wechat_peer_id VARCHAR(128) NULL,
    avatar VARCHAR(512) NULL,
    initial_capital DECIMAL(18, 2) NOT NULL DEFAULT 1000000.00,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_trader_wechat_peer_id (wechat_peer_id),
    KEY idx_trader_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Smart Trader交易者';

CREATE TABLE IF NOT EXISTS trade_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    trader_id BIGINT NOT NULL,
    event_type VARCHAR(24) NOT NULL,
    symbol VARCHAR(16) NULL,
    stock_name VARCHAR(64) NULL,
    side VARCHAR(16) NULL,
    quantity INT NULL,
    price DECIMAL(16, 4) NULL,
    trade_time DATETIME NULL,
    confidence DECIMAL(6, 4) NOT NULL,
    source VARCHAR(24) NOT NULL,
    raw_text TEXT NOT NULL,
    idempotency_key VARCHAR(128) NULL,
    status VARCHAR(24) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_trade_event_idempotency (idempotency_key),
    KEY idx_trade_event_trader_created (trader_id, create_time),
    KEY idx_trade_event_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Smart Trader交易事件';

CREATE TABLE IF NOT EXISTS trade_evidence (
    id BIGINT NOT NULL AUTO_INCREMENT,
    trade_event_id BIGINT NOT NULL,
    trader_id BIGINT NOT NULL,
    source VARCHAR(24) NOT NULL,
    raw_text TEXT NULL,
    image_url VARCHAR(1024) NULL,
    parsed_result MEDIUMTEXT NOT NULL,
    confidence DECIMAL(6, 4) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_trade_evidence_event (trade_event_id),
    KEY idx_trade_evidence_trader (trader_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Smart Trader交易证据';

CREATE TABLE IF NOT EXISTS trader_trade (
    id BIGINT NOT NULL AUTO_INCREMENT,
    trader_id BIGINT NOT NULL,
    symbol VARCHAR(16) NOT NULL,
    stock_name VARCHAR(64) NULL,
    side VARCHAR(8) NOT NULL,
    quantity INT NOT NULL,
    price DECIMAL(16, 4) NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    trade_time DATETIME NULL,
    evidence_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'VALID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_trader_trade_evidence (evidence_id),
    KEY idx_trader_trade_trader_time (trader_id, trade_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Smart Trader正式交易流水';

CREATE TABLE IF NOT EXISTS trader_position (
    id BIGINT NOT NULL AUTO_INCREMENT, trader_id BIGINT NOT NULL, symbol VARCHAR(16) NOT NULL, stock_name VARCHAR(64) NULL, quantity INT NOT NULL DEFAULT 0, avg_cost DECIMAL(16, 4) NOT NULL DEFAULT 0, market_price DECIMAL(16, 4) NULL, market_value DECIMAL(18, 2) NULL, profit DECIMAL(18, 2) NULL, profit_rate DECIMAL(12, 6) NULL, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id), UNIQUE KEY uk_trader_position (trader_id, symbol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Smart Trader当前持仓';

CREATE TABLE IF NOT EXISTS trader_portfolio_daily (
    id BIGINT NOT NULL AUTO_INCREMENT, trader_id BIGINT NOT NULL, trade_date DATE NOT NULL, cash DECIMAL(18, 2) NOT NULL, market_value DECIMAL(18, 2) NOT NULL, total_asset DECIMAL(18, 2) NOT NULL, daily_profit DECIMAL(18, 2) NOT NULL, daily_profit_rate DECIMAL(12, 6) NOT NULL, total_profit DECIMAL(18, 2) NOT NULL, total_profit_rate DECIMAL(12, 6) NOT NULL, max_drawdown DECIMAL(12, 6) NOT NULL, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id), UNIQUE KEY uk_trader_portfolio_daily (trader_id, trade_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Smart Trader每日账户';

CREATE TABLE IF NOT EXISTS trader_ranking_daily (
    id BIGINT NOT NULL AUTO_INCREMENT, trade_date DATE NOT NULL, trader_id BIGINT NOT NULL, total_return DECIMAL(12, 6) NOT NULL, daily_return DECIMAL(12, 6) NOT NULL, max_drawdown DECIMAL(12, 6) NOT NULL, win_rate DECIMAL(12, 6) NOT NULL, profit_loss_ratio DECIMAL(12, 6) NOT NULL, sharpe DECIMAL(12, 6) NULL, trader_score DECIMAL(12, 6) NOT NULL, return_ranking INT NULL, daily_ranking INT NULL, steady_ranking INT NULL, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id), UNIQUE KEY uk_trader_ranking_daily (trade_date, trader_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Smart Trader每日排名';

CREATE TABLE IF NOT EXISTS trader_profile (
    id BIGINT NOT NULL AUTO_INCREMENT, trader_id BIGINT NOT NULL, style VARCHAR(32) NOT NULL, preferred_industries MEDIUMTEXT NULL, average_holding_days DECIMAL(12, 4) NULL, win_rate DECIMAL(12, 6) NULL, profit_loss_ratio DECIMAL(12, 6) NULL, max_drawdown DECIMAL(12, 6) NULL, turnover_rate DECIMAL(12, 6) NULL, volatility DECIMAL(12, 6) NULL, concentration DECIMAL(12, 6) NULL, summary VARCHAR(512) NULL, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id), UNIQUE KEY uk_trader_profile (trader_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Smart Trader画像';

CREATE TABLE IF NOT EXISTS smart_money_factor (
    id BIGINT NOT NULL AUTO_INCREMENT, trade_date DATE NOT NULL, symbol VARCHAR(16) NOT NULL, stock_name VARCHAR(64) NULL, net_buy_amount DECIMAL(18, 2) NOT NULL, trader_total_asset DECIMAL(18, 2) NOT NULL, factor_value DECIMAL(12, 6) NOT NULL, trader_count INT NOT NULL, consensus DECIMAL(12, 6) NOT NULL, return_1d DECIMAL(12, 6) NULL, return_5d DECIMAL(12, 6) NULL, return_10d DECIMAL(12, 6) NULL, return_20d DECIMAL(12, 6) NULL, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id), UNIQUE KEY uk_smart_money_factor (trade_date, symbol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Smart Money因子';
