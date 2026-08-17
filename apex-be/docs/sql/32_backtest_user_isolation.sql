USE apex;

ALTER TABLE backtest_job ADD COLUMN user_id BIGINT NULL COMMENT '所属用户ID' AFTER id;

UPDATE backtest_job t1
JOIN (
    SELECT MIN(user_id) AS user_id
    FROM apex_user_profile
    WHERE role = 'ADMIN'
) t2 ON 1 = 1
SET t1.user_id = t2.user_id
WHERE t1.user_id IS NULL;

ALTER TABLE backtest_job MODIFY COLUMN user_id BIGINT NOT NULL COMMENT '所属用户ID';
ALTER TABLE backtest_job ADD KEY idx_backtest_user_status_id (user_id, status, id);
