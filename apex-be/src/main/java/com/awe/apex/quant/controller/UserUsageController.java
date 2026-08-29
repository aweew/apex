package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.quant.domain.dto.UserActivityReq;
import com.awe.apex.quant.domain.dto.UserUsageOverviewResp;
import com.awe.apex.quant.service.ApexUserAuthService;
import com.awe.apex.quant.service.UserUsageService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户使用情况接口。
 */
@RestController
@RequestMapping("/api/usage")
public class UserUsageController {

    @Resource
    private UserUsageService userUsageService;

    @Resource
    private ApexUserAuthService userAuthService;

    /**
     * 记录当前用户页面访问事件。
     *
     * @param req 页面访问请求
     * @return 空
     */
    @PostMapping("/events/page-view")
    public Result<Void> recordPageView(@Valid @RequestBody UserActivityReq req) {
        userUsageService.recordPageView(req);
        return Result.success();
    }

    /**
     * 查询管理员用户使用情况总览。
     *
     * @param days 统计周期天数
     * @return 用户使用情况总览
     */
    @GetMapping("/admin/overview")
    public Result<UserUsageOverviewResp> overview(@RequestParam(defaultValue = "30") int days) {
        userAuthService.requireAdmin();
        return Result.success(userUsageService.overview(days));
    }
}
