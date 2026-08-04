USE apex;

CREATE TABLE IF NOT EXISTS portfolio (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    name VARCHAR(64) NOT NULL COMMENT '组合名称',
    note VARCHAR(512) NULL COMMENT '备注',
    owner_label VARCHAR(64) NULL COMMENT '实盘归属人标签',
    is_default TINYINT NOT NULL DEFAULT 0 COMMENT '是否默认组合（我的持仓）',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/ARCHIVED',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序，越小越前',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_portfolio_status (status),
    KEY idx_portfolio_default (is_default)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实盘组合';

CREATE TABLE IF NOT EXISTS portfolio_holding (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    portfolio_id BIGINT NOT NULL COMMENT '组合ID',
    code VARCHAR(16) NOT NULL COMMENT '证券代码',
    name VARCHAR(64) NULL COMMENT '证券简称',
    quantity INT NOT NULL DEFAULT 0 COMMENT '持仓数量（股）',
    cost_price DECIMAL(16, 4) NULL COMMENT '成本价',
    stop_loss DECIMAL(16, 4) NULL COMMENT '止损价',
    take_profit DECIMAL(16, 4) NULL COMMENT '止盈价',
    note VARCHAR(256) NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_portfolio_holding_code (portfolio_id, code),
    KEY idx_portfolio_holding_pf (portfolio_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组合持仓';

CREATE TABLE IF NOT EXISTS portfolio_daily (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    portfolio_id BIGINT NOT NULL COMMENT '组合ID',
    trade_date DATE NOT NULL COMMENT '交易日',
    market_value DECIMAL(18, 2) NULL COMMENT '市值',
    cost_value DECIMAL(18, 2) NULL COMMENT '成本市值',
    total_pnl DECIMAL(18, 2) NULL COMMENT '累计浮盈',
    today_pnl DECIMAL(18, 2) NULL COMMENT '当日浮盈',
    today_pct DECIMAL(10, 4) NULL COMMENT '当日涨跌幅%',
    position_count INT NOT NULL DEFAULT 0 COMMENT '持仓只数',
    cash DECIMAL(18, 2) NOT NULL DEFAULT 0 COMMENT '现金（预留）',
    payload MEDIUMTEXT NULL COMMENT '持仓明细JSON（可选）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_portfolio_daily (portfolio_id, trade_date),
    KEY idx_portfolio_daily_date (trade_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组合每日快照';
