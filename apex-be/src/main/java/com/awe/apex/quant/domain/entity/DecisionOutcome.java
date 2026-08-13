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
 * 智能决策结果归因
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("decision_outcome")
public class DecisionOutcome implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 决策特征快照ID
     */
    private Long featureSnapshotId;

    /**
     * 操作清单ID
     */
    private Long actionId;

    /**
     * 决策运行ID
     */
    private Long runId;

    /**
     * 证券代码
     */
    private String code;

    /**
     * 决策交易日
     */
    private LocalDate actionDate;

    /**
     * 理论入场交易日
     */
    private LocalDate entryDate;

    /**
     * 首根日线开盘价
     */
    private BigDecimal entryPrice;

    /**
     * 计算状态 PENDING/PARTIAL/COMPLETE/INVALID
     */
    private String status;

    /**
     * 1交易日成本后收益率
     */
    private BigDecimal return1d;

    /**
     * 3交易日成本后收益率
     */
    private BigDecimal return3d;

    /**
     * 5交易日成本后收益率
     */
    private BigDecimal return5d;

    /**
     * 10交易日成本后收益率
     */
    private BigDecimal return10d;

    /**
     * 20交易日成本后收益率
     */
    private BigDecimal return20d;

    /**
     * 1交易日相对沪深300超额收益率
     */
    private BigDecimal excess1d;

    /**
     * 3交易日相对沪深300超额收益率
     */
    private BigDecimal excess3d;

    /**
     * 5交易日相对沪深300超额收益率
     */
    private BigDecimal excess5d;

    /**
     * 10交易日相对沪深300超额收益率
     */
    private BigDecimal excess10d;

    /**
     * 20交易日相对沪深300超额收益率
     */
    private BigDecimal excess20d;

    /**
     * 入场后20根日线内最大有利变动
     */
    private BigDecimal mfe;

    /**
     * 入场后20根日线内最大不利变动
     */
    private BigDecimal mae;

    /**
     * 是否触及止损
     */
    private Integer stopHit;

    /**
     * 首次止损日期
     */
    private LocalDate stopHitDate;

    /**
     * 是否触及止盈
     */
    private Integer targetHit;

    /**
     * 首次止盈日期
     */
    private LocalDate targetHitDate;

    /**
     * 20交易日成本后理论收益率
     */
    private BigDecimal netReturn;

    /**
     * 采纳状态
     */
    private String adoptionStatus;

    /**
     * 实际成交价
     */
    private BigDecimal actualPrice;

    /**
     * 实际仓位
     */
    private BigDecimal actualWeight;

    /**
     * 实际成交滑点
     */
    private BigDecimal slippage;

    /**
     * 实际盈亏
     */
    private BigDecimal actualPnl;

    /**
     * 结果质量状态
     */
    private String qualityStatus;

    /**
     * 最后计算时间
     */
    private LocalDateTime calculatedAt;

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
