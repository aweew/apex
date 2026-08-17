package com.awe.apex.quant.domain.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 滚动样本外评估请求
 */
@Data
public class RollingBacktestReq {

    /**
     * 证券代码
     */
    private String code;

    /**
     * 策略ID
     */
    private String strategyId;

    /**
     * 本次实验实际使用的策略参数快照
     */
    private RollingStrategyConfig strategyConfig;

    /**
     * 是否按历史实验快照精确复跑
     */
    private Boolean exactReplay;

    /**
     * 精确复跑期望的成交模型版本
     */
    private String expectedExecutionModelVersion;

    /**
     * 精确复跑期望的复权口径
     */
    private String expectedPriceAdjustment;

    /**
     * 精确复跑期望的行情数据指纹
     */
    private String expectedDataFingerprint;

    /**
     * 开始日期
     */
    private String beginDate;

    /**
     * 结束日期
     */
    private String endDate;

    /**
     * 初始资金
     */
    private BigDecimal initCash;

    /**
     * 基准代码
     */
    private String benchmarkCode;

    /**
     * 窗口模式，ROLLING固定窗或EXPANDING扩展窗
     */
    private String windowMode;

    /**
     * 样本内交易日数
     */
    private Integer trainDays;

    /**
     * 样本外交易日数
     */
    private Integer testDays;

    /**
     * 相邻样本外窗口步长
     */
    private Integer stepDays;

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
}
