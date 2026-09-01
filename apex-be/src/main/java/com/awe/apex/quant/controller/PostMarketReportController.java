package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.quant.domain.dto.PostMarketReportResp;
import com.awe.apex.quant.service.IPostMarketReportService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 最新交易日盘后总结接口。
 */
@RestController
@RequestMapping("/api/post-market-report")
public class PostMarketReportController {

    @Resource
    private IPostMarketReportService postMarketReportService;

    /**
     * 读取盘后可见窗口内的最新交易日总结。
     *
     * @return 最新盘后总结
     */
    @GetMapping
    public Result<PostMarketReportResp> latest() {
        return Result.success(postMarketReportService.latest(false));
    }

    /**
     * 使用本地收盘数据重新生成最新交易日总结。
     *
     * @return 最新盘后总结
     */
    @PostMapping("/refresh")
    public Result<PostMarketReportResp> refresh() {
        return Result.success(postMarketReportService.refresh());
    }
}
