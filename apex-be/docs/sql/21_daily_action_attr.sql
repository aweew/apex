USE apex;

ALTER TABLE daily_action
    ADD COLUMN mainline_match TINYINT NULL,
    ADD COLUMN mainline_name VARCHAR(64) NULL,
    ADD COLUMN score_explain VARCHAR(512) NULL,
    ADD COLUMN strategies_csv VARCHAR(64) NULL;
