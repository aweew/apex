package com.awe.apex.quant.controller;

import com.awe.apex.quant.domain.dto.CorrelationMatrixResp;
import com.awe.apex.quant.domain.dto.WatchlistImportReq;
import com.awe.apex.quant.domain.dto.WatchlistImportResp;
import com.awe.apex.quant.domain.dto.WatchlistMoverResp;
import com.awe.apex.quant.domain.dto.WatchlistResp;
import com.awe.apex.quant.service.IWatchlistService;
import com.awe.apex.common.api.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 自选股接口
 */
@RestController
@RequestMapping("/api/watchlist")
public class WatchlistController {

    @Resource
    private IWatchlistService watchlistService;

    /**
     * 自选列表
     *
     * @param groupName 分组
     * @return 列表
     */
    @GetMapping
    public Result<List<WatchlistResp>> list(@RequestParam(required = false) String groupName) {
        return Result.success(watchlistService.listWatchlist(groupName));
    }

    /**
     * 从妙想文件导入自选
     *
     * @param req 导入请求
     * @return 导入结果
     */
    @PostMapping("/import")
    public Result<WatchlistImportResp> importWatchlist(@RequestBody WatchlistImportReq req) {
        return Result.success(watchlistService.importFromMxFile(req));
    }

    /**
     * 刷新分组行情快照
     */
    @PostMapping("/refresh-quotes")
    public Result<Map<String, Object>> refreshQuotes(@RequestParam(required = false) String groupName,
                                                     @RequestParam(required = false, defaultValue = "40") Integer limit,
                                                     @RequestParam(required = false, defaultValue = "true") Boolean preferMissing) {
        return Result.success(watchlistService.refreshQuotes(groupName, limit, preferMissing));
    }

    /**
     * 多轮补齐行情覆盖
     */
    @PostMapping("/fill-quotes")
    public Result<Map<String, Object>> fillQuotes(@RequestParam(required = false) String groupName,
                                                  @RequestParam(required = false, defaultValue = "3") Integer rounds,
                                                  @RequestParam(required = false, defaultValue = "40") Integer limit) {
        return Result.success(watchlistService.fillQuotes(groupName, rounds, limit));
    }

    /**
     * 自选异动提醒
     */
    @GetMapping("/movers")
    public Result<WatchlistMoverResp> movers(@RequestParam(required = false) String groupName,
                                             @RequestParam(required = false, defaultValue = "5") BigDecimal threshold,
                                             @RequestParam(required = false, defaultValue = "10") Integer limit) {
        return Result.success(watchlistService.movers(groupName, threshold, limit));
    }

    /**
     * 自选日收益相关性
     */
    @GetMapping("/correlation")
    public Result<CorrelationMatrixResp> correlation(@RequestParam(required = false) String groupName,
                                                     @RequestParam(required = false, defaultValue = "8") Integer limit,
                                                     @RequestParam(required = false, defaultValue = "60") Integer lookback) {
        return Result.success(watchlistService.correlation(groupName, limit, lookback));
    }
}
