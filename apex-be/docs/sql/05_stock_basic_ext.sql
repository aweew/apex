USE apex;

ALTER TABLE stock_basic ADD COLUMN pe_ttm DECIMAL(16, 4) NULL COMMENT '市盈率TTM' AFTER st_flag;
ALTER TABLE stock_basic ADD COLUMN pb DECIMAL(16, 4) NULL COMMENT '市净率' AFTER pe_ttm;
ALTER TABLE stock_basic ADD COLUMN total_mv DECIMAL(20, 2) NULL COMMENT '总市值' AFTER pb;
ALTER TABLE stock_basic ADD COLUMN circ_mv DECIMAL(20, 2) NULL COMMENT '流通市值' AFTER total_mv;
ALTER TABLE stock_basic ADD COLUMN industry VARCHAR(64) NULL COMMENT '行业' AFTER circ_mv;
ALTER TABLE stock_basic ADD COLUMN latest_price DECIMAL(16, 4) NULL COMMENT '最新价' AFTER industry;
ALTER TABLE stock_basic ADD COLUMN pct_chg DECIMAL(10, 4) NULL COMMENT '涨跌幅%' AFTER latest_price;
ALTER TABLE stock_basic ADD COLUMN source VARCHAR(32) NULL COMMENT '数据来源' AFTER pct_chg;
ALTER TABLE stock_basic ADD COLUMN quote_time DATETIME NULL COMMENT '行情更新时间' AFTER source;
