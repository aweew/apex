package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.MarketHeatmapResp;
import com.awe.apex.quant.domain.dto.WatchlistResp;

import java.util.List;

/**
 * 大盘云图
 */
public interface IMarketHeatmapService {

    /**
     * 构建云图色块
     *
     * @param boardType INDUSTRY/CONCEPT/THEME
     * @param colorBy   pctChg/pe/netInflow
     * @param sizeBy    circMv/amount/stockCount
     * @param limit     最多块数
     * @param excludeSt 行业源是否排 ST（已在聚合内默认排除）
     * @return 云图
     */
    MarketHeatmapResp heatmap(String boardType, String colorBy, String sizeBy, Integer limit, Boolean excludeSt);

    /**
     * 行业下钻：按流通市值取成分
     *
     * @param industry 行业名
     * @param limit    条数
     * @return 个股列表
     */
    List<WatchlistResp> industryStocks(String industry, Integer limit);
}
