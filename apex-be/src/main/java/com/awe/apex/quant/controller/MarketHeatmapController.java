package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.quant.domain.dto.MarketHeatmapResp;
import com.awe.apex.quant.domain.dto.WatchlistResp;
import com.awe.apex.quant.service.IMarketHeatmapService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 大盘云图
 */
@RestController
@RequestMapping("/api/market/heatmap")
public class MarketHeatmapController {

    @Resource
    private IMarketHeatmapService marketHeatmapService;

    /**
     * 云图色块数据
     *
     * @param type      INDUSTRY/CONCEPT/THEME
     * @param colorBy   pctChg/pe/netInflow
     * @param sizeBy    circMv/amount/stockCount
     * @param limit     块数上限
     * @param excludeSt 兼容参数
     * @return 云图
     */
    @GetMapping
    public Result<MarketHeatmapResp> heatmap(
            @RequestParam(defaultValue = "INDUSTRY") String type,
            @RequestParam(defaultValue = "pctChg") String colorBy,
            @RequestParam(required = false) String sizeBy,
            @RequestParam(defaultValue = "80") Integer limit,
            @RequestParam(required = false) Boolean excludeSt) {
        return Result.success(marketHeatmapService.heatmap(type, colorBy, sizeBy, limit, excludeSt));
    }

    /**
     * 行业下钻成分股
     *
     * @param industry 行业名
     * @param limit    条数
     * @return 列表
     */
    @GetMapping("/industry-stocks")
    public Result<List<WatchlistResp>> industryStocks(
            @RequestParam String industry,
            @RequestParam(defaultValue = "40") Integer limit) {
        return Result.success(marketHeatmapService.industryStocks(industry, limit));
    }
}
