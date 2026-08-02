USE apex;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
