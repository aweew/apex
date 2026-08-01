USE apex;

ALTER TABLE bar_daily ADD COLUMN turnover_rate DECIMAL(10, 4) NULL COMMENT '换手率%' AFTER pct_chg;
