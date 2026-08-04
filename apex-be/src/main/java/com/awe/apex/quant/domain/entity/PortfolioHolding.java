package com.awe.apex.quant.domain.entity;

import com.awe.apex.quant.domain.dto.ObserveTechSignal;
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
import java.time.LocalDateTime;
import java.util.List;

/**
 * 组合持仓
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("portfolio_holding")
public class PortfolioHolding implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 组合ID
     */
    private Long portfolioId;

    /**
     * 证券代码
     */
    private String code;

    /**
     * 证券简称
     */
    private String name;

    /**
     * 持仓数量（股）
     */
    private Integer quantity;

    /**
     * 成本价
     */
    private BigDecimal costPrice;

    /**
     * 止损价
     */
    private BigDecimal stopLoss;

    /**
     * 止盈价
     */
    private BigDecimal takeProfit;

    /**
     * 备注
     */
    private String note;

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
     * 现价（非表字段）
     */
    @TableField(exist = false)
    private BigDecimal marketPrice;

    /**
     * 涨跌幅%（非表字段）
     */
    @TableField(exist = false)
    private BigDecimal pctChg;

    /**
     * 市值（非表字段）
     */
    @TableField(exist = false)
    private BigDecimal marketValue;

    /**
     * 累计浮盈（非表字段）
     */
    @TableField(exist = false)
    private BigDecimal pnl;

    /**
     * 累计浮盈比例（非表字段，小数）
     */
    @TableField(exist = false)
    private BigDecimal pnlPct;

    /**
     * 今日浮盈（非表字段）
     */
    @TableField(exist = false)
    private BigDecimal todayPnl;

    /**
     * 仓位占比%（非表字段）
     */
    @TableField(exist = false)
    private BigDecimal weightPct;

    /**
     * 行业（非表字段）
     */
    @TableField(exist = false)
    private String industry;

    /**
     * 概念串（非表字段）
     */
    @TableField(exist = false)
    private String concepts;

    /**
     * 题材标签（非表字段）
     */
    @TableField(exist = false)
    private List<String> themeTags;

    /**
     * 技术信号（非表字段）
     */
    @TableField(exist = false)
    private List<ObserveTechSignal> techSignals;

    /**
     * 技术摘要（非表字段）
     */
    @TableField(exist = false)
    private String techSummary;

    /**
     * 估值档位（非表字段）
     */
    @TableField(exist = false)
    private String valuationLevel;

    /**
     * 估值标签（非表字段）
     */
    @TableField(exist = false)
    private String valuationLabel;

    /**
     * 估值说明（非表字段）
     */
    @TableField(exist = false)
    private String valuationSummary;

    /**
     * 评价（非表字段）
     */
    @TableField(exist = false)
    private String verdict;

    /**
     * 建议（非表字段）
     */
    @TableField(exist = false)
    private String advice;
}
