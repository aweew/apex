package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 连板天梯 / 涨停复盘
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LimitUpLadderResp {

    /**
     * 交易日
     */
    private LocalDate tradeDate;

    /**
     * 可选交易日
     */
    private List<LocalDate> availableDates;

    /**
     * 全市场涨停总数，可能高于天梯收录卡片数
     */
    private Integer totalCount;

    /**
     * 最高连板
     */
    private Integer maxLianban;

    /**
     * 热点题材 Top
     */
    private List<LimitUpThemeStat> themes;

    /**
     * 梯队（高连板在前）
     */
    private List<LimitUpTier> tiers;

    /**
     * 同步时间
     */
    private LocalDateTime syncedAt;

    /**
     * 赚钱效应（昨涨停今表现）
     */
    private LimitUpEffectResp effect;

    /**
     * 说明
     */
    private String message;
}
