USE apex;

CREATE TABLE apex_user_profile (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '系统用户ID',
    paper_account_id BIGINT NULL COMMENT '模拟账户ID',
    role VARCHAR(16) NOT NULL COMMENT '用户角色 ADMIN/MEMBER',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_apex_user_profile_user (user_id),
    UNIQUE KEY uk_apex_user_profile_account (paper_account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Apex用户资产档案';

CREATE TABLE apex_user_invite (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    token_hash CHAR(64) NOT NULL COMMENT '令牌SHA-256摘要',
    creator_user_id BIGINT NOT NULL COMMENT '创建管理员ID',
    purpose VARCHAR(16) NOT NULL COMMENT '用途 INVITE/RESET',
    target_user_id BIGINT NULL COMMENT '重置目标用户ID',
    used_user_id BIGINT NULL COMMENT '使用用户ID',
    expire_time DATETIME NOT NULL COMMENT '失效时间',
    used_time DATETIME NULL COMMENT '使用时间',
    revoked_time DATETIME NULL COMMENT '作废时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_apex_user_invite_token (token_hash),
    KEY idx_apex_user_invite_expire (expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Apex私有邀请';

ALTER TABLE paper_account ADD COLUMN user_id BIGINT NULL COMMENT '所属用户ID' AFTER id;
ALTER TABLE portfolio ADD COLUMN user_id BIGINT NULL COMMENT '所属用户ID' AFTER id;
ALTER TABLE watchlist ADD COLUMN user_id BIGINT NULL COMMENT '所属用户ID' AFTER id;
ALTER TABLE observe_pool ADD COLUMN user_id BIGINT NULL COMMENT '所属用户ID' AFTER id;
ALTER TABLE my_holding ADD COLUMN user_id BIGINT NULL COMMENT '所属用户ID' AFTER id;

ALTER TABLE paper_account ADD UNIQUE KEY uk_paper_account_user (user_id);
ALTER TABLE portfolio ADD KEY idx_portfolio_user (user_id, status);
ALTER TABLE watchlist ADD KEY idx_watchlist_user_group (user_id, group_name);
ALTER TABLE observe_pool ADD KEY idx_observe_user_status (user_id, status);
ALTER TABLE my_holding ADD KEY idx_my_holding_user (user_id);

INSERT INTO apex_user_profile (user_id, role)
SELECT t1.id, 'ADMIN'
FROM sys_user t1
WHERE NOT EXISTS (SELECT 1 FROM apex_user_profile t2)
ORDER BY t1.id
LIMIT 1;

UPDATE paper_account t1
JOIN apex_user_profile t2 ON t2.role = 'ADMIN'
SET t1.user_id = t2.user_id
WHERE t1.user_id IS NULL;

UPDATE apex_user_profile t1
JOIN paper_account t2 ON t2.user_id = t1.user_id
SET t1.paper_account_id = t2.id
WHERE t1.role = 'ADMIN' AND t1.paper_account_id IS NULL;

UPDATE portfolio t1
JOIN apex_user_profile t2 ON t2.role = 'ADMIN'
SET t1.user_id = t2.user_id
WHERE t1.user_id IS NULL;

UPDATE watchlist t1
JOIN apex_user_profile t2 ON t2.role = 'ADMIN'
SET t1.user_id = t2.user_id
WHERE t1.user_id IS NULL;

UPDATE observe_pool t1
JOIN apex_user_profile t2 ON t2.role = 'ADMIN'
SET t1.user_id = t2.user_id
WHERE t1.user_id IS NULL;

UPDATE my_holding t1
JOIN apex_user_profile t2 ON t2.role = 'ADMIN'
SET t1.user_id = t2.user_id
WHERE t1.user_id IS NULL;
