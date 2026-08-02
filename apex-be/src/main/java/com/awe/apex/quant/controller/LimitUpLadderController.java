package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.quant.domain.dto.LimitUpLadderResp;
import com.awe.apex.quant.domain.dto.LimitUpRefreshResp;
import com.awe.apex.quant.service.ILimitUpLadderService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 连板天梯 / 涨停复盘
 */
@RestController
@RequestMapping("/api/limit-up")
public class LimitUpLadderController {

    @Resource
    private ILimitUpLadderService limitUpLadderService;

    /**
     * 连板天梯
     *
     * @param tradeDate 交易日 yyyy-MM-dd，可空
     * @return 天梯
     */
    @GetMapping("/ladder")
    public Result<LimitUpLadderResp> ladder(@RequestParam(required = false) String tradeDate) {
        return Result.success(limitUpLadderService.ladder(tradeDate));
    }

    /**
     * 刷新涨停池
     *
     * @param tradeDate 交易日可空
     * @return 结果
     */
    @PostMapping("/refresh")
    public Result<LimitUpRefreshResp> refresh(@RequestParam(required = false) String tradeDate) {
        return Result.success(limitUpLadderService.refresh(tradeDate));
    }
}
