package com.awe.apex.quant.domain.dto;

import com.awe.apex.quant.backtest.BacktestCostConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 回测成本假设响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestCostResp {

    /**
     * 单边佣金比例
     */
    private BigDecimal commissionRate;

    /**
     * 卖出印花税比例
     */
    private BigDecimal stampTaxRate;

    /**
     * 买入滑点比例
     */
    private BigDecimal buySlippage;

    /**
     * 卖出滑点比例
     */
    private BigDecimal sellSlippage;

    /**
     * 构建成本响应
     *
     * @param costConfig 成本配置
     * @return 成本响应
     */
    public static BacktestCostResp from(BacktestCostConfig costConfig) {
        return BacktestCostResp.builder()
                .commissionRate(costConfig.getCommissionRate().setScale(8, RoundingMode.UNNECESSARY))
                .stampTaxRate(costConfig.getStampTaxRate().setScale(8, RoundingMode.UNNECESSARY))
                .buySlippage(costConfig.getBuySlippage().setScale(8, RoundingMode.UNNECESSARY))
                .sellSlippage(costConfig.getSellSlippage().setScale(8, RoundingMode.UNNECESSARY))
                .build();
    }
}
