package com.awe.apex.quant.config;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 参数说明修复
 */
@Slf4j
@Component
public class ConfigRemarkBootstrap implements ApplicationRunner {

    @Resource
    private JdbcTemplate jdbcTemplate;

    /**
     * 修复历史编码异常的系统参数与风控规则说明
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        try {
            repairConfigRemark("commission_rate", "佣金");
            repairConfigRemark("stamp_tax_rate", "印花税");
            repairConfigRemark("buy_slippage", "买入滑点");
            repairConfigRemark("sell_slippage", "卖出滑点");
            repairConfigRemark("fill_mode", "撮合模式 CLOSE/NEXT_OPEN");
            repairConfigRemark("atr_stop_mult", "ATR止损倍数");
            repairConfigRemark("atr_take_mult", "ATR止盈倍数");
            repairConfigRemark("risk_per_trade", "单笔风险占总资产比例");
            repairConfigRemark("target_ann_vol", "目标年化波动");
            repairConfigRemark("target_beta", "组合目标Beta");

            repairRiskRemark("total_position_limit", "总仓位上限");
            repairRiskRemark("single_stock_limit", "单票上限");
            repairRiskRemark("industry_limit", "同行业上限");
            repairRiskRemark("stop_loss_pct", "默认止损比例");
            repairRiskRemark("take_profit_pct", "默认止盈比例");
            repairRiskRemark("max_hold_days", "最长持仓天数告警");
            log.info("参数说明检查完成");
        } catch (Exception ex) {
            log.warn("参数说明修复跳过: {}", ex.getMessage());
        }
    }

    private void repairConfigRemark(String configKey, String remark) {
        jdbcTemplate.update(
                "UPDATE system_config SET remark = ? WHERE config_key = ? AND (remark IS NULL OR remark <> ?)",
                remark, configKey, remark);
    }

    private void repairRiskRemark(String ruleKey, String remark) {
        jdbcTemplate.update(
                "UPDATE risk_rule SET remark = ? WHERE rule_key = ? AND (remark IS NULL OR remark <> ?)",
                remark, ruleKey, remark);
    }
}
