USE apex;

ALTER TABLE watchlist ADD UNIQUE KEY uk_watchlist_user_code_group (user_id, code, group_name);
ALTER TABLE my_holding ADD UNIQUE KEY uk_my_holding_user_code (user_id, code);

ALTER TABLE watchlist DROP INDEX uk_watchlist_code_group;
ALTER TABLE my_holding DROP INDEX uk_my_holding_code;
