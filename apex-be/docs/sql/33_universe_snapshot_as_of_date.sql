USE apex;

ALTER TABLE universe_snapshot ADD COLUMN user_id BIGINT NULL COMMENT '所属用户ID' AFTER id;
ALTER TABLE universe_snapshot ADD COLUMN as_of_date DATE NULL COMMENT '数据截止日' AFTER batch_no;

UPDATE universe_snapshot t1
JOIN (
    SELECT MIN(user_id) AS user_id
    FROM apex_user_profile
    WHERE role = 'ADMIN'
) t2 ON 1 = 1
SET t1.user_id = t2.user_id
WHERE t1.user_id IS NULL;

UPDATE universe_snapshot t1
SET t1.as_of_date = DATE(t1.create_time)
WHERE t1.as_of_date IS NULL;

ALTER TABLE universe_snapshot MODIFY COLUMN user_id BIGINT NOT NULL COMMENT '所属用户ID';
ALTER TABLE universe_snapshot MODIFY COLUMN as_of_date DATE NOT NULL COMMENT '数据截止日';
ALTER TABLE universe_snapshot ADD KEY idx_universe_user_batch (user_id, batch_no);
ALTER TABLE universe_snapshot ADD KEY idx_universe_user_as_of_id (user_id, as_of_date, id);
