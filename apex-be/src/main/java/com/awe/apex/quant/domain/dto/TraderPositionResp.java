package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 交易者当前持仓响应。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TraderPositionResp {
    /** 证券代码 */ private String symbol;
    /** 证券简称 */ private String stockName;
    /** 数量 */ private Integer quantity;
    /** 平均成本 */ private BigDecimal avgCost;
    /** 最新价 */ private BigDecimal marketPrice;
    /** 市值 */ private BigDecimal marketValue;
    /** 浮动盈亏 */ private BigDecimal profit;
    /** 浮动收益率 */ private BigDecimal profitRate;
}
