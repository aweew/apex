package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 大盘云图响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketHeatmapResp {

    /**
     * 维度 INDUSTRY / CONCEPT / THEME
     */
    private String boardType;

    /**
     * 着色指标 pctChg / pe / netInflow
     */
    private String colorBy;

    /**
     * 块大小指标 circMv / amount / stockCount
     */
    private String sizeBy;

    /**
     * 数据来源 stock_basic / sector_quote
     */
    private String source;

    /**
     * 交易日（板块源）
     */
    private LocalDate tradeDate;

    /**
     * 生成时间
     */
    private LocalDateTime asOf;

    /**
     * 色块列表
     */
    private List<MarketHeatmapNode> nodes;

    /**
     * 说明
     */
    private String note;
}
