package com.awe.apex.quant.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.backtest.BacktestCostConfig;
import com.awe.apex.quant.backtest.BacktestEngine;
import com.awe.apex.quant.backtest.BacktestEquityPoint;
import com.awe.apex.quant.backtest.BacktestResult;
import com.awe.apex.quant.backtest.BacktestRunContext;
import com.awe.apex.quant.backtest.BacktestTradePoint;
import com.awe.apex.quant.backtest.RollingBacktestAnalyzer;
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
import com.awe.apex.quant.domain.dto.RollingBacktestReq;
import com.awe.apex.quant.domain.dto.RollingBacktestResp;
import com.awe.apex.quant.domain.dto.RollingStrategyConfig;
import com.awe.apex.quant.domain.entity.BacktestEquity;
import com.awe.apex.quant.domain.entity.BacktestJob;
import com.awe.apex.quant.domain.entity.BacktestTrade;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.domain.entity.UniverseSnapshot;
import com.awe.apex.quant.mapper.BacktestEquityMapper;
import com.awe.apex.quant.mapper.BacktestJobMapper;
import com.awe.apex.quant.mapper.BacktestTradeMapper;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.market.DailyBarClient;
import com.awe.apex.quant.market.MarketCodeUtils;
import com.awe.apex.quant.service.IBacktestService;
import com.awe.apex.quant.service.IBacktestExperimentService;
import com.awe.apex.quant.service.IBarDailyService;
import com.awe.apex.quant.service.IConfigService;
import com.awe.apex.quant.service.IUniverseService;
import com.awe.apex.quant.strategy.BarSeries;
import com.awe.apex.quant.strategy.Strategy;
import com.awe.apex.quant.strategy.StrategyParams;
import com.awe.apex.quant.strategy.impl.ParamMaStrategy;
import com.awe.apex.quant.strategy.impl.S1MaTrendStrategy;
import com.awe.apex.quant.strategy.impl.S2RsiPullbackStrategy;
import com.awe.apex.quant.strategy.impl.S3BreakoutVolumeStrategy;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/**
 * 回测服务实现
 */
@Service
public class BacktestServiceImpl implements IBacktestService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter COMPACT_DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);
    private static final String PORTFOLIO_CODE = "PORTFOLIO";
    private static final String DEFAULT_BENCHMARK_CODE = "000300";
    private static final long MAX_BAR_BOUNDARY_GAP_DAYS = 14;
    private static final BigDecimal MAX_BACKTEST_COST_RATE = new BigDecimal("0.05");
    private static final int INIT_CASH_SCALE = 2;
    private static final int BACKTEST_COST_SCALE = 8;
    private static final int MAX_PARAM_SWEEP_CANDIDATES = 100;
    private static final int MAX_LEADERBOARD_JOB_SCAN = 30000;
    private static final List<String> COMPARISON_STRATEGY_IDS = List.of("S1", "S2", "S3");
    private static final String COMPARISON_STRATEGY_IDS_CSV = String.join(",", COMPARISON_STRATEGY_IDS);

    @Resource
    private List<Strategy> strategies;

    @Resource
    private BarDailyMapper barDailyMapper;

    @Resource
    private StockBasicMapper stockBasicMapper;

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

    @Resource
    private IBacktestExperimentService backtestExperimentService;

    @Resource
    private StrategyParams strategyParams;

    /**
     * 运行回测
     *
     * @param req 请求
     * @return 任务
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BacktestJob run(BacktestRunReq req) {
        validateBacktestRequest(req);
        return runWithCost(req, resolveCostConfig());
    }

    BacktestJob runWithCost(BacktestRunReq req, BacktestCostConfig costConfig) {
        return executeBacktest(req, costConfig, null);
    }

    BacktestJob runComparedWithCost(BacktestRunReq req, BacktestCostConfig costConfig,
                                    BacktestRunContext runContext) {
        if (Objects.isNull(runContext) || StringUtils.isBlank(runContext.getComparisonBatchId())
                || StringUtils.isBlank(runContext.getComparisonStrategyIds())
                || StringUtils.isBlank(runContext.getComparisonConfigFingerprint())) {
            throw new BusinessException("策略对比批次信息不能为空");
        }
        return executeBacktest(req, costConfig, runContext);
    }

    private BacktestJob executeBacktest(BacktestRunReq req, BacktestCostConfig costConfig,
                                        BacktestRunContext requestedContext) {
        String code = validateBacktestRequest(req);
        Strategy registeredStrategy = requireStrategy(req.getStrategyId());
        BacktestRunContext runContext = Objects.nonNull(requestedContext)
                ? requestedContext : freezeBacktestStrategy(registeredStrategy);
        Strategy strategy = runContext.getStrategy();
        if (Objects.isNull(strategy) || !req.getStrategyId().equalsIgnoreCase(strategy.strategyId())
                || StringUtils.isBlank(runContext.getStrategyParameters())) {
            throw new BusinessException("回测策略快照无效");
        }

        LocalDate end = parseDate(req.getEndDate(), LocalDate.now());
        LocalDate begin = parseDate(req.getBeginDate(), end.minusYears(1));
        validateDateRange(begin, end);
        BigDecimal initCash = resolveInitCash(req.getInitCash());

        List<BarDaily> bars = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                .eq(BarDaily::getCode, code)
                .ge(BarDaily::getTradeDate, begin)
                .le(BarDaily::getTradeDate, end)
                .orderByAsc(BarDaily::getTradeDate));
        if (bars.size() < 60) {
            throw new BusinessException("日线不足 60 根，请先同步行情: " + code);
        }
        validateBarData(code, bars);

        BarSeries barSeries = buildBarSeries(code, bars);
        BacktestResult result = backtestEngine.run(strategy, barSeries, initCash, costConfig);
        LocalDateTime now = LocalDateTime.now();
        BacktestJob job = BacktestJob.builder()
                .userId(currentUserId())
                .code(code)
                .strategyId(strategy.strategyId())
                .comparisonBatchId(runContext.getComparisonBatchId())
                .comparisonStrategyIds(runContext.getComparisonStrategyIds())
                .strategyParameters(runContext.getStrategyParameters())
                .comparisonConfigFingerprint(runContext.getComparisonConfigFingerprint())
                .beginDate(bars.get(0).getTradeDate())
                .endDate(bars.get(bars.size() - 1).getTradeDate())
                .initCash(initCash)
                .commissionRate(costConfig.getCommissionRate())
                .stampTaxRate(costConfig.getStampTaxRate())
                .buySlippage(costConfig.getBuySlippage())
                .sellSlippage(costConfig.getSellSlippage())
                .executionModelVersion(BacktestEngine.EXECUTION_MODEL_VERSION)
                .priceAdjustment(BacktestEngine.PRICE_ADJUSTMENT)
                .dataFingerprint(calculateDataFingerprint(barSeries))
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
        return requireOwnedJob(id);
    }

    /**
     * 成交明细
     *
     * @param jobId 任务ID
     * @return 列表
     */
    @Override
    public List<BacktestTrade> listTrades(Long jobId) {
        requireOwnedJob(jobId);
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
        requireOwnedJob(jobId);
        return queryEquities(jobId);
    }

    private List<BacktestEquity> queryEquities(Long jobId) {
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
                .eq(BacktestJob::getUserId, currentUserId())
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
    @Transactional(rollbackFor = Exception.class)
    public List<BatchBacktestItemResp> batchRun(BatchBacktestReq req) {
        BatchBacktestReq safe = Objects.nonNull(req) ? req : new BatchBacktestReq();
        String strategyId = StringUtils.isNotBlank(safe.getStrategyId()) ? safe.getStrategyId() : "S1";
        requireStrategy(strategyId);
        int limit = Objects.nonNull(safe.getLimit()) ? Math.max(1, Math.min(safe.getLimit(), 20)) : 8;
        LocalDate backtestEndDate = parseDate(safe.getEndDate(), LocalDate.now());
        LocalDate backtestBeginDate = parseDate(safe.getBeginDate(), backtestEndDate.minusYears(1));
        validateDateRange(backtestBeginDate, backtestEndDate);
        List<String> codes = new ArrayList<>();
        if (CollUtil.isNotEmpty(safe.getCodes())) {
            for (String code : safe.getCodes()) {
                addNormalizedCode(codes, code);
                if (codes.size() >= limit) {
                    break;
                }
            }
        } else {
            List<UniverseSnapshot> universe = universeService.latestAsOf(backtestBeginDate);
            if (CollUtil.isEmpty(universe)) {
                throw new BusinessException("回测开始日没有可用股票池快照，请传入 codes 或先按该日期刷新股票池");
            }
            for (UniverseSnapshot item : universe) {
                addNormalizedCode(codes, item.getCode());
                if (codes.size() >= limit) {
                    break;
                }
            }
        }
        if (codes.size() > limit) {
            codes = codes.subList(0, limit);
        }
        if (CollUtil.isEmpty(codes)) {
            throw new BusinessException("批量回测至少需要 1 只有效股票");
        }
        BacktestCostConfig costConfig = resolveCostConfig();
        List<BatchBacktestItemResp> results = new ArrayList<>();
        for (String code : codes) {
            try {
                BacktestRunReq runReq = new BacktestRunReq();
                runReq.setCode(code);
                runReq.setStrategyId(strategyId);
                runReq.setBeginDate(backtestBeginDate.toString());
                runReq.setEndDate(backtestEndDate.toString());
                BacktestJob job = runWithCost(runReq, costConfig);
                results.add(BatchBacktestItemResp.builder()
                        .code(code)
                        .jobId(job.getId())
                        .totalReturn(job.getTotalReturn())
                        .maxDrawdown(job.getMaxDrawdown())
                        .sharpe(job.getSharpe())
                        .sortino(job.getSortino())
                        .tradeCount(job.getTradeCount())
                        .build());
            } catch (BusinessException ex) {
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
    @Transactional(rollbackFor = Exception.class)
    public List<StrategyCompareItemResp> compareStrategies(BacktestRunReq req) {
        if (Objects.isNull(req) || StringUtils.isBlank(req.getCode())) {
            throw new BusinessException("code 不能为空");
        }
        String code = requireSecurityCode(req.getCode());
        LocalDate endDate = parseDate(req.getEndDate(), LocalDate.now());
        LocalDate beginDate = parseDate(req.getBeginDate(), endDate.minusYears(1));
        validateDateRange(beginDate, endDate);
        BigDecimal initCash = resolveInitCash(req.getInitCash());
        BacktestCostConfig costConfig = resolveCostConfig();
        String comparisonBatchId = IdUtil.fastSimpleUUID();
        List<BacktestRunContext> runContexts = new ArrayList<>();
        for (String strategyId : COMPARISON_STRATEGY_IDS) {
            runContexts.add(freezeBacktestStrategy(requireStrategy(strategyId)));
        }
        String comparisonConfigFingerprint = calculateComparisonConfigFingerprint(runContexts);
        for (BacktestRunContext runContext : runContexts) {
            runContext.setComparisonBatchId(comparisonBatchId);
            runContext.setComparisonStrategyIds(COMPARISON_STRATEGY_IDS_CSV);
            runContext.setComparisonConfigFingerprint(comparisonConfigFingerprint);
        }
        List<StrategyCompareItemResp> list = new ArrayList<>();
        for (BacktestRunContext runContext : runContexts) {
            String strategyId = runContext.getStrategy().strategyId();
            try {
                BacktestRunReq runReq = new BacktestRunReq();
                runReq.setCode(code);
                runReq.setStrategyId(strategyId);
                runReq.setBeginDate(beginDate.toString());
                runReq.setEndDate(endDate.toString());
                runReq.setInitCash(initCash);
                BacktestJob job = runComparedWithCost(runReq, costConfig, runContext);
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
            } catch (BusinessException ex) {
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
        BacktestRunContext runContext = freezeBacktestStrategy(requireStrategy(strategyId));
        Strategy strategy = runContext.getStrategy();
        int limit = Objects.nonNull(safe.getLimit()) ? Math.max(2, Math.min(safe.getLimit(), 15)) : 8;
        BigDecimal initCash = resolveInitCash(safe.getInitCash());
        LocalDate end = parseDate(safe.getEndDate(), LocalDate.now());
        LocalDate begin = parseDate(safe.getBeginDate(), end.minusYears(1));
        validateDateRange(begin, end);

        List<String> codes = new ArrayList<>();
        if (CollUtil.isNotEmpty(safe.getCodes())) {
            for (String code : safe.getCodes()) {
                addNormalizedCode(codes, code);
                if (codes.size() >= limit) {
                    break;
                }
            }
        } else {
            List<UniverseSnapshot> universe = universeService.latestAsOf(begin);
            if (CollUtil.isEmpty(universe)) {
                throw new BusinessException("回测开始日没有可用股票池快照，请传入 codes 或先按该日期刷新股票池");
            }
            for (UniverseSnapshot item : universe) {
                addNormalizedCode(codes, item.getCode());
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

        BacktestCostConfig costConfig = resolveCostConfig();
        BigDecimal legCash = initCash.divide(BigDecimal.valueOf(codes.size()), 2, RoundingMode.DOWN);
        List<BatchBacktestItemResp> legs = new ArrayList<>();
        List<List<BacktestEquityPoint>> legCurves = new ArrayList<>();
        List<String> usedCodes = new ArrayList<>();
        List<String> legDataFingerprints = new ArrayList<>();
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
                validateResearchBars(code, bars, begin, end, 60);
                BarSeries barSeries = buildBarSeries(code, bars);
                BacktestResult result = backtestEngine.run(strategy, barSeries, legCash, costConfig);
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
                usedCodes.add(code);
                legDataFingerprints.add(calculateDataFingerprint(barSeries));
                LocalDate b = bars.get(0).getTradeDate();
                LocalDate e = bars.get(bars.size() - 1).getTradeDate();
                if (Objects.isNull(curveBegin) || b.isAfter(curveBegin)) {
                    curveBegin = b;
                }
                if (Objects.isNull(curveEnd) || e.isBefore(curveEnd)) {
                    curveEnd = e;
                }
            } catch (BusinessException ex) {
                legs.add(BatchBacktestItemResp.builder().code(code).error(ex.getMessage()).build());
            }
        }
        if (legCurves.size() < 2 || Objects.isNull(curveBegin) || Objects.isNull(curveEnd)) {
            throw new BusinessException("组合回测失败：至少需要 2 个可用标的");
        }
        List<BarDaily> benchmarkBars = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                .eq(BarDaily::getCode, DEFAULT_BENCHMARK_CODE)
                .ge(BarDaily::getTradeDate, curveBegin)
                .le(BarDaily::getTradeDate, curveEnd)
                .orderByAsc(BarDaily::getTradeDate));
        validateResearchBars(DEFAULT_BENCHMARK_CODE, benchmarkBars, curveBegin, curveEnd, 60);
        List<EquityPointResp> benchCurve = buyHoldEquityCurve(benchmarkBars, initCash);

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
            BigDecimal sum = initCash.subtract(legCash.multiply(BigDecimal.valueOf(maps.size())));
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

        BigDecimal finalEquity = CollUtil.isEmpty(portfolioEquities) ? initCash
                : portfolioEquities.get(portfolioEquities.size() - 1).getEquity();
        BigDecimal totalReturn = finalEquity.subtract(initCash).divide(initCash, MC);
        BigDecimal maxDrawdown = calcMaxDrawdown(portfolioEquities);
        BigDecimal sharpe = calcSharpeFromEquities(portfolioEquities);
        int tradeDays = Math.max(portfolioEquities.size(), 1);
        BigDecimal annualReturn = BigDecimal.valueOf(Math.pow(1 + totalReturn.doubleValue(), 252.0 / tradeDays) - 1)
                .setScale(6, RoundingMode.HALF_UP);

        LocalDateTime now = LocalDateTime.now();
        BacktestJob job = BacktestJob.builder()
                .userId(currentUserId())
                .code(PORTFOLIO_CODE)
                .strategyId(strategy.strategyId())
                .strategyParameters(runContext.getStrategyParameters())
                .beginDate(curveBegin)
                .endDate(curveEnd)
                .initCash(initCash)
                .commissionRate(costConfig.getCommissionRate())
                .stampTaxRate(costConfig.getStampTaxRate())
                .buySlippage(costConfig.getBuySlippage())
                .sellSlippage(costConfig.getSellSlippage())
                .executionModelVersion(BacktestEngine.EXECUTION_MODEL_VERSION)
                .priceAdjustment(BacktestEngine.PRICE_ADJUSTMENT)
                .dataFingerprint(calculatePortfolioDataFingerprint(
                        usedCodes, legDataFingerprints, benchmarkBars))
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

        return PortfolioBacktestResp.builder()
                .job(job)
                .codes(usedCodes)
                .legs(legs)
                .equities(savedEquities)
                .benchmarkCode(DEFAULT_BENCHMARK_CODE)
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
        String stockCode = requireSecurityCode(req.getCode());
        String bench = StringUtils.isNotBlank(benchmarkCode)
                ? MarketCodeUtils.normalizeCode(benchmarkCode) : DEFAULT_BENCHMARK_CODE;
        if (!MarketCodeUtils.isIndex(bench)) {
            throw new BusinessException("基准仅支持已登记指数代码");
        }
        LocalDate end = parseDate(req.getEndDate(), LocalDate.now());
        LocalDate begin = parseDate(req.getBeginDate(), end.minusYears(1));
        validateDateRange(begin, end);

        ensureBars(bench, begin, end);
        ensureBars(stockCode, begin, end);

        BacktestJob job = run(req);
        LocalDate comparisonBegin = job.getBeginDate();
        LocalDate comparisonEnd = job.getEndDate();
        if (Objects.isNull(comparisonBegin) || Objects.isNull(comparisonEnd)
                || comparisonBegin.isAfter(comparisonEnd)) {
            throw new BusinessException("回测实际区间无效，无法比较基准");
        }
        List<BarDaily> stockBars = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                .eq(BarDaily::getCode, stockCode)
                .ge(BarDaily::getTradeDate, comparisonBegin)
                .le(BarDaily::getTradeDate, comparisonEnd)
                .orderByAsc(BarDaily::getTradeDate));
        List<BarDaily> benchmarkBars = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                .eq(BarDaily::getCode, bench)
                .ge(BarDaily::getTradeDate, comparisonBegin)
                .le(BarDaily::getTradeDate, comparisonEnd)
                .orderByAsc(BarDaily::getTradeDate));
        validateResearchBars(stockCode, stockBars, comparisonBegin, comparisonEnd, 60);
        validateResearchBars(bench, benchmarkBars, comparisonBegin, comparisonEnd, 60);
        BigDecimal stockBh = buyHoldReturn(stockBars);
        BigDecimal benchBh = buyHoldReturn(benchmarkBars);
        BigDecimal strategyReturn = Objects.nonNull(job.getTotalReturn()) ? job.getTotalReturn() : BigDecimal.ZERO;
        BigDecimal excess = strategyReturn.subtract(benchBh);
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
                .benchmarkEquities(buyHoldEquityCurve(benchmarkBars, initCash))
                .stockEquities(buyHoldEquityCurve(stockBars, initCash))
                .disclaimer(BacktestEngine.DISCLAIMER)
                .build();
    }

    /**
     * 策略绩效榜（近 N 次完整同口径策略对比任务聚合）
     *
     * @param limit 配对批次样本量
     * @return 榜单
     */
    @Override
    public List<StrategyLeaderboardItemResp> strategyLeaderboard(Integer limit) {
        int batchSample = Objects.nonNull(limit) && limit > 0 ? Math.min(limit, 500) : 100;
        int jobSample = batchSample * COMPARISON_STRATEGY_IDS.size();
        List<BacktestJob> jobs = new ArrayList<>();
        List<List<BacktestJob>> comparableBatches = new ArrayList<>();
        Long beforeJobId = null;
        while (comparableBatches.size() < batchSample && jobs.size() < MAX_LEADERBOARD_JOB_SCAN) {
            int querySize = Math.min(jobSample, MAX_LEADERBOARD_JOB_SCAN - jobs.size());
            LambdaQueryWrapper<BacktestJob> query = Wrappers.<BacktestJob>lambdaQuery()
                    .eq(BacktestJob::getUserId, currentUserId())
                    .eq(BacktestJob::getStatus, "SUCCESS")
                    .isNotNull(BacktestJob::getStrategyId)
                    .isNotNull(BacktestJob::getComparisonBatchId)
                    .isNotNull(BacktestJob::getComparisonStrategyIds)
                    .isNotNull(BacktestJob::getStrategyParameters)
                    .isNotNull(BacktestJob::getComparisonConfigFingerprint)
                    .isNotNull(BacktestJob::getInitCash)
                    .isNotNull(BacktestJob::getCommissionRate)
                    .isNotNull(BacktestJob::getStampTaxRate)
                    .isNotNull(BacktestJob::getBuySlippage)
                    .isNotNull(BacktestJob::getSellSlippage)
                    .isNotNull(BacktestJob::getExecutionModelVersion)
                    .isNotNull(BacktestJob::getPriceAdjustment)
                    .isNotNull(BacktestJob::getDataFingerprint)
                    .isNotNull(BacktestJob::getTotalReturn)
                    .isNotNull(BacktestJob::getSharpe)
                    .isNotNull(BacktestJob::getMaxDrawdown)
                    .ne(BacktestJob::getCode, PORTFOLIO_CODE);
            if (Objects.nonNull(beforeJobId)) {
                query.lt(BacktestJob::getId, beforeJobId);
            }
            List<BacktestJob> page = backtestJobMapper.selectList(query
                    .orderByDesc(BacktestJob::getId)
                    .last("limit " + querySize));
            if (CollUtil.isEmpty(page)) {
                break;
            }
            jobs.addAll(page);
            comparableBatches = selectComparableLeaderboardBatches(jobs, batchSample);
            if (comparableBatches.size() >= batchSample || page.size() < querySize) {
                break;
            }
            Long oldestJobId = page.get(page.size() - 1).getId();
            if (Objects.isNull(oldestJobId) || Objects.equals(oldestJobId, beforeJobId)) {
                break;
            }
            beforeJobId = oldestJobId;
        }
        Map<String, List<BacktestJob>> byStrategy = new HashMap<>();
        for (List<BacktestJob> batchJobs : comparableBatches) {
            for (BacktestJob job : batchJobs) {
                byStrategy.computeIfAbsent(job.getStrategyId(), strategyId -> new ArrayList<>()).add(job);
            }
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
                    .strategyParameters(group.get(0).getStrategyParameters())
                    .comparisonConfigFingerprint(group.get(0).getComparisonConfigFingerprint())
                    .executionModelVersion(group.get(0).getExecutionModelVersion())
                    .priceAdjustment(group.get(0).getPriceAdjustment())
                    .initCash(group.get(0).getInitCash())
                    .commissionRate(group.get(0).getCommissionRate())
                    .stampTaxRate(group.get(0).getStampTaxRate())
                    .buySlippage(group.get(0).getBuySlippage())
                    .sellSlippage(group.get(0).getSellSlippage())
                    .avgReturn(sumRet.divide(BigDecimal.valueOf(n), 6, RoundingMode.HALF_UP))
                    .avgSharpe(sumSharpe.divide(BigDecimal.valueOf(n), 6, RoundingMode.HALF_UP))
                    .avgMaxDrawdown(sumDd.divide(BigDecimal.valueOf(n), 6, RoundingMode.HALF_UP))
                    .bestReturn(best)
                    .worstReturn(worst)
                    .build());
        }
        list.sort(Comparator.comparing(StrategyLeaderboardItemResp::getAvgSharpe,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(StrategyLeaderboardItemResp::getStrategyId));
        return list;
    }

    private List<List<BacktestJob>> selectComparableLeaderboardBatches(List<BacktestJob> jobs, int batchSample) {
        Map<String, List<BacktestJob>> byBatch = new LinkedHashMap<>();
        for (BacktestJob job : jobs) {
            if (StringUtils.isBlank(job.getComparisonBatchId())) {
                continue;
            }
            byBatch.computeIfAbsent(job.getComparisonBatchId(), batchId -> new ArrayList<>()).add(job);
        }
        List<List<BacktestJob>> comparableBatches = new ArrayList<>();
        BacktestJob activeCohort = null;
        for (List<BacktestJob> batchJobs : byBatch.values()) {
            if (!isComparableBatch(batchJobs)) {
                continue;
            }
            BacktestJob batchReference = batchJobs.get(0);
            if (Objects.isNull(activeCohort)) {
                activeCohort = batchReference;
            }
            if (!hasSameLeaderboardCohort(activeCohort, batchReference)) {
                continue;
            }
            comparableBatches.add(batchJobs);
            if (comparableBatches.size() >= batchSample) {
                break;
            }
        }
        return comparableBatches;
    }

    private boolean hasSameLeaderboardCohort(BacktestJob reference, BacktestJob candidate) {
        return Objects.equals(reference.getComparisonConfigFingerprint(),
                        candidate.getComparisonConfigFingerprint())
                && Objects.equals(reference.getExecutionModelVersion(), candidate.getExecutionModelVersion())
                && Objects.equals(reference.getPriceAdjustment(), candidate.getPriceAdjustment())
                && hasSameDecimalValue(reference.getInitCash(), candidate.getInitCash())
                && hasSameDecimalValue(reference.getCommissionRate(), candidate.getCommissionRate())
                && hasSameDecimalValue(reference.getStampTaxRate(), candidate.getStampTaxRate())
                && hasSameDecimalValue(reference.getBuySlippage(), candidate.getBuySlippage())
                && hasSameDecimalValue(reference.getSellSlippage(), candidate.getSellSlippage());
    }

    private boolean isComparableBatch(List<BacktestJob> jobs) {
        if (CollUtil.isEmpty(jobs)) {
            return false;
        }
        BacktestJob reference = jobs.get(0);
        if (StringUtils.isBlank(reference.getComparisonStrategyIds())
                || StringUtils.isBlank(reference.getComparisonConfigFingerprint())) {
            return false;
        }
        Set<String> expectedStrategyIds = new HashSet<>();
        String[] strategyIds = reference.getComparisonStrategyIds().split(",");
        for (String strategyId : strategyIds) {
            if (StringUtils.isBlank(strategyId) || !expectedStrategyIds.add(strategyId.trim())) {
                return false;
            }
        }
        if (jobs.size() != expectedStrategyIds.size()) {
            return false;
        }
        Set<String> actualStrategyIds = new HashSet<>();
        for (BacktestJob job : jobs) {
            if (!hasCompleteComparisonAudit(job)
                    || !hasSameComparisonScope(reference, job)
                    || !expectedStrategyIds.contains(job.getStrategyId())
                    || !actualStrategyIds.add(job.getStrategyId())) {
                return false;
            }
        }
        return actualStrategyIds.equals(expectedStrategyIds);
    }

    private boolean hasCompleteComparisonAudit(BacktestJob job) {
        return Objects.nonNull(job.getTotalReturn())
                && Objects.nonNull(job.getSharpe())
                && Objects.nonNull(job.getMaxDrawdown())
                && StringUtils.isNotBlank(job.getStrategyParameters())
                && Objects.nonNull(job.getInitCash())
                && Objects.nonNull(job.getCommissionRate())
                && Objects.nonNull(job.getStampTaxRate())
                && Objects.nonNull(job.getBuySlippage())
                && Objects.nonNull(job.getSellSlippage())
                && StringUtils.isNotBlank(job.getExecutionModelVersion())
                && StringUtils.isNotBlank(job.getPriceAdjustment())
                && StringUtils.isNotBlank(job.getDataFingerprint());
    }

    private boolean hasSameComparisonScope(BacktestJob reference, BacktestJob candidate) {
        return Objects.equals(reference.getComparisonStrategyIds(), candidate.getComparisonStrategyIds())
                && Objects.equals(reference.getComparisonConfigFingerprint(),
                        candidate.getComparisonConfigFingerprint())
                && Objects.equals(reference.getCode(), candidate.getCode())
                && Objects.equals(reference.getBeginDate(), candidate.getBeginDate())
                && Objects.equals(reference.getEndDate(), candidate.getEndDate())
                && hasSameDecimalValue(reference.getInitCash(), candidate.getInitCash())
                && hasSameDecimalValue(reference.getCommissionRate(), candidate.getCommissionRate())
                && hasSameDecimalValue(reference.getStampTaxRate(), candidate.getStampTaxRate())
                && hasSameDecimalValue(reference.getBuySlippage(), candidate.getBuySlippage())
                && hasSameDecimalValue(reference.getSellSlippage(), candidate.getSellSlippage())
                && Objects.equals(reference.getExecutionModelVersion(), candidate.getExecutionModelVersion())
                && Objects.equals(reference.getPriceAdjustment(), candidate.getPriceAdjustment())
                && Objects.equals(reference.getDataFingerprint(), candidate.getDataFingerprint());
    }

    private boolean hasSameDecimalValue(BigDecimal reference, BigDecimal candidate) {
        return Objects.nonNull(reference) && Objects.nonNull(candidate) && reference.compareTo(candidate) == 0;
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
        String code = requireSecurityCode(req.getCode());
        LocalDate end = parseDate(req.getEndDate(), LocalDate.now());
        LocalDate begin = parseDate(req.getBeginDate(), end.minusYears(1));
        validateDateRange(begin, end);
        BigDecimal initCash = resolveInitCash(req.getInitCash());
        List<Integer> fastList = parsePeriods(req.getFastPeriods(), List.of(5, 10, 20));
        List<Integer> slowList = parsePeriods(req.getSlowPeriods(), List.of(20, 60, 120));
        int candidateCount = 0;
        for (Integer fast : fastList) {
            for (Integer slow : slowList) {
                if (fast < slow) {
                    candidateCount++;
                    if (candidateCount > MAX_PARAM_SWEEP_CANDIDATES) {
                        throw new BusinessException("均线参数扫描最多支持100组候选");
                    }
                }
            }
        }
        if (candidateCount == 0) {
            throw new BusinessException("没有可用的均线参数组合，快线周期必须小于慢线周期");
        }
        BacktestCostConfig costConfig = resolveCostConfig();
        ensureBars(code, begin, end);
        List<BarDaily> bars = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                .eq(BarDaily::getCode, code)
                .ge(BarDaily::getTradeDate, begin)
                .le(BarDaily::getTradeDate, end)
                .orderByAsc(BarDaily::getTradeDate));
        if (bars.size() < 60) {
            throw new BusinessException("日线不足60根，无法参数扫描");
        }
        validateResearchBars(code, bars, begin, end, 60);
        BarSeries series = buildBarSeries(code, bars);
        List<ParamSweepItemResp> list = new ArrayList<>();
        for (Integer fast : fastList) {
            for (Integer slow : slowList) {
                if (fast >= slow) {
                    continue;
                }
                try {
                    ParamMaStrategy strategy = new ParamMaStrategy(fast, slow);
                    BacktestResult result = backtestEngine.run(strategy, series, initCash, costConfig);
                    list.add(ParamSweepItemResp.builder()
                            .strategyId(strategy.strategyId())
                            .fast(fast)
                            .slow(slow)
                            .totalReturn(result.getTotalReturn())
                            .maxDrawdown(result.getMaxDrawdown())
                            .sharpe(result.getSharpe())
                            .tradeCount(result.getTradeCount())
                            .build());
                } catch (BusinessException ex) {
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
            if (StringUtils.isBlank(t)) {
                throw new BusinessException("均线周期必须是2到250之间的整数");
            }
            try {
                int period = Integer.parseInt(t);
                if (period < 2 || period > 250) {
                    throw new BusinessException("均线周期必须是2到250之间的整数");
                }
                if (!list.contains(period)) {
                    list.add(period);
                }
            } catch (NumberFormatException exception) {
                throw new BusinessException("均线周期必须是2到250之间的整数", exception);
            }
        }
        if (CollUtil.isEmpty(list)) {
            throw new BusinessException("均线周期必须是2到250之间的整数");
        }
        return list;
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
        String code = requireSecurityCode(req.getCode());
        Strategy strategy = requireStrategy(req.getStrategyId());
        LocalDate end = parseDate(req.getEndDate(), LocalDate.now());
        LocalDate begin = parseDate(req.getBeginDate(), end.minusYears(2));
        validateDateRange(begin, end);
        BigDecimal initCash = resolveInitCash(req.getInitCash());
        BigDecimal ratio = Objects.nonNull(inSampleRatio) ? inSampleRatio : new BigDecimal("0.7");
        if (ratio.compareTo(new BigDecimal("0.5")) < 0 || ratio.compareTo(new BigDecimal("0.9")) > 0) {
            throw new BusinessException("样本内比例必须在0.5到0.9之间");
        }
        BacktestCostConfig costConfig = resolveCostConfig();
        ensureBars(code, begin, end);
        List<BarDaily> bars = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                .eq(BarDaily::getCode, code)
                .ge(BarDaily::getTradeDate, begin)
                .le(BarDaily::getTradeDate, end)
                .orderByAsc(BarDaily::getTradeDate));
        if (bars.size() < 120) {
            throw new BusinessException("日线不足120根，无法 walk-forward");
        }
        validateResearchBars(code, bars, begin, end, 120);
        int split = Math.max(60, (int) (bars.size() * ratio.doubleValue()));
        if (split >= bars.size() - 20) {
            split = bars.size() - 20;
        }
        List<BarDaily> inBars = bars.subList(0, split);
        BarSeries fullSeries = buildBarSeries(code, bars);
        BacktestResult inResult = backtestEngine.run(strategy, fullSeries.prefix(split), initCash, costConfig);
        BacktestResult outResult = backtestEngine.run(strategy, fullSeries, initCash, costConfig, split);
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
     * 执行多窗口滚动样本外评估
     *
     * @param req 实验请求
     * @return 滚动评估结果
     */
    @Override
    public RollingBacktestResp rollingEvaluate(RollingBacktestReq req) {
        if (Objects.isNull(req) || StringUtils.isBlank(req.getCode())) {
            throw new BusinessException("code 不能为空");
        }
        if (StringUtils.isBlank(req.getStrategyId())) {
            throw new BusinessException("strategyId 不能为空");
        }
        validateExactReplayRequest(req);

        Strategy strategy = requireStrategy(req.getStrategyId());
        strategy = freezeRollingStrategy(req, strategy);

        LocalDate currentDate = LocalDate.now();
        LocalDate endDate = parseDate(req.getEndDate(), currentDate);
        LocalDate beginDate = parseDate(req.getBeginDate(), endDate.minusYears(3));
        validateDateRange(beginDate, endDate);

        String code = MarketCodeUtils.normalizeCode(req.getCode());
        String benchmarkCode = StringUtils.isNotBlank(req.getBenchmarkCode())
                ? MarketCodeUtils.normalizeCode(req.getBenchmarkCode()) : DEFAULT_BENCHMARK_CODE;
        if (StringUtils.isBlank(code) || !code.matches("\\d{6}")) {
            throw new BusinessException("证券代码格式无效");
        }
        if (!MarketCodeUtils.isIndex(benchmarkCode)) {
            throw new BusinessException("基准仅支持已登记指数代码");
        }
        req.setCode(code);
        req.setBenchmarkCode(benchmarkCode);
        String requestedWindowMode = StringUtils.isNotBlank(req.getWindowMode())
                ? req.getWindowMode().trim() : "ROLLING";
        if ("ROLLING".equalsIgnoreCase(requestedWindowMode)) {
            req.setWindowMode("ROLLING");
        } else if ("EXPANDING".equalsIgnoreCase(requestedWindowMode)) {
            req.setWindowMode("EXPANDING");
        } else {
            throw new BusinessException("窗口模式仅支持 ROLLING 或 EXPANDING");
        }
        int trainDays = Objects.nonNull(req.getTrainDays()) ? req.getTrainDays() : 252;
        int testDays = Objects.nonNull(req.getTestDays()) ? req.getTestDays() : 63;
        int stepDays = Objects.nonNull(req.getStepDays()) ? req.getStepDays() : testDays;
        if (trainDays < 60) {
            throw new BusinessException("样本内窗口不能少于60个交易日");
        }
        if (testDays < 20) {
            throw new BusinessException("样本外窗口不能少于20个交易日");
        }
        if (stepDays < testDays) {
            throw new BusinessException("步长不能小于样本外窗口，避免重复统计");
        }
        if (trainDays > 1250 || testDays > 500 || stepDays > 500) {
            throw new BusinessException("滚动窗口参数超过允许范围");
        }
        req.setInitCash(resolveInitCash(req.getInitCash()));
        BacktestCostConfig defaultCost = null;
        if (Objects.isNull(req.getCommissionRate()) || Objects.isNull(req.getStampTaxRate())
                || Objects.isNull(req.getBuySlippage()) || Objects.isNull(req.getSellSlippage())) {
            defaultCost = resolveCostConfig();
        }
        BacktestCostConfig experimentCost = BacktestCostConfig.of(
                Objects.nonNull(req.getCommissionRate()) ? req.getCommissionRate() : defaultCost.getCommissionRate(),
                Objects.nonNull(req.getStampTaxRate()) ? req.getStampTaxRate() : defaultCost.getStampTaxRate(),
                Objects.nonNull(req.getBuySlippage()) ? req.getBuySlippage() : defaultCost.getBuySlippage(),
                Objects.nonNull(req.getSellSlippage()) ? req.getSellSlippage() : defaultCost.getSellSlippage());
        validateCostConfig(experimentCost);
        experimentCost = BacktestCostConfig.of(
                experimentCost.getCommissionRate().setScale(BACKTEST_COST_SCALE, RoundingMode.UNNECESSARY),
                experimentCost.getStampTaxRate().setScale(BACKTEST_COST_SCALE, RoundingMode.UNNECESSARY),
                experimentCost.getBuySlippage().setScale(BACKTEST_COST_SCALE, RoundingMode.UNNECESSARY),
                experimentCost.getSellSlippage().setScale(BACKTEST_COST_SCALE, RoundingMode.UNNECESSARY));
        int minimumBars = trainDays + testDays + stepDays;
        ensureBars(code, beginDate, endDate, minimumBars);
        ensureBars(benchmarkCode, beginDate, endDate, minimumBars);

        List<BarDaily> stockBars = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                .eq(BarDaily::getCode, code)
                .ge(BarDaily::getTradeDate, beginDate)
                .le(BarDaily::getTradeDate, endDate)
                .orderByAsc(BarDaily::getTradeDate));
        List<BarDaily> benchmarkBars = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                .eq(BarDaily::getCode, benchmarkCode)
                .ge(BarDaily::getTradeDate, beginDate)
                .le(BarDaily::getTradeDate, endDate)
                .orderByAsc(BarDaily::getTradeDate));
        validateResearchBars(code, stockBars, beginDate, endDate, minimumBars);
        validateResearchBars(benchmarkCode, benchmarkBars, beginDate, endDate, minimumBars);

        RollingBacktestResp result = new RollingBacktestAnalyzer(backtestEngine).evaluate(
                code, strategy, stockBars, benchmarkBars, req, experimentCost, isStStock(code));
        if (Boolean.TRUE.equals(req.getExactReplay())
                && !req.getExpectedDataFingerprint().equalsIgnoreCase(result.getDataFingerprint())) {
            throw new BusinessException("行情数据已变化，无法精确复跑");
        }
        result.setStrategyParameters(buildStrategyParameters(strategy, req.getStrategyConfig()));
        req.setCode(result.getCode());
        req.setStrategyId(result.getStrategyId());
        req.setBeginDate(result.getDataBeginDate().toString());
        req.setEndDate(result.getDataEndDate().toString());
        req.setBenchmarkCode(result.getBenchmarkCode());
        req.setWindowMode(result.getWindowMode());
        req.setTrainDays(result.getTrainDays());
        req.setTestDays(result.getTestDays());
        req.setStepDays(result.getStepDays());
        req.setCommissionRate(result.getCost().getCommissionRate());
        req.setStampTaxRate(result.getCost().getStampTaxRate());
        req.setBuySlippage(result.getCost().getBuySlippage());
        req.setSellSlippage(result.getCost().getSellSlippage());
        result.setExperimentId(backtestExperimentService.save(req, result));
        return result;
    }

    private void validateExactReplayRequest(RollingBacktestReq req) {
        if (!Boolean.TRUE.equals(req.getExactReplay())) {
            return;
        }
        if (Objects.isNull(req.getStrategyConfig())
                || StringUtils.isBlank(req.getStrategyConfig().getLogicVersion())
                || StringUtils.isBlank(req.getBeginDate())
                || StringUtils.isBlank(req.getEndDate())
                || Objects.isNull(req.getInitCash())
                || StringUtils.isBlank(req.getBenchmarkCode())
                || StringUtils.isBlank(req.getWindowMode())
                || Objects.isNull(req.getTrainDays())
                || Objects.isNull(req.getTestDays())
                || Objects.isNull(req.getStepDays())
                || Objects.isNull(req.getCommissionRate())
                || Objects.isNull(req.getStampTaxRate())
                || Objects.isNull(req.getBuySlippage())
                || Objects.isNull(req.getSellSlippage())
                || StringUtils.isBlank(req.getExpectedExecutionModelVersion())
                || StringUtils.isBlank(req.getExpectedPriceAdjustment())
                || StringUtils.isBlank(req.getExpectedDataFingerprint())) {
            throw new BusinessException("精确复跑缺少完整实验快照");
        }
        if (!req.getExpectedDataFingerprint().matches("(?i)[0-9a-f]{64}")) {
            throw new BusinessException("精确复跑行情数据指纹格式无效");
        }
        if (!Objects.equals(BacktestEngine.EXECUTION_MODEL_VERSION, req.getExpectedExecutionModelVersion())) {
            throw new BusinessException("回测执行模型版本已更新，无法按原版本复跑");
        }
        if (!Objects.equals(BacktestEngine.PRICE_ADJUSTMENT, req.getExpectedPriceAdjustment())) {
            throw new BusinessException("行情复权口径已更新，无法按原口径复跑");
        }
    }

    private Strategy freezeRollingStrategy(RollingBacktestReq req, Strategy registeredStrategy) {
        String strategyId = registeredStrategy.strategyId();
        RollingStrategyConfig requestedConfig = req.getStrategyConfig();
        if (Objects.nonNull(requestedConfig) && (StringUtils.isBlank(requestedConfig.getStrategyId())
                || !strategyId.equalsIgnoreCase(requestedConfig.getStrategyId()))) {
            throw new BusinessException("策略参数快照与所选策略不一致");
        }
        if (Objects.nonNull(requestedConfig) && StringUtils.isBlank(requestedConfig.getLogicVersion())) {
            throw new BusinessException("策略参数快照缺少逻辑版本，无法精确复跑");
        }
        if (Objects.nonNull(requestedConfig)
                && !Objects.equals(registeredStrategy.logicVersion(), requestedConfig.getLogicVersion())) {
            throw new BusinessException("策略逻辑版本已更新，无法按原版本复跑");
        }
        if (S1MaTrendStrategy.ID.equalsIgnoreCase(strategyId)) {
            return freezeS1Strategy(req, requestedConfig);
        }
        if (S2RsiPullbackStrategy.ID.equalsIgnoreCase(strategyId)) {
            return freezeS2Strategy(req, requestedConfig);
        }
        if (S3BreakoutVolumeStrategy.ID.equalsIgnoreCase(strategyId)) {
            return freezeS3Strategy(req, requestedConfig);
        }
        req.setStrategyConfig(RollingStrategyConfig.builder()
                .strategyId(strategyId)
                .logicVersion(registeredStrategy.logicVersion())
                .build());
        return registeredStrategy;
    }

    private BacktestRunContext freezeBacktestStrategy(Strategy registeredStrategy) {
        String strategyId = registeredStrategy.strategyId();
        if (Objects.isNull(strategyParams)) {
            String strategyName = StringUtils.isNotBlank(registeredStrategy.strategyName())
                    ? registeredStrategy.strategyName() : strategyId;
            return BacktestRunContext.builder()
                    .strategy(registeredStrategy)
                    .strategyParameters("version=" + registeredStrategy.logicVersion() + ", name=" + strategyName)
                    .build();
        }
        if (S1MaTrendStrategy.ID.equalsIgnoreCase(strategyId)) {
            int fastMa = strategyParams.s1FastMa();
            int slowMa = strategyParams.s1SlowMa();
            int volumeMa = strategyParams.s1VolMa();
            if (fastMa <= 0 || slowMa <= fastMa || volumeMa <= 0) {
                throw new BusinessException("S1策略参数无效，慢均线周期必须大于快均线周期且周期均大于0");
            }
            return BacktestRunContext.builder()
                    .strategy(new S1MaTrendStrategy(fastMa, slowMa, volumeMa))
                    .strategyParameters("version=" + S1MaTrendStrategy.LOGIC_VERSION
                            + ", fastMa=" + fastMa + ", slowMa=" + slowMa + ", volumeMa=" + volumeMa)
                    .build();
        }
        if (S2RsiPullbackStrategy.ID.equalsIgnoreCase(strategyId)) {
            int ma = strategyParams.s2Ma();
            int rsiPeriod = strategyParams.s2RsiPeriod();
            BigDecimal oversold = strategyParams.s2RsiOversold();
            BigDecimal rebound = strategyParams.s2RsiRebound();
            BigDecimal overbought = strategyParams.s2RsiOverbought();
            if (ma <= 0 || rsiPeriod <= 0 || Objects.isNull(oversold) || Objects.isNull(rebound)
                    || Objects.isNull(overbought) || oversold.signum() < 0 || oversold.compareTo(rebound) >= 0
                    || rebound.compareTo(overbought) >= 0 || overbought.compareTo(new BigDecimal("100")) > 0) {
                throw new BusinessException("S2策略参数无效，RSI阈值必须在0到100之间依次递增");
            }
            return BacktestRunContext.builder()
                    .strategy(new S2RsiPullbackStrategy(ma, rsiPeriod, oversold, rebound, overbought))
                    .strategyParameters("version=" + S2RsiPullbackStrategy.LOGIC_VERSION
                            + ", ma=" + ma + ", rsiPeriod=" + rsiPeriod
                            + ", oversold=" + canonicalDecimal(oversold)
                            + ", rebound=" + canonicalDecimal(rebound)
                            + ", overbought=" + canonicalDecimal(overbought))
                    .build();
        }
        if (S3BreakoutVolumeStrategy.ID.equalsIgnoreCase(strategyId)) {
            int lookback = strategyParams.s3Lookback();
            BigDecimal volumeRatio = strategyParams.s3VolumeRatio();
            if (lookback <= 0 || Objects.isNull(volumeRatio) || volumeRatio.signum() <= 0) {
                throw new BusinessException("S3策略参数无效，回看周期和成交量比率必须大于0");
            }
            return BacktestRunContext.builder()
                    .strategy(new S3BreakoutVolumeStrategy(lookback, volumeRatio))
                    .strategyParameters("version=" + S3BreakoutVolumeStrategy.LOGIC_VERSION
                            + ", lookback=" + lookback
                            + ", volumeRatio=" + canonicalDecimal(volumeRatio))
                    .build();
        }
        String strategyName = StringUtils.isNotBlank(registeredStrategy.strategyName())
                ? registeredStrategy.strategyName() : strategyId;
        return BacktestRunContext.builder()
                .strategy(registeredStrategy)
                .strategyParameters("version=" + registeredStrategy.logicVersion() + ", name=" + strategyName)
                .build();
    }

    private Strategy freezeS1Strategy(RollingBacktestReq req, RollingStrategyConfig requestedConfig) {
        int fastMa = Objects.nonNull(requestedConfig) && Objects.nonNull(requestedConfig.getS1FastMa())
                ? requestedConfig.getS1FastMa() : strategyParams.s1FastMa();
        int slowMa = Objects.nonNull(requestedConfig) && Objects.nonNull(requestedConfig.getS1SlowMa())
                ? requestedConfig.getS1SlowMa() : strategyParams.s1SlowMa();
        int volumeMa = Objects.nonNull(requestedConfig) && Objects.nonNull(requestedConfig.getS1VolumeMa())
                ? requestedConfig.getS1VolumeMa() : strategyParams.s1VolMa();
        if (fastMa <= 0 || slowMa <= fastMa || volumeMa <= 0) {
            throw new BusinessException("S1策略参数无效，慢均线周期必须大于快均线周期且周期均大于0");
        }
        req.setStrategyConfig(RollingStrategyConfig.builder()
                .strategyId(S1MaTrendStrategy.ID)
                .logicVersion(S1MaTrendStrategy.LOGIC_VERSION)
                .s1FastMa(fastMa)
                .s1SlowMa(slowMa)
                .s1VolumeMa(volumeMa)
                .build());
        return new S1MaTrendStrategy(fastMa, slowMa, volumeMa);
    }

    private Strategy freezeS2Strategy(RollingBacktestReq req, RollingStrategyConfig requestedConfig) {
        int ma = Objects.nonNull(requestedConfig) && Objects.nonNull(requestedConfig.getS2Ma())
                ? requestedConfig.getS2Ma() : strategyParams.s2Ma();
        int rsiPeriod = Objects.nonNull(requestedConfig) && Objects.nonNull(requestedConfig.getS2RsiPeriod())
                ? requestedConfig.getS2RsiPeriod() : strategyParams.s2RsiPeriod();
        BigDecimal rsiOversold = Objects.nonNull(requestedConfig)
                && Objects.nonNull(requestedConfig.getS2RsiOversold())
                ? requestedConfig.getS2RsiOversold() : strategyParams.s2RsiOversold();
        BigDecimal rsiRebound = Objects.nonNull(requestedConfig)
                && Objects.nonNull(requestedConfig.getS2RsiRebound())
                ? requestedConfig.getS2RsiRebound() : strategyParams.s2RsiRebound();
        BigDecimal rsiOverbought = Objects.nonNull(requestedConfig)
                && Objects.nonNull(requestedConfig.getS2RsiOverbought())
                ? requestedConfig.getS2RsiOverbought() : strategyParams.s2RsiOverbought();
        if (ma <= 0 || rsiPeriod <= 0 || rsiOversold.signum() < 0
                || rsiOversold.compareTo(rsiRebound) >= 0
                || rsiRebound.compareTo(rsiOverbought) >= 0
                || rsiOverbought.compareTo(new BigDecimal("100")) > 0) {
            throw new BusinessException("S2策略参数无效，RSI阈值必须在0到100之间依次递增");
        }
        req.setStrategyConfig(RollingStrategyConfig.builder()
                .strategyId(S2RsiPullbackStrategy.ID)
                .logicVersion(S2RsiPullbackStrategy.LOGIC_VERSION)
                .s2Ma(ma)
                .s2RsiPeriod(rsiPeriod)
                .s2RsiOversold(rsiOversold)
                .s2RsiRebound(rsiRebound)
                .s2RsiOverbought(rsiOverbought)
                .build());
        return new S2RsiPullbackStrategy(ma, rsiPeriod, rsiOversold, rsiRebound, rsiOverbought);
    }

    private Strategy freezeS3Strategy(RollingBacktestReq req, RollingStrategyConfig requestedConfig) {
        int lookback = Objects.nonNull(requestedConfig) && Objects.nonNull(requestedConfig.getS3Lookback())
                ? requestedConfig.getS3Lookback() : strategyParams.s3Lookback();
        BigDecimal volumeRatio = Objects.nonNull(requestedConfig)
                && Objects.nonNull(requestedConfig.getS3VolumeRatio())
                ? requestedConfig.getS3VolumeRatio() : strategyParams.s3VolumeRatio();
        if (lookback <= 0 || volumeRatio.signum() <= 0) {
            throw new BusinessException("S3策略参数无效，回看周期和成交量比率必须大于0");
        }
        req.setStrategyConfig(RollingStrategyConfig.builder()
                .strategyId(S3BreakoutVolumeStrategy.ID)
                .logicVersion(S3BreakoutVolumeStrategy.LOGIC_VERSION)
                .s3Lookback(lookback)
                .s3VolumeRatio(volumeRatio)
                .build());
        return new S3BreakoutVolumeStrategy(lookback, volumeRatio);
    }

    private String buildStrategyParameters(Strategy strategy, RollingStrategyConfig strategyConfig) {
        String strategyId = strategy.strategyId();
        if (strategyId.startsWith("MA_")) {
            String[] periodParts = strategyId.split("_");
            if (periodParts.length == 3) {
                return "version=" + strategy.logicVersion()
                        + ", fast=" + periodParts[1] + ", slow=" + periodParts[2];
            }
        }
        if ("S1".equalsIgnoreCase(strategyId)) {
            return "version=" + strategy.logicVersion()
                    + ", fastMa=" + strategyConfig.getS1FastMa() + ", slowMa=" + strategyConfig.getS1SlowMa()
                    + ", volumeMa=" + strategyConfig.getS1VolumeMa();
        }
        if ("S2".equalsIgnoreCase(strategyId)) {
            return "version=" + strategy.logicVersion()
                    + ", ma=" + strategyConfig.getS2Ma() + ", rsiPeriod=" + strategyConfig.getS2RsiPeriod()
                    + ", oversold=" + strategyConfig.getS2RsiOversold()
                    + ", rebound=" + strategyConfig.getS2RsiRebound()
                    + ", overbought=" + strategyConfig.getS2RsiOverbought();
        }
        if ("S3".equalsIgnoreCase(strategyId)) {
            return "version=" + strategy.logicVersion()
                    + ", lookback=" + strategyConfig.getS3Lookback()
                    + ", volumeRatio=" + strategyConfig.getS3VolumeRatio();
        }
        return "version=" + strategy.logicVersion() + ", name=" + strategy.strategyName();
    }

    /**
     * 月度收益
     *
     * @param jobId 任务
     * @return 列表
     */
    @Override
    public List<MonthlyReturnResp> monthlyReturns(Long jobId) {
        BacktestJob job = requireOwnedJob(jobId);
        List<BacktestEquity> equities = queryEquities(jobId);
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
        BigDecimal prev = Objects.nonNull(job.getInitCash()) && job.getInitCash().signum() > 0
                ? job.getInitCash() : null;
        if (Objects.isNull(prev)) {
            for (BacktestEquity equity : equities) {
                if (Objects.nonNull(equity.getEquity()) && equity.getEquity().signum() > 0) {
                    prev = equity.getEquity();
                    break;
                }
            }
        }
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
        List<BigDecimal[]> openLots = new ArrayList<>();
        BigDecimal realizedPnl = BigDecimal.ZERO;
        int closedTradeCount = 0;
        List<BacktestTrade> sortedTrades = new ArrayList<>(trades);
        sortedTrades.sort(Comparator.comparing(BacktestTrade::getTradeDate, Comparator.nullsLast(LocalDate::compareTo))
                .thenComparing(BacktestTrade::getId, Comparator.nullsLast(Long::compareTo)));
        for (BacktestTrade trade : sortedTrades) {
            int quantity = Objects.nonNull(trade.getQuantity()) ? trade.getQuantity() : 0;
            if (quantity <= 0 || Objects.isNull(trade.getPrice())) {
                continue;
            }
            BigDecimal tradeFee = Objects.nonNull(trade.getFee()) && trade.getFee().signum() > 0
                    ? trade.getFee() : BigDecimal.ZERO;
            BigDecimal feePerShare = tradeFee.divide(BigDecimal.valueOf(quantity), 12, RoundingMode.HALF_UP);
            if ("BUY".equalsIgnoreCase(trade.getSide())) {
                openLots.add(new BigDecimal[]{BigDecimal.valueOf(quantity), trade.getPrice().add(feePerShare)});
                continue;
            }
            if (!"SELL".equalsIgnoreCase(trade.getSide())) {
                continue;
            }
            BigDecimal netSellPrice = trade.getPrice().subtract(feePerShare);
            int remainingQuantity = quantity;
            while (remainingQuantity > 0 && CollUtil.isNotEmpty(openLots)) {
                BigDecimal[] openLot = openLots.get(0);
                int lotQuantity = openLot[0].intValue();
                int matchedQuantity = Math.min(remainingQuantity, lotQuantity);
                BigDecimal tradePnl = netSellPrice.subtract(openLot[1])
                        .multiply(BigDecimal.valueOf(matchedQuantity));
                realizedPnl = realizedPnl.add(tradePnl);
                closedTradeCount++;
                openLot[0] = BigDecimal.valueOf(lotQuantity - matchedQuantity);
                remainingQuantity -= matchedQuantity;
                if (openLot[0].signum() <= 0) {
                    openLots.remove(0);
                }
            }
        }
        if (closedTradeCount == 0) {
            return BigDecimal.ZERO;
        }
        return realizedPnl.divide(BigDecimal.valueOf(closedTradeCount), 2, RoundingMode.HALF_UP);
    }

    private BacktestJob requireOwnedJob(Long jobId) {
        if (Objects.isNull(jobId)) {
            throw new BusinessException("回测任务不存在");
        }
        BacktestJob job = backtestJobMapper.selectOne(Wrappers.<BacktestJob>lambdaQuery()
                .eq(BacktestJob::getId, jobId)
                .eq(BacktestJob::getUserId, currentUserId())
                .last("limit 1"));
        if (Objects.isNull(job)) {
            throw new BusinessException("回测任务不存在");
        }
        return job;
    }

    private Long currentUserId() {
        return StpUtil.getLoginIdAsLong();
    }

    private void addNormalizedCode(List<String> codes, String code) {
        String normalizedCode = MarketCodeUtils.normalizeCode(code);
        if (StringUtils.isNotBlank(normalizedCode) && normalizedCode.matches("\\d{6}")
                && !codes.contains(normalizedCode)) {
            codes.add(normalizedCode);
        }
    }

    private String validateBacktestRequest(BacktestRunReq req) {
        if (Objects.isNull(req)) {
            throw new BusinessException("回测请求不能为空");
        }
        if (StringUtils.isBlank(req.getCode())) {
            throw new BusinessException("code 不能为空");
        }
        if (StringUtils.isBlank(req.getStrategyId())) {
            throw new BusinessException("strategyId 不能为空");
        }
        return requireSecurityCode(req.getCode());
    }

    private String requireSecurityCode(String requestedCode) {
        String code = MarketCodeUtils.normalizeCode(requestedCode);
        if (StringUtils.isBlank(code) || !code.matches("\\d{6}")) {
            throw new BusinessException("证券代码格式无效");
        }
        return code;
    }

    private Strategy requireStrategy(String strategyId) {
        if (StringUtils.isBlank(strategyId)) {
            throw new BusinessException("strategyId 不能为空");
        }
        for (Strategy strategy : strategies) {
            if (Objects.nonNull(strategy) && strategyId.equalsIgnoreCase(strategy.strategyId())) {
                return strategy;
            }
        }
        throw new BusinessException("未知策略: " + strategyId);
    }

    private List<EquityPointResp> buyHoldEquityCurve(List<BarDaily> bars, BigDecimal initCash) {
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
        ensureBars(code, begin, end, 60);
    }

    private void ensureBars(String code, LocalDate begin, LocalDate end, int minimumBars) {
        Long count = barDailyMapper.selectCount(Wrappers.<BarDaily>lambdaQuery()
                .eq(BarDaily::getCode, code)
                .ge(BarDaily::getTradeDate, begin)
                .le(BarDaily::getTradeDate, end));
        if (Objects.nonNull(count) && count >= Math.max(60, minimumBars)
                && barsCoverRequestedRange(code, begin, end)
                && barsUseQfqSource(code, begin, end)) {
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

    private void validateResearchBars(String code, List<BarDaily> bars, LocalDate begin,
                                      LocalDate end, int minimumBars) {
        if (Objects.isNull(bars) || bars.size() < Math.max(60, minimumBars)) {
            throw new BusinessException("日线数量不足，无法覆盖研究窗口: " + code);
        }
        BarDaily firstBar = bars.get(0);
        BarDaily lastBar = bars.get(bars.size() - 1);
        if (Objects.isNull(firstBar.getTradeDate()) || Objects.isNull(lastBar.getTradeDate())
                || firstBar.getTradeDate().isAfter(begin.plusDays(MAX_BAR_BOUNDARY_GAP_DAYS))
                || lastBar.getTradeDate().isBefore(end.minusDays(MAX_BAR_BOUNDARY_GAP_DAYS))) {
            throw new BusinessException("日线未完整覆盖请求区间: " + code);
        }
        validateBarData(code, bars);
    }

    private void validateBarData(String code, List<BarDaily> bars) {
        LocalDate previousTradeDate = null;
        for (BarDaily bar : bars) {
            if (Objects.isNull(bar) || Objects.isNull(bar.getTradeDate())
                    || (Objects.nonNull(previousTradeDate) && !bar.getTradeDate().isAfter(previousTradeDate))) {
                throw new BusinessException("日线日期数据无效: " + code);
            }
            if (!DailyBarClient.SOURCE_EASTMONEY.equalsIgnoreCase(bar.getSource())) {
                throw new BusinessException("日线复权口径不一致: " + code);
            }
            if (Objects.isNull(bar.getOpenPrice()) || Objects.isNull(bar.getHighPrice())
                    || Objects.isNull(bar.getLowPrice()) || Objects.isNull(bar.getClosePrice())
                    || bar.getOpenPrice().signum() <= 0 || bar.getHighPrice().signum() <= 0
                    || bar.getLowPrice().signum() <= 0 || bar.getClosePrice().signum() <= 0
                    || bar.getHighPrice().compareTo(bar.getLowPrice()) < 0
                    || bar.getHighPrice().compareTo(bar.getOpenPrice()) < 0
                    || bar.getHighPrice().compareTo(bar.getClosePrice()) < 0
                    || bar.getLowPrice().compareTo(bar.getOpenPrice()) > 0
                    || bar.getLowPrice().compareTo(bar.getClosePrice()) > 0) {
                throw new BusinessException("日线价格数据无效: " + code + ", " + bar.getTradeDate());
            }
            if (Objects.isNull(bar.getVolume()) || bar.getVolume().signum() < 0) {
                throw new BusinessException("日线成交量数据无效: " + code + ", " + bar.getTradeDate());
            }
            previousTradeDate = bar.getTradeDate();
        }
    }

    private boolean barsUseQfqSource(String code, LocalDate begin, LocalDate end) {
        Long incompatibleCount = barDailyMapper.selectCount(Wrappers.<BarDaily>lambdaQuery()
                .eq(BarDaily::getCode, code)
                .ge(BarDaily::getTradeDate, begin)
                .le(BarDaily::getTradeDate, end)
                .and(query -> query.isNull(BarDaily::getSource)
                        .or().ne(BarDaily::getSource, DailyBarClient.SOURCE_EASTMONEY)));
        return Objects.nonNull(incompatibleCount) && incompatibleCount == 0;
    }

    private boolean barsCoverRequestedRange(String code, LocalDate begin, LocalDate end) {
        BarDaily firstBar = barDailyMapper.selectOne(Wrappers.<BarDaily>lambdaQuery()
                .eq(BarDaily::getCode, code)
                .ge(BarDaily::getTradeDate, begin)
                .le(BarDaily::getTradeDate, end)
                .orderByAsc(BarDaily::getTradeDate)
                .last("LIMIT 1"));
        BarDaily lastBar = barDailyMapper.selectOne(Wrappers.<BarDaily>lambdaQuery()
                .eq(BarDaily::getCode, code)
                .ge(BarDaily::getTradeDate, begin)
                .le(BarDaily::getTradeDate, end)
                .orderByDesc(BarDaily::getTradeDate)
                .last("LIMIT 1"));
        return Objects.nonNull(firstBar) && Objects.nonNull(firstBar.getTradeDate())
                && Objects.nonNull(lastBar) && Objects.nonNull(lastBar.getTradeDate())
                && !firstBar.getTradeDate().isAfter(begin.plusDays(MAX_BAR_BOUNDARY_GAP_DAYS))
                && !lastBar.getTradeDate().isBefore(end.minusDays(MAX_BAR_BOUNDARY_GAP_DAYS));
    }

    private BigDecimal resolveInitCash(BigDecimal requestedInitCash) {
        BigDecimal initCash = Objects.nonNull(requestedInitCash)
                ? requestedInitCash : new BigDecimal("1000000");
        if (initCash.signum() <= 0) {
            throw new BusinessException("初始资金必须大于0");
        }
        if (initCash.stripTrailingZeros().scale() > INIT_CASH_SCALE) {
            throw new BusinessException("初始资金最多支持2位小数");
        }
        return initCash.setScale(INIT_CASH_SCALE, RoundingMode.UNNECESSARY);
    }

    private BarSeries buildBarSeries(String code, List<BarDaily> bars) {
        return BarSeries.from(bars, isStStock(code));
    }

    private String calculateDataFingerprint(BarSeries series) {
        MessageDigest digest = createSha256Digest();
        for (int index = 0; index < series.size(); index++) {
            String canonicalBar = String.join("|",
                    Objects.toString(series.getDates().get(index), ""),
                    canonicalDecimal(series.getOpens().get(index)),
                    canonicalDecimal(series.getHighs().get(index)),
                    canonicalDecimal(series.getLows().get(index)),
                    canonicalDecimal(series.getCloses().get(index)),
                    canonicalDecimal(series.getVolumes().get(index)),
                    canonicalDecimal(series.getPriceLimitRates().get(index))) + "\n";
            digest.update(canonicalBar.getBytes(StandardCharsets.UTF_8));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private String calculateComparisonConfigFingerprint(List<BacktestRunContext> runContexts) {
        MessageDigest digest = createSha256Digest();
        for (BacktestRunContext runContext : runContexts) {
            String strategyConfig = runContext.getStrategy().strategyId() + "|"
                    + runContext.getStrategyParameters() + "\n";
            digest.update(strategyConfig.getBytes(StandardCharsets.UTF_8));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private String calculatePortfolioDataFingerprint(List<String> codes, List<String> dataFingerprints,
                                                     List<BarDaily> benchmarkBars) {
        MessageDigest digest = createSha256Digest();
        for (int index = 0; index < codes.size(); index++) {
            String portfolioLeg = codes.get(index) + "|" + dataFingerprints.get(index) + "\n";
            digest.update(portfolioLeg.getBytes(StandardCharsets.UTF_8));
        }
        String benchmarkFingerprint = calculateDataFingerprint(BarSeries.from(benchmarkBars, false));
        String benchmarkData = "BENCHMARK|" + DEFAULT_BENCHMARK_CODE + "|" + benchmarkFingerprint + "\n";
        digest.update(benchmarkData.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest.digest());
    }

    private MessageDigest createSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new BusinessException("无法生成回测审计指纹", exception);
        }
    }

    private String canonicalDecimal(BigDecimal value) {
        return Objects.nonNull(value) ? value.stripTrailingZeros().toPlainString() : "";
    }

    private boolean isStStock(String code) {
        if (Objects.isNull(stockBasicMapper)) {
            return false;
        }
        StockBasic stock = stockBasicMapper.selectOne(Wrappers.<StockBasic>lambdaQuery()
                .eq(StockBasic::getCode, code)
                .last("LIMIT 1"));
        return Objects.nonNull(stock) && (Objects.equals(stock.getStFlag(), 1)
                || (StringUtils.isNotBlank(stock.getName()) && stock.getName().toUpperCase().contains("ST")));
    }

    private BigDecimal buyHoldReturn(String code, LocalDate begin, LocalDate end) {
        List<BarDaily> bars = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                .eq(BarDaily::getCode, code)
                .ge(BarDaily::getTradeDate, begin)
                .le(BarDaily::getTradeDate, end)
                .orderByAsc(BarDaily::getTradeDate));
        return buyHoldReturn(bars);
    }

    private BigDecimal buyHoldReturn(List<BarDaily> bars) {
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
        BacktestCostConfig costConfig = BacktestCostConfig.of(
                configService.getDecimal("commission_rate", new BigDecimal("0.0005")),
                configService.getDecimal("stamp_tax_rate", new BigDecimal("0.0005")),
                configService.getDecimal("buy_slippage", new BigDecimal("0.001")),
                configService.getDecimal("sell_slippage", new BigDecimal("0.001"))
        );
        validateCostConfig(costConfig);
        return BacktestCostConfig.of(
                costConfig.getCommissionRate().setScale(BACKTEST_COST_SCALE, RoundingMode.UNNECESSARY),
                costConfig.getStampTaxRate().setScale(BACKTEST_COST_SCALE, RoundingMode.UNNECESSARY),
                costConfig.getBuySlippage().setScale(BACKTEST_COST_SCALE, RoundingMode.UNNECESSARY),
                costConfig.getSellSlippage().setScale(BACKTEST_COST_SCALE, RoundingMode.UNNECESSARY));
    }

    private void validateCostConfig(BacktestCostConfig costConfig) {
        if (Objects.isNull(costConfig)) {
            throw new BusinessException("回测成本配置不能为空");
        }
        BigDecimal[] costRates = new BigDecimal[]{costConfig.getCommissionRate(), costConfig.getStampTaxRate(),
                costConfig.getBuySlippage(), costConfig.getSellSlippage()};
        for (BigDecimal costRate : costRates) {
            if (Objects.isNull(costRate) || costRate.signum() < 0
                    || costRate.compareTo(MAX_BACKTEST_COST_RATE) > 0) {
                throw new BusinessException("成本和滑点必须在0到5%之间");
            }
            if (costRate.stripTrailingZeros().scale() > BACKTEST_COST_SCALE) {
                throw new BusinessException("成本和滑点最多支持8位小数");
            }
        }
    }

    private LocalDate parseDate(String value, LocalDate defaultDate) {
        if (StringUtils.isBlank(value)) {
            return defaultDate;
        }
        String text = value.trim();
        try {
            if (text.contains("-")) {
                return LocalDate.parse(text, DAY);
            }
            return LocalDate.parse(text, COMPACT_DAY);
        } catch (DateTimeParseException exception) {
            throw new BusinessException("日期格式应为 yyyy-MM-dd 或 yyyyMMdd");
        }
    }

    private void validateDateRange(LocalDate beginDate, LocalDate endDate) {
        if (beginDate.isAfter(endDate)) {
            throw new BusinessException("开始日期不能晚于结束日期");
        }
        if (endDate.isAfter(LocalDate.now())) {
            throw new BusinessException("结束日期不能晚于今天");
        }
    }
}
