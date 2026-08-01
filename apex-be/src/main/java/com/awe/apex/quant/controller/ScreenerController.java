package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.quant.domain.dto.ScreenerReq;
import com.awe.apex.quant.domain.dto.WatchlistResp;
import com.awe.apex.quant.service.IScreenerService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    /**
     * 按估值/行业/K线条件筛选自选
     */
    @PostMapping("/run")
    public Result<List<WatchlistResp>> run(@RequestBody(required = false) ScreenerReq req) {
        return Result.success(screenerService.run(req));
    }
}
