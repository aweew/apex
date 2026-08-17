USE apex;

ALTER TABLE strategy_signal ADD COLUMN user_id BIGINT NULL COMMENT '所属用户ID' AFTER id;
ALTER TABLE strategy_signal ADD KEY idx_strategy_signal_user_date (user_id, signal_date, id);
