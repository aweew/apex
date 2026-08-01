package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.BacktestRunReq;
import com.awe.apex.quant.domain.dto.BatchBacktestItemResp;
import com.awe.apex.quant.domain.dto.BatchBacktestReq;
import com.awe.apex.quant.domain.dto.BenchmarkCompareResp;
import com.awe.apex.quant.domain.dto.PortfolioBacktestReq;
import com.awe.apex.quant.domain.dto.PortfolioBacktestResp;
import com.awe.apex.quant.domain.dto.ParamSweepItemResp;
import com.awe.apex.quant.domain.dto.ParamSweepReq;
import com.awe.apex.quant.domain.dto.StrategyCompareItemResp;
import com.awe.apex.quant.domain.dto.StrategyLeaderboardItemResp;
import com.awe.apex.quant.domain.dto.MonteCarloResp;
import com.awe.apex.quant.domain.dto.MonthlyReturnResp;
import com.awe.apex.quant.domain.dto.WalkForwardResp;
import com.awe.apex.quant.domain.entity.BacktestEquity;
import com.awe.apex.quant.domain.entity.BacktestJob;
import com.awe.apex.quant.domain.entity.BacktestTrade;

import java.math.BigDecimal;
import java.util.List;

/**
 * 回测服务
 */
public interface IBacktestService {

    /**
     * 运行回测
     *
     * @param req 请求
     * @return 任务
     */
    BacktestJob run(BacktestRunReq req);

    /**
     * 查询任务
     *
     * @param id 任务ID
     * @return 任务
     */
    BacktestJob getJob(Long id);

    /**
     * 成交明细
     *
     * @param jobId 任务ID
     * @return 列表
     */
    List<BacktestTrade> listTrades(Long jobId);

    /**
     * 资金曲线
     *
     * @param jobId 任务ID
     * @return 列表
     */
    List<BacktestEquity> listEquities(Long jobId);

    /**
     * 最近回测任务
     *
     * @param limit 条数
     * @return 任务列表
     */
    List<BacktestJob> listJobs(Integer limit);

    /**
     * 批量回测（股票池或指定代码）
     *
     * @param req 请求
     * @return 结果列表
     */
    List<BatchBacktestItemResp> batchRun(BatchBacktestReq req);

    /**
     * 单票多策略对比
     *
     * @param req 基准请求（code/区间）
     * @return 对比结果
     */
    List<StrategyCompareItemResp> compareStrategies(BacktestRunReq req);

    /**
     * 多标的等权组合回测
     *
     * @param req 请求
     * @return 组合结果
     */
    PortfolioBacktestResp portfolioRun(PortfolioBacktestReq req);

    /**
     * 策略相对基准（默认沪深300）对比
     *
     * @param req 回测请求（可用 strategyId/code/区间）
     * @param benchmarkCode 基准代码，默认 000300
     * @return 对比结果
     */
    BenchmarkCompareResp compareBenchmark(BacktestRunReq req, String benchmarkCode);

    /**
     * 历史回测策略绩效榜
     *
     * @param limit 最近任务样本量上限
     * @return 按平均夏普排序
     */
    List<StrategyLeaderboardItemResp> strategyLeaderboard(Integer limit);

    /**
     * 均线参数扫描
     *
     * @param req 请求
     * @return 按夏普排序结果
     */
    List<ParamSweepItemResp> paramSweep(ParamSweepReq req);

    /**
     * 样本内外 walk-forward（默认前 70% 样本内）
     *
     * @param req           回测请求
     * @param inSampleRatio 样本内比例 0~1
     * @return 结果
     */
    WalkForwardResp walkForward(BacktestRunReq req, BigDecimal inSampleRatio);

    /**
     * 回测任务月度收益
     *
     * @param jobId 任务ID
     * @return 月度收益
     */
    List<MonthlyReturnResp> monthlyReturns(Long jobId);

    /**
     * 回测权益 Bootstrap 压力测试
     *
     * @param jobId       任务
     * @param paths       路径数
     * @param horizonDays 前瞻交易日
     * @return 分布
     */
    MonteCarloResp stressTest(Long jobId, Integer paths, Integer horizonDays);

    /**
     * 由成交推算每笔期望收益
     *
     * @param jobId 任务
     * @return 期望
     */
    BigDecimal tradeExpectancy(Long jobId);
}
