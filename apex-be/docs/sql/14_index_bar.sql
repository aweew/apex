USE apex;

CREATE TABLE IF NOT EXISTS index_bar (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(64) NOT NULL,
    region VARCHAR(16) NOT NULL,
    trade_date DATE NOT NULL,
    open_price DECIMAL(20, 4) NULL,
    high_price DECIMAL(20, 4) NULL,
    low_price DECIMAL(20, 4) NULL,
    close_price DECIMAL(20, 4) NULL,
    volume DECIMAL(24, 4) NULL,
    amount DECIMAL(24, 4) NULL,
    pct_chg DECIMAL(12, 4) NULL,
    source VARCHAR(32) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_index_bar_code_date (code, trade_date),
    KEY idx_index_bar_region_date (region, trade_date),
    KEY idx_index_bar_date (trade_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
