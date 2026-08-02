USE apex;

ALTER TABLE daily_action
    ADD COLUMN valuation_level VARCHAR(32) NULL,
    ADD COLUMN valuation_label VARCHAR(32) NULL,
    ADD COLUMN valuation_score DECIMAL(10, 2) NULL,
    ADD COLUMN valuation_summary VARCHAR(256) NULL,
    ADD COLUMN link_hint VARCHAR(64) NULL,
    ADD COLUMN risk_flags VARCHAR(256) NULL,
    ADD COLUMN executable_hint TINYINT NULL;
