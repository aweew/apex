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
     * 今日决策摘要
     */
    private DecisionBlock decision;

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
        /** 涨停家数 */ private Integer limitUpCount;
        /** 上涨家数（宽度） */ private Integer breadthUp;
        /** 下跌家数（宽度） */ private Integer breadthDown;
        /** 指数一行 */ private List<String> indexLines;
        /** 指数小看板 */ private List<MarketIndexItem> indexes;
        /** 量能：放量/缩量/平量 */ private String volumeTrend;
        /** 较5日均量% */ private BigDecimal volumeVsMa5Pct;
        /** 提示 Top */ private List<String> tips;
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
        /** 主线匹配 */ private Boolean mainlineMatch;
        /** 主线名 */ private String mainlineName;
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
