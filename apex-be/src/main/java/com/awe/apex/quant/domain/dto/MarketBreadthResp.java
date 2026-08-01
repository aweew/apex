package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 市场宽度（基于自选）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketBreadthResp {

    /**
     * 样本数
     */
    private Integer sampleCount;

    /**
     * 上涨家数
     */
    private Integer advanceCount;

    /**
     * 下跌家数
     */
    private Integer declineCount;

    /**
     * 平盘家数
     */
    private Integer flatCount;

    /**
     * 涨跌家数比
     */
    private BigDecimal advanceDeclineRatio;

    /**
     * 近20日上涨占比（pctChg20>0）
     */
    private BigDecimal above20DayPct;

    /**
     * 说明
     */
    private String message;
}
