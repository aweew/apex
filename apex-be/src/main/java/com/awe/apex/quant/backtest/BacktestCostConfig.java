package com.awe.apex.quant.backtest;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 回测成本假设
 */
@Data
@Builder
public class BacktestCostConfig {

    /**
     * 双边佣金合计比例（默认 0.05%）
     */
    @Builder.Default
    private BigDecimal commissionRate = new BigDecimal("0.0005");

    /**
     * 卖出印花税（默认 0.05%）
     */
    @Builder.Default
    private BigDecimal stampTaxRate = new BigDecimal("0.0005");

    /**
     * 买入滑点
     */
    @Builder.Default
    private BigDecimal buySlippage = new BigDecimal("0.001");

    /**
     * 卖出滑点
     */
    @Builder.Default
    private BigDecimal sellSlippage = new BigDecimal("0.001");

    /**
     * 默认配置
     *
     * @return 配置
     */
    public static BacktestCostConfig defaults() {
        return BacktestCostConfig.builder().build();
    }

    /**
     * 从系统参数构建成本假设
     *
     * @param commission 佣金
     * @param stampTax   印花税
     * @param buySlip    买滑点
     * @param sellSlip   卖滑点
     * @return 配置
     */
    public static BacktestCostConfig of(BigDecimal commission, BigDecimal stampTax,
                                       BigDecimal buySlip, BigDecimal sellSlip) {
        BacktestCostConfig.BacktestCostConfigBuilder builder = BacktestCostConfig.builder();
        if (commission != null) {
            builder.commissionRate(commission);
        }
        if (stampTax != null) {
            builder.stampTaxRate(stampTax);
        }
        if (buySlip != null) {
            builder.buySlippage(buySlip);
        }
        if (sellSlip != null) {
            builder.sellSlippage(sellSlip);
        }
        return builder.build();
    }
}
