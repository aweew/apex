package com.awe.apex.quant.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Smart Trader 当前持仓。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("trader_position")
public class TraderPosition implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    /** 主键 */ @TableId(type = IdType.AUTO) private Long id;
    /** 交易者ID */ private Long traderId;
    /** 证券代码 */ private String symbol;
    /** 证券简称 */ private String stockName;
    /** 当前数量 */ private Integer quantity;
    /** 剩余持仓平均成本 */ private BigDecimal avgCost;
    /** 最新市场价格 */ private BigDecimal marketPrice;
    /** 最新市场价值 */ private BigDecimal marketValue;
    /** 浮动盈亏 */ private BigDecimal profit;
    /** 浮动收益率 */ private BigDecimal profitRate;
    /** 更新时间 */ private LocalDateTime updateTime;
}
