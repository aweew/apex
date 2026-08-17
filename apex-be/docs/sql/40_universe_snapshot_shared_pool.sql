USE apex;

ALTER TABLE universe_snapshot CHANGE COLUMN user_id creator_user_id BIGINT NOT NULL COMMENT '创建用户ID';
ALTER TABLE universe_snapshot DROP INDEX idx_universe_user_batch;
ALTER TABLE universe_snapshot DROP INDEX idx_universe_user_as_of_id;
ALTER TABLE universe_snapshot ADD KEY idx_universe_batch (batch_no);
ALTER TABLE universe_snapshot ADD KEY idx_universe_as_of_id (as_of_date, id);
ALTER TABLE universe_snapshot ADD KEY idx_universe_creator_id (creator_user_id, id);
