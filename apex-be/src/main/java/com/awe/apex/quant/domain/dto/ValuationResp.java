package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 个股综合估值评估
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValuationResp {

    private String code;
    private String name;
    private String industry;
    private BigDecimal latestPrice;
    private BigDecimal pctChg;
    private BigDecimal peTtm;
    private BigDecimal pb;
    private BigDecimal totalMv;
    private BigDecimal circMv;

    /** 报告期 */
    private LocalDate reportDate;
    private BigDecimal roe;
    private BigDecimal debtRatio;
    private BigDecimal netMargin;
    private BigDecimal eps;
    private BigDecimal bps;
    private BigDecimal revenueYoy;
    private BigDecimal netProfitYoy;

    /** UNDERVALUED / SLIGHTLY_CHEAP / FAIR / SLIGHTLY_EXPENSIVE / OVERVALUED / UNKNOWN */
    private String level;
    /** 中文结论 */
    private String levelLabel;
    /** 综合分 0~100，越高越具性价比 */
    private BigDecimal score;
    /** 一句话总结 */
    private String summary;
    /** 建议：积极观察 / 可关注 / 中性 / 谨慎 / 回避追高 */
    private String actionHint;

    private BigDecimal industryPeMedian;
    private BigDecimal industryPbMedian;
    private BigDecimal pePercentile;
    private BigDecimal pbPercentile;
    private Integer industryPeerCount;

    private BigDecimal peg;
    private BigDecimal earningsYield;
    private BigDecimal fairPe;
    private BigDecimal fairPb;
    private BigDecimal fairPriceLow;
    private BigDecimal fairPriceMid;
    private BigDecimal fairPriceHigh;
    private BigDecimal marginOfSafety;

    private List<ValuationDimensionResp> dimensions;
    private List<String> bullPoints;
    private List<String> bearPoints;
    private List<String> assumptions;
    private String dataNote;
}
