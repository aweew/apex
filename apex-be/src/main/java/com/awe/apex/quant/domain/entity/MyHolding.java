package com.awe.apex.quant.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.awe.apex.quant.domain.dto.ObserveTechSignal;
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
 * 我的持仓（手动维护）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("my_holding")
public class MyHolding implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户ID */
    private Long userId;

    /**
     * 证券代码
     */
    private String code;

    /**
     * 简称
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
     * 市值（非表字段）
     */
    @TableField(exist = false)
    private BigDecimal marketValue;

    /**
     * 浮动盈亏（非表字段）
     */
    @TableField(exist = false)
    private BigDecimal pnl;

    /**
     * 浮动盈亏比例（非表字段）
     */
    @TableField(exist = false)
    private BigDecimal pnlPct;

    /**
     * 今日盈亏金额（非表字段）：相对昨收，现价变动 × 数量
     */
    @TableField(exist = false)
    private BigDecimal todayPnl;

    /**
     * 所属行业（非表字段，来自 stock_basic）
     */
    @TableField(exist = false)
    private String industry;

    /**
     * 今日涨跌幅 %（非表字段，来自 stock_basic.pct_chg）
     */
    @TableField(exist = false)
    private BigDecimal pctChg;

    /**
     * 行情时间（非表字段）
     */
    @TableField(exist = false)
    private LocalDateTime quoteTime;

    /**
     * 所属概念原文（非表字段，来自 company profile）
     */
    @TableField(exist = false)
    private String concepts;

    /**
     * 命中的核心题材（非表字段），至多一个主营题材，如 光模块(CPO)
     */
    @TableField(exist = false)
    private List<String> themeTags;

    /**
     * 技术指标标签（非表字段）
     */
    @TableField(exist = false)
    private List<ObserveTechSignal> techSignals;

    /**
     * 技术命中数
     */
    @TableField(exist = false)
    private Integer techHitCount;

    /**
     * 技术指标总数
     */
    @TableField(exist = false)
    private Integer techTotal;

    /**
     * 技术摘要，如 技术 5/8 · 共振偏强
     */
    @TableField(exist = false)
    private String techSummary;

    /**
     * 估值档位码 UNDERVALUED/FAIR/...
     */
    @TableField(exist = false)
    private String valuationLevel;

    /**
     * 估值档位中文
     */
    @TableField(exist = false)
    private String valuationLabel;

    /**
     * 估值性价比分
     */
    @TableField(exist = false)
    private BigDecimal valuationScore;

    /**
     * 估值一句话
     */
    @TableField(exist = false)
    private String valuationSummary;

    /** 动态市盈率（非表字段） */
    @TableField(exist = false)
    private BigDecimal peDynamic;

    /** 静态市盈率（非表字段） */
    @TableField(exist = false)
    private BigDecimal peStatic;

    /** 滚动市盈率 TTM（非表字段） */
    @TableField(exist = false)
    private BigDecimal peTtm;

    /**
     * 持仓评价：继续持有 / 谨慎持有 / 逢高减仓 / 止盈减仓 / 止损卖出 等
     */
    @TableField(exist = false)
    private String verdict;

    /**
     * 行动建议短句
     */
    @TableField(exist = false)
    private String advice;
}
