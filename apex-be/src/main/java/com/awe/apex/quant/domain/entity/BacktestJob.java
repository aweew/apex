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
 * 回测任务
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("backtest_job")
public class BacktestJob implements Serializable {

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
     * 策略对比批次ID
     */
    private String comparisonBatchId;

    /**
     * 策略对比集合，按策略ID排序并以逗号分隔
     */
    private String comparisonStrategyIds;

    /**
     * 当前策略参数快照
     */
    private String strategyParameters;

    /**
     * 对比策略配置SHA-256指纹
     */
    private String comparisonConfigFingerprint;

    /**
     * 开始日期
     */
    private LocalDate beginDate;

    /**
     * 结束日期
     */
    private LocalDate endDate;

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
     * 状态
     */
    private String status;

    /**
     * 免责声明
     */
    private String disclaimer;

    /**
     * 消息
     */
    private String message;

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
