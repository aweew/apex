USE apex;

CREATE TABLE IF NOT EXISTS paper_account (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    account_name VARCHAR(64) NOT NULL COMMENT '账户名',
    cash DECIMAL(18, 2) NOT NULL COMMENT '可用资金',
    init_cash DECIMAL(18, 2) NOT NULL COMMENT '初始资金',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_paper_account_name (account_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模拟账户';

CREATE TABLE IF NOT EXISTS paper_position (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    account_id BIGINT NOT NULL COMMENT '账户ID',
    code VARCHAR(16) NOT NULL COMMENT '证券代码',
    name VARCHAR(64) NULL COMMENT '简称',
    quantity INT NOT NULL COMMENT '持仓数量',
    cost_price DECIMAL(16, 4) NOT NULL COMMENT '成本价',
    stop_loss DECIMAL(16, 4) NULL COMMENT '止损价',
    take_profit DECIMAL(16, 4) NULL COMMENT '止盈价',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_paper_pos (account_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模拟持仓';

CREATE TABLE IF NOT EXISTS paper_order (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    account_id BIGINT NOT NULL COMMENT '账户ID',
    code VARCHAR(16) NOT NULL COMMENT '证券代码',
    side VARCHAR(8) NOT NULL COMMENT 'BUY/SELL',
    quantity INT NOT NULL COMMENT '数量',
    price DECIMAL(16, 4) NOT NULL COMMENT '成交价',
    amount DECIMAL(18, 2) NOT NULL COMMENT '成交额',
    fee DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '费用',
    trade_date DATE NOT NULL COMMENT '成交日',
    status VARCHAR(16) NOT NULL COMMENT 'FILLED/REJECTED',
    reason VARCHAR(256) NULL COMMENT '原因',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_paper_order_account (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模拟订单';

CREATE TABLE IF NOT EXISTS risk_rule (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    rule_key VARCHAR(64) NOT NULL COMMENT '规则键',
    rule_value VARCHAR(128) NOT NULL COMMENT '规则值',
    remark VARCHAR(256) NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_risk_rule_key (rule_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风控规则';

CREATE TABLE IF NOT EXISTS daily_action (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    action_date DATE NOT NULL COMMENT '清单日期',
    code VARCHAR(16) NOT NULL COMMENT '证券代码',
    name VARCHAR(64) NULL COMMENT '简称',
    action VARCHAR(16) NOT NULL COMMENT 'BUY/SELL/HOLD',
    strategy_id VARCHAR(16) NULL COMMENT '策略ID',
    reason VARCHAR(512) NULL COMMENT '理由',
    suggested_weight DECIMAL(10, 4) NULL COMMENT '建议仓位',
    exit_rule VARCHAR(256) NULL COMMENT '离场条件',
    score DECIMAL(10, 2) NULL COMMENT '综合评分',
    confluence_count INT NULL COMMENT '共振策略数',
    fund_note VARCHAR(256) NULL COMMENT '基本面要点',
    signal_id BIGINT NULL COMMENT '关联策略信号ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_daily_action_date (action_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日终操作清单';

CREATE TABLE IF NOT EXISTS journal_trade (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    trade_date DATE NOT NULL COMMENT '成交日',
    code VARCHAR(16) NOT NULL COMMENT '证券代码',
    side VARCHAR(8) NOT NULL COMMENT 'BUY/SELL',
    price DECIMAL(16, 4) NOT NULL COMMENT '成交价',
    quantity INT NOT NULL COMMENT '数量',
    amount DECIMAL(18, 2) NOT NULL COMMENT '成交额',
    related_action_id BIGINT NULL COMMENT '关联清单ID',
    note VARCHAR(512) NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_journal_date (trade_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人工成交日记';

CREATE TABLE IF NOT EXISTS system_config (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    config_key VARCHAR(64) NOT NULL COMMENT '配置键',
    config_value VARCHAR(256) NOT NULL COMMENT '配置值',
    remark VARCHAR(256) NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_system_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统参数';

INSERT INTO risk_rule (rule_key, rule_value, remark, deleted)
SELECT 'total_position_limit', '0.80', '总仓位上限', 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM risk_rule WHERE rule_key = 'total_position_limit');
INSERT INTO risk_rule (rule_key, rule_value, remark, deleted)
SELECT 'single_stock_limit', '0.15', '单票上限', 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM risk_rule WHERE rule_key = 'single_stock_limit');
INSERT INTO risk_rule (rule_key, rule_value, remark, deleted)
SELECT 'industry_limit', '0.30', '同行业上限', 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM risk_rule WHERE rule_key = 'industry_limit');
INSERT INTO risk_rule (rule_key, rule_value, remark, deleted)
SELECT 'stop_loss_pct', '0.08', '默认止损比例', 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM risk_rule WHERE rule_key = 'stop_loss_pct');
INSERT INTO risk_rule (rule_key, rule_value, remark, deleted)
SELECT 'take_profit_pct', '0.20', '默认止盈比例', 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM risk_rule WHERE rule_key = 'take_profit_pct');

INSERT INTO system_config (config_key, config_value, remark, deleted)
SELECT 'commission_rate', '0.0005', '佣金', 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'commission_rate');
INSERT INTO system_config (config_key, config_value, remark, deleted)
SELECT 'stamp_tax_rate', '0.0005', '印花税', 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'stamp_tax_rate');
INSERT INTO system_config (config_key, config_value, remark, deleted)
SELECT 'buy_slippage', '0.001', '买入滑点', 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'buy_slippage');
INSERT INTO system_config (config_key, config_value, remark, deleted)
SELECT 'sell_slippage', '0.001', '卖出滑点', 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'sell_slippage');
INSERT INTO system_config (config_key, config_value, remark, deleted)
SELECT 'fill_mode', 'CLOSE', '撮合模式 CLOSE/NEXT_OPEN', 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'fill_mode');
INSERT INTO system_config (config_key, config_value, remark, deleted)
SELECT 'atr_stop_mult', '2.0', 'ATR止损倍数', 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'atr_stop_mult');
INSERT INTO system_config (config_key, config_value, remark, deleted)
SELECT 'atr_take_mult', '3.0', 'ATR止盈倍数', 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'atr_take_mult');
INSERT INTO system_config (config_key, config_value, remark, deleted)
SELECT 'risk_per_trade', '0.01', '单笔风险占总资产比例', 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'risk_per_trade');
INSERT INTO system_config (config_key, config_value, remark, deleted)
SELECT 'target_ann_vol', '0.15', '目标年化波动', 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'target_ann_vol');
INSERT INTO risk_rule (rule_key, rule_value, remark, deleted)
SELECT 'max_hold_days', '60', '最长持仓天数告警', 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM risk_rule WHERE rule_key = 'max_hold_days');
INSERT INTO system_config (config_key, config_value, remark, deleted)
SELECT 'target_beta', '1.0', '组合目标Beta', 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'target_beta');
