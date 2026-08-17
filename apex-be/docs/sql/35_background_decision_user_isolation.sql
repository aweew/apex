USE apex;

ALTER TABLE sync_job ADD COLUMN user_id BIGINT NULL COMMENT '用户私有任务所属用户ID，共享任务为空' AFTER id;
ALTER TABLE sync_job ADD KEY idx_sync_job_user_type_status (user_id, task_type, status, id);

ALTER TABLE decision_run ADD COLUMN user_id BIGINT NULL COMMENT '所属用户ID' AFTER id;
ALTER TABLE decision_run ADD KEY idx_decision_run_user_publish (user_id, action_date, published, status);

ALTER TABLE daily_action ADD COLUMN user_id BIGINT NULL COMMENT '所属用户ID' AFTER id;
ALTER TABLE daily_action ADD KEY idx_daily_action_user_date (user_id, action_date, rank_no);
