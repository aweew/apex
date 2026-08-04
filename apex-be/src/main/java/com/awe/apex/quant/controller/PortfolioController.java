package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.quant.domain.dto.PortfolioHoldingSaveReq;
import com.awe.apex.quant.domain.dto.PortfolioImportReq;
import com.awe.apex.quant.domain.dto.PortfolioImportResp;
import com.awe.apex.quant.domain.dto.PortfolioSaveReq;
import com.awe.apex.quant.domain.dto.PortfolioSummaryResp;
import com.awe.apex.quant.domain.entity.Portfolio;
import com.awe.apex.quant.domain.entity.PortfolioDaily;
import com.awe.apex.quant.domain.entity.PortfolioHolding;
import com.awe.apex.quant.service.IPortfolioService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 实盘组合
 */
@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {

    @Resource
    private IPortfolioService portfolioService;

    /**
     * 组合列表
     *
     * @param includeArchived 是否含归档
     * @return 列表
     */
    @GetMapping("/list")
    public Result<List<PortfolioSummaryResp>> list(
            @RequestParam(required = false, defaultValue = "false") Boolean includeArchived) {
        return Result.success(portfolioService.listPortfolios(Boolean.TRUE.equals(includeArchived)));
    }

    /**
     * 新建/更新组合
     *
     * @param req 请求
     * @return 组合
     */
    @PostMapping("/save")
    public Result<Portfolio> save(@RequestBody PortfolioSaveReq req) {
        return Result.success(portfolioService.savePortfolio(req));
    }

    /**
     * 删除组合
     *
     * @param id 组合ID
     * @return 空
     */
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        portfolioService.removePortfolio(id);
        return Result.success(null);
    }

    /**
     * 组合详情
     *
     * @param id 组合ID
     * @return 详情
     */
    @GetMapping("/{id}/detail")
    public Result<PortfolioSummaryResp> detail(@PathVariable Long id) {
        return Result.success(portfolioService.detail(id));
    }

    /**
     * 保存持仓
     *
     * @param id  组合ID
     * @param req 请求
     * @return 持仓
     */
    @PostMapping("/{id}/holding/save")
    public Result<PortfolioHolding> saveHolding(@PathVariable Long id, @RequestBody PortfolioHoldingSaveReq req) {
        return Result.success(portfolioService.saveHolding(id, req));
    }

    /**
     * 删除持仓
     *
     * @param id        组合ID
     * @param holdingId 持仓ID
     * @return 空
     */
    @DeleteMapping("/{id}/holding/{holdingId}")
    public Result<Void> removeHolding(@PathVariable Long id, @PathVariable Long holdingId) {
        portfolioService.removeHolding(id, holdingId);
        return Result.success(null);
    }

    /**
     * 文本导入持仓
     *
     * @param id  组合ID
     * @param req 文本
     * @return 结果
     */
    @PostMapping("/{id}/import")
    public Result<PortfolioImportResp> importHoldings(@PathVariable Long id, @RequestBody PortfolioImportReq req) {
        return Result.success(portfolioService.importHoldings(id, req));
    }

    /**
     * 刷新组合持仓行情
     *
     * @param id          组合ID
     * @param onlyMissing 是否只刷缺现价
     * @return 结果
     */
    @PostMapping("/{id}/refresh-quotes")
    public Result<Map<String, Object>> refreshQuotes(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "false") Boolean onlyMissing) {
        return Result.success(portfolioService.refreshQuotes(id, onlyMissing));
    }

    /**
     * 打当日快照
     *
     * @param id 组合ID
     * @return 快照
     */
    @PostMapping("/{id}/snapshot")
    public Result<PortfolioDaily> snapshot(@PathVariable Long id) {
        return Result.success(portfolioService.snapshot(id));
    }

    /**
     * 全部活跃组合打快照
     *
     * @return 结果
     */
    @PostMapping("/snapshot-all")
    public Result<Map<String, Object>> snapshotAll() {
        int ok = portfolioService.snapshotAll();
        Map<String, Object> result = new HashMap<>();
        result.put("success", ok);
        result.put("message", "已为 " + ok + " 个组合写入今日快照");
        return Result.success(result);
    }

    /**
     * 日收益序列
     *
     * @param id   组合ID
     * @param days 近 N 日
     * @return 列表
     */
    @GetMapping("/{id}/daily")
    public Result<List<PortfolioDaily>> daily(@PathVariable Long id,
                                              @RequestParam(required = false, defaultValue = "60") Integer days) {
        return Result.success(portfolioService.listDaily(id, days));
    }
}
