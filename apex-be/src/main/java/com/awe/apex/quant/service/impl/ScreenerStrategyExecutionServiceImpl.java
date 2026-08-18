package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.bo.ScreenerCandidateBO;
import com.awe.apex.quant.domain.bo.ScreenerRuleEvaluationBO;
import com.awe.apex.quant.domain.dto.IntradayAcceptanceMetric;
import com.awe.apex.quant.domain.dto.ScreenerDataIssueResp;
import com.awe.apex.quant.domain.dto.ScreenerIntradayFetchResp;
import com.awe.apex.quant.domain.dto.ScreenerMarketSnapshot;
import com.awe.apex.quant.domain.dto.ScreenerMarketSnapshotBatch;
import com.awe.apex.quant.domain.dto.ScreenerRuleEvidenceResp;
import com.awe.apex.quant.domain.dto.ScreenerStrategyDataStatusResp;
import com.awe.apex.quant.domain.dto.ScreenerStrategyMatchResp;
import com.awe.apex.quant.domain.dto.ScreenerStrategyResp;
import com.awe.apex.quant.domain.dto.ScreenerStrategyRuleResp;
import com.awe.apex.quant.domain.dto.ScreenerStrategyRunReq;
import com.awe.apex.quant.domain.dto.ScreenerStrategyRunResp;
import com.awe.apex.quant.domain.dto.StockIntradayResp;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.entity.LimitUpPool;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.domain.enums.ScreenerRunModeEnum;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.LimitUpPoolMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.market.ScreenerMarketSnapshotClient;
import com.awe.apex.quant.screener.ScreenerIntradayReviewService;
import com.awe.apex.quant.screener.ScreenerMetricCalculator;
import com.awe.apex.quant.screener.ScreenerStrategyRuleEvaluator;
import com.awe.apex.quant.service.IScreenerStrategyExecutionService;
import com.awe.apex.quant.service.IScreenerStrategyService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 实时截面、历史日线与分时复核三阶段选股实现
 */
@Slf4j
@Service
public class ScreenerStrategyExecutionServiceImpl implements IScreenerStrategyExecutionService {

    private static final String STAGE_SNAPSHOT = "SNAPSHOT";
    private static final String STAGE_HISTORY = "HISTORY";
    private static final String STAGE_INTRADAY = "INTRADAY";

    @Resource
    private ApexUserContext userContext;

    @Resource
    private IScreenerStrategyService strategyService;

    @Resource
    private ScreenerMarketSnapshotClient snapshotClient;

    @Resource
    private ScreenerStrategyRuleEvaluator ruleEvaluator;

    @Resource
    private ScreenerMetricCalculator metricCalculator;

    @Resource
    private ScreenerIntradayReviewService intradayReviewService;

    @Resource
    private StockBasicMapper stockBasicMapper;

    @Resource
    private BarDailyMapper barDailyMapper;

    @Resource
    private LimitUpPoolMapper limitUpPoolMapper;

    @Value("${apex.screener.intraday-max-candidates:120}")
    private Integer intradayMaxCandidates;

    /**
     * 按单策略 AND 规则运行选股。
     *
     * @param req 运行请求
     * @return 选股结果与数据状态
     */
    @Override
    public ScreenerStrategyRunResp run(ScreenerStrategyRunReq req) {
        if (Objects.isNull(req)) {
            throw new BusinessException("策略运行请求不能为空");
        }
        ScreenerStrategyResp strategy = strategyService.resolveRunnable(req.getStrategyId(), req.getTemplateKey());
        int limit = Objects.nonNull(req.getLimit()) ? Math.max(1, Math.min(req.getLimit(), 200)) : 50;
        LocalDateTime runAt = LocalDateTime.now();
        List<String> notes = defaultNotes(strategy);
        Map<String, ScreenerDataIssueResp> issueMap = new HashMap<>();

        ScreenerMarketSnapshotBatch snapshotBatch;
        try {
            snapshotBatch = snapshotClient.fetchAll();
        } catch (Exception ex) {
            recordIssue(issueMap, STAGE_SNAPSHOT, "SNAPSHOT_FETCH_FAILED", 1,
                    "实时截面拉取失败：" + safeMessage(ex));
            return emptyResult(strategy, runAt, issueMap, notes);
        }
        List<ScreenerMarketSnapshot> snapshots = snapshotBatch.getItems();
        if (CollUtil.isEmpty(snapshots)) {
            recordIssue(issueMap, STAGE_SNAPSHOT, "SNAPSHOT_EMPTY", 1, "实时截面股票列表为空");
            return emptyResult(strategy, runAt, issueMap, notes);
        }
        enrichFundamentals(snapshots);
        if (Objects.isNull(snapshotBatch.getAsOf())) {
            recordIssue(issueMap, STAGE_SNAPSHOT, "SNAPSHOT_TIME_MISSING", snapshots.size(),
                    "实时截面未返回行情时间");
        }

        Map<String, LimitUpPool> limitPoolMap = new HashMap<>();
        Map<String, Integer> themeCountMap = new HashMap<>();
        LocalDate limitPoolDate = null;
        if (requiresLimitPool(strategy.getRules())) {
            LimitUpPool latest = limitUpPoolMapper.selectOne(Wrappers.<LimitUpPool>lambdaQuery()
                    .orderByDesc(LimitUpPool::getTradeDate)
                    .orderByDesc(LimitUpPool::getId)
                    .last("LIMIT 1"));
            if (Objects.isNull(latest) || Objects.isNull(latest.getTradeDate())) {
                recordIssue(issueMap, STAGE_SNAPSHOT, "LIMIT_POOL_EMPTY", snapshots.size(),
                        "涨停池为空，依赖封板质量的规则无法执行");
            } else {
                limitPoolDate = latest.getTradeDate();
                LocalDate snapshotDate = Objects.nonNull(snapshotBatch.getAsOf())
                        ? snapshotBatch.getAsOf().toLocalDate() : null;
                if (Objects.isNull(snapshotDate) || !snapshotDate.equals(limitPoolDate)) {
                    recordIssue(issueMap, STAGE_SNAPSHOT, "LIMIT_POOL_STALE", snapshots.size(),
                            "涨停池交易日与实时截面不一致");
                } else {
                    List<LimitUpPool> rows = limitUpPoolMapper.selectList(Wrappers.<LimitUpPool>lambdaQuery()
                            .eq(LimitUpPool::getTradeDate, limitPoolDate));
                    buildLimitPoolIndexes(rows, limitPoolMap, themeCountMap);
                }
                notes.add("涨停池截止 " + limitPoolDate + "，首板模型只负责候选过滤，不包含交易执行。");
            }
        }

        List<ScreenerCandidateBO> snapshotMatches = new ArrayList<>();
        for (ScreenerMarketSnapshot snapshot : snapshots) {
            LimitUpPool limitUp = limitPoolMap.get(snapshot.getCode());
            String theme = resolveTheme(limitUp);
            ScreenerCandidateBO candidate = ScreenerCandidateBO.builder()
                    .snapshot(snapshot)
                    .limitUpPool(limitUp)
                    .themeLinkageCount(StringUtils.isNotBlank(theme) ? themeCountMap.get(theme) : null)
                    .evidence(new ArrayList<>())
                    .build();
            if (passesSnapshotRules(candidate, strategy.getRules(), issueMap)) {
                snapshotMatches.add(candidate);
            }
        }

        boolean needsHistory = hasHistoricalRules(strategy.getRules());
        List<ScreenerCandidateBO> historicalMatches = snapshotMatches;
        LocalDate dailyAsOf = null;
        if (needsHistory && CollUtil.isNotEmpty(snapshotMatches)) {
            int maxLookback = maxHistoricalLookback(strategy.getRules());
            LocalDate asOfDate = Objects.nonNull(snapshotBatch.getAsOf())
                    ? snapshotBatch.getAsOf().toLocalDate() : LocalDate.now();
            Map<String, List<BarDaily>> barMap = loadBars(snapshotMatches, asOfDate, maxLookback);
            List<BarDaily> benchmarkBars = loadBenchmarkBars(asOfDate, Math.max(21, maxLookback + 1));
            BigDecimal benchmarkRet20 = metricCalculator.calculateReturnPct(benchmarkBars, 20);
            historicalMatches = new ArrayList<>();
            for (ScreenerCandidateBO candidate : snapshotMatches) {
                List<BarDaily> bars = barMap.getOrDefault(candidate.getSnapshot().getCode(), List.of());
                candidate.setBars(bars);
                LocalDate candidateDate = null;
                if (CollUtil.isNotEmpty(bars)) {
                    candidateDate = bars.get(bars.size() - 1).getTradeDate();
                    candidate.setDailyAsOf(candidateDate);
                    if (Objects.nonNull(candidateDate)
                            && (Objects.isNull(dailyAsOf) || candidateDate.isAfter(dailyAsOf))) {
                        dailyAsOf = candidateDate;
                    }
                }
                if (ScreenerRunModeEnum.CLOSE.getCode().equals(strategy.getRunMode())
                        && !Objects.equals(asOfDate, candidateDate)) {
                    recordIssue(issueMap, STAGE_HISTORY, "CLOSE_DAILY_BAR_STALE", 1,
                            "收盘策略日线未同步到实时截面交易日");
                    continue;
                }
                if (passesHistoricalRules(candidate, strategy.getRules(), benchmarkRet20, issueMap)) {
                    historicalMatches.add(candidate);
                }
            }
        }

        int historicalMatchedCount = historicalMatches.size();
        List<ScreenerCandidateBO> finalMatches = historicalMatches;
        String intradayAsOf = null;
        int intradayCandidateCount = 0;
        int intradayReviewedCount = 0;
        if (hasIntradayRules(strategy.getRules()) && CollUtil.isNotEmpty(historicalMatches)) {
            historicalMatches.sort(candidateComparator());
            int cap = Objects.nonNull(intradayMaxCandidates)
                    ? Math.max(1, Math.min(intradayMaxCandidates, 300)) : 120;
            List<ScreenerCandidateBO> reviewCandidates = historicalMatches;
            if (historicalMatches.size() > cap) {
                reviewCandidates = new ArrayList<>(historicalMatches.subList(0, cap));
                notes.add("分时复核候选超过 " + cap + " 只，本次按当日涨幅优先复核前 " + cap + " 只。");
            }
            intradayCandidateCount = reviewCandidates.size();
            List<String> codes = new ArrayList<>();
            for (ScreenerCandidateBO candidate : reviewCandidates) {
                codes.add(candidate.getSnapshot().getCode());
            }
            Map<String, ScreenerIntradayFetchResp> intradayMap = new HashMap<>();
            for (ScreenerIntradayFetchResp response : intradayReviewService.review(codes)) {
                intradayMap.put(response.getCode(), response);
            }
            finalMatches = new ArrayList<>();
            for (ScreenerCandidateBO candidate : reviewCandidates) {
                ScreenerIntradayFetchResp fetched = intradayMap.get(candidate.getSnapshot().getCode());
                if (Objects.isNull(fetched) || Objects.nonNull(fetched.getError())
                        || Objects.isNull(fetched.getIntraday())) {
                    String message = Objects.nonNull(fetched) ? fetched.getError() : "分时结果缺失";
                    recordIssue(issueMap, STAGE_INTRADAY, "INTRADAY_FETCH_FAILED", 1,
                            "分时拉取失败或缺失：" + message);
                    continue;
                }
                StockIntradayResp intraday = fetched.getIntraday();
                if (!sameTradeDate(snapshotBatch.getAsOf(), intraday.getTradeDate())) {
                    recordIssue(issueMap, STAGE_INTRADAY, "INTRADAY_DATE_MISMATCH", 1,
                            "分时交易日与实时截面不一致");
                    continue;
                }
                IntradayAcceptanceMetric metric = metricCalculator.calculateIntradayAcceptance(
                        intraday.getPoints(), "09:35");
                candidate.setIntradayMetric(metric);
                intradayReviewedCount++;
                if (StringUtils.isNotBlank(metric.getLatestTime())
                        && (StringUtils.isBlank(intradayAsOf) || metric.getLatestTime().compareTo(intradayAsOf) > 0)) {
                    intradayAsOf = metric.getLatestTime();
                }
                if (passesIntradayRules(candidate, strategy.getRules(), issueMap)) {
                    finalMatches.add(candidate);
                }
            }
        }

        finalMatches.sort(candidateComparator());
        int matchedCount = finalMatches.size();
        List<ScreenerStrategyMatchResp> responses = new ArrayList<>();
        int responseCount = Math.min(limit, finalMatches.size());
        for (int index = 0; index < responseCount; index++) {
            responses.add(toMatch(finalMatches.get(index)));
        }
        List<ScreenerDataIssueResp> issues = finalizeIssues(issueMap);
        log.info("选股策略运行完成，用户编号={}，策略编号={}，模板标识={}，快照命中数={}，"
                        + "历史命中数={}，最终命中数={}，返回数量={}",
                userContext.currentUserIdOrNull(), strategy.getId(), strategy.getTemplateKey(),
                snapshotMatches.size(), historicalMatchedCount, matchedCount, responses.size());
        return ScreenerStrategyRunResp.builder()
                .strategy(strategy)
                .snapshotMatchedCount(snapshotMatches.size())
                .historicalMatchedCount(historicalMatchedCount)
                .matchedCount(matchedCount)
                .matches(responses)
                .dataStatus(ScreenerStrategyDataStatusResp.builder()
                        .runAt(runAt)
                        .snapshotAsOf(snapshotBatch.getAsOf())
                        .dailyAsOf(dailyAsOf)
                        .intradayAsOf(intradayAsOf)
                        .snapshotCount(snapshots.size())
                        .intradayCandidateCount(intradayCandidateCount)
                        .intradayReviewedCount(intradayReviewedCount)
                        .degraded(CollUtil.isNotEmpty(issues))
                        .issues(issues)
                        .notes(notes)
                        .build())
                .build();
    }

    private void enrichFundamentals(List<ScreenerMarketSnapshot> snapshots) {
        Map<String, ScreenerMarketSnapshot> snapshotMap = new HashMap<>();
        List<String> codes = new ArrayList<>();
        for (ScreenerMarketSnapshot snapshot : snapshots) {
            snapshotMap.put(snapshot.getCode(), snapshot);
            codes.add(snapshot.getCode());
        }
        int batchSize = 500;
        for (int start = 0; start < codes.size(); start += batchSize) {
            List<String> batch = codes.subList(start, Math.min(start + batchSize, codes.size()));
            List<StockBasic> basics = stockBasicMapper.selectList(Wrappers.<StockBasic>lambdaQuery()
                    .in(StockBasic::getCode, batch));
            for (StockBasic basic : basics) {
                ScreenerMarketSnapshot snapshot = snapshotMap.get(basic.getCode());
                if (Objects.isNull(snapshot)) {
                    continue;
                }
                snapshot.setPeTtm(basic.getPeTtm());
                if (Objects.isNull(snapshot.getPb())) {
                    snapshot.setPb(basic.getPb());
                }
                if (StringUtils.isBlank(snapshot.getIndustry())) {
                    snapshot.setIndustry(basic.getIndustry());
                }
            }
        }
    }

    private void buildLimitPoolIndexes(List<LimitUpPool> rows, Map<String, LimitUpPool> limitPoolMap,
                                       Map<String, Integer> themeCountMap) {
        if (CollUtil.isEmpty(rows)) {
            return;
        }
        for (LimitUpPool row : rows) {
            limitPoolMap.put(row.getCode(), row);
            String theme = resolveTheme(row);
            if (StringUtils.isNotBlank(theme)) {
                themeCountMap.put(theme, themeCountMap.getOrDefault(theme, 0) + 1);
            }
        }
    }

    private boolean passesSnapshotRules(ScreenerCandidateBO candidate, List<ScreenerStrategyRuleResp> rules,
                                         Map<String, ScreenerDataIssueResp> issueMap) {
        for (ScreenerStrategyRuleResp rule : rules) {
            if (ruleEvaluator.isHistoricalRule(rule) || ruleEvaluator.isIntradayRule(rule)) {
                continue;
            }
            ScreenerRuleEvaluationBO evaluation = ruleEvaluator.evaluateSnapshot(candidate, rule);
            if (!Boolean.TRUE.equals(evaluation.getPassed())) {
                if (Boolean.TRUE.equals(evaluation.getMissing())) {
                    recordIssue(issueMap, STAGE_SNAPSHOT, rule.getRuleType(), 1,
                            evaluation.getMissingMessage());
                }
                return false;
            }
            addEvidence(candidate, rule, evaluation, STAGE_SNAPSHOT);
        }
        return true;
    }

    private boolean passesHistoricalRules(ScreenerCandidateBO candidate, List<ScreenerStrategyRuleResp> rules,
                                           BigDecimal benchmarkRet20,
                                           Map<String, ScreenerDataIssueResp> issueMap) {
        for (ScreenerStrategyRuleResp rule : rules) {
            if (!ruleEvaluator.isHistoricalRule(rule)) {
                continue;
            }
            ScreenerRuleEvaluationBO evaluation = ruleEvaluator.evaluateHistorical(candidate, rule, benchmarkRet20);
            if (!Boolean.TRUE.equals(evaluation.getPassed())) {
                if (Boolean.TRUE.equals(evaluation.getMissing())) {
                    recordIssue(issueMap, STAGE_HISTORY, rule.getRuleType(), 1,
                            evaluation.getMissingMessage());
                }
                return false;
            }
            addEvidence(candidate, rule, evaluation, STAGE_HISTORY);
        }
        return true;
    }

    private boolean passesIntradayRules(ScreenerCandidateBO candidate, List<ScreenerStrategyRuleResp> rules,
                                         Map<String, ScreenerDataIssueResp> issueMap) {
        for (ScreenerStrategyRuleResp rule : rules) {
            if (!ruleEvaluator.isIntradayRule(rule)) {
                continue;
            }
            ScreenerRuleEvaluationBO evaluation = ruleEvaluator.evaluateIntraday(candidate, rule);
            if (!Boolean.TRUE.equals(evaluation.getPassed())) {
                if (Boolean.TRUE.equals(evaluation.getMissing())) {
                    recordIssue(issueMap, STAGE_INTRADAY, rule.getRuleType(), 1,
                            evaluation.getMissingMessage());
                }
                return false;
            }
            addEvidence(candidate, rule, evaluation, STAGE_INTRADAY);
        }
        return true;
    }

    private Map<String, List<BarDaily>> loadBars(List<ScreenerCandidateBO> candidates, LocalDate asOfDate,
                                                  int maxLookback) {
        List<String> codes = new ArrayList<>();
        for (ScreenerCandidateBO candidate : candidates) {
            codes.add(candidate.getSnapshot().getCode());
        }
        Map<String, List<BarDaily>> barMap = new HashMap<>();
        LocalDate beginDate = asOfDate.minusDays(maxLookback * 2L + 60L);
        int batchSize = 300;
        for (int start = 0; start < codes.size(); start += batchSize) {
            List<String> batch = codes.subList(start, Math.min(start + batchSize, codes.size()));
            List<BarDaily> bars = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                    .in(BarDaily::getCode, batch)
                    .ge(BarDaily::getTradeDate, beginDate)
                    .le(BarDaily::getTradeDate, asOfDate)
                    .orderByAsc(BarDaily::getCode)
                    .orderByAsc(BarDaily::getTradeDate));
            for (BarDaily bar : bars) {
                barMap.computeIfAbsent(bar.getCode(), key -> new ArrayList<>()).add(bar);
            }
        }
        int keep = maxLookback + 1;
        for (Map.Entry<String, List<BarDaily>> entry : barMap.entrySet()) {
            List<BarDaily> bars = entry.getValue();
            if (bars.size() > keep) {
                entry.setValue(new ArrayList<>(bars.subList(bars.size() - keep, bars.size())));
            }
        }
        return barMap;
    }

    private List<BarDaily> loadBenchmarkBars(LocalDate asOfDate, int limit) {
        List<BarDaily> bars = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                .eq(BarDaily::getCode, "000300")
                .le(BarDaily::getTradeDate, asOfDate)
                .orderByDesc(BarDaily::getTradeDate)
                .last("LIMIT " + limit));
        bars.sort(Comparator.comparing(BarDaily::getTradeDate));
        return bars;
    }

    private ScreenerStrategyMatchResp toMatch(ScreenerCandidateBO candidate) {
        ScreenerMarketSnapshot snapshot = candidate.getSnapshot();
        LimitUpPool limitUp = candidate.getLimitUpPool();
        IntradayAcceptanceMetric intraday = candidate.getIntradayMetric();
        return ScreenerStrategyMatchResp.builder()
                .code(snapshot.getCode())
                .name(snapshot.getName())
                .market(snapshot.getMarket())
                .industry(snapshot.getIndustry())
                .latestPrice(snapshot.getLatestPrice())
                .pctChg(snapshot.getPctChg())
                .turnoverRate(snapshot.getTurnoverRate())
                .volumeRatio(snapshot.getVolumeRatio())
                .peTtm(snapshot.getPeTtm())
                .pb(snapshot.getPb())
                .totalMv(snapshot.getTotalMv())
                .circMv(snapshot.getCircMv())
                .rangeReturn(candidate.getRangeReturn())
                .limitUpCount(candidate.getLimitUpCount())
                .upDays(candidate.getUpDays())
                .rs20(candidate.getRs20())
                .atrPct(candidate.getAtrPct())
                .pricePosition(candidate.getPricePosition())
                .daysSinceLimitUp(candidate.getDaysSinceLimitUp())
                .volumeMaRatio(candidate.getVolumeMaRatio())
                .closeMaDistancePct(candidate.getCloseMaDistancePct())
                .breakoutPreviousHigh(candidate.getBreakoutPreviousHigh())
                .maBullishAlignment(candidate.getMaBullishAlignment())
                .firstSealTime(Objects.nonNull(limitUp) ? limitUp.getFirstSealTime() : null)
                .lastSealTime(Objects.nonNull(limitUp) ? limitUp.getLastSealTime() : null)
                .breakCount(Objects.nonNull(limitUp) ? limitUp.getBreakCount() : null)
                .sealAmount(Objects.nonNull(limitUp) ? limitUp.getSealAmount() : null)
                .amount(Objects.nonNull(snapshot.getAmount()) ? snapshot.getAmount()
                        : Objects.nonNull(limitUp) ? limitUp.getAmount() : null)
                .theme(resolveTheme(limitUp))
                .themeLinkageCount(candidate.getThemeLinkageCount())
                .intradayAboveAvgRatio(Objects.nonNull(intraday) ? intraday.getAboveAvgRatio() : null)
                .intradayCurrentAboveAvg(Objects.nonNull(intraday) ? intraday.getCurrentAboveAvg() : null)
                .intradayMaxBelowMinutes(Objects.nonNull(intraday)
                        ? intraday.getMaxConsecutiveBelowMinutes() : null)
                .intradayPointCount(Objects.nonNull(intraday) ? intraday.getPointCount() : null)
                .intradayAsOf(Objects.nonNull(intraday) ? intraday.getLatestTime() : null)
                .evidence(candidate.getEvidence())
                .build();
    }

    private void addEvidence(ScreenerCandidateBO candidate, ScreenerStrategyRuleResp rule,
                             ScreenerRuleEvaluationBO evaluation, String stage) {
        candidate.getEvidence().add(ScreenerRuleEvidenceResp.builder()
                .ruleType(rule.getRuleType())
                .ruleName(rule.getRuleName())
                .actualValue(evaluation.getActualValue())
                .expectedValue(evaluation.getExpectedValue())
                .stage(stage)
                .build());
    }

    private boolean hasHistoricalRules(List<ScreenerStrategyRuleResp> rules) {
        for (ScreenerStrategyRuleResp rule : rules) {
            if (ruleEvaluator.isHistoricalRule(rule)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasIntradayRules(List<ScreenerStrategyRuleResp> rules) {
        for (ScreenerStrategyRuleResp rule : rules) {
            if (ruleEvaluator.isIntradayRule(rule)) {
                return true;
            }
        }
        return false;
    }

    private boolean requiresLimitPool(List<ScreenerStrategyRuleResp> rules) {
        for (ScreenerStrategyRuleResp rule : rules) {
            if (ruleEvaluator.isLimitPoolRule(rule)) {
                return true;
            }
        }
        return false;
    }

    private int maxHistoricalLookback(List<ScreenerStrategyRuleResp> rules) {
        int max = 21;
        for (ScreenerStrategyRuleResp rule : rules) {
            if (ruleEvaluator.isHistoricalRule(rule) && Objects.nonNull(rule.getLookbackDays())) {
                max = Math.max(max, rule.getLookbackDays() + 1);
            }
        }
        return Math.min(max, 251);
    }

    private Comparator<ScreenerCandidateBO> candidateComparator() {
        return Comparator.comparing(
                (ScreenerCandidateBO candidate) -> candidate.getSnapshot().getPctChg(),
                Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(candidate -> candidate.getSnapshot().getCode());
    }

    private boolean sameTradeDate(LocalDateTime snapshotAsOf, String intradayDate) {
        if (Objects.isNull(snapshotAsOf) || StringUtils.isBlank(intradayDate)) {
            return false;
        }
        try {
            return snapshotAsOf.toLocalDate().equals(LocalDate.parse(intradayDate));
        } catch (DateTimeParseException ex) {
            return false;
        }
    }

    private String resolveTheme(LimitUpPool row) {
        if (Objects.isNull(row)) {
            return null;
        }
        return StringUtils.isNotBlank(row.getTheme()) ? row.getTheme().trim()
                : StringUtils.isNotBlank(row.getIndustry()) ? row.getIndustry().trim() : null;
    }

    private void recordIssue(Map<String, ScreenerDataIssueResp> issueMap, String stage, String issueType,
                             int count, String message) {
        String key = stage + "|" + issueType;
        ScreenerDataIssueResp issue = issueMap.get(key);
        if (Objects.isNull(issue)) {
            issue = ScreenerDataIssueResp.builder()
                    .stage(stage)
                    .issueType(issueType)
                    .count(0)
                    .message(message)
                    .build();
            issueMap.put(key, issue);
        }
        issue.setCount(issue.getCount() + Math.max(1, count));
    }

    private List<ScreenerDataIssueResp> finalizeIssues(Map<String, ScreenerDataIssueResp> issueMap) {
        List<ScreenerDataIssueResp> issues = new ArrayList<>(issueMap.values());
        issues.sort(Comparator.comparing(ScreenerDataIssueResp::getStage)
                .thenComparing(ScreenerDataIssueResp::getIssueType));
        return issues;
    }

    private List<String> defaultNotes(ScreenerStrategyResp strategy) {
        List<String> notes = new ArrayList<>();
        notes.add("同一策略内所有规则按 AND 执行，任一必需数据缺失即不命中。");
        notes.add("盘中分时规则表示截至当前；收盘后运行才代表完整交易日。");
        if (ScreenerRunModeEnum.CLOSE.getCode().equals(strategy.getRunMode())) {
            notes.add("收盘策略要求日线已同步到实时截面交易日，盘中或日线未更新时不会产生候选。");
        }
        if (Boolean.TRUE.equals(strategy.getTemplate()) && StringUtils.isNotBlank(strategy.getDisclaimer())) {
            notes.add(strategy.getDisclaimer());
        }
        return notes;
    }

    private ScreenerStrategyRunResp emptyResult(ScreenerStrategyResp strategy, LocalDateTime runAt,
                                                 Map<String, ScreenerDataIssueResp> issueMap,
                                                 List<String> notes) {
        return ScreenerStrategyRunResp.builder()
                .strategy(strategy)
                .snapshotMatchedCount(0)
                .historicalMatchedCount(0)
                .matchedCount(0)
                .matches(List.of())
                .dataStatus(ScreenerStrategyDataStatusResp.builder()
                        .runAt(runAt)
                        .snapshotCount(0)
                        .intradayCandidateCount(0)
                        .intradayReviewedCount(0)
                        .degraded(true)
                        .issues(finalizeIssues(issueMap))
                        .notes(notes)
                        .build())
                .build();
    }

    private String safeMessage(Exception ex) {
        return Objects.nonNull(ex) && StringUtils.isNotBlank(ex.getMessage()) ? ex.getMessage() : "未知错误";
    }
}
