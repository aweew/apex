package com.awe.apex.quant.controller;

import com.awe.apex.common.api.PageResponse;
import com.awe.apex.common.api.Result;
import com.awe.apex.quant.domain.dto.ScreenerMetaResp;
import com.awe.apex.quant.domain.dto.ScreenerReq;
import com.awe.apex.quant.domain.dto.ScreenerStrategyRunReq;
import com.awe.apex.quant.domain.dto.ScreenerStrategyRunResp;
import com.awe.apex.quant.domain.dto.WatchlistResp;
import com.awe.apex.quant.service.IScreenerService;
import com.awe.apex.quant.service.IScreenerStrategyExecutionService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 条件选股
 */
@RestController
@RequestMapping("/api/screener")
public class ScreenerController {

    @Resource
    private IScreenerService screenerService;

    @Resource
    private IScreenerStrategyExecutionService strategyExecutionService;

    /**
     * 按估值/行业/K线条件筛选
     */
    @PostMapping("/run")
    public Result<List<WatchlistResp>> run(@RequestBody(required = false) ScreenerReq req) {
        return Result.success(screenerService.run(req));
    }

    /**
     * 运行可维护选股策略。
     *
     * @param req 策略运行请求
     * @return 策略命中结果与数据状态
     */
    @PostMapping("/strategy-run")
    public Result<ScreenerStrategyRunResp> strategyRun(@RequestBody ScreenerStrategyRunReq req) {
        return Result.success(strategyExecutionService.run(req));
    }

    /**
     * 全市场与股票池数量
     */
    @GetMapping("/meta")
    public Result<ScreenerMetaResp> meta() {
        return Result.success(screenerService.meta());
    }

    /**
     * 分页浏览全市场股票（stock_basic）
     */
    @GetMapping("/market")
    public Result<PageResponse<WatchlistResp>> market(@RequestParam(required = false) String keyword,
                                                      @RequestParam(defaultValue = "1") Integer page,
                                                      @RequestParam(defaultValue = "50") Integer size,
                                                      @RequestParam(defaultValue = "true") Boolean excludeSt) {
        return Result.success(screenerService.listMarket(keyword, page, size, excludeSt));
    }
}
