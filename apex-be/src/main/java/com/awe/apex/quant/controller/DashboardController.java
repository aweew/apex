package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.quant.domain.dto.DashboardResp;
import com.awe.apex.quant.service.IDashboardService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仪表盘
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Resource
    private IDashboardService dashboardService;

    /**
     * 概览
     */
    @GetMapping("/overview")
    public Result<DashboardResp> overview(@RequestParam(required = false) Long accountId) {
        return Result.success(dashboardService.overview(accountId));
    }
}
