USE apex;

CREATE TABLE IF NOT EXISTS observe_pool (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    code VARCHAR(16) NOT NULL COMMENT '证券代码',
    name VARCHAR(64) NULL COMMENT '证券简称',
    market VARCHAR(8) NULL COMMENT '市场',
    side VARCHAR(8) NULL DEFAULT 'BUY' COMMENT '方向BUY/SELL',
    reason VARCHAR(256) NULL COMMENT '关注原因',
    guide_text TEXT NULL COMMENT '详细操作指导',
    trigger_type VARCHAR(32) NOT NULL DEFAULT 'PRICE_ABOVE' COMMENT '触发类型',
    trigger_expr VARCHAR(512) NULL COMMENT '补充触发条件',
    trigger_price DECIMAL(16, 4) NULL COMMENT '触发价',
    stop_loss DECIMAL(16, 4) NULL COMMENT '止损价',
    target_price DECIMAL(16, 4) NULL COMMENT '目标价',
    base_price DECIMAL(16, 4) NULL COMMENT '基准价',
    priority INT NOT NULL DEFAULT 3 COMMENT '优先级1-5',
    status VARCHAR(16) NOT NULL DEFAULT 'WATCHING' COMMENT '状态',
    triggered_at DATETIME NULL COMMENT '触发时间',
    note VARCHAR(512) NULL COMMENT '备注',
    tags VARCHAR(256) NULL COMMENT '标签逗号分隔',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_observe_code (code),
    KEY idx_observe_status (status),
    KEY idx_observe_priority (priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='观察池';
