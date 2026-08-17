USE apex;

ALTER TABLE sync_job DROP INDEX idx_sync_job_user_type_status;
ALTER TABLE sync_job DROP COLUMN user_id;
