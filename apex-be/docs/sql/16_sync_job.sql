USE apex;

CREATE TABLE IF NOT EXISTS sync_job (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_type VARCHAR(32) NOT NULL,
    task_name VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    params_json VARCHAR(1024) NULL,
    progress_pct INT NOT NULL DEFAULT 0,
    done_items INT NULL,
    total_items INT NULL,
    message VARCHAR(512) NULL,
    log_tail MEDIUMTEXT NULL,
    exit_code INT NULL,
    pid BIGINT NULL,
    started_at DATETIME NULL,
    finished_at DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_sync_job_type_status (task_type, status),
    KEY idx_sync_job_started (started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
