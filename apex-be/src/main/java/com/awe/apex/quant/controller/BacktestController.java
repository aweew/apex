package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.quant.domain.dto.BacktestDetailResp;
import com.awe.apex.quant.domain.dto.BacktestRunReq;
import com.awe.apex.quant.domain.dto.BatchBacktestItemResp;
import com.awe.apex.quant.domain.dto.BatchBacktestReq;
import com.awe.apex.quant.domain.dto.BenchmarkCompareResp;
import com.awe.apex.quant.domain.dto.ParamSweepItemResp;
import com.awe.apex.quant.domain.dto.ParamSweepReq;
import com.awe.apex.quant.domain.dto.PortfolioBacktestReq;
import com.awe.apex.quant.domain.dto.PortfolioBacktestResp;
import com.awe.apex.quant.domain.dto.StrategyCompareItemResp;
import com.awe.apex.quant.domain.dto.StrategyLeaderboardItemResp;
import com.awe.apex.quant.domain.dto.MonteCarloResp;
import com.awe.apex.quant.domain.dto.MonthlyReturnResp;
import com.awe.apex.quant.domain.dto.WalkForwardResp;
import com.awe.apex.quant.domain.entity.BacktestJob;
import com.awe.apex.quant.service.IBacktestService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 回测接口
 */
@RestController
@RequestMapping("/api/backtest")
public class BacktestController {

    @Resource
    private IBacktestService backtestService;

    /**
     * 运行回测
     *
     * @param req 请求
     * @return 任务
     */
    @PostMapping("/run")
    public Result<BacktestJob> run(@Valid @RequestBody BacktestRunReq req) {
        return Result.success(backtestService.run(req));
    }

    /**
     * 最近回测任务
     *
     * @param limit 条数
     * @return 任务列表
     */
    @GetMapping("/jobs")
    public Result<List<BacktestJob>> jobs(@RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(backtestService.listJobs(limit));
    }

    /**
     * 批量回测（默认取最新股票池前 N 只）
     */
    @PostMapping("/batch")
    public Result<List<BatchBacktestItemResp>> batch(@RequestBody(required = false) BatchBacktestReq req) {
        return Result.success(backtestService.batchRun(req));
    }

    /**
     * 单票多策略对比 S1/S2/S3
     */
    @PostMapping("/compare")
    public Result<List<StrategyCompareItemResp>> compare(@RequestBody BacktestRunReq req) {
        return Result.success(backtestService.compareStrategies(req));
    }

    /**
     * 多标的等权组合回测
     */
    @PostMapping("/portfolio")
    public Result<PortfolioBacktestResp> portfolio(@RequestBody(required = false) PortfolioBacktestReq req) {
        return Result.success(backtestService.portfolioRun(req));
    }

    /**
     * 策略 vs 基准（默认沪深300）
     */
    @PostMapping("/benchmark")
    public Result<BenchmarkCompareResp> benchmark(@RequestBody BacktestRunReq req,
                                                  @RequestParam(required = false, defaultValue = "000300") String benchmarkCode) {
        return Result.success(backtestService.compareBenchmark(req, benchmarkCode));
    }

    /**
     * 策略绩效榜
     */
    @GetMapping("/leaderboard")
    public Result<List<StrategyLeaderboardItemResp>> leaderboard(@RequestParam(defaultValue = "100") Integer limit) {
        return Result.success(backtestService.strategyLeaderboard(limit));
    }

    /**
     * 均线参数扫描
     */
    @PostMapping("/sweep")
    public Result<List<ParamSweepItemResp>> sweep(@RequestBody ParamSweepReq req) {
        return Result.success(backtestService.paramSweep(req));
    }

    /**
     * 样本内外 walk-forward
     */
    @PostMapping("/walk-forward")
    public Result<WalkForwardResp> walkForward(@RequestBody BacktestRunReq req,
                                               @RequestParam(required = false, defaultValue = "0.7") BigDecimal inSampleRatio) {
        return Result.success(backtestService.walkForward(req, inSampleRatio));
    }

    /**
     * 回测月度收益
     */
    @GetMapping("/{id:\\d+}/monthly")
    public Result<List<MonthlyReturnResp>> monthly(@PathVariable Long id) {
        return Result.success(backtestService.monthlyReturns(id));
    }

    /**
     * 回测权益压力测试
     */
    @GetMapping("/{id:\\d+}/stress")
    public Result<MonteCarloResp> stress(@PathVariable Long id,
                                         @RequestParam(required = false, defaultValue = "500") Integer paths,
                                         @RequestParam(required = false, defaultValue = "20") Integer horizonDays) {
        return Result.success(backtestService.stressTest(id, paths, horizonDays));
    }

    /**
     * 回测详情
     *
     * @param id 任务ID
     * @return 详情
     */
    @GetMapping("/{id:\\d+}")
    public Result<BacktestDetailResp> detail(@PathVariable Long id) {
        BacktestJob job = backtestService.getJob(id);
        return Result.success(BacktestDetailResp.builder()
                .job(job)
                .trades(backtestService.listTrades(id))
                .equities(backtestService.listEquities(id))
                .expectancy(backtestService.tradeExpectancy(id))
                .disclaimer(job.getDisclaimer())
                .build());
    }
}
