USE apex;

CREATE TABLE IF NOT EXISTS market_news (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source VARCHAR(32) NOT NULL,
    external_id VARCHAR(64) NOT NULL,
    title VARCHAR(512) NOT NULL,
    summary TEXT NULL,
    content TEXT NULL,
    url VARCHAR(1024) NULL,
    published_at DATETIME NULL,
    related_codes VARCHAR(256) NULL,
    sentiment VARCHAR(16) NULL,
    snapshot_time DATETIME NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_market_news_source_ext (source, external_id),
    KEY idx_market_news_pub (published_at),
    KEY idx_market_news_source_pub (source, published_at),
    KEY idx_market_news_snap (snapshot_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
