package com.awe.apex.quant.domain.entity;

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
}
