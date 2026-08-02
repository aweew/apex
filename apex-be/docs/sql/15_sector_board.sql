USE apex;

CREATE TABLE IF NOT EXISTS sector_basic (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(64) NOT NULL,
    board_type VARCHAR(16) NOT NULL,
    source VARCHAR(32) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sector_basic_code_type (code, board_type),
    KEY idx_sector_basic_type (board_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sector_quote (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(64) NOT NULL,
    board_type VARCHAR(16) NOT NULL,
    trade_date DATE NOT NULL,
    pct_chg DECIMAL(12, 4) NULL,
    net_inflow DECIMAL(24, 4) NULL,
    main_net_inflow DECIMAL(24, 4) NULL,
    amount DECIMAL(24, 4) NULL,
    up_count INT NULL,
    down_count INT NULL,
    lead_stock_code VARCHAR(16) NULL,
    lead_stock_name VARCHAR(64) NULL,
    lead_stock_pct DECIMAL(12, 4) NULL,
    synced_at DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sector_quote_code_type_date (code, board_type, trade_date),
    KEY idx_sector_quote_type_date (board_type, trade_date),
    KEY idx_sector_quote_type_pct (board_type, trade_date, pct_chg),
    KEY idx_sector_quote_type_inflow (board_type, trade_date, net_inflow)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sector_constituent (
    id BIGINT NOT NULL AUTO_INCREMENT,
    sector_code VARCHAR(32) NOT NULL,
    board_type VARCHAR(16) NOT NULL,
    stock_code VARCHAR(16) NOT NULL,
    stock_name VARCHAR(64) NULL,
    pct_chg DECIMAL(12, 4) NULL,
    latest_price DECIMAL(16, 4) NULL,
    trade_date DATE NOT NULL,
    synced_at DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sector_cons (sector_code, board_type, stock_code, trade_date),
    KEY idx_sector_cons_sector_date (sector_code, board_type, trade_date),
    KEY idx_sector_cons_stock (stock_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
