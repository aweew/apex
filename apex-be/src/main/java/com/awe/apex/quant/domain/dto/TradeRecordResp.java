package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户交易记录响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeRecordResp {

    /** 记录ID */
    private Long id;

    /** 成交日 */
    private LocalDate tradeDate;

    /** 成交或持仓变动时间 */
    private LocalDateTime tradeTime;

    /** 证券代码 */
    private String code;

    /** 证券简称 */
    private String stockName;

    /** 证券市场 */
    private String market;

    /** 交易方向 */
    private String side;

    /** 持仓变动类型 */
    private String changeType;

    /** 本次变化数量 */
    private Integer quantity;

    /** 变动前数量 */
    private Integer beforeQuantity;

    /** 变动后数量 */
    private Integer afterQuantity;

    /** 成交价或估算参考价 */
    private BigDecimal price;

    /** 成交额或估算金额 */
    private BigDecimal amount;

    /** 组合ID */
    private Long portfolioId;

    /** 交易发生时的组合名称 */
    private String portfolioName;

    /** 交易发生时的组合归属人标签 */
    private String ownerLabel;

    /** 记录来源 */
    private String source;

    /** 价格来源 */
    private String priceSource;

    /** 价格是否为估算值 */
    private Boolean estimated;

    /** 当前最新价 */
    private BigDecimal latestPrice;

    /** 卖出后截至最新价的涨跌幅百分比 */
    private BigDecimal latestReturnPct;

    /** 卖出后最大上涨幅百分比 */
    private BigDecimal maxRisePct;

    /** 卖出后最大下跌幅百分比 */
    private BigDecimal maxFallPct;

    /** 备注 */
    private String note;
}
