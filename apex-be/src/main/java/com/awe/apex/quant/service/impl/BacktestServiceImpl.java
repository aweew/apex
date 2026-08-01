package com.awe.apex.quant.service.impl;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.backtest.BacktestCostConfig;
import com.awe.apex.quant.backtest.BacktestEngine;
import com.awe.apex.quant.backtest.BacktestEquityPoint;
import com.awe.apex.quant.backtest.BacktestResult;
import com.awe.apex.quant.backtest.BacktestTradePoint;
import com.awe.apex.quant.domain.dto.BacktestRunReq;
import com.awe.apex.quant.domain.dto.BarSyncReq;
import com.awe.apex.quant.domain.dto.BatchBacktestItemResp;
import com.awe.apex.quant.domain.dto.BatchBacktestReq;
import com.awe.apex.quant.domain.dto.BenchmarkCompareResp;
import com.awe.apex.quant.domain.dto.PortfolioBacktestReq;
import com.awe.apex.quant.domain.dto.PortfolioBacktestResp;
import com.awe.apex.quant.domain.dto.EquityPointResp;
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
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.entity.UniverseSnapshot;
import com.awe.apex.quant.mapper.BacktestEquityMapper;
import com.awe.apex.quant.mapper.BacktestJobMapper;
import com.awe.apex.quant.mapper.BacktestTradeMapper;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.market.MarketCodeUtils;
import com.awe.apex.quant.service.IBacktestService;
import com.awe.apex.quant.service.IBarDailyService;
import com.awe.apex.quant.service.IConfigService;
import com.awe.apex.quant.service.IUniverseService;
import com.awe.apex.quant.strategy.BarSeries;
import com.awe.apex.quant.strategy.Strategy;
import com.awe.apex.quant.strategy.impl.ParamMaStrategy;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.TreeSet;

/**
 * 回测服务实现
 */
@Service
public class BacktestServiceImpl implements IBacktestService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);
    private static final String PORTFOLIO_CODE = "PORTFOLIO";

    @Resource
    private List<Strategy> strategies;

    @Resource
    private BarDailyMapper barDailyMapper;

    @Resource
    private BacktestJobMapper backtestJobMapper;

    @Resource
    private BacktestTradeMapper backtestTradeMapper;

    @Resource
    private IUniverseService universeService;

    @Resource
    private BacktestEquityMapper backtestEquityMapper;

    @Resource
    private BacktestEngine backtestEngine;

    @Resource
    private IConfigService configService;

    @Resource
    private IBarDailyService barDailyService;

    /**
     * 运行回测
     *
     * @param req 请求
     * @return 任务
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BacktestJob run(BacktestRunReq req) {
        String code = MarketCodeUtils.normalizeCode(req.getCode());
        Strategy strategy = strategies.stream()
                .filter(s -> s.strategyId().equalsIgnoreCase(req.getStrategyId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("未知策略: " + req.getStrategyId()));

        LocalDate end = parseDate(req.getEndDate(), LocalDate.now());
        LocalDate begin = parseDate(req.getBeginDate(), end.minusYears(1));
        BigDecimal initCash = Objects.nonNull(req.getInitCash()) ? req.getInitCash() : new BigDecimal("1000000");

        List<BarDaily> bars = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                .eq(BarDaily::getCode, code)
                .ge(BarDaily::getTradeDate, begin)
                .le(BarDaily::getTradeDate, end)
                .orderByAsc(BarDaily::getTradeDate));
        if (bars.size() < 60) {
            throw new BusinessException("日线不足 60 根，请先同步行情: " + code);
        }

        BacktestResult result = backtestEngine.run(strategy, BarSeries.from(bars), initCash, resolveCostConfig());
        LocalDateTime now = LocalDateTime.now();
        BacktestJob job = BacktestJob.builder()
                .code(code)
                .strategyId(strategy.strategyId())
                .beginDate(bars.get(0).getTradeDate())
                .endDate(bars.get(bars.size() - 1).getTradeDate())
                .initCash(initCash)
                .finalCash(result.getFinalCash())
                .totalReturn(result.getTotalReturn())
                .annualReturn(result.getAnnualReturn())
                .maxDrawdown(result.getMaxDrawdown())
                .sharpe(result.getSharpe())
                .sortino(result.getSortino())
                .winRate(result.getWinRate())
                .profitFactor(result.getProfitFactor())
                .avgHoldDays(result.getAvgHoldDays())
                .tradeCount(result.getTradeCount())
                .status("SUCCESS")
                .disclaimer(result.getDisclaimer())
                .message("ok")
                .createTime(now)
                .updateTime(now)
                .deleted(0)
                .build();
        backtestJobMapper.insert(job);

        for (BacktestTradePoint trade : result.getTrades()) {
            backtestTradeMapper.insert(BacktestTrade.builder()
                    .jobId(job.getId())
                    .tradeDate(trade.getTradeDate())
                    .side(trade.getSide())
                    .price(trade.getPrice())
                    .quantity(trade.getQuantity())
                    .amount(trade.getAmount())
                    .fee(trade.getFee())
                    .reason(trade.getReason())
                    .createTime(now)
                    .updateTime(now)
                    .deleted(0)
                    .build());
        }
        for (BacktestEquityPoint equity : result.getEquities()) {
            backtestEquityMapper.insert(BacktestEquity.builder()
                    .jobId(job.getId())
                    .tradeDate(equity.getTradeDate())
                    .equity(equity.getEquity())
                    .createTime(now)
                    .updateTime(now)
                    .deleted(0)
                    .build());
        }
        return job;
    }

    /**
     * 查询任务
     *
     * @param id 任务ID
     * @return 任务
     */
    @Override
    public BacktestJob getJob(Long id) {
        BacktestJob job = backtestJobMapper.selectById(id);
        if (Objects.isNull(job)) {
            throw new BusinessException("回测任务不存在");
        }
        return job;
    }

    /**
     * 成交明细
     *
     * @param jobId 任务ID
     * @return 列表
     */
    @Override
    public List<BacktestTrade> listTrades(Long jobId) {
        return backtestTradeMapper.selectList(Wrappers.<BacktestTrade>lambdaQuery()
                .eq(BacktestTrade::getJobId, jobId)
                .orderByAsc(BacktestTrade::getTradeDate));
    }

    /**
     * 资金曲线
     *
     * @param jobId 任务ID
     * @return 列表
     */
    @Override
    public List<BacktestEquity> listEquities(Long jobId) {
        return backtestEquityMapper.selectList(Wrappers.<BacktestEquity>lambdaQuery()
                .eq(BacktestEquity::getJobId, jobId)
                .orderByAsc(BacktestEquity::getTradeDate));
    }

    /**
     * 最近回测任务
     *
     * @param limit 条数
     * @return 任务列表
     */
    @Override
    public List<BacktestJob> listJobs(Integer limit) {
        int size = Objects.isNull(limit) ? 20 : Math.max(1, Math.min(limit, 100));
        return backtestJobMapper.selectList(Wrappers.<BacktestJob>lambdaQuery()
                .orderByDesc(BacktestJob::getId)
                .last("limit " + size));
    }

    /**
     * 批量回测（股票池或指定代码）
     *
     * @param req 请求
     * @return 结果列表
     */
    @Override
    public List<BatchBacktestItemResp> batchRun(BatchBacktestReq req) {
        BatchBacktestReq safe = Objects.nonNull(req) ? req : new BatchBacktestReq();
        String strategyId = StringUtils.isNotBlank(safe.getStrategyId()) ? safe.getStrategyId() : "S1";
        int limit = Objects.nonNull(safe.getLimit()) ? Math.max(1, Math.min(safe.getLimit(), 20)) : 8;
        List<String> codes = new ArrayList<>();
        if (Objects.nonNull(safe.getCodes()) && !safe.getCodes().isEmpty()) {
            for (String code : safe.getCodes()) {
                codes.add(MarketCodeUtils.normalizeCode(code));
            }
        } else {
            List<UniverseSnapshot> universe = universeService.latest();
            for (UniverseSnapshot item : universe) {
                codes.add(item.getCode());
                if (codes.size() >= limit) {
                    break;
                }
            }
        }
        if (codes.size() > limit) {
            codes = codes.subList(0, limit);
        }
        List<BatchBacktestItemResp> results = new ArrayList<>();
        for (String code : codes) {
            try {
                BacktestRunReq runReq = new BacktestRunReq();
                runReq.setCode(code);
                runReq.setStrategyId(strategyId);
                runReq.setBeginDate(safe.getBeginDate());
                runReq.setEndDate(safe.getEndDate());
                BacktestJob job = run(runReq);
                results.add(BatchBacktestItemResp.builder()
                        .code(code)
                        .jobId(job.getId())
                        .totalReturn(job.getTotalReturn())
                        .maxDrawdown(job.getMaxDrawdown())
                        .sharpe(job.getSharpe())
                        .sortino(job.getSortino())
                        .tradeCount(job.getTradeCount())
                        .build());
            } catch (Exception ex) {
                results.add(BatchBacktestItemResp.builder()
                        .code(code)
                        .error(ex.getMessage())
                        .build());
            }
        }
        results.sort(Comparator.comparing(
                (BatchBacktestItemResp r) -> Objects.nonNull(r.getTotalReturn()) ? r.getTotalReturn() : new BigDecimal("-999"),
                Comparator.reverseOrder()));
        return results;
    }

    /**
     * 单票多策略对比
     *
     * @param req 基准请求（code/区间）
     * @return 对比结果
     */
    @Override
    public List<StrategyCompareItemResp> compareStrategies(BacktestRunReq req) {
        if (Objects.isNull(req) || StringUtils.isBlank(req.getCode())) {
            throw new BusinessException("code 不能为空");
        }
        String[] ids = new String[]{"S1", "S2", "S3"};
        List<StrategyCompareItemResp> list = new ArrayList<>();
        for (String strategyId : ids) {
            try {
                BacktestRunReq runReq = new BacktestRunReq();
                runReq.setCode(req.getCode());
                runReq.setStrategyId(strategyId);
                runReq.setBeginDate(req.getBeginDate());
                runReq.setEndDate(req.getEndDate());
                runReq.setInitCash(req.getInitCash());
                BacktestJob job = run(runReq);
                list.add(StrategyCompareItemResp.builder()
                        .strategyId(strategyId)
                        .jobId(job.getId())
                        .totalReturn(job.getTotalReturn())
                        .annualReturn(job.getAnnualReturn())
                        .maxDrawdown(job.getMaxDrawdown())
                        .sharpe(job.getSharpe())
                        .winRate(job.getWinRate())
                        .tradeCount(job.getTradeCount())
                        .build());
            } catch (Exception ex) {
                list.add(StrategyCompareItemResp.builder()
                        .strategyId(strategyId)
                        .error(ex.getMessage())
                        .build());
            }
        }
        return list;
    }

    /**
     * 多标的等权组合回测：每只分配 initCash/N，权益按日加总（缺失日向前填充）
     *
     * @param req 请求
     * @return 组合结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PortfolioBacktestResp portfolioRun(PortfolioBacktestReq req) {
        PortfolioBacktestReq safe = Objects.nonNull(req) ? req : new PortfolioBacktestReq();
        String strategyId = StringUtils.isNotBlank(safe.getStrategyId()) ? safe.getStrategyId() : "S1";
        Strategy strategy = strategies.stream()
                .filter(s -> s.strategyId().equalsIgnoreCase(strategyId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("未知策略: " + strategyId));
        int limit = Objects.nonNull(safe.getLimit()) ? Math.max(2, Math.min(safe.getLimit(), 15)) : 8;
        BigDecimal initCash = Objects.nonNull(safe.getInitCash()) ? safe.getInitCash() : new BigDecimal("1000000");
        LocalDate end = parseDate(safe.getEndDate(), LocalDate.now());
        LocalDate begin = parseDate(safe.getBeginDate(), end.minusYears(1));

        List<String> codes = new ArrayList<>();
        if (Objects.nonNull(safe.getCodes()) && !safe.getCodes().isEmpty()) {
            for (String code : safe.getCodes()) {
                codes.add(MarketCodeUtils.normalizeCode(code));
            }
        } else {
            List<UniverseSnapshot> universe = universeService.latest();
            for (UniverseSnapshot item : universe) {
                codes.add(item.getCode());
                if (codes.size() >= limit) {
                    break;
                }
            }
        }
        if (codes.size() > limit) {
            codes = new ArrayList<>(codes.subList(0, limit));
        }
        if (codes.size() < 2) {
            throw new BusinessException("组合回测至少需要 2 只股票，请先刷新股票池或传入 codes");
        }

        BigDecimal legCash = initCash.divide(BigDecimal.valueOf(codes.size()), 2, RoundingMode.HALF_UP);
        List<BatchBacktestItemResp> legs = new ArrayList<>();
        List<List<BacktestEquityPoint>> legCurves = new ArrayList<>();
        int tradeCount = 0;
        LocalDate curveBegin = null;
        LocalDate curveEnd = null;

        for (String code : codes) {
            try {
                List<BarDaily> bars = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                        .eq(BarDaily::getCode, code)
                        .ge(BarDaily::getTradeDate, begin)
                        .le(BarDaily::getTradeDate, end)
                        .orderByAsc(BarDaily::getTradeDate));
                if (bars.size() < 60) {
                    legs.add(BatchBacktestItemResp.builder().code(code).error("日线不足60根").build());
                    continue;
                }
                BacktestResult result = backtestEngine.run(strategy, BarSeries.from(bars), legCash, resolveCostConfig());
                tradeCount += Objects.nonNull(result.getTradeCount()) ? result.getTradeCount() : 0;
                legs.add(BatchBacktestItemResp.builder()
                        .code(code)
                        .totalReturn(result.getTotalReturn())
                        .maxDrawdown(result.getMaxDrawdown())
                        .sharpe(result.getSharpe())
                        .sortino(result.getSortino())
                        .tradeCount(result.getTradeCount())
                        .build());
                legCurves.add(result.getEquities());
                LocalDate b = bars.get(0).getTradeDate();
                LocalDate e = bars.get(bars.size() - 1).getTradeDate();
                if (Objects.isNull(curveBegin) || b.isAfter(curveBegin)) {
                    curveBegin = b;
                }
                if (Objects.isNull(curveEnd) || e.isBefore(curveEnd)) {
                    curveEnd = e;
                }
            } catch (Exception ex) {
                legs.add(BatchBacktestItemResp.builder().code(code).error(ex.getMessage()).build());
            }
        }
        if (legCurves.isEmpty() || Objects.isNull(curveBegin) || Objects.isNull(curveEnd)) {
            throw new BusinessException("组合回测失败：无可用标的日线");
        }

        TreeSet<LocalDate> dates = new TreeSet<>();
        List<Map<LocalDate, BigDecimal>> maps = new ArrayList<>();
        for (List<BacktestEquityPoint> curve : legCurves) {
            Map<LocalDate, BigDecimal> map = new HashMap<>();
            for (BacktestEquityPoint point : curve) {
                map.put(point.getTradeDate(), point.getEquity());
                if (!point.getTradeDate().isBefore(curveBegin) && !point.getTradeDate().isAfter(curveEnd)) {
                    dates.add(point.getTradeDate());
                }
            }
            maps.add(map);
        }

        List<BacktestEquityPoint> portfolioEquities = new ArrayList<>();
        BigDecimal[] last = new BigDecimal[maps.size()];
        for (int i = 0; i < last.length; i++) {
            last[i] = legCash;
        }
        for (LocalDate date : dates) {
            BigDecimal sum = BigDecimal.ZERO;
            for (int i = 0; i < maps.size(); i++) {
                BigDecimal eq = maps.get(i).get(date);
                if (Objects.nonNull(eq)) {
                    last[i] = eq;
                }
                sum = sum.add(last[i]);
            }
            portfolioEquities.add(BacktestEquityPoint.builder()
                    .tradeDate(date)
                    .equity(sum.setScale(2, RoundingMode.HALF_UP))
                    .build());
        }

        BigDecimal finalEquity = portfolioEquities.isEmpty() ? initCash
                : portfolioEquities.get(portfolioEquities.size() - 1).getEquity();
        BigDecimal totalReturn = finalEquity.subtract(initCash).divide(initCash, MC);
        BigDecimal maxDrawdown = calcMaxDrawdown(portfolioEquities);
        BigDecimal sharpe = calcSharpeFromEquities(portfolioEquities);
        int tradeDays = Math.max(portfolioEquities.size(), 1);
        BigDecimal annualReturn = BigDecimal.valueOf(Math.pow(1 + totalReturn.doubleValue(), 252.0 / tradeDays) - 1)
                .setScale(6, RoundingMode.HALF_UP);

        LocalDateTime now = LocalDateTime.now();
        BacktestJob job = BacktestJob.builder()
                .code(PORTFOLIO_CODE)
                .strategyId(strategy.strategyId())
                .beginDate(curveBegin)
                .endDate(curveEnd)
                .initCash(initCash)
                .finalCash(finalEquity)
                .totalReturn(totalReturn.setScale(6, RoundingMode.HALF_UP))
                .annualReturn(annualReturn)
                .maxDrawdown(maxDrawdown)
                .sharpe(sharpe)
                .winRate(BigDecimal.ZERO)
                .profitFactor(BigDecimal.ZERO)
                .avgHoldDays(BigDecimal.ZERO)
                .tradeCount(tradeCount)
                .status("SUCCESS")
                .disclaimer(BacktestEngine.DISCLAIMER)
                .message("等权组合 n=" + legCurves.size())
                .createTime(now)
                .updateTime(now)
                .deleted(0)
                .build();
        backtestJobMapper.insert(job);

        List<BacktestEquity> savedEquities = new ArrayList<>();
        for (BacktestEquityPoint point : portfolioEquities) {
            BacktestEquity equity = BacktestEquity.builder()
                    .jobId(job.getId())
                    .tradeDate(point.getTradeDate())
                    .equity(point.getEquity())
                    .createTime(now)
                    .updateTime(now)
                    .deleted(0)
                    .build();
            backtestEquityMapper.insert(equity);
            savedEquities.add(equity);
        }

        List<String> usedCodes = new ArrayList<>();
        for (BatchBacktestItemResp leg : legs) {
            if (Objects.isNull(leg.getError())) {
                usedCodes.add(leg.getCode());
            }
        }
        List<EquityPointResp> benchCurve = buyHoldEquityCurve("000300", curveBegin, curveEnd, initCash);
        return PortfolioBacktestResp.builder()
                .job(job)
                .codes(usedCodes)
                .legs(legs)
                .equities(savedEquities)
                .benchmarkCode("000300")
                .benchmarkEquities(benchCurve)
                .disclaimer(BacktestEngine.DISCLAIMER)
                .build();
    }

    /**
     * 策略相对基准对比
     *
     * @param req           回测请求
     * @param benchmarkCode 基准代码
     * @return 对比
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BenchmarkCompareResp compareBenchmark(BacktestRunReq req, String benchmarkCode) {
        if (Objects.isNull(req) || StringUtils.isBlank(req.getCode())) {
            throw new BusinessException("code 不能为空");
        }
        if (StringUtils.isBlank(req.getStrategyId())) {
            req.setStrategyId("S1");
        }
        String bench = StringUtils.isNotBlank(benchmarkCode)
                ? MarketCodeUtils.normalizeCode(benchmarkCode) : "000300";
        LocalDate end = parseDate(req.getEndDate(), LocalDate.now());
        LocalDate begin = parseDate(req.getBeginDate(), end.minusYears(1));

        ensureBars(bench, begin, end);
        ensureBars(MarketCodeUtils.normalizeCode(req.getCode()), begin, end);

        BacktestJob job = run(req);
        String stockCode = MarketCodeUtils.normalizeCode(req.getCode());
        BigDecimal stockBh = buyHoldReturn(stockCode, begin, end);
        BigDecimal benchBh = buyHoldReturn(bench, begin, end);
        BigDecimal strategyReturn = Objects.nonNull(job.getTotalReturn()) ? job.getTotalReturn() : BigDecimal.ZERO;
        BigDecimal excess = strategyReturn.subtract(benchBh == null ? BigDecimal.ZERO : benchBh);
        BigDecimal initCash = Objects.nonNull(job.getInitCash()) ? job.getInitCash() : BigDecimal.valueOf(1000000);

        return BenchmarkCompareResp.builder()
                .job(job)
                .code(job.getCode())
                .benchmarkCode(bench)
                .strategyReturn(strategyReturn)
                .stockBuyHoldReturn(stockBh)
                .benchmarkReturn(benchBh)
                .excessReturn(excess.setScale(6, RoundingMode.HALF_UP))
                .strategyEquities(listEquities(job.getId()))
                .benchmarkEquities(buyHoldEquityCurve(bench, begin, end, initCash))
                .stockEquities(buyHoldEquityCurve(stockCode, begin, end, initCash))
                .disclaimer(BacktestEngine.DISCLAIMER)
                .build();
    }

    /**
     * 策略绩效榜（近 N 次成功回测聚合）
     *
     * @param limit 样本量
     * @return 榜单
     */
    @Override
    public List<StrategyLeaderboardItemResp> strategyLeaderboard(Integer limit) {
        int sample = Objects.nonNull(limit) && limit > 0 ? Math.min(limit, 500) : 100;
        List<BacktestJob> jobs = backtestJobMapper.selectList(Wrappers.<BacktestJob>lambdaQuery()
                .eq(BacktestJob::getStatus, "SUCCESS")
                .isNotNull(BacktestJob::getStrategyId)
                .ne(BacktestJob::getCode, PORTFOLIO_CODE)
                .orderByDesc(BacktestJob::getId)
                .last("limit " + sample));
        Map<String, List<BacktestJob>> byStrategy = new HashMap<>();
        for (BacktestJob job : jobs) {
            byStrategy.computeIfAbsent(job.getStrategyId(), k -> new ArrayList<>()).add(job);
        }
        List<StrategyLeaderboardItemResp> list = new ArrayList<>();
        for (Map.Entry<String, List<BacktestJob>> entry : byStrategy.entrySet()) {
            List<BacktestJob> group = entry.getValue();
            BigDecimal sumRet = BigDecimal.ZERO;
            BigDecimal sumSharpe = BigDecimal.ZERO;
            BigDecimal sumDd = BigDecimal.ZERO;
            BigDecimal best = null;
            BigDecimal worst = null;
            int n = 0;
            for (BacktestJob job : group) {
                BigDecimal ret = Objects.nonNull(job.getTotalReturn()) ? job.getTotalReturn() : BigDecimal.ZERO;
                BigDecimal sharpe = Objects.nonNull(job.getSharpe()) ? job.getSharpe() : BigDecimal.ZERO;
                BigDecimal dd = Objects.nonNull(job.getMaxDrawdown()) ? job.getMaxDrawdown() : BigDecimal.ZERO;
                sumRet = sumRet.add(ret);
                sumSharpe = sumSharpe.add(sharpe);
                sumDd = sumDd.add(dd);
                if (Objects.isNull(best) || ret.compareTo(best) > 0) {
                    best = ret;
                }
                if (Objects.isNull(worst) || ret.compareTo(worst) < 0) {
                    worst = ret;
                }
                n++;
            }
            if (n == 0) {
                continue;
            }
            list.add(StrategyLeaderboardItemResp.builder()
                    .strategyId(entry.getKey())
                    .jobCount(n)
                    .avgReturn(sumRet.divide(BigDecimal.valueOf(n), 6, RoundingMode.HALF_UP))
                    .avgSharpe(sumSharpe.divide(BigDecimal.valueOf(n), 6, RoundingMode.HALF_UP))
                    .avgMaxDrawdown(sumDd.divide(BigDecimal.valueOf(n), 6, RoundingMode.HALF_UP))
                    .bestReturn(best)
                    .worstReturn(worst)
                    .build());
        }
        list.sort(Comparator.comparing(StrategyLeaderboardItemResp::getAvgSharpe,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return list;
    }

    /**
     * 均线参数扫描
     *
     * @param req 请求
     * @return 结果
     */
    @Override
    public List<ParamSweepItemResp> paramSweep(ParamSweepReq req) {
        if (Objects.isNull(req) || StringUtils.isBlank(req.getCode())) {
            throw new BusinessException("code 不能为空");
        }
        String code = MarketCodeUtils.normalizeCode(req.getCode());
        LocalDate end = parseDate(req.getEndDate(), LocalDate.now());
        LocalDate begin = parseDate(req.getBeginDate(), end.minusYears(1));
        BigDecimal initCash = Objects.nonNull(req.getInitCash()) ? req.getInitCash() : BigDecimal.valueOf(1000000);
        ensureBars(code, begin, end);
        List<BarDaily> bars = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                .eq(BarDaily::getCode, code)
                .ge(BarDaily::getTradeDate, begin)
                .le(BarDaily::getTradeDate, end)
                .orderByAsc(BarDaily::getTradeDate));
        if (bars.size() < 60) {
            throw new BusinessException("日线不足60根，无法参数扫描");
        }
        BarSeries series = BarSeries.from(bars);
        List<Integer> fastList = parsePeriods(req.getFastPeriods(), List.of(5, 10, 20));
        List<Integer> slowList = parsePeriods(req.getSlowPeriods(), List.of(20, 60, 120));
        List<ParamSweepItemResp> list = new ArrayList<>();
        for (Integer fast : fastList) {
            for (Integer slow : slowList) {
                if (fast >= slow) {
                    continue;
                }
                try {
                    ParamMaStrategy strategy = new ParamMaStrategy(fast, slow);
                    BacktestResult result = backtestEngine.run(strategy, series, initCash, resolveCostConfig());
                    list.add(ParamSweepItemResp.builder()
                            .strategyId(strategy.strategyId())
                            .fast(fast)
                            .slow(slow)
                            .totalReturn(result.getTotalReturn())
                            .maxDrawdown(result.getMaxDrawdown())
                            .sharpe(result.getSharpe())
                            .tradeCount(result.getTradeCount())
                            .build());
                } catch (Exception ex) {
                    list.add(ParamSweepItemResp.builder()
                            .strategyId("MA_" + fast + "_" + slow)
                            .fast(fast)
                            .slow(slow)
                            .error(ex.getMessage())
                            .build());
                }
            }
        }
        list.sort(Comparator.comparing(ParamSweepItemResp::getSharpe,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return list;
    }

    private List<Integer> parsePeriods(String text, List<Integer> defaults) {
        if (StringUtils.isBlank(text)) {
            return defaults;
        }
        List<Integer> list = new ArrayList<>();
        for (String part : text.split(",")) {
            String t = part.trim();
            if (t.isEmpty()) {
                continue;
            }
            try {
                int v = Integer.parseInt(t);
                if (v >= 2 && v <= 250) {
                    list.add(v);
                }
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        return list.isEmpty() ? defaults : list;
    }

    /**
     * 样本内外 walk-forward
     *
     * @param req           请求
     * @param inSampleRatio 样本内比例
     * @return 结果
     */
    @Override
    public WalkForwardResp walkForward(BacktestRunReq req, BigDecimal inSampleRatio) {
        if (Objects.isNull(req) || StringUtils.isBlank(req.getCode())) {
            throw new BusinessException("code 不能为空");
        }
        if (StringUtils.isBlank(req.getStrategyId())) {
            req.setStrategyId("S1");
        }
        String code = MarketCodeUtils.normalizeCode(req.getCode());
        Strategy strategy = strategies.stream()
                .filter(s -> s.strategyId().equalsIgnoreCase(req.getStrategyId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("未知策略: " + req.getStrategyId()));
        LocalDate end = parseDate(req.getEndDate(), LocalDate.now());
        LocalDate begin = parseDate(req.getBeginDate(), end.minusYears(2));
        BigDecimal initCash = Objects.nonNull(req.getInitCash()) ? req.getInitCash() : BigDecimal.valueOf(1000000);
        BigDecimal ratio = Objects.nonNull(inSampleRatio) ? inSampleRatio : new BigDecimal("0.7");
        if (ratio.compareTo(new BigDecimal("0.5")) < 0 || ratio.compareTo(new BigDecimal("0.9")) > 0) {
            ratio = new BigDecimal("0.7");
        }
        ensureBars(code, begin, end);
        List<BarDaily> bars = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                .eq(BarDaily::getCode, code)
                .ge(BarDaily::getTradeDate, begin)
                .le(BarDaily::getTradeDate, end)
                .orderByAsc(BarDaily::getTradeDate));
        if (bars.size() < 120) {
            throw new BusinessException("日线不足120根，无法 walk-forward");
        }
        int split = Math.max(60, (int) (bars.size() * ratio.doubleValue()));
        if (split >= bars.size() - 20) {
            split = bars.size() - 20;
        }
        List<BarDaily> inBars = bars.subList(0, split);
        List<BarDaily> outBars = bars.subList(split, bars.size());
        BacktestResult inResult = backtestEngine.run(strategy, BarSeries.from(inBars), initCash, resolveCostConfig());
        BacktestResult outResult = backtestEngine.run(strategy, BarSeries.from(outBars), initCash, resolveCostConfig());
        BigDecimal inRet = Objects.nonNull(inResult.getTotalReturn()) ? inResult.getTotalReturn() : BigDecimal.ZERO;
        BigDecimal outRet = Objects.nonNull(outResult.getTotalReturn()) ? outResult.getTotalReturn() : BigDecimal.ZERO;
        return WalkForwardResp.builder()
                .code(code)
                .strategyId(strategy.strategyId())
                .inSampleEnd(inBars.get(inBars.size() - 1).getTradeDate())
                .inSampleReturn(inRet)
                .inSampleMaxDrawdown(inResult.getMaxDrawdown())
                .inSampleSharpe(inResult.getSharpe())
                .outSampleReturn(outRet)
                .outSampleMaxDrawdown(outResult.getMaxDrawdown())
                .outSampleSharpe(outResult.getSharpe())
                .returnDecay(outRet.subtract(inRet).setScale(6, RoundingMode.HALF_UP))
                .disclaimer(BacktestEngine.DISCLAIMER)
                .build();
    }

    /**
     * 月度收益
     *
     * @param jobId 任务
     * @return 列表
     */
    @Override
    public List<MonthlyReturnResp> monthlyReturns(Long jobId) {
        List<BacktestEquity> equities = listEquities(jobId);
        List<MonthlyReturnResp> list = new ArrayList<>();
        if (equities.size() < 2) {
            return list;
        }
        Map<YearMonth, BacktestEquity> monthEnd = new LinkedHashMap<>();
        for (BacktestEquity point : equities) {
            if (Objects.isNull(point.getTradeDate()) || Objects.isNull(point.getEquity())) {
                continue;
            }
            monthEnd.put(YearMonth.from(point.getTradeDate()), point);
        }
        BigDecimal prev = null;
        for (Map.Entry<YearMonth, BacktestEquity> entry : monthEnd.entrySet()) {
            BigDecimal endEq = entry.getValue().getEquity();
            BigDecimal ret = BigDecimal.ZERO;
            if (Objects.nonNull(prev) && prev.signum() > 0) {
                ret = endEq.subtract(prev).divide(prev, 6, RoundingMode.HALF_UP);
            }
            list.add(MonthlyReturnResp.builder()
                    .month(entry.getKey().toString())
                    .monthReturn(ret)
                    .endEquity(endEq)
                    .build());
            prev = endEq;
        }
        return list;
    }

    /**
     * 回测权益压力测试
     *
     * @param jobId       任务
     * @param paths       路径
     * @param horizonDays 长度
     * @return 分布
     */
    @Override
    public MonteCarloResp stressTest(Long jobId, Integer paths, Integer horizonDays) {
        if (Objects.isNull(jobId)) {
            throw new BusinessException("jobId 不能为空");
        }
        List<BacktestEquity> equities = listEquities(jobId);
        List<BigDecimal> hist = new ArrayList<>();
        for (int i = 1; i < equities.size(); i++) {
            BigDecimal prev = equities.get(i - 1).getEquity();
            BigDecimal curr = equities.get(i).getEquity();
            if (Objects.nonNull(prev) && prev.signum() > 0 && Objects.nonNull(curr)) {
                hist.add(curr.subtract(prev).divide(prev, 8, RoundingMode.HALF_UP));
            }
        }
        int pathN = Objects.nonNull(paths) ? Math.max(50, Math.min(paths, 2000)) : 500;
        int horizon = Objects.nonNull(horizonDays) ? Math.max(5, Math.min(horizonDays, 120)) : 20;
        if (hist.size() < 5) {
            return MonteCarloResp.builder()
                    .sampleDays(hist.size())
                    .paths(pathN)
                    .horizonDays(horizon)
                    .terminalReturnP5(BigDecimal.ZERO)
                    .terminalReturnP50(BigDecimal.ZERO)
                    .terminalReturnP95(BigDecimal.ZERO)
                    .avgMaxDrawdown(BigDecimal.ZERO)
                    .maxDrawdownP95(BigDecimal.ZERO)
                    .message("回测权益样本不足，无法压力测试")
                    .build();
        }
        Random random = new Random(42L + jobId);
        List<BigDecimal> terminals = new ArrayList<>();
        List<BigDecimal> maxDds = new ArrayList<>();
        for (int p = 0; p < pathN; p++) {
            BigDecimal equity = BigDecimal.ONE;
            BigDecimal peak = BigDecimal.ONE;
            BigDecimal maxDd = BigDecimal.ZERO;
            for (int d = 0; d < horizon; d++) {
                BigDecimal r = hist.get(random.nextInt(hist.size()));
                equity = equity.multiply(BigDecimal.ONE.add(r));
                if (equity.compareTo(peak) > 0) {
                    peak = equity;
                }
                if (peak.signum() > 0) {
                    BigDecimal dd = peak.subtract(equity).divide(peak, 8, RoundingMode.HALF_UP);
                    if (dd.compareTo(maxDd) > 0) {
                        maxDd = dd;
                    }
                }
            }
            terminals.add(equity.subtract(BigDecimal.ONE));
            maxDds.add(maxDd);
        }
        terminals.sort(BigDecimal::compareTo);
        maxDds.sort(BigDecimal::compareTo);
        BigDecimal avgDd = maxDds.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(maxDds.size()), 6, RoundingMode.HALF_UP);
        int p5 = Math.max(0, (int) Math.floor((terminals.size() - 1) * 0.05));
        int p50 = Math.max(0, (int) Math.floor((terminals.size() - 1) * 0.50));
        int p95 = Math.max(0, (int) Math.floor((terminals.size() - 1) * 0.95));
        return MonteCarloResp.builder()
                .sampleDays(hist.size())
                .paths(pathN)
                .horizonDays(horizon)
                .terminalReturnP5(terminals.get(p5).setScale(6, RoundingMode.HALF_UP))
                .terminalReturnP50(terminals.get(p50).setScale(6, RoundingMode.HALF_UP))
                .terminalReturnP95(terminals.get(p95).setScale(6, RoundingMode.HALF_UP))
                .avgMaxDrawdown(avgDd)
                .maxDrawdownP95(maxDds.get(p95).setScale(6, RoundingMode.HALF_UP))
                .message("回测#" + jobId + " Bootstrap " + pathN + "×" + horizon + "日")
                .build();
    }

    /**
     * 成交期望
     *
     * @param jobId 任务
     * @return 期望
     */
    @Override
    public BigDecimal tradeExpectancy(Long jobId) {
        List<BacktestTrade> trades = listTrades(jobId);
        List<BigDecimal[]> open = new ArrayList<>();
        BigDecimal grossWin = BigDecimal.ZERO;
        BigDecimal grossLoss = BigDecimal.ZERO;
        int wins = 0;
        int closed = 0;
        List<BacktestTrade> sorted = new ArrayList<>(trades);
        sorted.sort(Comparator.comparing(BacktestTrade::getTradeDate, Comparator.nullsLast(LocalDate::compareTo))
                .thenComparing(BacktestTrade::getId, Comparator.nullsLast(Long::compareTo)));
        for (BacktestTrade trade : sorted) {
            int qty = Objects.nonNull(trade.getQuantity()) ? trade.getQuantity() : 0;
            if (qty <= 0 || Objects.isNull(trade.getPrice())) {
                continue;
            }
            if ("BUY".equalsIgnoreCase(trade.getSide())) {
                open.add(new BigDecimal[]{BigDecimal.valueOf(qty), trade.getPrice()});
                continue;
            }
            if (!"SELL".equalsIgnoreCase(trade.getSide())) {
                continue;
            }
            int remain = qty;
            while (remain > 0 && !open.isEmpty()) {
                BigDecimal[] lot = open.get(0);
                int lotQty = lot[0].intValue();
                int matched = Math.min(remain, lotQty);
                BigDecimal pnl = trade.getPrice().subtract(lot[1]).multiply(BigDecimal.valueOf(matched));
                closed++;
                if (pnl.signum() >= 0) {
                    wins++;
                    grossWin = grossWin.add(pnl);
                } else {
                    grossLoss = grossLoss.add(pnl.abs());
                }
                lot[0] = BigDecimal.valueOf(lotQty - matched);
                remain -= matched;
                if (lot[0].signum() <= 0) {
                    open.remove(0);
                }
            }
        }
        if (closed == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal winRate = BigDecimal.valueOf(wins).divide(BigDecimal.valueOf(closed), 6, RoundingMode.HALF_UP);
        int losses = closed - wins;
        BigDecimal avgWin = wins == 0 ? BigDecimal.ZERO : grossWin.divide(BigDecimal.valueOf(wins), 6, RoundingMode.HALF_UP);
        BigDecimal avgLoss = losses == 0 ? BigDecimal.ZERO : grossLoss.divide(BigDecimal.valueOf(losses), 6, RoundingMode.HALF_UP);
        return winRate.multiply(avgWin)
                .subtract(BigDecimal.ONE.subtract(winRate).multiply(avgLoss))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private List<EquityPointResp> buyHoldEquityCurve(String code, LocalDate begin, LocalDate end, BigDecimal initCash) {
        List<BarDaily> bars = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                .eq(BarDaily::getCode, code)
                .ge(BarDaily::getTradeDate, begin)
                .le(BarDaily::getTradeDate, end)
                .orderByAsc(BarDaily::getTradeDate));
        List<EquityPointResp> points = new ArrayList<>();
        if (bars.size() < 2 || Objects.isNull(bars.get(0).getClosePrice()) || bars.get(0).getClosePrice().signum() <= 0) {
            return points;
        }
        BigDecimal first = bars.get(0).getClosePrice();
        BigDecimal cash = Objects.nonNull(initCash) ? initCash : BigDecimal.valueOf(1000000);
        for (BarDaily bar : bars) {
            if (Objects.isNull(bar.getClosePrice())) {
                continue;
            }
            BigDecimal equity = cash.multiply(bar.getClosePrice()).divide(first, 2, RoundingMode.HALF_UP);
            points.add(EquityPointResp.builder().tradeDate(bar.getTradeDate()).equity(equity).build());
        }
        return points;
    }

    private void ensureBars(String code, LocalDate begin, LocalDate end) {
        Long count = barDailyMapper.selectCount(Wrappers.<BarDaily>lambdaQuery()
                .eq(BarDaily::getCode, code)
                .ge(BarDaily::getTradeDate, begin)
                .le(BarDaily::getTradeDate, end));
        if (Objects.nonNull(count) && count >= 60) {
            // 指数若收益异常为 0，可能是错误市场映射的脏数据，强制重拉
            if (!MarketCodeUtils.isIndex(code)
                    || buyHoldReturn(code, begin, end).abs().compareTo(new BigDecimal("0.0001")) > 0) {
                return;
            }
        }
        BarSyncReq syncReq = new BarSyncReq();
        syncReq.setCodes(List.of(code));
        syncReq.setBeginDate(begin.toString());
        syncReq.setEndDate(end.toString());
        barDailyService.syncBars(syncReq);
    }

    private BigDecimal buyHoldReturn(String code, LocalDate begin, LocalDate end) {
        List<BarDaily> bars = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                .eq(BarDaily::getCode, code)
                .ge(BarDaily::getTradeDate, begin)
                .le(BarDaily::getTradeDate, end)
                .orderByAsc(BarDaily::getTradeDate));
        if (bars.size() < 2) {
            return BigDecimal.ZERO;
        }
        BigDecimal first = bars.get(0).getClosePrice();
        BigDecimal last = bars.get(bars.size() - 1).getClosePrice();
        if (Objects.isNull(first) || Objects.isNull(last) || first.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return last.subtract(first).divide(first, 6, RoundingMode.HALF_UP);
    }

    private BigDecimal calcMaxDrawdown(List<BacktestEquityPoint> equities) {
        BigDecimal peak = BigDecimal.ZERO;
        BigDecimal maxDd = BigDecimal.ZERO;
        for (BacktestEquityPoint point : equities) {
            if (point.getEquity().compareTo(peak) > 0) {
                peak = point.getEquity();
            }
            if (peak.signum() > 0) {
                BigDecimal dd = peak.subtract(point.getEquity()).divide(peak, MC);
                if (dd.compareTo(maxDd) > 0) {
                    maxDd = dd;
                }
            }
        }
        return maxDd.setScale(6, RoundingMode.HALF_UP);
    }

    private BigDecimal calcSharpeFromEquities(List<BacktestEquityPoint> equities) {
        if (equities.size() < 3) {
            return BigDecimal.ZERO;
        }
        List<BigDecimal> dailyReturns = new ArrayList<>();
        for (int i = 1; i < equities.size(); i++) {
            BigDecimal prev = equities.get(i - 1).getEquity();
            BigDecimal curr = equities.get(i).getEquity();
            if (prev.signum() > 0) {
                dailyReturns.add(curr.subtract(prev).divide(prev, MC));
            }
        }
        if (dailyReturns.size() < 2) {
            return BigDecimal.ZERO;
        }
        BigDecimal mean = dailyReturns.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(dailyReturns.size()), MC);
        BigDecimal variance = BigDecimal.ZERO;
        for (BigDecimal r : dailyReturns) {
            BigDecimal diff = r.subtract(mean);
            variance = variance.add(diff.multiply(diff, MC));
        }
        variance = variance.divide(BigDecimal.valueOf(dailyReturns.size() - 1), MC);
        if (variance.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        double std = Math.sqrt(variance.doubleValue());
        if (std == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(mean.doubleValue() / std * Math.sqrt(252)).setScale(6, RoundingMode.HALF_UP);
    }

    private BacktestCostConfig resolveCostConfig() {
        return BacktestCostConfig.of(
                configService.getDecimal("commission_rate", new BigDecimal("0.0005")),
                configService.getDecimal("stamp_tax_rate", new BigDecimal("0.0005")),
                configService.getDecimal("buy_slippage", new BigDecimal("0.001")),
                configService.getDecimal("sell_slippage", new BigDecimal("0.001"))
        );
    }

    private LocalDate parseDate(String value, LocalDate defaultDate) {
        if (StringUtils.isBlank(value)) {
            return defaultDate;
        }
        String text = value.trim();
        if (text.contains("-")) {
            return LocalDate.parse(text, DAY);
        }
        return LocalDate.parse(text, DateTimeFormatter.ofPattern("yyyyMMdd"));
    }
}
