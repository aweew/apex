package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 策略绩效榜单项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyLeaderboardItemResp {

    /**
     * 策略ID
     */
    private String strategyId;

    /**
     * 完整配对批次数
     */
    private Integer jobCount;

    /**
     * 当前策略参数快照
     */
    private String strategyParameters;

    /**
     * 对比策略配置SHA-256指纹
     */
    private String comparisonConfigFingerprint;

    /**
     * 成交语义版本
     */
    private String executionModelVersion;

    /**
     * 行情复权口径
     */
    private String priceAdjustment;

    /**
     * 初始资金
     */
    private BigDecimal initCash;

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
     * 平均收益
     */
    private BigDecimal avgReturn;

    /**
     * 平均夏普
     */
    private BigDecimal avgSharpe;

    /**
     * 平均最大回撤
     */
    private BigDecimal avgMaxDrawdown;

    /**
     * 最佳收益
     */
    private BigDecimal bestReturn;

    /**
     * 最差收益
     */
    private BigDecimal worstReturn;
}
