package com.awe.apex.quant.backtest;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 回测结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestResult {

    /**
     * 初始资金
     */
    private BigDecimal initCash;

    /**
     * 期末权益
     */
    private BigDecimal finalCash;

    /**
     * 累计收益
     */
    private BigDecimal totalReturn;

    /**
     * 年化收益
     */
    private BigDecimal annualReturn;

    /**
     * 最大回撤
     */
    private BigDecimal maxDrawdown;

    /**
     * 夏普
     */
    private BigDecimal sharpe;

    /**
     * Sortino
     */
    private BigDecimal sortino;

    /**
     * 胜率
     */
    private BigDecimal winRate;

    /**
     * 盈亏比
     */
    private BigDecimal profitFactor;

    /**
     * 平均持仓天数
     */
    private BigDecimal avgHoldDays;

    /**
     * 成交笔数
     */
    private Integer tradeCount;

    /**
     * 期末未平仓数量
     */
    private Integer endingPositionQuantity;

    /**
     * 期末未平仓市值
     */
    private BigDecimal endingPositionMarketValue;

    /**
     * 成交明细
     */
    private List<BacktestTradePoint> trades;

    /**
     * 资金曲线
     */
    private List<BacktestEquityPoint> equities;

    /**
     * 免责声明
     */
    private String disclaimer;
}
