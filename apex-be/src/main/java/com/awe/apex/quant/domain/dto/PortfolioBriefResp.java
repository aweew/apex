package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 组合级研判简报：总结 / 思路 / 操作建议 / 关注点 / 风险
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioBriefResp {

    /**
     * 组合姿态：防守 / 均衡 / 偏进攻
     */
    private String stance;

    /**
     * 一句话总结
     */
    private String summary;

    /**
     * 持仓思路（结构与主线）
     */
    private String thesis;

    /**
     * 操作建议（按优先级）
     */
    @Builder.Default
    private List<PortfolioTipItem> actions = new ArrayList<>();

    /**
     * 关注点
     */
    @Builder.Default
    private List<String> watchPoints = new ArrayList<>();

    /**
     * 风险预警
     */
    @Builder.Default
    private List<PortfolioTipItem> risks = new ArrayList<>();

    /**
     * 单票最高仓位%
     */
    private BigDecimal maxWeightPct;

    /**
     * 最高仓位代码
     */
    private String maxWeightCode;

    /**
     * 主题材名称
     */
    private String topTheme;

    /**
     * 主题材市值占比%
     */
    private BigDecimal topThemePct;

    /**
     * ETF 市值占比%
     */
    private BigDecimal etfPct;

    /**
     * 止损触及只数
     */
    private Integer stopHitCount;

    /**
     * 止盈触及只数
     */
    private Integer takeHitCount;

    /**
     * 技术偏弱只数
     */
    private Integer weakTechCount;

    /**
     * 估值偏贵只数
     */
    private Integer richValCount;
}
