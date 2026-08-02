package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 每日市场简报（大盘/风格/量能/情绪）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketBriefingResp {

    /**
     * 数据截至日
     */
    private LocalDate asOf;

    /**
     * 操作立场：进攻 / 均衡 / 防守
     */
    private String stance;

    /**
     * 立场分 0-100（越高越偏进攻）
     */
    private Integer stanceScore;

    /**
     * 立场一句话
     */
    private String stanceReason;

    /**
     * 建议总仓区间文案
     */
    private String positionAdvice;

    /**
     * 买入仓位系数（1=正常，&lt;1 降权）
     */
    private BigDecimal buyWeightFactor;

    /**
     * 关键因子
     */
    private List<MarketFactorItem> factors;

    /**
     * 操作提示
     */
    private List<MarketTipItem> tips;

    /**
     * 指数快照文案
     */
    private List<String> indexLines;

    /**
     * 指数结构化快照（看板用）
     */
    private List<MarketIndexItem> indexes;

    /**
     * 量能：放量 / 缩量 / 平量
     */
    private String volumeTrend;

    /**
     * 较 5 日均量涨跌幅 %
     */
    private BigDecimal volumeVsMa5Pct;

    /**
     * 三市成交总额（元，上证+深成+北证50）
     */
    private BigDecimal indexVolume;

    /**
     * 三市成交总额展示文案
     */
    private String indexVolumeText;

    /**
     * 跌停家数
     */
    private Integer limitDownCount;

    /**
     * 热点板块/题材
     */
    private List<String> hotThemes;

    /**
     * 数据充分性 GREEN/YELLOW/RED
     */
    private String dataLevel;

    /**
     * 关键数据是否足够支撑进攻立场
     */
    private Boolean dataSufficient;

    /**
     * 上涨家数（全市场，不含平盘）
     */
    private Integer breadthUp;

    /**
     * 下跌家数（不含平盘）
     */
    private Integer breadthDown;

    /**
     * 平盘家数
     */
    private Integer breadthFlat;

    /**
     * 说明
     */
    private String message;
}
