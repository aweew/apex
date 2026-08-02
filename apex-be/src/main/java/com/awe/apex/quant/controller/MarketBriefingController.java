package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.quant.domain.dto.MarketBriefingResp;
import com.awe.apex.quant.service.IMarketBriefingService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 市场简报
 */
@RestController
@RequestMapping("/api/market")
public class MarketBriefingController {

    @Resource
    private IMarketBriefingService marketBriefingService;

    /**
     * 每日市场简报
     *
     * @return 简报
     */
    @GetMapping("/briefing")
    public Result<MarketBriefingResp> briefing() {
        return Result.success(marketBriefingService.briefing());
    }
}
