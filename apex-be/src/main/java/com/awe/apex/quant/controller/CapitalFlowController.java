package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.quant.domain.dto.CapitalFlowOverviewResp;
import com.awe.apex.quant.service.ApexUserAuthService;
import com.awe.apex.quant.service.ICapitalFlowService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 资金面与龙虎榜接口。
 */
@RestController
@RequestMapping("/api/capital-flow")
public class CapitalFlowController {

    @Resource
    private ICapitalFlowService capitalFlowService;

    @Resource
    private ApexUserAuthService userAuthService;

    /**
     * 查询资金面总览。
     *
     * @param limit 每类榜单条数
     * @return 资金面总览
     */
    @GetMapping("/overview")
    public Result<CapitalFlowOverviewResp> overview(@RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(capitalFlowService.overview(limit));
    }

    /**
     * 管理员刷新资金面数据。
     *
     * @param mode 同步模式flow、lhb或all
     * @param limit 每类榜单条数
     * @return 刷新后的资金面总览
     */
    @PostMapping("/refresh")
    public Result<CapitalFlowOverviewResp> refresh(
            @RequestParam(defaultValue = "all") String mode,
            @RequestParam(defaultValue = "20") Integer limit) {
        userAuthService.requireAdmin();
        return Result.success(capitalFlowService.refresh(mode, limit));
    }
}
