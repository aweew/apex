package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.quant.domain.dto.HotOverviewResp;
import com.awe.apex.quant.domain.entity.MarketHot;
import com.awe.apex.quant.service.IHotService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 多平台热点股票
 */
@RestController
@RequestMapping("/api/hot")
public class HotController {

    @Resource
    private IHotService hotService;

    /**
     * 热点总览
     *
     * @param limit 每源条数
     * @return 总览
     */
    @GetMapping("/overview")
    public Result<HotOverviewResp> overview(@RequestParam(defaultValue = "40") Integer limit) {
        return Result.success(hotService.overview(limit));
    }

    /**
     * 按来源列表
     *
     * @param source 来源
     * @param limit  条数
     * @return 列表
     */
    @GetMapping("/list")
    public Result<List<MarketHot>> list(@RequestParam(defaultValue = "eastmoney") String source,
                                        @RequestParam(defaultValue = "40") Integer limit) {
        return Result.success(hotService.listBySource(source, limit));
    }

    /**
     * 刷新热点（调用 AKShare 脚本）
     *
     * @param sources 来源
     * @param limit   每源条数
     * @return 结果
     */
    @PostMapping("/refresh")
    public Result<Map<String, Object>> refresh(
            @RequestParam(required = false, defaultValue = "eastmoney,xueqiu,baidu") String sources,
            @RequestParam(required = false, defaultValue = "50") Integer limit) {
        return Result.success(hotService.refresh(sources, limit));
    }
}
