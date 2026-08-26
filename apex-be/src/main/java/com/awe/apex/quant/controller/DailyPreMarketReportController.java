package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.quant.domain.dto.DailyPreMarketReportResp;
import com.awe.apex.quant.service.IDailyPreMarketReportService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Apex 每日盘前研报。
 */
@RestController
@RequestMapping("/api/pre-market-report")
public class DailyPreMarketReportController {

    @Resource
    private IDailyPreMarketReportService dailyPreMarketReportService;

    /**
     * 查询当前用户的当日盘前研报。
     *
     * @return 每日盘前研报
     */
    @GetMapping
    public Result<DailyPreMarketReportResp> latest() {
        return Result.success(dailyPreMarketReportService.latest(false));
    }

    /**
     * 重新生成当前用户的当日盘前研报。
     *
     * @return 每日盘前研报
     */
    @PostMapping("/refresh")
    public Result<DailyPreMarketReportResp> refresh() {
        return Result.success(dailyPreMarketReportService.latest(true));
    }
}
