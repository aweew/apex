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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("paper_position")
public class PaperPosition implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 账户ID */ private Long accountId;
    /** 证券代码 */ private String code;
    /** 简称 */ private String name;
    /** 持仓数量 */ private Integer quantity;
    /** 成本价 */ private BigDecimal costPrice;
    /** 止损价 */ private BigDecimal stopLoss;
    /** 止盈价 */ private BigDecimal takeProfit;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic private Integer deleted;

    /** 现价（非表字段） */
    @TableField(exist = false)
    private BigDecimal marketPrice;
    /** 市值（非表字段） */
    @TableField(exist = false)
    private BigDecimal marketValue;
    /** 浮动盈亏金额（非表字段） */
    @TableField(exist = false)
    private BigDecimal pnl;
    /** 浮动盈亏比例（非表字段） */
    @TableField(exist = false)
    private BigDecimal pnlPct;

    /** 持有天数（非表字段，自建仓日估算） */
    @TableField(exist = false)
    private Integer holdDays;
}
