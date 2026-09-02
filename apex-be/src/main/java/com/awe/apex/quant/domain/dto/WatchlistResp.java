package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 自选股响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistResp {

    /**
     * 主键
     */
    private Long id;

    /**
     * 证券代码
     */
    private String code;

    /**
     * 证券简称
     */
    private String name;

    /**
     * 市场
     */
    private String market;

    /**
     * 分组
     */
    private String groupName;

    /**
     * 来源
     */
    private String source;

    /**
     * 最新价
     */
    private BigDecimal latestPrice;

    /**
     * 涨跌幅（%）
     */
    private BigDecimal pctChg;

    /**
     * 近5日涨跌幅（%）
     */
    private BigDecimal pctChg5;

    /**
     * 近20日涨跌幅（%）
     */
    private BigDecimal pctChg20;

    /**
     * 近60日涨跌幅（%）
     */
    private BigDecimal pctChg60;

    /**
     * 市盈率 TTM
     */
    private BigDecimal peTtm;

    /**
     * 动态市盈率（全市场实时列表展示兜底）
     */
    private BigDecimal peDynamic;

    /**
     * 市净率
     */
    private BigDecimal pb;

    /**
     * 行业
     */
    private String industry;

    /**
     * 总市值（元）
     */
    private BigDecimal totalMv;

    /**
     * 流通市值（元）
     */
    private BigDecimal circMv;

    /**
     * 最后一根日线日期
     */
    private LocalDate lastBarDate;

    /**
     * 本地日线条数
     */
    private Integer barCount;

    /**
     * 同步状态：OK / STALE / EMPTY
     */
    private String syncStatus;

    /**
     * 是否在策略股票池中（全市场浏览时填充）
     */
    private Boolean inUniverse;

    /**
     * 量比（选股时填充）
     */
    private BigDecimal volumeRatio;

    /**
     * 连续上涨天数（选股时填充）
     */
    private Integer upDays;

    /**
     * 相对沪深300的20日超额涨跌（百分点）
     */
    private BigDecimal rs20VsHs300;

    /**
     * ATR14占现价%
     */
    private BigDecimal atrPct;

    /** 最近 20 个交易日收盘价，用于列表缩略图。 */
    private List<BigDecimal> sparkCloses;
}
