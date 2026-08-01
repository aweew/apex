USE apex;

CREATE TABLE IF NOT EXISTS market_hot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source VARCHAR(32) NOT NULL,
    snapshot_time DATETIME NOT NULL,
    rank_no INT NOT NULL,
    code VARCHAR(16) NULL,
    name VARCHAR(64) NULL,
    price DECIMAL(16, 4) NULL,
    pct_chg DECIMAL(12, 4) NULL,
    heat_score DECIMAL(20, 4) NULL,
    heat_text VARCHAR(64) NULL,
    payload JSON NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_market_hot_source_time (source, snapshot_time),
    KEY idx_market_hot_code_time (code, snapshot_time),
    KEY idx_market_hot_time_rank (snapshot_time, rank_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
