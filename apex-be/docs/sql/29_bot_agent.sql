CREATE TABLE IF NOT EXISTS bot_call_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    request_id VARCHAR(80) NOT NULL,
    operation VARCHAR(64) NOT NULL,
    user_id VARCHAR(128) NULL,
    conversation_id VARCHAR(128) NULL,
    data_level VARCHAR(16) NOT NULL,
    error_message VARCHAR(512) NULL,
    duration_ms BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_bot_audit_request (request_id),
    KEY idx_bot_audit_created (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
