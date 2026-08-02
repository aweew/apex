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
     * 热点板块/题材
     */
    private List<String> hotThemes;

    /**
     * 说明
     */
    private String message;
}
