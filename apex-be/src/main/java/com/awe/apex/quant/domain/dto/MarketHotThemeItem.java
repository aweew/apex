package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 主线题材项（名称 + 涨幅）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketHotThemeItem {

    /**
     * 题材/板块代码
     */
    private String code;

    /**
     * 题材/板块名称
     */
    private String name;

    /**
     * 当日涨跌幅 %
     */
    private BigDecimal pctChg;

    /**
     * 近3日涨跌幅 %
     */
    private BigDecimal pctChg3d;

    /**
     * 近5日涨跌幅 %
     */
    private BigDecimal pctChg5d;

    /**
     * 板块类型 INDUSTRY/CONCEPT/THEME
     */
    private String boardType;

    /**
     * 板块行情交易日
     */
    private LocalDate tradeDate;

    /**
     * 板块行情同步时间
     */
    private LocalDateTime syncedAt;
}
