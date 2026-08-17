USE apex;

ALTER TABLE backtest_job ADD COLUMN comparison_batch_id VARCHAR(32) NULL COMMENT '策略对比批次ID' AFTER strategy_id;
ALTER TABLE backtest_job ADD COLUMN comparison_strategy_ids VARCHAR(256) NULL COMMENT '策略对比集合' AFTER comparison_batch_id;
ALTER TABLE backtest_job ADD COLUMN strategy_parameters VARCHAR(512) NULL COMMENT '当前策略参数快照' AFTER comparison_strategy_ids;
ALTER TABLE backtest_job ADD COLUMN comparison_config_fingerprint CHAR(64) NULL COMMENT '对比策略配置SHA-256指纹' AFTER strategy_parameters;
ALTER TABLE backtest_job ADD COLUMN commission_rate DECIMAL(12, 8) NULL COMMENT '单边佣金比例' AFTER init_cash;
ALTER TABLE backtest_job ADD COLUMN stamp_tax_rate DECIMAL(12, 8) NULL COMMENT '卖出印花税比例' AFTER commission_rate;
ALTER TABLE backtest_job ADD COLUMN buy_slippage DECIMAL(12, 8) NULL COMMENT '买入滑点比例' AFTER stamp_tax_rate;
ALTER TABLE backtest_job ADD COLUMN sell_slippage DECIMAL(12, 8) NULL COMMENT '卖出滑点比例' AFTER buy_slippage;
ALTER TABLE backtest_job ADD COLUMN execution_model_version VARCHAR(32) NULL COMMENT '成交语义版本' AFTER sell_slippage;
ALTER TABLE backtest_job ADD COLUMN price_adjustment VARCHAR(16) NULL COMMENT '行情复权口径' AFTER execution_model_version;
ALTER TABLE backtest_job ADD COLUMN data_fingerprint CHAR(64) NULL COMMENT '行情数据SHA-256指纹' AFTER price_adjustment;
ALTER TABLE backtest_job ADD KEY idx_backtest_user_comparison_batch (user_id, comparison_batch_id);
