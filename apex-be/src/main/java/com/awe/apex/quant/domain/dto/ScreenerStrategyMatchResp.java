package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 策略选股命中结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenerStrategyMatchResp {

    /** 证券代码 */
    private String code;

    /** 证券简称 */
    private String name;

    /** 所属市场 */
    private String market;

    /** 所属行业 */
    private String industry;

    /** 最新价 */
    private BigDecimal latestPrice;

    /** 当日涨跌幅 */
    private BigDecimal pctChg;

    /** 当日换手率 */
    private BigDecimal turnoverRate;

    /** 实时量比 */
    private BigDecimal volumeRatio;

    /** 滚动市盈率 */
    private BigDecimal peTtm;

    /** 市净率 */
    private BigDecimal pb;

    /** 总市值 */
    private BigDecimal totalMv;

    /** 流通市值 */
    private BigDecimal circMv;

    /** 区间涨跌幅 */
    private BigDecimal rangeReturn;

    /** 近期涨停次数 */
    private Integer limitUpCount;

    /** 连续上涨天数 */
    private Integer upDays;

    /** 20日相对沪深300强度 */
    private BigDecimal rs20;

    /** ATR14占现价比例 */
    private BigDecimal atrPct;

    /** 区间价格位置 */
    private BigDecimal pricePosition;

    /** 距最近涨停交易日数 */
    private Integer daysSinceLimitUp;

    /** 最新成交量相对前期均量百分比 */
    private BigDecimal volumeMaRatio;

    /** 最新收盘价相对均线距离百分比 */
    private BigDecimal closeMaDistancePct;

    /** 是否突破前期高点 */
    private Boolean breakoutPreviousHigh;

    /** MA5、MA10、MA20是否多头排列 */
    private Boolean maBullishAlignment;

    /** 首次封板时间 */
    private String firstSealTime;

    /** 最后封板时间 */
    private String lastSealTime;

    /** 炸板次数 */
    private Integer breakCount;

    /** 封单金额 */
    private BigDecimal sealAmount;

    /** 成交额 */
    private BigDecimal amount;

    /** 涨停题材 */
    private String theme;

    /** 同题材涨停家数 */
    private Integer themeLinkageCount;

    /** 分时均价线上方占比 */
    private BigDecimal intradayAboveAvgRatio;

    /** 当前价是否不低于分时均价 */
    private Boolean intradayCurrentAboveAvg;

    /** 最大连续跌破均价分钟数 */
    private Integer intradayMaxBelowMinutes;

    /** 分时点数量 */
    private Integer intradayPointCount;

    /** 分时截止分钟 */
    private String intradayAsOf;

    /** 命中依据 */
    private List<ScreenerRuleEvidenceResp> evidence;

    /** 最近 20 个交易日收盘价，用于结果缩略图。 */
    private List<BigDecimal> sparkCloses;
}
