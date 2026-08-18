package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.quant.domain.dto.MyHoldingSaveReq;
import com.awe.apex.quant.domain.dto.HoldingTradeReq;
import com.awe.apex.quant.domain.entity.MyHolding;
import com.awe.apex.quant.service.IMyHoldingService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 我的持仓
 */
@RestController
@RequestMapping("/api/holding")
public class MyHoldingController {

    @Resource
    private IMyHoldingService myHoldingService;

    /**
     * 持仓列表
     *
     * @return 列表
     */
    @GetMapping("/list")
    public Result<List<MyHolding>> list() {
        return Result.success(myHoldingService.listHoldings());
    }

    /**
     * 新增或更新持仓
     *
     * @param req 请求
     * @return 持仓
     */
    @PostMapping("/save")
    public Result<MyHolding> save(@RequestBody MyHoldingSaveReq req) {
        return Result.success(myHoldingService.save(req));
    }

    /**
     * 买入或卖出真实持仓
     *
     * @param req 成交请求
     * @return 变更后的持仓，全部卖出时为空
     */
    @PostMapping("/trade")
    public Result<MyHolding> tradeHolding(@RequestBody HoldingTradeReq req) {
        return Result.success(myHoldingService.tradeHolding(req));
    }

    /**
     * 删除持仓
     *
     * @param id 主键
     * @return 空
     */
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        myHoldingService.remove(id);
        return Result.success(null);
    }

    /**
     * 刷新持仓行情
     *
     * @param onlyMissing 是否只刷缺现价的
     * @return 结果
     */
    @PostMapping("/refresh-quotes")
    public Result<Map<String, Object>> refreshQuotes(
            @RequestParam(required = false, defaultValue = "true") Boolean onlyMissing) {
        return Result.success(myHoldingService.refreshQuotes(onlyMissing));
    }
}
