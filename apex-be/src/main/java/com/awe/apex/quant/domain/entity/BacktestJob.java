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
     * 证券代码
     */
    private String code;

    /**
     * 策略ID
     */
    private String strategyId;

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
