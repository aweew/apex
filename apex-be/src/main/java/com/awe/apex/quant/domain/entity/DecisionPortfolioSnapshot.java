package com.awe.apex.quant.domain.entity;

import com.awe.apex.quant.domain.dto.DecisionPortfolioHolding;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 决策运行绑定的组合时点快照
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("decision_portfolio_snapshot")
public class DecisionPortfolioSnapshot implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 决策运行ID
     */
    private Long runId;

    /**
     * 默认组合ID
     */
    private Long portfolioId;

    /**
     * 决策日
     */
    private LocalDate actionDate;

    /**
     * 可用现金
     */
    private BigDecimal cash;

    /**
     * 持仓市值
     */
    private BigDecimal marketValue;

    /**
     * 总权益
     */
    private BigDecimal totalEquity;

    /**
     * 历史峰值权益
     */
    private BigDecimal peakEquity;

    /**
     * 当前回撤比例
     */
    private BigDecimal drawdown;

    /**
     * 当前持仓比例
     */
    private BigDecimal exposureRatio;

    /**
     * 市场状态
     */
    private String marketRegime;

    /**
     * 市场状态与风控配置共同约束的总仓上限
     */
    private BigDecimal exposureLimit;

    /**
     * 单票仓位上限
     */
    private BigDecimal singleStockLimit;

    /**
     * 行业仓位上限
     */
    private BigDecimal industryLimit;

    /**
     * ATR止损倍数
     */
    private BigDecimal atrStopMultiplier;

    /**
     * ATR止盈倍数
     */
    private BigDecimal atrTakeMultiplier;

    /**
     * 市场状态判定原因
     */
    private String regimeReason;

    /**
     * 行业暴露JSON
     */
    private String industryExposureJson;

    /**
     * 持仓明细JSON
     */
    private String holdingPayload;

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

    /**
     * 已解析持仓明细
     */
    @Builder.Default
    @TableField(exist = false)
    private List<DecisionPortfolioHolding> holdings = List.of();

    /**
     * 已解析行业暴露
     */
    @Builder.Default
    @TableField(exist = false)
    private Map<String, BigDecimal> industryExposure = new LinkedHashMap<>();
}
