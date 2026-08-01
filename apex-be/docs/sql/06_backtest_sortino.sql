USE apex;

ALTER TABLE backtest_job ADD COLUMN sortino DECIMAL(12, 6) NULL COMMENT 'Sortino' AFTER sharpe;
