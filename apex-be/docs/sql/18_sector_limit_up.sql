USE apex;

ALTER TABLE sector_quote
    ADD COLUMN limit_up_count INT NULL AFTER down_count,
    ADD COLUMN max_lianban INT NULL AFTER limit_up_count;
