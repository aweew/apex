package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

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
     * 板块类型 INDUSTRY/CONCEPT/THEME
     */
    private String boardType;
}
