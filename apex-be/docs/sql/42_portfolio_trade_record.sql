USE apex;

ALTER TABLE journal_trade
    ADD COLUMN stock_name VARCHAR(64) NULL COMMENT '证券简称' AFTER code;
ALTER TABLE journal_trade
    ADD COLUMN portfolio_id BIGINT NULL COMMENT '组合ID' AFTER user_id;
ALTER TABLE journal_trade
    ADD COLUMN portfolio_name VARCHAR(64) NULL COMMENT '交易发生时的组合名称' AFTER portfolio_id;
ALTER TABLE journal_trade
    ADD COLUMN owner_label VARCHAR(64) NULL COMMENT '交易发生时的组合归属人标签' AFTER portfolio_name;
ALTER TABLE journal_trade
    ADD COLUMN trade_time DATETIME NULL COMMENT '成交或持仓变动时间' AFTER trade_date;
ALTER TABLE journal_trade
    ADD COLUMN change_type VARCHAR(16) NOT NULL DEFAULT 'MANUAL' COMMENT '变动类型OPEN/ADD/REDUCE/CLEAR/MANUAL' AFTER side;
ALTER TABLE journal_trade
    ADD COLUMN before_quantity INT NULL COMMENT '变动前持仓数量' AFTER quantity;
ALTER TABLE journal_trade
    ADD COLUMN after_quantity INT NULL COMMENT '变动后持仓数量' AFTER before_quantity;
ALTER TABLE journal_trade
    ADD COLUMN price_source VARCHAR(24) NOT NULL DEFAULT 'USER_REPORTED' COMMENT '价格来源USER_REPORTED/BOT_REPORTED/MARKET_SNAPSHOT/DAILY_CLOSE/UNAVAILABLE' AFTER price;
ALTER TABLE journal_trade
    ADD COLUMN estimated TINYINT NOT NULL DEFAULT 0 COMMENT '价格是否为估算值，0否1是' AFTER price_source;
ALTER TABLE journal_trade
    ADD COLUMN source VARCHAR(24) NOT NULL DEFAULT 'MANUAL' COMMENT '记录来源MANUAL/DAILY_ACTION/PORTFOLIO_WEB/PORTFOLIO_IMPORT/HOLDING_WEB/WECHAT_BOT' AFTER related_action_id;
ALTER TABLE journal_trade
    ADD COLUMN source_ref VARCHAR(128) NULL COMMENT '来源请求或业务引用，用于幂等' AFTER source;
ALTER TABLE journal_trade
    MODIFY COLUMN price DECIMAL(16, 4) NULL COMMENT '成交价或估算参考价';
ALTER TABLE journal_trade
    MODIFY COLUMN amount DECIMAL(18, 2) NULL COMMENT '成交额或估算金额';
ALTER TABLE journal_trade
    ADD KEY idx_journal_trade_user_code_time (user_id, code, trade_time, id);
ALTER TABLE journal_trade
    ADD KEY idx_journal_trade_user_portfolio_time (user_id, portfolio_id, trade_time, id);
ALTER TABLE journal_trade
    ADD UNIQUE KEY uk_journal_trade_source_ref (user_id, source, source_ref, code);
