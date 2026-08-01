package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 行业涨跌热力
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndustryHeatItem {

    /**
     * 行业
     */
    private String industry;

    /**
     * 股票数
     */
    private Integer stockCount;

    /**
     * 平均涨跌幅%
     */
    private BigDecimal avgPctChg;

    /**
     * 上涨家数
     */
    private Integer upCount;

    /**
     * 下跌家数
     */
    private Integer downCount;
}
