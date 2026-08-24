package com.awe.apex.quant.controller;

import com.awe.apex.quant.domain.dto.BarSyncReq;
import com.awe.apex.quant.domain.dto.BarSyncResp;
import com.awe.apex.quant.domain.dto.FillBarsResp;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.service.IBarDailyService;
import com.awe.apex.common.api.Result;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 日线行情接口
 */
@RestController
@RequestMapping("/api/data/bars")
public class BarDailyController {

    @Resource
    private IBarDailyService barDailyService;

    /**
     * 同步日线
     *
     * @param req 同步请求
     * @return 同步结果
     */
    @PostMapping("/sync")
    public Result<BarSyncResp> sync(@Valid @RequestBody BarSyncReq req) {
        return Result.success(barDailyService.syncBars(req));
    }

    /**
     * 同步个股详情页日线
     *
     * @param req 同步请求
     * @return 同步结果
     */
    @PostMapping("/sync-fast")
    public Result<BarSyncResp> syncFast(@Valid @RequestBody BarSyncReq req) {
        return Result.success(barDailyService.syncBarsFast(req));
    }

    /**
     * 按自选分组同步日线
     */
    @PostMapping("/sync-group")
    public Result<BarSyncResp> syncGroup(@RequestParam(required = false) String groupName,
                                         @RequestParam(required = false) String beginDate,
                                         @RequestParam(required = false) String endDate) {
        return Result.success(barDailyService.syncWatchlistGroup(groupName, beginDate, endDate));
    }

    /**
     * 仅同步缺失/过期日线
     */
    @PostMapping("/sync-stale")
    public Result<BarSyncResp> syncStale(@RequestParam(required = false) String groupName,
                                         @RequestParam(required = false, defaultValue = "40") Integer limit) {
        return Result.success(barDailyService.syncStaleWatchlist(groupName, limit));
    }

    /**
     * 多轮补齐自选缺失/过期日线
     */
    @PostMapping("/fill")
    public Result<FillBarsResp> fill(@RequestParam(required = false) String groupName,
                                     @RequestParam(required = false, defaultValue = "3") Integer rounds,
                                     @RequestParam(required = false, defaultValue = "40") Integer limit) {
        return Result.success(barDailyService.fillWatchlist(groupName, rounds, limit));
    }

    /**
     * 查询日线
     *
     * @param code 证券代码
     * @param limit 条数
     * @return 日线列表（按日期升序）
     */
    @GetMapping("/list")
    public Result<List<BarDaily>> list(@RequestParam String code,
                                       @RequestParam(defaultValue = "60") Integer limit) {
        List<BarDaily> list = barDailyService.list(Wrappers.<BarDaily>lambdaQuery()
                .eq(BarDaily::getCode, code)
                .orderByDesc(BarDaily::getTradeDate)
                .last("limit " + Math.max(1, Math.min(limit, 500))));
        list.sort((a, b) -> a.getTradeDate().compareTo(b.getTradeDate()));
        return Result.success(list);
    }
}
