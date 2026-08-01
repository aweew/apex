package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 模拟盘相对基准绩效
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaperPerformanceResp {

    /**
     * 账户ID
     */
    private Long accountId;

    /**
     * 基准代码
     */
    private String benchmarkCode;

    /**
     * 统计起点（首笔成交日）
     */
    private LocalDate startDate;

    /**
     * 纸面累计收益
     */
    private BigDecimal paperReturn;

    /**
     * 时间加权收益（日收益连乘）
     */
    private BigDecimal timeWeightedReturn;

    /**
     * 基准买入持有收益
     */
    private BigDecimal benchmarkReturn;

    /**
     * 超额收益（Alpha 近似）
     */
    private BigDecimal alpha;

    /**
     * 相对基准 Beta（日收益回归）
     */
    private BigDecimal beta;

    /**
     * 近20日滚动 Beta
     */
    private BigDecimal rollingBeta20;

    /**
     * 近20日滚动 Alpha（累计超额）
     */
    private BigDecimal rollingAlpha20;

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
     * 信息比率（相对基准超额收益/跟踪误差）
     */
    private BigDecimal informationRatio;

    /**
     * 年化跟踪误差
     */
    private BigDecimal trackingError;

    /**
     * 第二基准代码（可选，如 000905）
     */
    private String altBenchmarkCode;

    /**
     * 第二基准收益
     */
    private BigDecimal altBenchmarkReturn;

    /**
     * 相对第二基准超额
     */
    private BigDecimal altAlpha;

    /**
     * 第二基准权益曲线
     */
    private List<EquityPointResp> altBenchmarkEquities;

    /**
     * 当前总资产
     */
    private BigDecimal totalAsset;

    /**
     * 纸面权益曲线
     */
    private List<EquityPointResp> paperEquities;

    /**
     * 基准归一权益曲线
     */
    private List<EquityPointResp> benchmarkEquities;

    /**
     * 回撤曲线（equity 字段存回撤比例 0~1）
     */
    private List<EquityPointResp> drawdownCurve;
}
