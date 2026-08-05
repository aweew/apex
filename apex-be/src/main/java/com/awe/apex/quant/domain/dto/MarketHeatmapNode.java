package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 大盘云图 · 色块节点
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketHeatmapNode {

    /**
     * 板块/行业代码（行业名可复用名称）
     */
    private String code;

    /**
     * 名称
     */
    private String name;

    /**
     * 块大小权重（流通市值或成交额）
     */
    private BigDecimal value;

    /**
     * 着色指标值（涨跌幅% / 市盈率 / 资金等）
     */
    private BigDecimal colorValue;

    /**
     * 涨跌幅%
     */
    private BigDecimal pctChg;

    /**
     * 流通市值（元）
     */
    private BigDecimal circMv;

    /**
     * 成交额（元，板块源）
     */
    private BigDecimal amount;

    /**
     * 成分股数量
     */
    private Integer stockCount;

    /**
     * 上涨家数
     */
    private Integer upCount;

    /**
     * 下跌家数
     */
    private Integer downCount;

    /**
     * 平均市盈率
     */
    private BigDecimal avgPe;

    /**
     * 资金净流入（元）
     */
    private BigDecimal netInflow;

    /**
     * 领涨股代码
     */
    private String leadStockCode;

    /**
     * 领涨股名称
     */
    private String leadStockName;

    /**
     * 领涨股涨跌幅%
     */
    private BigDecimal leadStockPct;
}
