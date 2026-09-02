package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 决策看板首页聚合
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardHomeResp {

    /**
     * 市场立场摘要
     */
    private MarketBlock market;

    /**
     * 隔夜美股与今日消息面
     */
    private MorningBriefingResp morningBriefing;

    /**
     * 盘前涨跌比预测与收盘回测。
     */
    private MarketBreadthForecastResp breadthForecast;

    /**
     * 集合竞价实时确认。
     */
    private OpeningAuctionResp openingAuction;

    /**
     * 盘前总结与今日操作指引
     */
    private DashboardCommandResp command;

    /**
     * 今日决策摘要
     */
    private DecisionBlock decision;

    /**
     * 观察池可执行/接近 Top（最多 6）
     */
    private List<ObservePoolResp> observeAlerts;

    /**
     * 账户快览
     */
    private AccountBlock account;

    /**
     * 数据可信度
     */
    private DataHealthBlock dataHealth;

    /**
     * 权益曲线（可选，短序列）
     */
    private List<EquityPointResp> equityCurve;

    /**
     * 说明
     */
    private String message;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MarketBlock {
        /** 截至日 */ private LocalDate asOf;
        /** 立场 */ private String stance;
        /** 评分 */ private Integer stanceScore;
        /** 理由 */ private String stanceReason;
        /** 仓位建议 */ private String positionAdvice;
        /** 数据等级 */ private String dataLevel;
        /** 主线题材 */ private List<String> hotThemes;
        /** 主线题材（含涨幅） */ private List<MarketHotThemeItem> hotThemeItems;
        /** 涨停家数 */ private Integer limitUpCount;
        /** 跌停家数 */ private Integer limitDownCount;
        /** 上涨家数（不含平盘） */ private Integer breadthUp;
        /** 下跌家数（不含平盘） */ private Integer breadthDown;
        /** 平盘家数 */ private Integer breadthFlat;
        /** 指数一行 */ private List<String> indexLines;
        /** 指数小看板 */ private List<MarketIndexItem> indexes;
        /** 量能：放量/缩量 */ private String volumeTrend;
        /** 较上一交易日成交额涨跌幅%（字段名历史遗留） */ private BigDecimal volumeVsMa5Pct;
        /** 量能文案如「缩量 -4.50%」 */ private String volumeLabel;
        /** 三市成交总额（元） */ private BigDecimal indexVolume;
        /** 较上一交易日三市成交额增减值（元） */ private BigDecimal indexVolumeChange;
        /** 三市成交总额文案 */ private String indexVolumeText;
        /** 提示 Top */ private List<String> tips;
        /** 赚钱效应观测 */ private MarketEffectResp effect;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DecisionBlock {
        /** 决策日 */ private LocalDate actionDate;
        /** 是否已有今日决策 */ private Boolean hasToday;
        /** 买 */ private Integer buyCount;
        /** 卖 */ private Integer sellCount;
        /** 持有 */ private Integer holdCount;
        /** 可执行提示条数 */ private Integer executableCount;
        /** 低估/偏低条数 */ private Integer valuationCheapCount;
        /** 合理估值条数 */ private Integer valuationFairCount;
        /** 高估/偏高条数 */ private Integer valuationRichCount;
        /** 主线匹配条数 */ private Integer mainlineMatchCount;
        /** 市场立场 */ private String stance;
        /** 风控摘要 */ private String riskNote;
        /** 买入 Top */ private List<HomeActionItem> topBuys;
        /** 卖出 Top（持仓行动） */ private List<HomeActionItem> topSells;
        /** 摘要文案 */ private String summary;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HomeActionItem {
        /** 代码 */ private String code;
        /** 名称 */ private String name;
        /** 动作 */ private String action;
        /** 策略 */ private String strategyId;
        /** 评分 */ private BigDecimal score;
        /** 建议仓位 */ private BigDecimal suggestedWeight;
        /** 今日涨跌幅% */ private BigDecimal pctChg;
        /** 近 20 日日线收盘价 */ private List<BigDecimal> sparkCloses;
        /** 主线匹配 */ private Boolean mainlineMatch;
        /** 主线名 */ private String mainlineName;
        /** 估值档位 */ private String valuationLevel;
        /** 估值标签 */ private String valuationLabel;
        /** 可执行提示 */ private Boolean executableHint;
        /** 联动提示 */ private String linkHint;
        /** 理由 */ private String reason;
        /** 离场规则 */ private String exitRule;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountBlock {
        /** 总资产 */ private BigDecimal totalAsset;
        /** 累计收益 */ private BigDecimal totalReturn;
        /** 仓位 */ private BigDecimal positionRatio;
        /** 最大回撤 */ private BigDecimal maxDrawdown;
        /** 胜率 */ private BigDecimal winRate;
        /** 严重告警数 */ private Integer criticalCount;
        /** 警告数 */ private Integer warnCount;
        /** 持仓数 */ private Integer positionCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataHealthBlock {
        /** GREEN/YELLOW/RED */ private String level;
        /** 短建议 */ private String suggestion;
        /** 自选过期数 */ private Integer barsStaleCount;
        /** 自选空K线数 */ private Integer barsEmptyCount;
        /** 自选总数 */ private Integer watchlistCount;
    }
}
