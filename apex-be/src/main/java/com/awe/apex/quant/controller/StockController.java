package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.quant.domain.dto.CompanyProfileResp;
import com.awe.apex.quant.domain.dto.StockAnalysisResp;
import com.awe.apex.quant.domain.dto.StockDetailResp;
import com.awe.apex.quant.domain.dto.StockFundamentalResp;
import com.awe.apex.quant.domain.dto.StockIntradayResp;
import com.awe.apex.quant.domain.dto.StockSearchItem;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.service.ICompanyProfileService;
import com.awe.apex.quant.service.IStockAnalysisService;
import com.awe.apex.quant.service.IStockFundamentalService;
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

    @Resource
    private IStockFundamentalService stockFundamentalService;

    @Resource
    private ICompanyProfileService companyProfileService;

    @Resource
    private IStockAnalysisService stockAnalysisService;

    /**
     * 按代码、名称或拼音缩写搜索股票
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
     * 同步实时行情
     *
     * @param code 证券代码
     * @return 实时行情
     */
    @PostMapping("/{code}/sync-quote")
    public Result<StockBasic> syncQuote(@PathVariable String code) {
        return Result.success(stockService.syncQuote(code));
    }

    /**
     * 股票详情（默认只读本地；refresh=true 才刷新外网基本信息）
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

    /**
     * 个股分时（东财最近交易日）
     *
     * @param code 证券代码
     * @return 分时
     */
    @GetMapping("/{code}/intraday")
    public Result<StockIntradayResp> intraday(@PathVariable String code) {
        return Result.success(stockService.intraday(code));
    }

    /**
     * 个股基本面（只读本地：摘要 / 分析指标 / 三大报表）
     *
     * @param code              证券代码
     * @param periodLimit       摘要与指标期数
     * @param reportPeriodLimit 报表展示期数
     * @return 基本面
     */
    @GetMapping("/{code}/fundamental")
    public Result<StockFundamentalResp> fundamental(@PathVariable String code,
                                                    @RequestParam(defaultValue = "40") Integer periodLimit,
                                                    @RequestParam(defaultValue = "12") Integer reportPeriodLimit) {
        return Result.success(stockFundamentalService.query(code, periodLimit, reportPeriodLimit));
    }

    /**
     * 公司概况（F10 基本资料；本地优先，refresh=true 强制拉东财）
     *
     * @param code    证券代码
     * @param refresh 是否强制刷新
     * @return 概况
     */
    @GetMapping("/{code}/profile")
    public Result<CompanyProfileResp> profile(@PathVariable String code,
                                              @RequestParam(defaultValue = "false") Boolean refresh) {
        return Result.success(companyProfileService.query(code, Boolean.TRUE.equals(refresh)));
    }

    /**
     * 刷新公司概况并落库
     *
     * @param code 证券代码
     * @return 概况
     */
    @PostMapping("/{code}/profile/refresh")
    public Result<CompanyProfileResp> refreshProfile(@PathVariable String code) {
        return Result.success(companyProfileService.query(code, true));
    }

    /**
     * 个股综合研判（技术 + 估值 + 资金情绪 + 策略结论）
     *
     * @param code     证券代码
     * @param side     BUY/SELL
     * @param barLimit K 线条数
     * @return 综合研判
     */
    @GetMapping("/{code}/analysis")
    public Result<StockAnalysisResp> analysis(@PathVariable String code,
                                              @RequestParam(defaultValue = "BUY") String side,
                                              @RequestParam(defaultValue = "120") Integer barLimit,
                                              @RequestParam(defaultValue = "false") Boolean withAi,
                                              @RequestParam(defaultValue = "false") Boolean forceAi) {
        return Result.success(stockAnalysisService.analyze(
                code, side, barLimit, Boolean.TRUE.equals(withAi), Boolean.TRUE.equals(forceAi)));
    }
}
