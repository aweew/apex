package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.quant.domain.dto.WeekendMarketReportResp;
import com.awe.apex.quant.service.IWeekendMarketReportService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 全市场周末消息面专题研报。
 */
@RestController
@RequestMapping("/api/weekend-report")
public class WeekendMarketReportController {

    @Resource
    private IWeekendMarketReportService weekendMarketReportService;

    /**
     * 读取最新周末研报。
     *
     * @return 周末研报
     */
    @GetMapping
    public Result<WeekendMarketReportResp> latest() {
        return Result.success(weekendMarketReportService.latest(false));
    }

    /**
     * 刷新资讯和观点后重新生成周末研报。
     *
     * @return 周末研报
     */
    @PostMapping("/refresh")
    public Result<WeekendMarketReportResp> refresh() {
        return Result.success(weekendMarketReportService.refresh());
    }
}
