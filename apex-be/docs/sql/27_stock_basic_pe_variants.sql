USE apex;

ALTER TABLE stock_basic
    ADD COLUMN pe_dynamic DECIMAL(16, 4) NULL COMMENT '动态市盈率' AFTER st_flag,
    ADD COLUMN pe_static DECIMAL(16, 4) NULL COMMENT '静态市盈率' AFTER pe_dynamic,
    MODIFY COLUMN pe_ttm DECIMAL(16, 4) NULL COMMENT '滚动市盈率TTM' AFTER pe_static;

-- 历史版本曾将动态市盈率写入 pe_ttm，必须清空后再由 f164 正确回填。
UPDATE stock_basic SET pe_ttm = NULL WHERE pe_ttm IS NOT NULL;
