USE apex;

ALTER TABLE daily_action
    ADD COLUMN score DECIMAL(10, 2) NULL,
    ADD COLUMN confluence_count INT NULL,
    ADD COLUMN fund_note VARCHAR(256) NULL,
    ADD COLUMN signal_id BIGINT NULL;
