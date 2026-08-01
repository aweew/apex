package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 模拟盘绩效摘要
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaperMetricsResp {

    /**
     * 初始资金
     */
    private BigDecimal initCash;

    /**
     * 当前总资产
     */
    private BigDecimal totalAsset;

    /**
     * 累计收益
     */
    private BigDecimal totalReturn;

    /**
     * 订单数
     */
    private Integer orderCount;

    /**
     * 持仓数
     */
    private Integer positionCount;

    /**
     * 未实现盈亏
     */
    private BigDecimal unrealizedPnl;

    /**
     * 已实现盈亏（FIFO 闭合）
     */
    private BigDecimal realizedPnl;

    /**
     * 闭合交易胜率
     */
    private BigDecimal winRate;

    /**
     * 闭合交易次数
     */
    private Integer closedTradeCount;

    /**
     * 累计成交额
     */
    private BigDecimal totalTurnover;

    /**
     * 换手率（成交额 / 初始资金）
     */
    private BigDecimal turnoverRate;

    /**
     * 最大回撤（MTM 权益曲线）
     */
    private BigDecimal maxDrawdown;

    /**
     * 年化夏普（MTM 日收益）
     */
    private BigDecimal sharpe;

    /**
     * 历史法日度 VaR(95%)，损失正数
     */
    private BigDecimal dailyVar95;

    /**
     * 历史法日度 CVaR/ES(95%)，尾部平均损失
     */
    private BigDecimal dailyCvar95;

    /**
     * Calmar（年化收益/最大回撤）
     */
    private BigDecimal calmar;

    /**
     * 平均持仓天数（已闭合）
     */
    private BigDecimal avgHoldDays;

    /**
     * 当前连胜次数
     */
    private Integer winStreak;

    /**
     * 当前连亏次数
     */
    private Integer lossStreak;

    /**
     * 最近一次回撤修复天数（未修复则为当前水下天数）
     */
    private Integer drawdownRecoveryDays;

    /**
     * 水下时间占比（权益低于历史峰值的交易日比例）
     */
    private BigDecimal underwaterRatio;

    /**
     * 近20日滚动年化夏普
     */
    private BigDecimal rollingSharpe20;

    /**
     * 盈亏因子（毛盈利/毛亏损绝对值）
     */
    private BigDecimal profitFactor;

    /**
     * 平均盈亏比（平均盈利/平均亏损绝对值）
     */
    private BigDecimal payoffRatio;

    /**
     * 期望收益（每笔）：胜率×均盈 − 败率×均亏
     */
    private BigDecimal expectancy;

    /**
     * Sortino（下行波动年化）
     */
    private BigDecimal sortino;

    /**
     * Ulcer Index（回撤痛苦指数）
     */
    private BigDecimal ulcerIndex;

    /**
     * 累计手续费
     */
    private BigDecimal totalFee;

    /**
     * 费用率（fee/turnover）
     */
    private BigDecimal feeRate;

    /**
     * Omega 比率（阈值为 0）
     */
    private BigDecimal omega;

    /**
     * 权益连亏最长交易日数
     */
    private Integer maxLosingDays;

    /**
     * 现金拖累（1 - 平均股票仓位）
     */
    private BigDecimal cashDrag;
}
