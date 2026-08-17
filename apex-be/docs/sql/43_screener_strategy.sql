CREATE TABLE IF NOT EXISTS screener_strategy (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '所属用户ID',
    name VARCHAR(64) NOT NULL COMMENT '策略名称',
    description VARCHAR(512) NULL COMMENT '策略说明',
    source_type VARCHAR(24) NOT NULL COMMENT '来源类型USER或TEMPLATE_COPY',
    template_key VARCHAR(64) NULL COMMENT '复制来源的系统模板标识',
    run_mode VARCHAR(16) NOT NULL DEFAULT 'REALTIME' COMMENT '运行模式REALTIME或CLOSE',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用0否1是',
    sort_no INT NOT NULL DEFAULT 10 COMMENT '排序号',
    version_no INT NOT NULL DEFAULT 1 COMMENT '策略版本号',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除0否1是',
    PRIMARY KEY (id),
    KEY idx_screener_strategy_user_sort (user_id, enabled, sort_no, id),
    KEY idx_screener_strategy_template (user_id, template_key, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户选股策略';

CREATE TABLE IF NOT EXISTS screener_strategy_rule (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    strategy_id BIGINT NOT NULL COMMENT '策略ID',
    rule_type VARCHAR(48) NOT NULL COMMENT '规则类型',
    operator_code VARCHAR(16) NOT NULL COMMENT '操作符EQ GT GTE LT LTE BETWEEN',
    min_value DECIMAL(24, 6) NULL COMMENT '最小值或单值',
    max_value DECIMAL(24, 6) NULL COMMENT '最大值',
    int_value INT NULL COMMENT '整数参数',
    text_value VARCHAR(128) NULL COMMENT '文本参数',
    bool_value TINYINT NULL COMMENT '布尔参数0否1是',
    lookback_days INT NULL COMMENT '回看交易日数',
    tolerance_value DECIMAL(24, 6) NULL COMMENT '容错阈值',
    sort_no INT NOT NULL DEFAULT 10 COMMENT '排序号',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除0否1是',
    PRIMARY KEY (id),
    KEY idx_screener_rule_strategy_sort (strategy_id, sort_no, id),
    KEY idx_screener_rule_type (rule_type, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户选股策略规则';
