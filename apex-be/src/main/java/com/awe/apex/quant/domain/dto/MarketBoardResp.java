package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 行情看板（自选涨跌 + 行业热力 + 信号统计）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketBoardResp {

    /**
     * 涨幅榜
     */
    private List<WatchlistResp> gainers;

    /**
     * 跌幅榜
     */
    private List<WatchlistResp> losers;

    /**
     * 行业热力
     */
    private List<IndustryHeatItem> industryHeat;

    /**
     * 市场宽度
     */
    private MarketBreadthResp breadth;

    /**
     * 波动率体制（沪深300）
     */
    private VolRegimeResp volRegime;

    /**
     * 近五日 BUY 信号数
     */
    private Integer buySignalCount;

    /**
     * 近五日 SELL 信号数
     */
    private Integer sellSignalCount;

    /**
     * 股票池数量
     */
    private Integer universeCount;
}
