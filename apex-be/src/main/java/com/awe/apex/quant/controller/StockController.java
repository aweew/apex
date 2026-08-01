package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.quant.domain.dto.StockDetailResp;
import com.awe.apex.quant.domain.dto.StockSearchItem;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.service.IStockService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 股票基本信息与详情
 */
@RestController
@RequestMapping("/api/stock")
public class StockController {

    @Resource
    private IStockService stockService;

    /**
     * 搜索股票
     *
     * @param q     关键词
     * @param limit 条数
     * @return 结果
     */
    @GetMapping("/search")
    public Result<List<StockSearchItem>> search(@RequestParam String q,
                                                @RequestParam(defaultValue = "15") Integer limit) {
        return Result.success(stockService.search(q, limit));
    }

    /**
     * 同步基本信息
     *
     * @param code 证券代码
     * @return 基本信息
     */
    @PostMapping("/{code}/sync")
    public Result<StockBasic> sync(@PathVariable String code) {
        return Result.success(stockService.syncBasic(code));
    }

    /**
     * 股票详情（基本信息 + K 线）
     *
     * @param code     证券代码
     * @param barLimit K 线条数
     * @param refresh  是否刷新基本信息
     * @return 详情
     */
    @GetMapping("/{code}")
    public Result<StockDetailResp> detail(@PathVariable String code,
                                          @RequestParam(defaultValue = "120") Integer barLimit,
                                          @RequestParam(defaultValue = "false") Boolean refresh) {
        return Result.success(stockService.detail(code, barLimit, refresh));
    }
}
