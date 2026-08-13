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
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 高评分交易者聚合形成的 Smart Money 因子。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("smart_money_factor")
public class SmartMoneyFactor implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    /** 主键 */ @TableId(type = IdType.AUTO) private Long id;
    /** 交易日 */ private LocalDate tradeDate;
    /** 证券代码 */ private String symbol;
    /** 证券简称 */ private String stockName;
    /** 高分交易者净买入额 */ private BigDecimal netBuyAmount;
    /** 高分交易者资产规模 */ private BigDecimal traderTotalAsset;
    /** 因子值 */ private BigDecimal factorValue;
    /** 参与交易者数 */ private Integer traderCount;
    /** 买卖共识度 */ private BigDecimal consensus;
    /** T+1 收益率 */ private BigDecimal return1d;
    /** T+5 收益率 */ private BigDecimal return5d;
    /** T+10 收益率 */ private BigDecimal return10d;
    /** T+20 收益率 */ private BigDecimal return20d;
    /** 创建时间 */ private LocalDateTime createTime;
    /** 更新时间 */ private LocalDateTime updateTime;
}
