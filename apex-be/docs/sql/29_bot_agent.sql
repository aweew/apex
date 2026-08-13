CREATE TABLE IF NOT EXISTS bot_pending_operation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    operation_type VARCHAR(64) NOT NULL,
    portfolio_id BIGINT NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    conversation_id VARCHAR(128) NOT NULL,
    confirmation_code VARCHAR(16) NOT NULL,
    payload_json MEDIUMTEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    expire_time DATETIME NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirm_time DATETIME NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_bot_pending_code (confirmation_code),
    KEY idx_bot_pending_user_conversation (user_id, conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
