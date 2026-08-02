package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 综合分析 · 资金与情绪维度
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAnalysisCapitalResp {

    /**
     * 量比（当日量/近20日均量）
     */
    private BigDecimal volumeRatio;

    /**
     * 量能解读
     */
    private String volumeNote;

    /**
     * 所属行业/概念板块名
     */
    private String sectorName;

    /**
     * 板块类型 INDUSTRY/CONCEPT
     */
    private String sectorType;

    /**
     * 板块涨跌幅%
     */
    private BigDecimal sectorPctChg;

    /**
     * 板块资金净流入（元）
     */
    private BigDecimal sectorNetInflow;

    /**
     * 板块主力净流入（元）
     */
    private BigDecimal sectorMainNetInflow;

    /**
     * 板块资金解读
     */
    private String sectorNote;

    /**
     * 是否出现在多源热点共振
     */
    private Boolean hotHit;

    /**
     * 热点来源数
     */
    private Integer hotSourceCount;

    /**
     * 热点来源列表
     */
    private List<String> hotSources;

    /**
     * 热点最佳排名
     */
    private Integer hotBestRank;

    /**
     * 资金/情绪综合说明
     */
    private String summary;
}
