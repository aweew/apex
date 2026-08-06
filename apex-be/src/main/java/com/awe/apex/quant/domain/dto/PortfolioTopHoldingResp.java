package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 组合前三仓位摘要（列表/分享用）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioTopHoldingResp {

    /**
     * 证券代码
     */
    private String code;

    /**
     * 证券简称
     */
    private String name;

    /**
     * 今日涨跌幅%
     */
    private BigDecimal pctChg;

    /**
     * 仓位占比%
     */
    private BigDecimal weightPct;

    /**
     * 今日浮盈
     */
    private BigDecimal todayPnl;
}
