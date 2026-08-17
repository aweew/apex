package com.awe.apex.quant.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 回测实验快照
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("backtest_experiment")
public class BacktestExperiment implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户ID
     */
    private Long userId;

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
     * 实际策略参数
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
     * 实际数据开始日
     */
    private LocalDate dataBeginDate;

    /**
     * 实际数据结束日
     */
    private LocalDate dataEndDate;

    /**
     * 首个样本外窗口开始日
     */
    private LocalDate outSampleBeginDate;

    /**
     * 最后样本外窗口结束日
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
     * 样本外复合收益
     */
    private BigDecimal compoundedOutSampleReturn;

    /**
     * 基准复合收益
     */
    private BigDecimal compoundedBenchmarkReturn;

    /**
     * 复合超额收益
     */
    private BigDecimal compoundedExcessReturn;

    /**
     * 样本外整体夏普
     */
    private BigDecimal outSampleSharpe;

    /**
     * 样本外最差最大回撤
     */
    private BigDecimal worstOutSampleDrawdown;

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
     * 成交语义版本
     */
    private String executionModelVersion;

    /**
     * 行情复权口径
     */
    private String priceAdjustment;

    /**
     * 行情数据指纹
     */
    private String dataFingerprint;

    /**
     * 实际请求JSON
     */
    private String requestJson;

    /**
     * 完整结果JSON
     */
    private String resultJson;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 逻辑删除
     */
    @TableLogic
    private Integer deleted;
}
