package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 滚动样本外评估响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RollingBacktestResp {

    /**
     * 实验历史ID
     */
    private Long experimentId;

    /**
     * 证券代码
     */
    private String code;

    /**
     * 策略ID
     */
    private String strategyId;

    /**
     * 策略名称
     */
    private String strategyName;

    /**
     * 策略参数快照
     */
    private String strategyParameters;

    /**
     * 基准代码
     */
    private String benchmarkCode;

    /**
     * 窗口模式
     */
    private String windowMode;

    /**
     * 数据开始日
     */
    private LocalDate dataBeginDate;

    /**
     * 数据结束日
     */
    private LocalDate dataEndDate;

    /**
     * 首个样本外窗口开始日
     */
    private LocalDate outSampleBeginDate;

    /**
     * 最后一个样本外窗口结束日
     */
    private LocalDate outSampleEndDate;

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
     * 初始资金
     */
    private BigDecimal initCash;

    /**
     * 窗口数量
     */
    private Integer foldCount;

    /**
     * 选定样本外窗口对评估跨度的覆盖率
     */
    private BigDecimal coverageRate;

    /**
     * 样本外窗口复合收益
     */
    private BigDecimal compoundedOutSampleReturn;

    /**
     * 基准窗口复合收益
     */
    private BigDecimal compoundedBenchmarkReturn;

    /**
     * 复合超额收益
     */
    private BigDecimal compoundedExcessReturn;

    /**
     * 正收益窗口占比
     */
    private BigDecimal positiveFoldRate;

    /**
     * 跑赢基准窗口占比
     */
    private BigDecimal benchmarkWinRate;

    /**
     * 拼接样本外日收益计算的整体夏普
     */
    private BigDecimal outSampleSharpe;

    /**
     * 各样本外窗口夏普算术平均
     */
    private BigDecimal averageFoldSharpe;

    /**
     * 样本外最差最大回撤
     */
    private BigDecimal worstOutSampleDrawdown;

    /**
     * 平均年化收益衰减
     */
    private BigDecimal averageAnnualReturnDecay;

    /**
     * 成交语义版本
     */
    private String executionModelVersion;

    /**
     * 行情复权口径
     */
    private String priceAdjustment;

    /**
     * 实际行情输入SHA-256指纹
     */
    private String dataFingerprint;

    /**
     * 实际成本假设
     */
    private BacktestCostResp cost;

    /**
     * 各窗口结果
     */
    private List<RollingBacktestFoldResp> folds;

    /**
     * 免责声明
     */
    private String disclaimer;
}
