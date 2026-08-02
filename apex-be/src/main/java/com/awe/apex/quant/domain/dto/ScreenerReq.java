package com.awe.apex.quant.domain.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 选股器请求
 */
@Data
public class ScreenerReq {

    /**
     * 选股范围：空/全部市场/__MARKET__=全市场；其余=自选分组名
     */
    private String groupName;

    /**
     * PE 下限
     */
    private BigDecimal peMin;

    /**
     * PE 上限
     */
    private BigDecimal peMax;

    /**
     * PB 下限
     */
    private BigDecimal pbMin;

    /**
     * PB 上限
     */
    private BigDecimal pbMax;

    /**
     * 最小流通市值（元）
     */
    private BigDecimal minCircMv;

    /**
     * 最大流通市值（元）
     */
    private BigDecimal maxCircMv;

    /**
     * 行业包含
     */
    private String industry;

    /**
     * 涨跌幅下限（%）
     */
    private BigDecimal pctChgMin;

    /**
     * 涨跌幅上限（%）
     */
    private BigDecimal pctChgMax;

    /**
     * 近20日涨跌下限（%）
     */
    private BigDecimal pctChg20Min;

    /**
     * 近20日涨跌上限（%）
     */
    private BigDecimal pctChg20Max;

    /**
     * 要求本地 K 线不少于
     */
    private Integer minBars;

    /**
     * 排除 ST
     */
    private Boolean excludeSt;

    /**
     * 排除涨停（涨跌幅≥9.5）
     */
    private Boolean excludeLimitUp;

    /**
     * 排除跌停（涨跌幅≤-9.5）
     */
    private Boolean excludeLimitDown;

    /**
     * 量比下限（当日量/近20日均量）
     */
    private BigDecimal minVolumeRatio;

    /**
     * 连续上涨天数下限
     */
    private Integer minUpDays;

    /**
     * 相对沪深300的 RS20 下限（百分点）
     */
    private BigDecimal rs20Min;

    /**
     * ATR14/现价 上限（%，过滤过热波动）
     */
    private BigDecimal maxAtrPct;

    /**
     * ATR14/现价 下限（%）
     */
    private BigDecimal minAtrPct;

    /**
     * 返回条数
     */
    private Integer limit;
}
