USE apex;

ALTER TABLE portfolio
    ADD COLUMN cash_balance DECIMAL(18, 2) NOT NULL DEFAULT 0;

ALTER TABLE portfolio_daily
    ADD COLUMN total_equity DECIMAL(18, 2) NULL,
    ADD COLUMN peak_equity DECIMAL(18, 2) NULL,
    ADD COLUMN drawdown DECIMAL(12, 6) NULL;

ALTER TABLE decision_feature_snapshot
    ADD COLUMN selection_status VARCHAR(16) NOT NULL DEFAULT 'SELECTED',
    ADD COLUMN reject_reason VARCHAR(256) NULL,
    ADD COLUMN rank_no INT NULL;

ALTER TABLE decision_outcome
    MODIFY COLUMN action_id BIGINT NULL,
    ADD COLUMN feature_snapshot_id BIGINT NULL,
    ADD COLUMN entry_date DATE NULL,
    ADD COLUMN entry_price DECIMAL(16, 4) NULL,
    ADD UNIQUE KEY uk_decision_outcome_feature (feature_snapshot_id);

ALTER TABLE daily_action
    ADD COLUMN reference_price DECIMAL(16, 4) NULL,
    ADD COLUMN stop_loss_price DECIMAL(16, 4) NULL,
    ADD COLUMN take_profit_price DECIMAL(16, 4) NULL;

CREATE TABLE IF NOT EXISTS decision_portfolio_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_id BIGINT NOT NULL,
    portfolio_id BIGINT NOT NULL,
    action_date DATE NOT NULL,
    cash DECIMAL(18, 2) NOT NULL DEFAULT 0,
    market_value DECIMAL(18, 2) NOT NULL DEFAULT 0,
    total_equity DECIMAL(18, 2) NOT NULL DEFAULT 0,
    peak_equity DECIMAL(18, 2) NOT NULL DEFAULT 0,
    drawdown DECIMAL(12, 6) NOT NULL DEFAULT 0,
    exposure_ratio DECIMAL(12, 6) NOT NULL DEFAULT 0,
    market_regime VARCHAR(16) NULL,
    exposure_limit DECIMAL(12, 6) NULL,
    single_stock_limit DECIMAL(12, 6) NULL,
    industry_limit DECIMAL(12, 6) NULL,
    atr_stop_multiplier DECIMAL(12, 6) NULL,
    atr_take_multiplier DECIMAL(12, 6) NULL,
    regime_reason VARCHAR(256) NULL,
    industry_exposure_json MEDIUMTEXT NULL,
    holding_payload MEDIUMTEXT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_decision_portfolio_run (run_id),
    KEY idx_decision_portfolio_date (portfolio_id, action_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
