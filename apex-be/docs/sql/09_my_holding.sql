USE apex;

CREATE TABLE IF NOT EXISTS my_holding (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(16) NOT NULL,
    name VARCHAR(64) NULL,
    quantity INT NOT NULL DEFAULT 0,
    cost_price DECIMAL(16, 4) NULL,
    stop_loss DECIMAL(16, 4) NULL,
    take_profit DECIMAL(16, 4) NULL,
    note VARCHAR(256) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_my_holding_code (code),
    KEY idx_my_holding_update (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
