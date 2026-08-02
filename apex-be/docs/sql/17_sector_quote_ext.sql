USE apex;

ALTER TABLE sector_quote
    ADD COLUMN pct_chg_3d DECIMAL(12, 4) NULL AFTER pct_chg,
    ADD COLUMN pct_chg_5d DECIMAL(12, 4) NULL AFTER pct_chg_3d,
    ADD COLUMN move_reason VARCHAR(512) NULL AFTER lead_stock_pct;
