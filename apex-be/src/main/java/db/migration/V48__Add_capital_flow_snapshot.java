package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

/**
 * 新增资金面与龙虎榜快照表。
 */
public class V48__Add_capital_flow_snapshot extends BaseJavaMigration {

    /**
     * 创建北向资金、个股资金流和龙虎榜快照表。
     *
     * @param context Flyway迁移上下文
     * @throws Exception SQL执行失败
     */
    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS northbound_flow (
                        id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                        trade_date DATE NOT NULL COMMENT '交易日',
                        net_buy_amount DECIMAL(20, 2) NULL COMMENT '北向资金净买额元',
                        buy_amount DECIMAL(20, 2) NULL COMMENT '北向资金买入额元',
                        sell_amount DECIMAL(20, 2) NULL COMMENT '北向资金卖出额元',
                        cumulative_net_buy_amount DECIMAL(20, 2) NULL COMMENT '北向资金累计净买额元',
                        data_status VARCHAR(16) NOT NULL COMMENT '数据状态PUBLISHED已披露NOT_DISCLOSED未披露',
                        synced_at DATETIME NOT NULL COMMENT '同步时间',
                        create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                        update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                        deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
                        PRIMARY KEY (id),
                        UNIQUE KEY uk_northbound_flow_date (trade_date)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='北向资金日快照'
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS stock_fund_flow (
                        id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                        code VARCHAR(16) NOT NULL COMMENT '证券代码',
                        name VARCHAR(64) NOT NULL COMMENT '证券名称',
                        trade_date DATE NOT NULL COMMENT '交易日',
                        pct_chg DECIMAL(10, 4) NULL COMMENT '涨跌幅百分比',
                        main_net_inflow DECIMAL(20, 2) NULL COMMENT '主力净流入元',
                        main_net_inflow_pct DECIMAL(10, 4) NULL COMMENT '主力净流入占比百分比',
                        super_large_net_inflow DECIMAL(20, 2) NULL COMMENT '超大单净流入元',
                        large_net_inflow DECIMAL(20, 2) NULL COMMENT '大单净流入元',
                        medium_net_inflow DECIMAL(20, 2) NULL COMMENT '中单净流入元',
                        small_net_inflow DECIMAL(20, 2) NULL COMMENT '小单净流入元',
                        synced_at DATETIME NOT NULL COMMENT '同步时间',
                        create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                        update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                        deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
                        PRIMARY KEY (id),
                        UNIQUE KEY uk_stock_fund_flow_code_date (code, trade_date),
                        KEY idx_stock_fund_flow_date_main (trade_date, main_net_inflow)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='个股资金流日快照'
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS dragon_tiger_item (
                        id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                        code VARCHAR(16) NOT NULL COMMENT '证券代码',
                        name VARCHAR(64) NOT NULL COMMENT '证券名称',
                        trade_date DATE NOT NULL COMMENT '交易日',
                        reason VARCHAR(512) NOT NULL COMMENT '上榜原因',
                        close_price DECIMAL(16, 4) NULL COMMENT '收盘价',
                        pct_chg DECIMAL(10, 4) NULL COMMENT '涨跌幅百分比',
                        turnover_rate DECIMAL(10, 4) NULL COMMENT '换手率百分比',
                        net_buy_amount DECIMAL(20, 2) NULL COMMENT '龙虎榜净买额元',
                        buy_amount DECIMAL(20, 2) NULL COMMENT '龙虎榜买入额元',
                        sell_amount DECIMAL(20, 2) NULL COMMENT '龙虎榜卖出额元',
                        amount DECIMAL(20, 2) NULL COMMENT '龙虎榜成交额元',
                        synced_at DATETIME NOT NULL COMMENT '同步时间',
                        create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                        update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                        deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
                        PRIMARY KEY (id),
                        UNIQUE KEY uk_dragon_tiger_code_date_reason (code, trade_date, reason),
                        KEY idx_dragon_tiger_date_net_buy (trade_date, net_buy_amount)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='龙虎榜日明细'
                    """);
        }
    }
}
