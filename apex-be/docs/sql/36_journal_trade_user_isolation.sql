USE apex;

ALTER TABLE journal_trade ADD COLUMN user_id BIGINT NULL COMMENT '所属用户ID' AFTER id;
ALTER TABLE journal_trade ADD KEY idx_journal_trade_user_date (user_id, trade_date, id);
