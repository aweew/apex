package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.JsonUtils;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.ai.AiChatProperties;
import com.awe.apex.quant.ai.KimiChatClient;
import com.awe.apex.quant.cache.RedisCacheService;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.decision.DecisionContext;
import com.awe.apex.quant.decision.DecisionActionPublisher;
import com.awe.apex.quant.decision.DecisionDataReadiness;
import com.awe.apex.quant.decision.DecisionFeature;
import com.awe.apex.quant.decision.DecisionFeatureBuilder;
import com.awe.apex.quant.decision.DecisionFeatureInput;
import com.awe.apex.quant.decision.DecisionFeatureSource;
import com.awe.apex.quant.decision.DecisionEntryGate;
import com.awe.apex.quant.decision.DecisionEntryGateReq;
import com.awe.apex.quant.decision.DecisionEntryGateResp;
import com.awe.apex.quant.decision.DecisionMode;
import com.awe.apex.quant.decision.DecisionPerformanceCalibrator;
import com.awe.apex.quant.decision.DecisionPortfolioSnapshotManager;
import com.awe.apex.quant.decision.DecisionRunManager;
import com.awe.apex.quant.decision.MarketRegimeEnum;
import com.awe.apex.quant.decision.MarketRegimeResolver;
import com.awe.apex.quant.decision.MarketRegimeResult;
import com.awe.apex.quant.decision.PositionRiskCalculator;
import com.awe.apex.quant.decision.PositionRiskInput;
import com.awe.apex.quant.decision.PositionRiskResult;
import com.awe.apex.quant.decision.PortfolioDrawdownReducer;
import com.awe.apex.quant.decision.DecisionScoreReq;
import com.awe.apex.quant.decision.DecisionScoreResp;
import com.awe.apex.quant.decision.DecisionScorer;
import com.awe.apex.quant.decision.MainlineBoardRules;
import com.awe.apex.quant.decision.MainlineMatcher;
import com.awe.apex.quant.domain.dto.DecisionAttrBucket;
import com.awe.apex.quant.domain.dto.DecisionAdviceActionResp;
import com.awe.apex.quant.domain.dto.DecisionAdviceResp;
import com.awe.apex.quant.domain.dto.DecisionAttributionResp;
import com.awe.apex.quant.domain.dto.DecisionBuyAiResp;
import com.awe.apex.quant.domain.dto.DecisionBuyAiStockNote;
import com.awe.apex.quant.domain.dto.DecisionHistoryItem;
import com.awe.apex.quant.domain.dto.DecisionItemResp;
import com.awe.apex.quant.domain.dto.DecisionPortfolioHolding;
import com.awe.apex.quant.domain.dto.DecisionRunReq;
import com.awe.apex.quant.domain.dto.DecisionTodayResp;
import com.awe.apex.quant.domain.dto.DecisionStrategyPerformance;
import com.awe.apex.quant.domain.dto.HotConfluenceItem;
import com.awe.apex.quant.domain.dto.LimitUpLadderResp;
import com.awe.apex.quant.domain.dto.LimitUpStockItem;
import com.awe.apex.quant.domain.dto.LimitUpTier;
import com.awe.apex.quant.domain.dto.MarketBriefingResp;
import com.awe.apex.quant.domain.dto.MarketHotThemeItem;
import com.awe.apex.quant.domain.dto.RiskOverviewResp;
import com.awe.apex.quant.domain.dto.SectorBoardItem;
import com.awe.apex.quant.domain.dto.SignalConfluenceItem;
import com.awe.apex.quant.domain.dto.SignalConfluenceResp;
import com.awe.apex.quant.domain.dto.SignalRunReq;
import com.awe.apex.quant.domain.dto.ValuationBriefResp;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.entity.DailyAction;
import com.awe.apex.quant.domain.entity.DecisionRun;
import com.awe.apex.quant.domain.entity.DecisionPortfolioSnapshot;
import com.awe.apex.quant.domain.entity.MarketBriefingSnapshot;
import com.awe.apex.quant.domain.entity.MarketHot;
import com.awe.apex.quant.domain.entity.MyHolding;
import com.awe.apex.quant.domain.entity.RiskRule;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.domain.entity.StockFinAbstract;
import com.awe.apex.quant.domain.entity.StockFinIndicator;
import com.awe.apex.quant.domain.entity.StrategySignalEntity;
import com.awe.apex.quant.domain.entity.UniverseSnapshot;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.DailyActionMapper;
import com.awe.apex.quant.mapper.DecisionRunMapper;
import com.awe.apex.quant.mapper.DecisionOutcomeMapper;
import com.awe.apex.quant.mapper.DecisionPortfolioSnapshotMapper;
import com.awe.apex.quant.mapper.MarketBriefingSnapshotMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.mapper.StockFinAbstractMapper;
import com.awe.apex.quant.mapper.StockFinIndicatorMapper;
import com.awe.apex.quant.market.MarketCodeUtils;
import com.awe.apex.quant.market.TradingCalendar;
import com.awe.apex.quant.service.IDecisionService;
import com.awe.apex.quant.service.IHotService;
import com.awe.apex.quant.service.ILimitUpLadderService;
import com.awe.apex.quant.service.IMarketBriefingService;
import com.awe.apex.quant.service.IObservePoolService;
import com.awe.apex.quant.service.IPortfolioService;
import com.awe.apex.quant.service.IRiskService;
import com.awe.apex.quant.service.ISectorBoardService;
import com.awe.apex.quant.service.ISignalService;
import com.awe.apex.quant.service.IUniverseService;
import com.awe.apex.quant.service.IValuationService;
import com.awe.apex.quant.service.TaskProgressListener;
import com.awe.apex.quant.strategy.StrategyParams;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 智能决策：编排股票池 / 策略信号 / 共振 / 热点 / 基本面 / 风控
 */
@Slf4j
@Service
public class DecisionServiceImpl implements IDecisionService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final BigDecimal ROE_EXCLUDE = new BigDecimal("3");
    private static final BigDecimal ROE_WEAK = new BigDecimal("8");
    private static final BigDecimal DEBT_EXCLUDE = new BigDecimal("80");
    private static final BigDecimal DEBT_WEAK = new BigDecimal("70");

    @Resource
    private IUniverseService universeService;

    @Resource
    private ISignalService signalService;

    @Resource
    private IHotService hotService;

    @Resource
    private IRiskService riskService;

    @Resource
    private DailyActionMapper dailyActionMapper;

    @Resource
    private DecisionRunMapper decisionRunMapper;

    @Resource
    private DecisionPortfolioSnapshotMapper decisionPortfolioSnapshotMapper;

    @Resource
    private StockBasicMapper stockBasicMapper;

    @Resource
    private StockFinAbstractMapper stockFinAbstractMapper;

    @Resource
    private StockFinIndicatorMapper stockFinIndicatorMapper;

    @Resource
    private IMarketBriefingService marketBriefingService;

    @Resource
    private ISectorBoardService sectorBoardService;

    @Resource
    private BarDailyMapper barDailyMapper;

    @Resource
    private MarketBriefingSnapshotMapper marketBriefingSnapshotMapper;

    @Resource
    private StrategyParams strategyParams;

    @Resource
    private IObservePoolService observePoolService;

    @Resource
    private IPortfolioService portfolioService;

    @Resource
    private ILimitUpLadderService limitUpLadderService;

    @Resource
    private IValuationService valuationService;

    @Resource
    private DecisionScorer decisionScorer;

    @Resource
    private DecisionEntryGate decisionEntryGate;

    @Resource
    private DecisionFeatureBuilder decisionFeatureBuilder;

    @Resource
    private DecisionRunManager decisionRunManager;

    @Resource
    private DecisionActionPublisher decisionActionPublisher;

    @Resource
    private DecisionPortfolioSnapshotManager portfolioSnapshotManager;

    @Resource
    private MarketRegimeResolver marketRegimeResolver;

    @Resource
    private PositionRiskCalculator positionRiskCalculator;

    @Resource
    private PortfolioDrawdownReducer portfolioDrawdownReducer;

    @Resource
    private DecisionPerformanceCalibrator performanceCalibrator;

    @Resource
    private DecisionOutcomeMapper decisionOutcomeMapper;

    @Resource
    private KimiChatClient kimiChatClient;

    @Resource
    private AiChatProperties aiChatProperties;

    @Resource
    private RedisCacheService redisCacheService;

    @Resource
    private ApexUserContext userContext;

    private static final BigDecimal FALLBACK_STOP_PCT = new BigDecimal("0.08");
    private static final BigDecimal FALLBACK_TAKE_PCT = new BigDecimal("0.20");
    private static final String BUY_AI_DISCLAIMER = "AI 总结仅供研究参考，不构成投资建议；请结合本地规则评分与风控自行决策。";
    private static final String BUY_AI_CACHE_PREFIX = "apex:decision:buy-ai:";

    /**
     * 一键生成今日决策：刷新股票池 → 跑策略 → 共振/基本面/风控 → 落库 → 同步观察池
     *
     * @param req 请求
     * @return 今日决策
     */
    @Override
    public DecisionTodayResp run(DecisionRunReq req) {
        return run(req, null);
    }

    /**
     * 一键生成今日决策并上报执行进度
     *
     * @param req              请求
     * @param progressListener 进度监听器
     * @return 今日决策
     */
    @Override
    public DecisionTodayResp run(DecisionRunReq req, TaskProgressListener progressListener) {
        boolean progressEnabled = Objects.nonNull(progressListener);
        TaskProgressListener reporter = progressEnabled
                ? progressListener : (completed, total, message) -> { };
        DecisionRunReq safe = Objects.nonNull(req) ? req : new DecisionRunReq();
        String groupName = StringUtils.isNotBlank(safe.getGroupName()) ? safe.getGroupName().trim() : "我的自选";
        DecisionContext context = DecisionContext.from(safe);
        reporter.onProgress(2, 100, "正在创建决策运行");
        DecisionRun decisionRun = decisionRunManager.start(context, groupName, decisionConfigSnapshot());
        long startedAt = System.currentTimeMillis();
        log.info("一键决策开始，运行编号={}，模式={}，决策日期={}，分组名称={}，是否包含北交所={}",
                decisionRun.getRunNo(), decisionRun.getMode(), decisionRun.getActionDate(), groupName,
                Boolean.TRUE.equals(safe.getIncludeBj()));
        try {
            DecisionTodayResp response = executeRun(safe, groupName, context, decisionRun, reporter, progressEnabled);
            String dataLevel = Objects.nonNull(response.getMarketBriefing())
                    ? response.getMarketBriefing().getDataLevel() : null;
            reporter.onProgress(98, 100, "正在发布决策结果");
            finishRun(context, decisionRun, response, dataLevel);
            reporter.onProgress(99, 100, "决策结果已生成");
            log.info("一键决策完成，运行编号={}，模式={}，股票池数量={}，买入数量={}，卖出数量={}，持有数量={}，耗时毫秒={}",
                    decisionRun.getRunNo(), decisionRun.getMode(), response.getUniverseCount(), response.getBuyCount(),
                    response.getSellCount(), response.getHoldCount(), System.currentTimeMillis() - startedAt);
            return response;
        } catch (RuntimeException ex) {
            decisionRunManager.fail(decisionRun, ex);
            log.error("一键决策失败，运行编号={}，模式={}，耗时毫秒={}", decisionRun.getRunNo(), decisionRun.getMode(),
                    System.currentTimeMillis() - startedAt, ex);
            throw ex;
        }
    }

    void finishRun(DecisionContext context, DecisionRun run,
                   DecisionTodayResp response, String dataLevel) {
        if (context.getMode() == DecisionMode.SHADOW || !DecisionDataReadiness.canPublish(dataLevel)) {
            if (!DecisionDataReadiness.canPublish(dataLevel)) {
                response.setMessage(response.getMessage() + "；市场关键数据未就绪，本次仅保留未发布运行记录");
            }
            decisionRunManager.completeUnpublished(run, dataLevel, response.getMessage());
            return;
        }
        decisionActionPublisher.publish(run, response.getItems(), dataLevel, response.getMessage());
    }

    private DecisionTodayResp executeRun(DecisionRunReq safe, String groupName,
                                         DecisionContext context, DecisionRun decisionRun,
                                         TaskProgressListener progressListener, boolean progressEnabled) {
        LocalDate actionDate = context.getActionDate();

        // 0. 市场简报（大盘/风格/量能/涨停/主线）→ 调节买入仓位
        progressListener.onProgress(5, 100, "正在生成市场简报");
        MarketBriefingResp briefing = resolveRunBriefing(context);
        BigDecimal buyFactor = Objects.nonNull(briefing.getBuyWeightFactor())
                ? briefing.getBuyWeightFactor() : BigDecimal.ONE;
        List<String> mainlineNames = resolveMainlineNames(briefing, context.getMode() != DecisionMode.REPLAY);
        progressListener.onProgress(12, 100, "市场简报完成，正在固化组合快照");

        // 1. 固化市场状态和默认组合时点，后续仓位只使用该快照
        MarketRegimeResult marketRegime = resolveRunMarketRegime(context);
        RiskOverviewResp configuredRisk = DecisionMode.REPLAY.equals(context.getMode())
                ? null : loadRiskLimits();
        DecisionPortfolioSnapshot portfolioSnapshot = portfolioSnapshotManager.resolve(
                context, decisionRun, marketRegime, configuredRisk);
        if (DecisionMode.REPLAY.equals(context.getMode())) {
            marketRegime = snapshotMarketRegime(portfolioSnapshot);
        }
        RiskOverviewResp risk = portfolioRiskOverview(portfolioSnapshot);
        progressListener.onProgress(20, 100, "组合快照完成，正在读取全市场股票池");

        // 2. 默认组合快照继续负责持有和风险预算；实时卖出范围覆盖全部活跃组合
        List<MyHolding> holdings = toDecisionHoldings(portfolioSnapshot.getHoldings());
        Map<String, MyHolding> posMap = new HashMap<>();
        for (MyHolding holding : holdings) {
            posMap.put(holding.getCode(), holding);
        }
        Set<String> sellHoldingCodes = new HashSet<>();
        if (context.getMode() == DecisionMode.REPLAY) {
            for (MyHolding holding : holdings) {
                String normalizedCode = MarketCodeUtils.normalizeHoldingCode(holding.getCode());
                if (StringUtils.isNotBlank(normalizedCode)) {
                    sellHoldingCodes.add(normalizedCode);
                }
            }
        } else {
            sellHoldingCodes.addAll(portfolioService.listActiveHoldingCodes());
        }

        // 3. 读取管理员发布的全A共享股票池；回放只使用截止日已存在的批次
        List<UniverseSnapshot> universeList = context.getMode() == DecisionMode.REPLAY
                ? universeService.latestAsOf(actionDate) : universeService.latest();
        if (CollUtil.isEmpty(universeList)) {
            throw new BusinessException("共享股票池尚未发布，请管理员先刷新全市场股票池");
        }
        int universeCount = universeList.size();
        boolean includeBj = Boolean.TRUE.equals(safe.getIncludeBj());
        progressListener.onProgress(24, 100, "股票池已就绪，正在准备策略扫描");

        // 4. 跑 S1/S2/S3：买入扫描全A质量池+热点，卖出单独扫描全部活跃组合持仓
        List<String> signalCodes = new ArrayList<>();
        Set<String> signalCodeSet = new HashSet<>();
        if (CollUtil.isNotEmpty(universeList)) {
            for (UniverseSnapshot snapshot : universeList) {
                if (StringUtils.isNotBlank(snapshot.getCode()) && signalCodeSet.add(snapshot.getCode())) {
                    signalCodes.add(snapshot.getCode());
                }
            }
        }
        Map<String, HotConfluenceItem> hotMap = context.getMode() == DecisionMode.REPLAY
                ? Map.of() : hotService.confluenceMap(50);
        int hotScanCount = 0;
        for (String hotCode : hotMap.keySet()) {
            if (StringUtils.isBlank(hotCode)) {
                continue;
            }
            // 热点里的京市：未勾选含京市则不扫买入；已持仓京市仍可走后面持仓附加
            if (!includeBj && MarketCodeUtils.isBj(hotCode)) {
                continue;
            }
            if (signalCodeSet.add(hotCode)) {
                signalCodes.add(hotCode);
                hotScanCount++;
            }
        }
        SignalRunReq signalReq = new SignalRunReq();
        signalReq.setAsOfDate(actionDate);
        signalReq.setSellCodes(new ArrayList<>(sellHoldingCodes));
        if (CollUtil.isNotEmpty(signalCodes)) {
            signalReq.setCodes(signalCodes);
        } else {
            signalReq.setUseUniverse(true);
        }
        Set<String> allScanCodes = new HashSet<>(signalCodeSet);
        allScanCodes.addAll(sellHoldingCodes);
        int signalScanCount = allScanCodes.size();
        List<StrategySignalEntity> signals;
        if (progressEnabled) {
            signals = signalService.run(signalReq, (completed, total, message) -> {
                int scanProgress = total > 0 ? 28 + completed * 42 / total : 28;
                progressListener.onProgress(scanProgress, 100, message);
            });
        } else {
            signals = signalService.run(signalReq);
        }

        // 4. 多策略共振（窗口/最少策略数可配置）
        progressListener.onProgress(71, 100, "策略扫描完成，正在计算多策略共振");
        SignalConfluenceResp confluenceResp = signalService.confluence(
                strategyParams.decisionConfluenceWindow(),
                strategyParams.decisionConfluenceMinStrategies(),
                actionDate);
        Map<String, SignalConfluenceItem> buyConfluence = new HashMap<>();
        Map<String, SignalConfluenceItem> sellConfluence = new HashMap<>();
        if (Objects.nonNull(confluenceResp) && CollUtil.isNotEmpty(confluenceResp.getItems())) {
            for (SignalConfluenceItem item : confluenceResp.getItems()) {
                if ("BUY".equalsIgnoreCase(item.getSide())) {
                    buyConfluence.put(item.getCode(), item);
                } else if ("SELL".equalsIgnoreCase(item.getSide())) {
                    sellConfluence.put(item.getCode(), item);
                }
            }
        }
        progressListener.onProgress(75, 100, "共振分析完成，正在加载行情与基本面");

        // 5. 风控参数只读规则，账户权益和仓位来自默认组合快照
        BigDecimal singleLimit = Objects.nonNull(risk.getSingleLimit()) ? risk.getSingleLimit() : new BigDecimal("0.15");

        Set<String> codesNeeded = new HashSet<>(signalCodeSet);
        for (StrategySignalEntity signal : signals) {
            codesNeeded.add(signal.getCode());
        }
        Map<String, StockBasic> basicMap = loadBasics(codesNeeded);
        if (context.getMode() == DecisionMode.REPLAY) {
            applyReplayPrices(basicMap, actionDate);
        }
        Map<String, FundSnapshot> fundMap = context.getMode() == DecisionMode.REPLAY
                ? Map.of() : loadFunds(codesNeeded);
        Map<String, BigDecimal> performanceAdjustments = resolvePerformanceAdjustments(context.getMode());
        progressListener.onProgress(78, 100, "行情与基本面完成，正在计算估值");

        // 持仓缺止损/止盈时先补全（供卖出/持有/加仓离场规则使用）
        boolean snapshotRiskLevelsChanged = false;
        for (MyHolding holding : holdings) {
            String code = holding.getCode();
            StockBasic basic = basicMap.get(code);
            if (Objects.isNull(basic) && StringUtils.isNotBlank(code)) {
                basic = basicMap.get(MarketCodeUtils.normalizeHoldingCode(code));
            }
            BigDecimal price = Objects.nonNull(basic) ? basic.getLatestPrice() : holding.getMarketPrice();
            if (ensureHoldingStopTake(holding, price, actionDate,
                    portfolioSnapshot.getAtrStopMultiplier(), portfolioSnapshot.getAtrTakeMultiplier())) {
                snapshotRiskLevelsChanged = true;
            }
        }
        if (snapshotRiskLevelsChanged) {
            Map<String, DecisionPortfolioHolding> snapshotHoldingMap = new HashMap<>();
            for (DecisionPortfolioHolding snapshotHolding : portfolioSnapshot.getHoldings()) {
                snapshotHoldingMap.put(snapshotHolding.getCode(), snapshotHolding);
            }
            for (MyHolding holding : holdings) {
                DecisionPortfolioHolding snapshotHolding = snapshotHoldingMap.get(holding.getCode());
                if (Objects.nonNull(snapshotHolding)) {
                    snapshotHolding.setStopLoss(holding.getStopLoss());
                    snapshotHolding.setTakeProfit(holding.getTakeProfit());
                }
            }
            portfolioSnapshotManager.updateHoldingRiskLevels(portfolioSnapshot);
        }

        // 5b. 买入信号批量估值（决策加减分 / 观察池理由）
        Set<String> buyValCodes = new HashSet<>();
        for (StrategySignalEntity signal : signals) {
            if (Objects.nonNull(signal) && "BUY".equalsIgnoreCase(signal.getSide())
                    && StringUtils.isNotBlank(signal.getCode())) {
                buyValCodes.add(signal.getCode());
            }
        }
        Map<String, ValuationBriefResp> valuationMap = context.getMode() == DecisionMode.REPLAY
                ? Map.of() : valuationService.briefBatch(buyValCodes);
        progressListener.onProgress(82, 100, "估值完成，正在生成买卖决策");

        // 6. 组装决策：买入=全A机会；卖出=全部活跃组合持仓；另收集「值得观察」候选
        List<DecisionItemResp> buys = new ArrayList<>();
        List<DecisionItemResp> sells = new ArrayList<>();
        List<DecisionItemResp> holds = new ArrayList<>();
        List<DecisionItemResp> observeCandidates = new ArrayList<>();
        Map<String, DecisionItemResp> featureCandidates = new HashMap<>();
        Map<String, DecisionFeatureInput> featureInputs = new HashMap<>();
        Map<String, String> featureSelectionStatus = new HashMap<>();
        Map<String, String> featureRejectReason = new HashMap<>();
        Set<String> covered = new HashSet<>();
        Set<String> observeCodes = new HashSet<>();
        for (StrategySignalEntity signal : signals) {
            String code = signal.getCode();
            String side = StringUtils.isNotBlank(signal.getSide()) ? signal.getSide().toUpperCase() : "";
            StockBasic basic = basicMap.get(code);
            String name = Objects.nonNull(basic) ? basic.getName() : null;
            MyHolding holdingInMap = posMap.get(code);
            if (StringUtils.isBlank(name) && Objects.nonNull(holdingInMap)) {
                name = holdingInMap.getName();
            }

            // 卖出：只处理全部活跃组合持仓；股票池内未持仓的卖出信号忽略
            if ("SELL".equals(side)) {
                String normalizedCode = MarketCodeUtils.normalizeHoldingCode(code);
                if (!sellHoldingCodes.contains(normalizedCode)) {
                    continue;
                }
                SignalConfluenceItem cf = sellConfluence.get(code);
                int cfCount = Objects.nonNull(cf) ? cf.getStrategyCount() : 1;
                HotConfluenceItem hot = hotMap.get(code);
                int hotCnt = Objects.nonNull(hot) && Objects.nonNull(hot.getSourceCount()) ? hot.getSourceCount() : 0;
                BigDecimal score = decisionScorer.scoreSell(baseScore(signal.getScore()), cfCount, hotCnt);
                String reason = humanReason(signal, cf, null, "持仓卖出", hot);
                String exitRule = mergeExitRule(exitRuleOf(signal.getStrategyId()), holdingInMap);
                int minCf = strategyParams.decisionConfluenceMinStrategies();
                DecisionItemResp item = DecisionItemResp.builder()
                        .actionDate(actionDate)
                        .code(code)
                        .name(name)
                        .action("SELL")
                        .strategyId(signal.getStrategyId())
                        .reason(reason)
                        .score(score)
                        .suggestedWeight(null)
                        .exitRule(exitRule)
                        .confluenceCount(cfCount)
                        .confluence(cfCount >= minCf)
                        .strategies(Objects.nonNull(cf) ? cf.getStrategies() : List.of(signal.getStrategyId()))
                        .fundNote(fundNoteOf(fundMap.get(code)))
                        .signalId(signal.getId())
                        .scoreExplain("策略" + signal.getStrategyId() + " 卖出 · " + exitRule
                                + (cfCount >= minCf ? " · 多策略共振卖出" : ""))
                        .executableHint(false)
                        .build();
                featureInputs.put(featureKey(code, "SELL"), DecisionFeatureInput.builder()
                        .signalScore(baseScore(signal.getScore()))
                        .buyWeightFactor(buyFactor)
                        .singleLimit(singleLimit)
                        .build());
                sells.add(item);
                covered.add(code);
                continue;
            }

            // 买入：来自股票池策略机会，不要求已在「我的持仓」
            if (!"BUY".equals(side)) {
                continue;
            }
            // 默认不含京市买入；持仓京市的卖出信号不受影响
            if (!includeBj && MarketCodeUtils.isBj(code)) {
                continue;
            }

            FundSnapshot fund = fundMap.get(code);
            FundGate gate = evaluateFund(fund);
            SignalConfluenceItem cf = buyConfluence.get(code);
            int cfCount = Objects.nonNull(cf) ? cf.getStrategyCount() : 1;
            HotConfluenceItem hot = hotMap.get(code);
            int hotCnt = Objects.nonNull(hot) && Objects.nonNull(hot.getSourceCount()) ? hot.getSourceCount() : 0;
            String industry = Objects.nonNull(basic) ? basic.getIndustry() : null;
            MainlineMatcher.Hit mainHit = MainlineMatcher.match(industry, mainlineNames);
            boolean offMainline = !mainHit.match && CollUtil.isNotEmpty(mainlineNames)
                    && StringUtils.isNotBlank(industry);
            boolean alreadyHeld = Objects.nonNull(holdingInMap);
            String buyLabel = alreadyHeld ? "加仓" : "买入";
            String reason = humanReason(signal, cf, gate, buyLabel, hot);
            if (alreadyHeld) {
                reason = trimReason(reason + " · 已在我的持仓");
            }
            if (mainHit.match) {
                reason = trimReason(reason + " · 主线「" + mainHit.name + "」同向");
            } else if (offMainline) {
                reason = trimReason(reason + " · 逆主线降权");
            }
            if (buyFactor.compareTo(BigDecimal.ONE) != 0) {
                reason = trimReason(reason + " · 市场" + briefing.getStance()
                        + "仓位×" + buyFactor.setScale(2, RoundingMode.HALF_UP));
            }

            ValuationBriefResp valBrief = valuationMap.get(code);
            if (Objects.nonNull(valBrief) && StringUtils.isNotBlank(valBrief.getLevelLabel())
                    && !"UNKNOWN".equals(valBrief.getLevel())) {
                reason = trimReason(reason + " · 估值" + valBrief.getLevelLabel());
            }

            int minCf = strategyParams.decisionConfluenceMinStrategies();
            DecisionScoreReq scoreReq = DecisionScoreReq.builder()
                    .signalScore(baseScore(signal.getScore()))
                    .strategyId(signal.getStrategyId())
                    .confluenceCount(cfCount)
                    .hotSourceCount(hotCnt)
                    .fundExclude(gate.exclude)
                    .fundWeak(gate.weak)
                    .mainlineMatch(mainHit.match)
                    .offMainline(offMainline)
                    .valuation(valBrief)
                    .marketStance(briefing.getStance())
                    .buyWeightFactor(buyFactor)
                    .singleLimit(singleLimit)
                    .observeOnly(gate.exclude)
                    .build();
            DecisionScoreResp scored = decisionScorer.scoreBuy(scoreReq);
            BigDecimal performanceAdjustment = performanceAdjustments.get(signal.getStrategyId());
            if (Objects.nonNull(performanceAdjustment) && performanceAdjustment.signum() != 0) {
                scored.setFinalScore(scored.getFinalScore().add(performanceAdjustment)
                        .max(BigDecimal.ZERO).min(new BigDecimal("100"))
                        .setScale(2, RoundingMode.HALF_UP));
                scored.setScoreExplain(trimReason(scored.getScoreExplain()
                        + " · 成熟样本校准" + (performanceAdjustment.signum() > 0 ? "+" : "")
                        + performanceAdjustment.toPlainString()));
                if (scored.getFinalScore().compareTo(strategyParams.decisionExecutableScore()) < 0) {
                    scored.setExecutableHint(false);
                }
            }
            DecisionEntryGateResp entryGate = decisionEntryGate.evaluate(DecisionEntryGateReq.builder()
                    .dataSufficient(Boolean.TRUE.equals(briefing.getDataSufficient()))
                    .breadthUp(briefing.getBreadthUp())
                    .mainlineMatch(mainHit.match)
                    .offMainline(offMainline)
                    .hotSourceCount(hotCnt)
                    .build());
            if (!entryGate.isPassed()) {
                scored.setExecutableHint(false);
                for (String blockReason : entryGate.getBlockReasons()) {
                    if (!scored.getRiskFlags().contains(blockReason)) {
                        scored.getRiskFlags().add(blockReason);
                    }
                }
            }
            featureInputs.put(featureKey(code, "BUY"), DecisionFeatureInput.builder()
                    .signalScore(scoreReq.getSignalScore())
                    .fundExclude(scoreReq.isFundExclude())
                    .fundWeak(scoreReq.isFundWeak())
                    .offMainline(scoreReq.isOffMainline())
                    .valuationScoreDelta(Objects.nonNull(valBrief) ? valBrief.getScoreDelta() : null)
                    .buyWeightFactor(scoreReq.getBuyWeightFactor())
                    .singleLimit(scoreReq.getSingleLimit())
                    .observeOnly(scoreReq.isObserveOnly())
                    .entryGatePassed(entryGate.isPassed())
                    .build());
            if (StringUtils.isNotBlank(scored.getLinkHint())) {
                reason = trimReason(reason + " · " + scored.getLinkHint());
            }

            // 基本面硬剔除：不进「今日买入」，但仍可进观察池盯信号
            BigDecimal buyPrice = Objects.nonNull(basic) ? basic.getLatestPrice() : null;
            String buyExitRule = buyExitRule(signal.getStrategyId(), code, buyPrice, holdingInMap, actionDate,
                    portfolioSnapshot.getAtrStopMultiplier(), portfolioSnapshot.getAtrTakeMultiplier());

            if (gate.exclude) {
                DecisionItemResp rejectedItem = DecisionItemResp.builder()
                        .actionDate(actionDate)
                        .code(code)
                        .name(name)
                        .action("BUY")
                        .strategyId(signal.getStrategyId())
                        .reason(trimReason("观察：" + reason + " · 基本面警示仅观察不急买"))
                        .score(scored.getFinalScore())
                        .suggestedWeight(BigDecimal.ZERO)
                        .exitRule(buyExitRule)
                        .confluenceCount(cfCount)
                        .confluence(cfCount >= minCf)
                        .strategies(Objects.nonNull(cf) ? cf.getStrategies() : List.of(signal.getStrategyId()))
                        .fundNote(gate.note)
                        .signalId(signal.getId())
                        .mainlineMatch(mainHit.match)
                        .mainlineName(mainHit.name)
                        .scoreExplain(scored.getScoreExplain())
                        .valuationLevel(Objects.nonNull(valBrief) ? valBrief.getLevel() : null)
                        .valuationLabel(Objects.nonNull(valBrief) ? valBrief.getLevelLabel() : null)
                        .valuationScore(Objects.nonNull(valBrief) ? valBrief.getScore() : null)
                        .valuationSummary(Objects.nonNull(valBrief) ? valBrief.getSummary() : null)
                        .riskFlags(scored.getRiskFlags())
                        .executableHint(false)
                        .entryGatePassed(entryGate.isPassed())
                        .linkHint(scored.getLinkHint())
                        .build();
                putFeatureCandidate(featureCandidates, featureSelectionStatus, featureRejectReason,
                        rejectedItem, "REJECTED", "基本面未过买入门槛");
                if (!alreadyHeld && observeCodes.add(code)) {
                    observeCandidates.add(rejectedItem);
                }
                continue;
            }

            DecisionItemResp item = DecisionItemResp.builder()
                    .actionDate(actionDate)
                    .code(code)
                    .name(name)
                    .action("BUY")
                    .strategyId(signal.getStrategyId())
                    .reason(reason)
                    .score(scored.getFinalScore())
                    .suggestedWeight(scored.getSuggestedWeight())
                    .exitRule(buyExitRule)
                    .confluenceCount(cfCount)
                    .confluence(cfCount >= minCf)
                    .strategies(Objects.nonNull(cf) ? cf.getStrategies() : List.of(signal.getStrategyId()))
                    .fundNote(gate.note)
                    .signalId(signal.getId())
                    .mainlineMatch(mainHit.match)
                    .mainlineName(mainHit.name)
                    .scoreExplain(scored.getScoreExplain())
                    .valuationLevel(Objects.nonNull(valBrief) ? valBrief.getLevel() : null)
                    .valuationLabel(Objects.nonNull(valBrief) ? valBrief.getLevelLabel() : null)
                    .valuationScore(Objects.nonNull(valBrief) ? valBrief.getScore() : null)
                    .valuationSummary(Objects.nonNull(valBrief) ? valBrief.getSummary() : null)
                    .riskFlags(scored.getRiskFlags())
                    .executableHint(scored.isExecutableHint())
                    .entryGatePassed(entryGate.isPassed())
                    .linkHint(scored.getLinkHint())
                    .build();
            buys.add(item);
            putFeatureCandidate(featureCandidates, featureSelectionStatus, featureRejectReason,
                    item, "SELECTED", null);
            if (!alreadyHeld && observeCodes.add(code)) {
                // 观察池单独降权「近端大涨」：避免只堆这两天涨得好的票
                observeCandidates.add(toObserveBuyCandidate(item, basic));
            }
            // 已持仓出现买入信号时记入 covered，避免同时出现在「继续持有」
            if (alreadyHeld) {
                covered.add(code);
            }
        }

        // 情绪风向标：热点/高标等，用来读市场温度（可天天跌，不默认买入，如德明利一类）
        Set<String> moodCodes = new HashSet<>();
        for (Map.Entry<String, HotConfluenceItem> entry : hotMap.entrySet()) {
            String hotCode = entry.getKey();
            HotConfluenceItem hot = entry.getValue();
            if (StringUtils.isBlank(hotCode) || Objects.isNull(hot)) {
                continue;
            }
            int srcCnt = Objects.nonNull(hot.getSourceCount()) ? hot.getSourceCount() : 0;
            BigDecimal hotPct = Objects.nonNull(hot.getPctChg()) ? hot.getPctChg()
                    : (Objects.nonNull(basicMap.get(hotCode)) ? basicMap.get(hotCode).getPctChg() : null);
            boolean multiHot = srcCnt >= 2;
            boolean fallingFocus = Objects.nonNull(hotPct) && hotPct.compareTo(new BigDecimal("-3")) <= 0
                    && (srcCnt >= 1 || (Objects.nonNull(hot.getBestRank()) && hot.getBestRank() <= 15));
            if (!multiHot && !fallingFocus) {
                continue;
            }
            if (posMap.containsKey(hotCode) || observeCodes.contains(hotCode) || !moodCodes.add(hotCode)) {
                continue;
            }
            StockBasic basic = resolveBasic(hotCode, basicMap);
            if (!usableBasic(basic)) {
                continue;
            }
            String fallNote = Objects.nonNull(hotPct) && hotPct.signum() < 0
                    ? (" · 现跌 " + hotPct.setScale(2, RoundingMode.HALF_UP) + "% 仍处焦点，作情绪温度计")
                    : " · 多源热议，跟踪情绪是否退潮/修复";
            BigDecimal moodScore = new BigDecimal("62").add(new BigDecimal(Math.min(srcCnt, 4) * 4L));
            if (fallingFocus) {
                moodScore = moodScore.add(new BigDecimal("6"));
            }
            observeCandidates.add(DecisionItemResp.builder()
                    .actionDate(actionDate)
                    .code(hotCode)
                    .name(basic.getName())
                    .action("MOOD")
                    .strategyId("MOOD")
                    .reason(trimReason("情绪观察：热点共振 ×" + srcCnt + fallNote))
                    .score(moodScore.min(new BigDecimal("88")))
                    .suggestedWeight(BigDecimal.ZERO)
                    .exitRule("热度退潮或市场不再讨论则移出；非买卖信号")
                    .confluenceCount(srcCnt)
                    .confluence(srcCnt >= 2)
                    .strategies(List.of("MOOD"))
                    .scoreExplain("情绪风向标 · 非买入")
                    .build());
        }
        // 东财热榜前列：无论涨跌都可作情绪温度计（德明利式：天天跌仍是焦点）
        List<MarketHot> eastHot = context.getMode() == DecisionMode.REPLAY
                ? List.of() : hotService.listBySource("eastmoney", 40);
        if (CollUtil.isNotEmpty(eastHot)) {
            int rank = 0;
            for (MarketHot hotRow : eastHot) {
                rank++;
                if (Objects.isNull(hotRow) || StringUtils.isBlank(hotRow.getCode())) {
                    continue;
                }
                String hotCode = hotRow.getCode();
                BigDecimal pct = hotRow.getPctChg();
                boolean topFocus = rank <= 12;
                boolean hardFall = Objects.nonNull(pct) && pct.compareTo(new BigDecimal("-3")) <= 0;
                if (!topFocus && !hardFall) {
                    continue;
                }
                if (posMap.containsKey(hotCode) || observeCodes.contains(hotCode) || !moodCodes.add(hotCode)) {
                    continue;
                }
                StockBasic basic = resolveBasic(hotCode, basicMap);
                if (!usableBasic(basic)) {
                    continue;
                }
                String pctText = Objects.nonNull(pct)
                        ? (pct.setScale(2, RoundingMode.HALF_UP) + "%") : "--";
                String why = hardFall
                        ? ("热榜大跌 " + pctText + " · 跌也反映风险偏好")
                        : ("热榜第" + rank + " · 现 " + pctText + " · 焦点票读情绪");
                observeCandidates.add(DecisionItemResp.builder()
                        .actionDate(actionDate)
                        .code(hotCode)
                        .name(StringUtils.isNotBlank(hotRow.getName()) ? hotRow.getName() : basic.getName())
                        .action("MOOD")
                        .strategyId("MOOD")
                        .reason(trimReason("情绪观察：" + why))
                        .score(new BigDecimal(hardFall ? "72" : "66").add(new BigDecimal(Math.max(0, 12 - rank))))
                        .suggestedWeight(BigDecimal.ZERO)
                        .exitRule("热度消失后移出；默认不买入")
                        .confluenceCount(1)
                        .confluence(false)
                        .strategies(List.of("MOOD"))
                        .scoreExplain("热榜情绪标 · 非买入")
                        .build());
            }
        }
        // 连板高标：即使分歧下跌，也是短线情绪锚
        try {
            LimitUpLadderResp ladder = context.getMode() == DecisionMode.REPLAY
                    ? null : limitUpLadderService.ladder(null);
            if (Objects.nonNull(ladder) && CollUtil.isNotEmpty(ladder.getTiers())) {
                for (LimitUpTier tier : ladder.getTiers()) {
                    if (Objects.isNull(tier) || CollUtil.isEmpty(tier.getStocks())) {
                        continue;
                    }
                    int lb = Objects.nonNull(tier.getLianban()) ? tier.getLianban() : 0;
                    if (lb < 2) {
                        continue;
                    }
                    for (LimitUpStockItem stock : tier.getStocks()) {
                        if (Objects.isNull(stock) || StringUtils.isBlank(stock.getCode())) {
                            continue;
                        }
                        String code = stock.getCode();
                        if (posMap.containsKey(code) || observeCodes.contains(code) || !moodCodes.add(code)) {
                            continue;
                        }
                        StockBasic basic = resolveBasic(code, basicMap);
                        if (!usableBasic(basic) && Objects.isNull(stock.getLatestPrice())) {
                            continue;
                        }
                        String name = StringUtils.isNotBlank(stock.getName()) ? stock.getName()
                                : (Objects.nonNull(basic) ? basic.getName() : code);
                        BigDecimal pct = stock.getPctChg();
                        String theme = StringUtils.isNotBlank(stock.getTheme()) ? (" · 题材「" + stock.getTheme() + "」") : "";
                        observeCandidates.add(DecisionItemResp.builder()
                                .actionDate(actionDate)
                                .code(code)
                                .name(name)
                                .action("MOOD")
                                .strategyId("MOOD")
                                .reason(trimReason("情绪观察：" + lb + "板高标" + theme
                                        + (Objects.nonNull(pct) ? (" · 现 " + pct.setScale(2, RoundingMode.HALF_UP) + "%") : "")
                                        + " · 看短线情绪高度与分歧"))
                                .score(new BigDecimal("68").add(new BigDecimal(Math.min(lb, 5) * 3L)))
                                .suggestedWeight(BigDecimal.ZERO)
                                .exitRule("掉队或情绪退潮后移出；非默认买入")
                                .confluenceCount(lb)
                                .confluence(lb >= 3)
                                .strategies(List.of("MOOD"))
                                .scoreExplain("连板情绪锚 · 非买入")
                                .build());
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("情绪观察：连板天梯纳入失败: {}", ex.getMessage());
        }

        // 我的持仓：止损/止盈优先卖出
        for (MyHolding holding : holdings) {
            String code = holding.getCode();
            if (covered.contains(code)) {
                continue;
            }
            StockBasic basic = basicMap.get(code);
            BigDecimal price = Objects.nonNull(basic) ? basic.getLatestPrice() : holding.getMarketPrice();
            String stopSell = stopTakeReason(holding, price);
            if (StringUtils.isNotBlank(stopSell)) {
                DecisionItemResp item = DecisionItemResp.builder()
                        .actionDate(actionDate)
                        .code(code)
                        .name(StringUtils.isNotBlank(holding.getName()) ? holding.getName()
                                : (Objects.nonNull(basic) ? basic.getName() : null))
                        .action("SELL")
                        .strategyId("RISK")
                        .reason(stopSell)
                        .score(new BigDecimal("90"))
                        .suggestedWeight(null)
                        .exitRule(holdingExitRule(holding) + " · " + stopSell)
                        .confluenceCount(0)
                        .confluence(false)
                        .strategies(List.of("RISK"))
                        .fundNote(fundNoteOf(fundMap.get(code)))
                        .signalId(null)
                        .scoreExplain("风控优先 · 评分90 · " + stopSell)
                        .build();
                sells.add(item);
                covered.add(code);
            }
        }

        // 组合总仓超过回撤门槛时，已有全卖动作优先，其余持仓按权重等比例减仓
        Set<String> fullExitCodes = new HashSet<>();
        for (DecisionItemResp sell : sells) {
            if ("SELL".equals(sell.getAction())) {
                fullExitCodes.add(sell.getCode());
            }
        }
        List<DecisionItemResp> reductionActions = portfolioDrawdownReducer.buildReductionActions(
                actionDate, portfolioSnapshot.getHoldings(), portfolioSnapshot.getTotalEquity(),
                portfolioSnapshot.getExposureRatio(), risk.getTotalLimit(),
                portfolioSnapshot.getDrawdown(), fullExitCodes);
        Set<String> positionCutCodes = new HashSet<>(fullExitCodes);
        for (DecisionItemResp reductionAction : reductionActions) {
            positionCutCodes.add(reductionAction.getCode());
            sells.add(reductionAction);
            covered.add(reductionAction.getCode());
        }
        for (int buyIndex = buys.size() - 1; buyIndex >= 0; buyIndex--) {
            DecisionItemResp buy = buys.get(buyIndex);
            if (!positionCutCodes.contains(buy.getCode())) {
                continue;
            }
            String rejectReason = fullExitCodes.contains(buy.getCode())
                    ? "同票已有卖出动作，风险优先禁止加仓" : "组合回撤降仓，禁止同票加仓";
            rejectRiskCandidate(buy, rejectReason, featureSelectionStatus, featureRejectReason);
            buys.remove(buyIndex);
        }

        // 其余「我的持仓」→ HOLD（离场规则必含止损+止盈）
        for (MyHolding holding : holdings) {
            String code = holding.getCode();
            if (covered.contains(code)) {
                continue;
            }
            StockBasic basic = basicMap.get(code);
            DecisionItemResp item = DecisionItemResp.builder()
                    .actionDate(actionDate)
                    .code(code)
                    .name(StringUtils.isNotBlank(holding.getName()) ? holding.getName()
                            : (Objects.nonNull(basic) ? basic.getName() : null))
                    .action("HOLD")
                    .strategyId(null)
                    .reason("无新卖出信号，继续持有 · " + holdingExitRule(holding))
                    .score(null)
                    .suggestedWeight(null)
                    .exitRule(holdingExitRule(holding))
                    .confluenceCount(0)
                    .confluence(false)
                    .strategies(List.of())
                    .fundNote(fundNoteOf(fundMap.get(code)))
                    .signalId(null)
                    .scoreExplain(holdingExitRule(holding))
                    .build();
            holds.add(item);
            covered.add(code);
        }

        buys.sort(Comparator.comparing(DecisionItemResp::getScore, Comparator.nullsLast(Comparator.reverseOrder())));
        sells.sort(Comparator.comparing(DecisionItemResp::getScore, Comparator.nullsLast(Comparator.reverseOrder())));

        // 7. 按评分顺序逐笔占用风险预算，生成组合可同时执行的目标仓位
        progressListener.onProgress(88, 100, "候选决策完成，正在执行组合风控");
        applyPortfolioRiskBudget(buys, portfolioSnapshot, marketRegime, risk, basicMap,
                featureSelectionStatus, featureRejectReason, actionDate);

        List<DecisionItemResp> all = new ArrayList<>();
        all.addAll(sells);
        all.addAll(buys);
        all.addAll(holds);
        for (DecisionItemResp item : sells) {
            putFeatureCandidate(featureCandidates, featureSelectionStatus, featureRejectReason,
                    item, "SELECTED", null);
        }
        for (DecisionItemResp item : holds) {
            putFeatureCandidate(featureCandidates, featureSelectionStatus, featureRejectReason,
                    item, "SELECTED", null);
        }
        List<DecisionItemResp> rankedFeatureCandidates = new ArrayList<>(featureCandidates.values());
        rankedFeatureCandidates.sort(Comparator.comparing(DecisionItemResp::getScore,
                Comparator.nullsLast(Comparator.reverseOrder())));
        List<DecisionFeature> features = new ArrayList<>();
        int featureRank = 0;
        for (DecisionItemResp item : rankedFeatureCandidates) {
            featureRank++;
            HotConfluenceItem hot = hotMap.get(item.getCode());
            int hotSourceCount = Objects.nonNull(hot) && Objects.nonNull(hot.getSourceCount())
                    ? hot.getSourceCount() : 0;
            DecisionFeatureInput input = featureInputs.get(featureKey(item.getCode(), item.getAction()));
            if (Objects.isNull(input)) {
                input = DecisionFeatureInput.builder()
                        .signalScore(item.getScore())
                        .buyWeightFactor(briefing.getBuyWeightFactor())
                        .singleLimit(singleLimit)
                        .build();
            }
            String key = featureKey(item.getCode(), item.getAction());
            features.add(decisionFeatureBuilder.build(item, DecisionFeatureSource.builder()
                    .scoringInput(input)
                    .hotSourceCount(hotSourceCount)
                    .briefing(briefing)
                    .selectionStatus(featureSelectionStatus.getOrDefault(key, "SELECTED"))
                    .rejectReason(featureRejectReason.get(key))
                    .rankNo(featureRank)
                    .build()));
        }
        progressListener.onProgress(92, 100, "组合风控完成，正在保存决策特征");
        decisionRunManager.saveFeatures(decisionRun, features);
        if (context.getMode() == DecisionMode.LIVE) {
            saveBriefingSnapshot(actionDate, briefing);
        }

        // 8. 自动写入观察池：多策略分散 + 抑制追高（不要求马上买）
        int observeCreated = 0;
        int observeUpdated = 0;
        int observeUpserted = 0;
        progressListener.onProgress(96, 100, "正在更新观察池");
        try {
            if (context.getMode() == DecisionMode.LIVE) {
                List<DecisionItemResp> balanced = balanceObserveCandidates(observeCandidates, basicMap);
                Map<String, Object> obs = observePoolService.syncFromDecision(balanced, List.of());
                observeCreated = toInt(obs.get("created"));
                observeUpdated = toInt(obs.get("updated"));
                observeUpserted = toInt(obs.get("upserted"));
            }
        } catch (Exception ex) {
            log.warn("决策同步观察池失败: {}", ex.getMessage());
        }

        String riskNote = "市场状态 " + marketRegime.getMarketRegime().getDesc()
                + " · 单票上限 " + pctText(singleLimit)
                + " · 总仓 " + pctText(risk.getPositionRatio())
                + "/" + pctText(risk.getTotalLimit())
                + " · 回撤 " + pctText(portfolioSnapshot.getDrawdown())
                + " · 现金 " + portfolioSnapshot.getCash().setScale(2, RoundingMode.HALF_UP);
        if (Objects.nonNull(risk.getCriticalCount()) && risk.getCriticalCount() > 0) {
            riskNote = riskNote + " · 风控CRITICAL " + risk.getCriticalCount();
        }
        if (Objects.nonNull(briefing.getStance())) {
            riskNote = riskNote + " · 市场立场「" + briefing.getStance() + "」"
                    + (Objects.nonNull(briefing.getStanceScore()) ? (" " + briefing.getStanceScore()) : "");
        }

        int executableCount = 0;
        int mainlineMatchCount = 0;
        int valuationCheapCount = 0;
        int valuationFairCount = 0;
        int valuationRichCount = 0;
        for (DecisionItemResp item : all) {
            if (Boolean.TRUE.equals(item.getExecutableHint())) {
                executableCount++;
            }
            if (Boolean.TRUE.equals(item.getMainlineMatch())) {
                mainlineMatchCount++;
            }
            String level = item.getValuationLevel();
            if ("UNDERVALUED".equals(level) || "SLIGHTLY_CHEAP".equals(level)) {
                valuationCheapCount++;
            } else if ("FAIR".equals(level)) {
                valuationFairCount++;
            } else if ("OVERVALUED".equals(level) || "SLIGHTLY_EXPENSIVE".equals(level)) {
                valuationRichCount++;
            }
        }

        String barFreshnessHint = resolveBarFreshnessHint(actionDate);
        return DecisionTodayResp.builder()
                .runNo(decisionRun.getRunNo())
                .runMode(decisionRun.getMode())
                .asOfTime(decisionRun.getAsOfTime())
                .ruleVersion(decisionRun.getRuleVersion())
                .modelVersion(decisionRun.getModelVersion())
                .featureVersion(decisionRun.getFeatureVersion())
                .actionDate(actionDate)
                .groupName(groupName)
                .universeCount(universeCount)
                .buys(buys)
                .sells(sells)
                .holds(holds)
                .items(all)
                .buyCount(buys.size())
                .sellCount(sells.size())
                .holdCount(holds.size())
                .executableCount(executableCount)
                .mainlineMatchCount(mainlineMatchCount)
                .valuationCheapCount(valuationCheapCount)
                .valuationFairCount(valuationFairCount)
                .valuationRichCount(valuationRichCount)
                .riskNote(riskNote)
                .marketBriefing(briefing)
                .observeCreated(observeCreated)
                .observeUpdated(observeUpdated)
                .observeUpserted(observeUpserted)
                .message("市场「" + briefing.getStance() + "」· 买入 " + buys.size()
                        + " · 卖出 " + sells.size()
                        + " · 持有 " + holds.size()
                        + " · 可执行 " + executableCount
                        + " · 股票池 " + universeCount
                        + " · 持仓 " + holdings.size()
                        + " · 热点扩扫 " + hotScanCount
                        + " · 扫描 " + signalScanCount
                        + " · 观察池写入 " + observeUpserted
                        + "（新" + observeCreated + "/更" + observeUpdated + "）"
                        + barFreshnessHint)
                .build();
    }

    /**
     * 本地日线未到决策日时提示：信号会与「截至最新K线日」的结果接近
     */
    private String resolveBarFreshnessHint(LocalDate actionDate) {
        if (Objects.isNull(actionDate)) {
            return "";
        }
        List<Map<String, Object>> rows = barDailyMapper.selectMaps(Wrappers.<BarDaily>query()
                .select("MAX(trade_date) AS max_dt"));
        if (CollUtil.isEmpty(rows) || Objects.isNull(rows.get(0)) || Objects.isNull(rows.get(0).get("max_dt"))) {
            return " · 本地无日线";
        }
        LocalDate maxBarDate;
        Object raw = rows.get(0).get("max_dt");
        if (raw instanceof LocalDate localDate) {
            maxBarDate = localDate;
        } else if (raw instanceof java.sql.Date sqlDate) {
            maxBarDate = sqlDate.toLocalDate();
        } else {
            maxBarDate = LocalDate.parse(String.valueOf(raw).substring(0, 10));
        }
        if (maxBarDate.isBefore(actionDate)) {
            return " · 日线截至 " + maxBarDate + "（今日K线未同步，名单易与该日相同）";
        }
        return " · 日线截至 " + maxBarDate;
    }

    private int toInt(Object v) {
        if (Objects.isNull(v)) {
            return 0;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String featureKey(String code, String action) {
        return String.valueOf(code) + "|" + String.valueOf(action);
    }

    private void putFeatureCandidate(Map<String, DecisionItemResp> featureCandidates,
                                     Map<String, String> selectionStatus,
                                     Map<String, String> rejectReason,
                                     DecisionItemResp item, String status, String reason) {
        String key = featureKey(item.getCode(), item.getAction());
        DecisionItemResp existing = featureCandidates.get(key);
        BigDecimal existingScore = Objects.nonNull(existing) && Objects.nonNull(existing.getScore())
                ? existing.getScore() : null;
        BigDecimal candidateScore = item.getScore();
        boolean replace = Objects.isNull(existing)
                || (Objects.nonNull(candidateScore)
                && (Objects.isNull(existingScore) || candidateScore.compareTo(existingScore) > 0))
                || ("SELECTED".equals(status) && !"SELECTED".equals(selectionStatus.get(key)));
        if (!replace) {
            return;
        }
        featureCandidates.put(key, item);
        selectionStatus.put(key, status);
        if (StringUtils.isNotBlank(reason)) {
            rejectReason.put(key, reason);
        } else {
            rejectReason.remove(key);
        }
    }

    private Map<String, Object> decisionConfigSnapshot() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("confluenceWindow", strategyParams.decisionConfluenceWindow());
        config.put("confluenceMinStrategies", strategyParams.decisionConfluenceMinStrategies());
        config.put("scoreConfluence", strategyParams.decisionScoreConfluence());
        config.put("scoreHot", strategyParams.decisionScoreHot());
        config.put("scoreHotTriple", strategyParams.decisionScoreHotTriple());
        config.put("scoreMainline", strategyParams.decisionScoreMainline());
        config.put("scoreOffMainline", strategyParams.decisionScoreOffMainline());
        config.put("scoreFundPenalty", strategyParams.decisionScoreFundPenalty());
        config.put("scoreDefense", strategyParams.decisionScoreDefense());
        config.put("scoreOffense", strategyParams.decisionScoreOffense());
        config.put("linkUndervaluedS2", strategyParams.decisionLinkUndervaluedS2());
        config.put("linkOvervaluedS3", strategyParams.decisionLinkOvervaluedS3());
        config.put("executableScore", strategyParams.decisionExecutableScore());
        config.put("gateMinimumBreadthUp", strategyParams.decisionGateMinimumBreadthUp());
        config.put("gateMinimumHotSources", strategyParams.decisionGateMinimumHotSources());
        config.put("performanceCalibration", "COMPLETE_5D_EXCESS_V1");
        return config;
    }

    Map<String, BigDecimal> resolvePerformanceAdjustments(DecisionMode mode) {
        return DecisionMode.REPLAY.equals(mode) ? Map.of() : performanceCalibrator.loadAdjustments();
    }

    MarketRegimeResult resolveRunMarketRegime(DecisionContext context) {
        if (DecisionMode.REPLAY.equals(context.getMode())) {
            return null;
        }
        return marketRegimeResolver.resolve(context.getActionDate());
    }

    private List<MyHolding> toDecisionHoldings(List<DecisionPortfolioHolding> snapshotHoldings) {
        List<MyHolding> holdings = new ArrayList<>();
        if (CollUtil.isEmpty(snapshotHoldings)) {
            return holdings;
        }
        for (DecisionPortfolioHolding snapshotHolding : snapshotHoldings) {
            holdings.add(MyHolding.builder()
                    .code(snapshotHolding.getCode())
                    .name(snapshotHolding.getName())
                    .quantity(snapshotHolding.getQuantity())
                    .costPrice(snapshotHolding.getCostPrice())
                    .stopLoss(snapshotHolding.getStopLoss())
                    .takeProfit(snapshotHolding.getTakeProfit())
                    .marketPrice(snapshotHolding.getMarketPrice())
                    .marketValue(snapshotHolding.getMarketValue())
                    .industry(snapshotHolding.getIndustry())
                    .build());
        }
        return holdings;
    }

    private RiskOverviewResp loadRiskLimits() {
        BigDecimal totalLimit = new BigDecimal("0.80");
        BigDecimal singleLimit = new BigDecimal("0.15");
        BigDecimal industryLimit = new BigDecimal("0.30");
        List<RiskRule> riskRules = riskService.listRules();
        if (CollUtil.isNotEmpty(riskRules)) {
            for (RiskRule riskRule : riskRules) {
                if (Objects.isNull(riskRule) || StringUtils.isBlank(riskRule.getRuleValue())) {
                    continue;
                }
                try {
                    BigDecimal ruleValue = new BigDecimal(riskRule.getRuleValue());
                    if ("total_position_limit".equals(riskRule.getRuleKey())) {
                        totalLimit = ruleValue;
                    } else if ("single_stock_limit".equals(riskRule.getRuleKey())) {
                        singleLimit = ruleValue;
                    } else if ("industry_limit".equals(riskRule.getRuleKey())) {
                        industryLimit = ruleValue;
                    }
                } catch (NumberFormatException ex) {
                    log.warn("忽略非法风控规则，规则标识={}，规则值={}",
                            riskRule.getRuleKey(), riskRule.getRuleValue());
                }
            }
        }
        return RiskOverviewResp.builder()
                .totalLimit(totalLimit)
                .singleLimit(singleLimit)
                .industryLimit(industryLimit)
                .build();
    }

    private RiskOverviewResp portfolioRiskOverview(DecisionPortfolioSnapshot snapshot) {
        if (Objects.isNull(snapshot.getExposureLimit())
                || Objects.isNull(snapshot.getSingleStockLimit())
                || Objects.isNull(snapshot.getIndustryLimit())) {
            throw new BusinessException("决策组合快照缺少冻结风控参数");
        }
        return RiskOverviewResp.builder()
                .totalAsset(snapshot.getTotalEquity())
                .cash(snapshot.getCash())
                .positionValue(snapshot.getMarketValue())
                .positionRatio(snapshot.getExposureRatio())
                .totalLimit(snapshot.getExposureLimit())
                .singleLimit(snapshot.getSingleStockLimit())
                .industryLimit(snapshot.getIndustryLimit())
                .warnings(List.of())
                .alerts(List.of())
                .criticalCount(0)
                .warnCount(0)
                .build();
    }

    private void applyPortfolioRiskBudget(List<DecisionItemResp> buys,
                                          DecisionPortfolioSnapshot portfolioSnapshot,
                                          MarketRegimeResult marketRegime,
                                          RiskOverviewResp risk,
                                          Map<String, StockBasic> basicMap,
                                          Map<String, String> featureSelectionStatus,
                                          Map<String, String> featureRejectReason,
                                          LocalDate actionDate) {
        if (CollUtil.isEmpty(buys)) {
            return;
        }
        BigDecimal equity = portfolioSnapshot.getTotalEquity();
        if (Objects.isNull(equity) || equity.signum() <= 0) {
            for (DecisionItemResp item : buys) {
                rejectRiskCandidate(item, "默认组合总权益必须大于0",
                        featureSelectionStatus, featureRejectReason);
            }
            return;
        }

        BigDecimal availableCash = portfolioSnapshot.getCash();
        BigDecimal exposure = portfolioSnapshot.getExposureRatio();
        Map<String, BigDecimal> industryWeight = new HashMap<>(portfolioSnapshot.getIndustryExposure());
        Map<String, BigDecimal> codeWeight = new HashMap<>();
        if (CollUtil.isNotEmpty(portfolioSnapshot.getHoldings())) {
            for (DecisionPortfolioHolding holding : portfolioSnapshot.getHoldings()) {
                if (StringUtils.isBlank(holding.getCode()) || Objects.isNull(holding.getMarketValue())) {
                    continue;
                }
                codeWeight.put(holding.getCode(), holding.getMarketValue()
                        .divide(equity, 12, RoundingMode.HALF_UP));
            }
        }

        for (DecisionItemResp item : buys) {
            String featureKey = featureKey(item.getCode(), item.getAction());
            BigDecimal currentWeight = codeWeight.getOrDefault(item.getCode(), BigDecimal.ZERO);
            if (!Boolean.TRUE.equals(item.getExecutableHint())) {
                item.setSuggestedWeight(currentWeight.setScale(4, RoundingMode.HALF_UP));
                featureSelectionStatus.put(featureKey, "WATCH");
                String rejectReason = Boolean.FALSE.equals(item.getEntryGatePassed())
                        ? "未通过市场或板块开仓门禁" : "评分未达到可执行阈值";
                featureRejectReason.put(featureKey, rejectReason);
                appendRiskFlag(item, "仅观察：" + rejectReason);
                continue;
            }

            StockBasic basic = basicMap.get(item.getCode());
            BigDecimal price = Objects.nonNull(basic) ? basic.getLatestPrice() : null;
            if (Objects.isNull(price) || price.signum() <= 0) {
                rejectRiskCandidate(item, "缺少有效价格，无法按整手计算",
                        featureSelectionStatus, featureRejectReason);
                continue;
            }
            String industry = Objects.nonNull(basic) && StringUtils.isNotBlank(basic.getIndustry())
                    ? basic.getIndustry() : "未知行业";
            BigDecimal currentIndustryWeight = industryWeight.getOrDefault(industry, BigDecimal.ZERO);
            BigDecimal[] stopTake = suggestStopTake(item.getCode(), price, actionDate,
                    portfolioSnapshot.getAtrStopMultiplier(), portfolioSnapshot.getAtrTakeMultiplier());
            item.setReferencePrice(price);
            item.setStopLossPrice(stopTake[0]);
            item.setTakeProfitPrice(stopTake[1]);
            BigDecimal stopDistance = FALLBACK_STOP_PCT;
            if (Objects.nonNull(stopTake[0]) && stopTake[0].signum() > 0 && stopTake[0].compareTo(price) < 0) {
                stopDistance = price.subtract(stopTake[0]).divide(price, 8, RoundingMode.HALF_UP);
            }
            BigDecimal scoreTarget = Objects.nonNull(item.getSuggestedWeight())
                    && item.getSuggestedWeight().signum() > 0
                    ? item.getSuggestedWeight() : risk.getSingleLimit();
            BigDecimal candidateSingleLimit = risk.getSingleLimit().min(scoreTarget.max(currentWeight));
            PositionRiskResult positionRisk = positionRiskCalculator.calculate(PositionRiskInput.builder()
                    .equity(equity)
                    .cash(availableCash)
                    .currentExposure(exposure)
                    .currentDrawdown(portfolioSnapshot.getDrawdown())
                    .totalExposureLimit(risk.getTotalLimit())
                    .marketRegime(marketRegime.getMarketRegime())
                    .singleLimit(candidateSingleLimit)
                    .industryLimit(risk.getIndustryLimit())
                    .currentWeight(currentWeight)
                    .industryWeight(currentIndustryWeight)
                    .stopDistance(stopDistance)
                    .correlationClusterWeight(currentIndustryWeight)
                    .lotValue(price.multiply(new BigDecimal("100")))
                    .build());
            if (!Boolean.TRUE.equals(positionRisk.getCanOpenPosition())) {
                item.setSuggestedWeight(currentWeight.setScale(4, RoundingMode.HALF_UP));
                rejectRiskCandidate(item, positionRisk.getReason(),
                        featureSelectionStatus, featureRejectReason);
                continue;
            }

            BigDecimal incrementalWeight = positionRisk.getIncrementalWeight();
            BigDecimal incrementalAmount = incrementalWeight.multiply(equity);
            availableCash = availableCash.subtract(incrementalAmount).max(BigDecimal.ZERO);
            exposure = exposure.add(incrementalWeight).min(BigDecimal.ONE);
            codeWeight.put(item.getCode(), positionRisk.getTargetWeight());
            industryWeight.put(industry, currentIndustryWeight.add(incrementalWeight));
            item.setSuggestedWeight(positionRisk.getTargetWeight().setScale(4, RoundingMode.HALF_UP));
            item.setScoreExplain(trimReason(nullToEmpty(item.getScoreExplain())
                    + " · 风险预算通过，新增 " + pctText(incrementalWeight)));
            featureSelectionStatus.put(featureKey, "SELECTED");
            featureRejectReason.remove(featureKey);
        }
    }

    private void rejectRiskCandidate(DecisionItemResp item, String reason,
                                     Map<String, String> featureSelectionStatus,
                                     Map<String, String> featureRejectReason) {
        item.setExecutableHint(false);
        if (Objects.isNull(item.getSuggestedWeight())) {
            item.setSuggestedWeight(BigDecimal.ZERO);
        }
        appendRiskFlag(item, reason);
        String key = featureKey(item.getCode(), item.getAction());
        featureSelectionStatus.put(key, "REJECTED");
        featureRejectReason.put(key, reason);
    }

    private void appendRiskFlag(DecisionItemResp item, String reason) {
        List<String> riskFlags = new ArrayList<>();
        if (CollUtil.isNotEmpty(item.getRiskFlags())) {
            riskFlags.addAll(item.getRiskFlags());
        }
        if (StringUtils.isNotBlank(reason) && !riskFlags.contains(reason)) {
            riskFlags.add(reason);
        }
        item.setRiskFlags(riskFlags);
    }

    /**
     * 读取某日决策清单
     *
     * @param date      日期，可空=今天
     * @param groupName 分组（仅写入 message，可空）
     * @return 今日决策
     */
    @Override
    public DecisionTodayResp today(LocalDate date, String groupName) {
        return today(date, groupName, null, false);
    }

    /**
     * 使用已加载的市场简报读取某日决策清单
     *
     * @param date 日期，可空=今天
     * @param groupName 分组（仅写入 message，可空）
     * @param briefing 已加载的市场简报，可空
     * @return 今日决策
     */
    @Override
    public DecisionTodayResp today(LocalDate date, String groupName, MarketBriefingResp briefing) {
        return today(date, groupName, briefing, true);
    }

    private DecisionTodayResp today(LocalDate date, String groupName, MarketBriefingResp briefing,
                                   boolean reuseBriefing) {
        LocalDate actionDate = Objects.nonNull(date) ? date : LocalDate.now();
        String group = StringUtils.isNotBlank(groupName) ? groupName.trim() : "我的自选";
        List<DailyAction> rows = dailyActionMapper.selectList(Wrappers.<DailyAction>lambdaQuery()
                .eq(DailyAction::getUserId, userContext.currentUserId())
                .eq(DailyAction::getActionDate, actionDate)
                .orderByAsc(DailyAction::getAction)
                .orderByDesc(DailyAction::getScore));
        DecisionRun storedRun = loadDecisionRun(rows);
        if (actionDate.equals(LocalDate.now())) {
            if (!reuseBriefing) {
                // 今日大盘与看板对齐：走实时简报（指数/量能/涨跌家数会覆盖）
                briefing = marketBriefingService.briefing();
            }
        } else {
            briefing = loadBriefingSnapshot(actionDate);
            if (Objects.isNull(briefing)) {
                briefing = unavailableBriefing(actionDate);
            }
        }
        List<String> mainlineNames = resolveMainlineNames(briefing,
                actionDate.equals(LocalDate.now()) && !reuseBriefing);
        Set<String> codes = new HashSet<>();
        for (DailyAction row : rows) {
            if (StringUtils.isNotBlank(row.getCode())) {
                codes.add(row.getCode());
            }
        }
        Map<String, StockBasic> basicMap = loadBasics(codes);

        List<DecisionItemResp> buys = new ArrayList<>();
        List<DecisionItemResp> sells = new ArrayList<>();
        List<DecisionItemResp> holds = new ArrayList<>();
        List<DecisionItemResp> all = new ArrayList<>();
        for (DailyAction row : rows) {
            StockBasic basic = basicMap.get(row.getCode());
            MainlineMatcher.Hit hit = MainlineMatcher.match(
                    Objects.nonNull(basic) ? basic.getIndustry() : null, mainlineNames);
            Boolean mainlineMatch = null;
            String mainlineName = null;
            if (Objects.nonNull(row.getMainlineMatch())) {
                mainlineMatch = row.getMainlineMatch() == 1;
                mainlineName = row.getMainlineName();
            } else if ("BUY".equalsIgnoreCase(row.getAction())) {
                mainlineMatch = hit.match;
                mainlineName = hit.name;
            }
            List<String> strategies = parseStrategiesCsv(row.getStrategiesCsv());
            if (CollUtil.isEmpty(strategies) && StringUtils.isNotBlank(row.getStrategyId())) {
                strategies = List.of(row.getStrategyId());
            }
            List<String> riskFlags = parseStrategiesCsv(row.getRiskFlags());
            Boolean executableHint = null;
            if (Objects.nonNull(row.getExecutableHint())) {
                executableHint = row.getExecutableHint() == 1;
            }
            DecisionItemResp item = DecisionItemResp.builder()
                    .id(row.getId())
                    .actionDate(row.getActionDate())
                    .code(row.getCode())
                    .name(row.getName())
                    .action(row.getAction())
                    .strategyId(row.getStrategyId())
                    .reason(row.getReason())
                    .score(row.getScore())
                    .suggestedWeight(row.getSuggestedWeight())
                    .referencePrice(row.getReferencePrice())
                    .stopLossPrice(row.getStopLossPrice())
                    .takeProfitPrice(row.getTakeProfitPrice())
                    .exitRule(row.getExitRule())
                    .confluenceCount(row.getConfluenceCount())
                    .confluence(Objects.nonNull(row.getConfluenceCount()) && row.getConfluenceCount() >= 2)
                    .strategies(strategies)
                    .fundNote(row.getFundNote())
                    .signalId(row.getSignalId())
                    .mainlineMatch(mainlineMatch)
                    .mainlineName(mainlineName)
                    .scoreExplain(row.getScoreExplain())
                    .valuationLevel(row.getValuationLevel())
                    .valuationLabel(row.getValuationLabel())
                    .valuationScore(row.getValuationScore())
                    .valuationSummary(row.getValuationSummary())
                    .linkHint(row.getLinkHint())
                    .riskFlags(riskFlags)
                    .executableHint(executableHint)
                    .build();
            all.add(item);
            if ("BUY".equalsIgnoreCase(row.getAction())) {
                buys.add(item);
            } else if ("SELL".equalsIgnoreCase(row.getAction())
                    || "REDUCE".equalsIgnoreCase(row.getAction())) {
                sells.add(item);
            } else {
                holds.add(item);
            }
        }
        int executableCount = 0;
        int mainlineMatchCount = 0;
        int valuationCheapCount = 0;
        int valuationFairCount = 0;
        int valuationRichCount = 0;
        for (DecisionItemResp item : all) {
            if (Boolean.TRUE.equals(item.getExecutableHint())) {
                executableCount++;
            }
            if (Boolean.TRUE.equals(item.getMainlineMatch())) {
                mainlineMatchCount++;
            }
            String level = item.getValuationLevel();
            if ("UNDERVALUED".equals(level) || "SLIGHTLY_CHEAP".equals(level)) {
                valuationCheapCount++;
            } else if ("FAIR".equals(level)) {
                valuationFairCount++;
            } else if ("OVERVALUED".equals(level) || "SLIGHTLY_EXPENSIVE".equals(level)) {
                valuationRichCount++;
            }
        }
        String message = CollUtil.isEmpty(all)
                ? "今日尚无决策，请启动「后台生成决策」；下方市场简报已可参考"
                : "市场「" + briefing.getStance() + "」· 买 " + buys.size()
                + " / 卖 " + sells.size() + " / 持有 " + holds.size()
                + " · 可执行 " + executableCount;
        return DecisionTodayResp.builder()
                .runNo(Objects.nonNull(storedRun) ? storedRun.getRunNo() : null)
                .runMode(Objects.nonNull(storedRun) ? storedRun.getMode() : null)
                .asOfTime(Objects.nonNull(storedRun) ? storedRun.getAsOfTime() : null)
                .ruleVersion(Objects.nonNull(storedRun) ? storedRun.getRuleVersion() : null)
                .modelVersion(Objects.nonNull(storedRun) ? storedRun.getModelVersion() : null)
                .featureVersion(Objects.nonNull(storedRun) ? storedRun.getFeatureVersion() : null)
                .actionDate(actionDate)
                .groupName(group)
                .universeCount(null)
                .buys(buys)
                .sells(sells)
                .holds(holds)
                .items(all)
                .buyCount(buys.size())
                .sellCount(sells.size())
                .holdCount(holds.size())
                .executableCount(executableCount)
                .mainlineMatchCount(mainlineMatchCount)
                .valuationCheapCount(valuationCheapCount)
                .valuationFairCount(valuationFairCount)
                .valuationRichCount(valuationRichCount)
                .riskNote(Objects.nonNull(briefing.getPositionAdvice()) ? briefing.getPositionAdvice() : null)
                .marketBriefing(briefing)
                .message(message)
                .build();
    }

    private DecisionRun loadDecisionRun(List<DailyAction> rows) {
        if (CollUtil.isEmpty(rows)) {
            return null;
        }
        for (DailyAction row : rows) {
            if (Objects.nonNull(row.getRunId())) {
                DecisionRun run = decisionRunMapper.selectById(row.getRunId());
                return Objects.nonNull(run) && Objects.equals(run.getUserId(), userContext.currentUserId())
                        ? run : null;
            }
        }
        return null;
    }

    /**
     * 获取面向默认组合的最终决策提示
     *
     * @param date 决策日，可空=今天
     * @return 最终决策提示
     */
    @Override
    public DecisionAdviceResp advice(LocalDate date) {
        LocalDate actionDate = Objects.nonNull(date) ? date : LocalDate.now();
        List<DailyAction> actions = dailyActionMapper.selectList(Wrappers.<DailyAction>lambdaQuery()
                .eq(DailyAction::getUserId, userContext.currentUserId())
                .eq(DailyAction::getActionDate, actionDate)
                .orderByAsc(DailyAction::getRankNo)
                .orderByDesc(DailyAction::getScore));
        if (CollUtil.isEmpty(actions)) {
            throw new BusinessException("该日期尚无已发布决策: " + actionDate);
        }
        DecisionRun run = loadDecisionRun(actions);
        if (Objects.isNull(run)) {
            throw new BusinessException("决策缺少运行上下文: " + actionDate);
        }
        DecisionPortfolioSnapshot snapshot = decisionPortfolioSnapshotMapper.selectOne(
                Wrappers.<DecisionPortfolioSnapshot>lambdaQuery()
                        .eq(DecisionPortfolioSnapshot::getRunId, run.getId())
                        .last("LIMIT 1"));
        if (Objects.isNull(snapshot)) {
            throw new BusinessException("决策缺少组合快照: " + actionDate);
        }
        List<DecisionPortfolioHolding> holdings = JsonUtils.parseArray(
                snapshot.getHoldingPayload(), DecisionPortfolioHolding.class);
        Map<String, DecisionPortfolioHolding> holdingMap = new HashMap<>();
        if (CollUtil.isNotEmpty(holdings)) {
            for (DecisionPortfolioHolding holding : holdings) {
                holdingMap.put(holding.getCode(), holding);
            }
        }

        List<DecisionAdviceActionResp> adviceActions = new ArrayList<>();
        BigDecimal targetExposure = snapshot.getExposureRatio();
        int priority = 0;
        for (DailyAction action : actions) {
            DecisionPortfolioHolding holding = holdingMap.get(action.getCode());
            BigDecimal currentWeight = currentWeight(holding, snapshot.getTotalEquity());
            BigDecimal targetWeight = Objects.nonNull(action.getSuggestedWeight())
                    ? action.getSuggestedWeight() : currentWeight;
            String adviceAction = resolveAdviceAction(action, currentWeight, targetWeight);
            if ("SELL".equals(adviceAction)) {
                targetWeight = BigDecimal.ZERO;
            }
            boolean executable = "BUY".equals(adviceAction) || "ADD".equals(adviceAction)
                    || "REDUCE".equals(adviceAction) || "SELL".equals(adviceAction);
            if (!executable) {
                targetWeight = currentWeight;
            }
            BigDecimal referencePrice = resolveReferencePrice(action, holding);
            Integer quantity = calculateAdviceQuantity(adviceAction, currentWeight, targetWeight,
                    snapshot.getTotalEquity(), referencePrice, holding);
            if (executable && quantity <= 0) {
                executable = false;
                adviceAction = "WATCH";
                targetWeight = currentWeight;
            } else if (executable && !"SELL".equals(adviceAction)) {
                BigDecimal tradeWeight = referencePrice.multiply(BigDecimal.valueOf(quantity))
                        .divide(snapshot.getTotalEquity(), 8, RoundingMode.HALF_UP);
                targetWeight = "REDUCE".equals(adviceAction)
                        ? currentWeight.subtract(tradeWeight).max(BigDecimal.ZERO)
                        : currentWeight.add(tradeWeight).min(BigDecimal.ONE);
            }
            String riskNote = CollUtil.isNotEmpty(parseStrategiesCsv(action.getRiskFlags()))
                    ? String.join("；", parseStrategiesCsv(action.getRiskFlags())) : null;
            adviceActions.add(DecisionAdviceActionResp.builder()
                    .priority(++priority)
                    .code(action.getCode())
                    .name(action.getName())
                    .action(adviceAction)
                    .currentWeight(currentWeight.setScale(4, RoundingMode.HALF_UP))
                    .targetWeight(targetWeight.setScale(4, RoundingMode.HALF_UP))
                    .quantity(quantity)
                    .referencePrice(referencePrice)
                    .stopLossPrice(Objects.nonNull(action.getStopLossPrice())
                            ? action.getStopLossPrice() : Objects.nonNull(holding) ? holding.getStopLoss() : null)
                    .takeProfitPrice(Objects.nonNull(action.getTakeProfitPrice())
                            ? action.getTakeProfitPrice() : Objects.nonNull(holding) ? holding.getTakeProfit() : null)
                    .minHoldingDays(5)
                    .maxHoldingDays(20)
                    .reason(action.getReason())
                    .riskNote(riskNote)
                    .executable(executable)
                    .build());
            if ("BUY".equals(adviceAction) || "ADD".equals(adviceAction)) {
                targetExposure = targetExposure.add(targetWeight.subtract(currentWeight).max(BigDecimal.ZERO));
            } else if ("SELL".equals(adviceAction)) {
                targetExposure = targetExposure.subtract(currentWeight).max(BigDecimal.ZERO);
            } else if ("REDUCE".equals(adviceAction)) {
                targetExposure = targetExposure.subtract(currentWeight.subtract(targetWeight).max(BigDecimal.ZERO))
                        .max(BigDecimal.ZERO);
            }
        }
        String localSummary = buildLocalAdviceSummary(snapshot, adviceActions, targetExposure);
        String aiSummary = enhanceAdviceSummary(snapshot, adviceActions, localSummary);
        return DecisionAdviceResp.builder()
                .runNo(run.getRunNo())
                .actionDate(actionDate)
                .executionDate(TradingCalendar.nextTradingDay(actionDate))
                .executionTiming("下一交易日开盘，价格偏离参考价超过3%时取消追价并转观察")
                .marketRegime(snapshot.getMarketRegime())
                .regimeReason(snapshot.getRegimeReason())
                .currentExposure(snapshot.getExposureRatio())
                .targetExposure(targetExposure.setScale(4, RoundingMode.HALF_UP))
                .cash(snapshot.getCash())
                .totalEquity(snapshot.getTotalEquity())
                .drawdown(snapshot.getDrawdown())
                .summary(StringUtils.isNotBlank(aiSummary) ? aiSummary : localSummary)
                .aiEnhanced(StringUtils.isNotBlank(aiSummary))
                .actions(adviceActions)
                .reviewSchedule(List.of("第5个交易日复核趋势与止损", "第10个交易日复核超额收益",
                        "第20个交易日强制退出或重新评估"))
                .generatedAt(LocalDateTime.now())
                .build();
    }

    private BigDecimal currentWeight(DecisionPortfolioHolding holding, BigDecimal totalEquity) {
        if (Objects.isNull(holding) || Objects.isNull(holding.getMarketValue())
                || Objects.isNull(totalEquity) || totalEquity.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return holding.getMarketValue().divide(totalEquity, 8, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveReferencePrice(DailyAction action, DecisionPortfolioHolding holding) {
        if (Objects.nonNull(action.getReferencePrice())) {
            return action.getReferencePrice();
        }
        return Objects.nonNull(holding) ? holding.getMarketPrice() : null;
    }

    private MarketRegimeResult snapshotMarketRegime(DecisionPortfolioSnapshot snapshot) {
        MarketRegimeEnum marketRegime = MarketRegimeEnum.UNKNOWN;
        if (Objects.nonNull(snapshot) && StringUtils.isNotBlank(snapshot.getMarketRegime())) {
            try {
                marketRegime = MarketRegimeEnum.valueOf(snapshot.getMarketRegime());
            } catch (IllegalArgumentException ignored) {
                marketRegime = MarketRegimeEnum.UNKNOWN;
            }
        }
        return MarketRegimeResult.builder()
                .marketRegime(marketRegime)
                .totalExposureLimit(Objects.nonNull(snapshot) && Objects.nonNull(snapshot.getExposureLimit())
                        ? snapshot.getExposureLimit() : marketRegime.getTotalExposureLimit())
                .reason(Objects.nonNull(snapshot) ? snapshot.getRegimeReason() : "历史市场状态缺失")
                .build();
    }

    private String resolveAdviceAction(DailyAction action, BigDecimal currentWeight, BigDecimal targetWeight) {
        if ("SELL".equalsIgnoreCase(action.getAction())) {
            return "SELL";
        }
        if ("REDUCE".equalsIgnoreCase(action.getAction()) && targetWeight.compareTo(currentWeight) < 0) {
            return "REDUCE";
        }
        if ("HOLD".equalsIgnoreCase(action.getAction())) {
            return "HOLD";
        }
        if (!"BUY".equalsIgnoreCase(action.getAction())
                || !Objects.equals(action.getExecutableHint(), 1)
                || targetWeight.compareTo(currentWeight) <= 0) {
            return "WATCH";
        }
        return currentWeight.signum() > 0 ? "ADD" : "BUY";
    }

    private Integer calculateAdviceQuantity(String action, BigDecimal currentWeight, BigDecimal targetWeight,
                                            BigDecimal totalEquity, BigDecimal referencePrice,
                                            DecisionPortfolioHolding holding) {
        if ("SELL".equals(action)) {
            return Objects.nonNull(holding) && Objects.nonNull(holding.getQuantity()) ? holding.getQuantity() : 0;
        }
        if ("REDUCE".equals(action)) {
            if (Objects.isNull(totalEquity) || Objects.isNull(referencePrice) || referencePrice.signum() <= 0
                    || Objects.isNull(holding) || Objects.isNull(holding.getQuantity())) {
                return 0;
            }
            int targetQuantity = targetWeight.multiply(totalEquity)
                    .divide(referencePrice.multiply(new BigDecimal("100")), 0, RoundingMode.DOWN)
                    .multiply(new BigDecimal("100")).intValue();
            return Math.max(holding.getQuantity() - targetQuantity, 0);
        }
        if (!"BUY".equals(action) && !"ADD".equals(action)) {
            return 0;
        }
        if (Objects.isNull(totalEquity) || Objects.isNull(referencePrice) || referencePrice.signum() <= 0) {
            return 0;
        }
        BigDecimal amount = targetWeight.subtract(currentWeight).max(BigDecimal.ZERO).multiply(totalEquity);
        return amount.divide(referencePrice.multiply(new BigDecimal("100")), 0, RoundingMode.DOWN)
                .multiply(new BigDecimal("100")).intValue();
    }

    private String buildLocalAdviceSummary(DecisionPortfolioSnapshot snapshot,
                                           List<DecisionAdviceActionResp> actions,
                                           BigDecimal targetExposure) {
        int executableCount = 0;
        int watchCount = 0;
        for (DecisionAdviceActionResp action : actions) {
            if (Boolean.TRUE.equals(action.getExecutable())) {
                executableCount++;
            } else if ("WATCH".equals(action.getAction())) {
                watchCount++;
            }
        }
        return "市场状态" + nullToEmpty(snapshot.getMarketRegime())
                + "，当前仓位" + pctText(snapshot.getExposureRatio())
                + "，建议调整至" + pctText(targetExposure)
                + "；按优先级执行" + executableCount + "项，观察" + watchCount
                + "项。单笔按止损距离控制风险，组合回撤达到12%停止新仓，15%仅减仓。";
    }

    private String enhanceAdviceSummary(DecisionPortfolioSnapshot snapshot,
                                        List<DecisionAdviceActionResp> actions,
                                        String localSummary) {
        if (!kimiChatClient.available()) {
            return null;
        }
        String system = "你是A股组合决策解释器。只能解释给定的冻结动作，不得修改动作、仓位、数量、价格、止损止盈或风险门禁。"
                + "只输出一段120到220字中文纯文本，不要Markdown，不得补充未提供的新闻或财务事实。";
        StringBuilder user = new StringBuilder();
        user.append("本地结论：").append(localSummary).append('\n')
                .append("市场状态：").append(snapshot.getMarketRegime())
                .append("；原因：").append(snapshot.getRegimeReason()).append('\n')
                .append("动作：\n");
        int limit = Math.min(actions.size(), 12);
        for (int index = 0; index < limit; index++) {
            DecisionAdviceActionResp action = actions.get(index);
            user.append(action.getPriority()).append('.')
                    .append(action.getCode()).append(' ')
                    .append(action.getAction()).append(" 当前=")
                    .append(action.getCurrentWeight()).append(" 目标=")
                    .append(action.getTargetWeight()).append(" 数量=")
                    .append(action.getQuantity()).append('\n');
        }
        return kimiChatClient.chat(system, user.toString(), 450);
    }

    /**
     * 决策历史 + 买入建议事后次日收益
     *
     * @param limit 天数
     * @return 历史
     */
    @Override
    public List<DecisionHistoryItem> history(Integer limit) {
        int size = Objects.isNull(limit) || limit <= 0 ? 15 : Math.min(limit, 60);
        // 1. 先取近 N 个不重复 action_date，避免 LIMIT 500 全表扫
        List<DailyAction> dateRows = dailyActionMapper.selectList(Wrappers.<DailyAction>lambdaQuery()
                .select(DailyAction::getActionDate)
                .eq(DailyAction::getUserId, userContext.currentUserId())
                .isNotNull(DailyAction::getActionDate)
                .groupBy(DailyAction::getActionDate)
                .orderByDesc(DailyAction::getActionDate)
                .last("LIMIT " + size));
        if (CollUtil.isEmpty(dateRows)) {
            return List.of();
        }
        List<LocalDate> dates = new ArrayList<>();
        for (DailyAction row : dateRows) {
            if (Objects.nonNull(row.getActionDate())) {
                dates.add(row.getActionDate());
            }
        }
        if (CollUtil.isEmpty(dates)) {
            return List.of();
        }
        // 2. 按日期 in 查询明细
        List<DailyAction> recent = dailyActionMapper.selectList(Wrappers.<DailyAction>lambdaQuery()
                .eq(DailyAction::getUserId, userContext.currentUserId())
                .in(DailyAction::getActionDate, dates)
                .orderByDesc(DailyAction::getActionDate));
        LinkedHashMap<LocalDate, List<DailyAction>> byDate = new LinkedHashMap<>();
        for (LocalDate day : dates) {
            byDate.put(day, new ArrayList<>());
        }
        for (DailyAction row : recent) {
            if (Objects.isNull(row.getActionDate())) {
                continue;
            }
            List<DailyAction> bucket = byDate.get(row.getActionDate());
            if (Objects.nonNull(bucket)) {
                bucket.add(row);
            }
        }
        List<DecisionHistoryItem> out = new ArrayList<>();
        int dayN = 0;
        for (Map.Entry<LocalDate, List<DailyAction>> entry : byDate.entrySet()) {
            if (dayN >= size) {
                break;
            }
            dayN++;
            LocalDate day = entry.getKey();
            List<DailyAction> rows = entry.getValue();
            int buy = 0;
            int sell = 0;
            int hold = 0;
            int executable = 0;
            int cheap = 0;
            int fair = 0;
            int rich = 0;
            int mainline = 0;
            List<String> buyCodes = new ArrayList<>();
            for (DailyAction row : rows) {
                if ("BUY".equalsIgnoreCase(row.getAction())) {
                    buy++;
                    if (StringUtils.isNotBlank(row.getCode())) {
                        buyCodes.add(row.getCode());
                    }
                } else if ("SELL".equalsIgnoreCase(row.getAction())
                        || "REDUCE".equalsIgnoreCase(row.getAction())) {
                    sell++;
                } else {
                    hold++;
                }
                if (Objects.nonNull(row.getExecutableHint()) && row.getExecutableHint() == 1) {
                    executable++;
                }
                if (Objects.nonNull(row.getMainlineMatch()) && row.getMainlineMatch() == 1) {
                    mainline++;
                }
                String level = row.getValuationLevel();
                if ("UNDERVALUED".equals(level) || "SLIGHTLY_CHEAP".equals(level)) {
                    cheap++;
                } else if ("FAIR".equals(level)) {
                    fair++;
                } else if ("OVERVALUED".equals(level) || "SLIGHTLY_EXPENSIVE".equals(level)) {
                    rich++;
                }
            }
            BigDecimal avg = avgNextDayPct(buyCodes, day);
            MarketBriefingSnapshot snap = null;
            try {
                snap = marketBriefingSnapshotMapper.selectOne(Wrappers.<MarketBriefingSnapshot>lambdaQuery()
                        .eq(MarketBriefingSnapshot::getTradeDate, day)
                        .last("LIMIT 1"));
            } catch (Exception ignored) {
                // 表未建时忽略
            }
            out.add(DecisionHistoryItem.builder()
                    .actionDate(day)
                    .buyCount(buy)
                    .sellCount(sell)
                    .holdCount(hold)
                    .executableCount(executable)
                    .valuationCheapCount(cheap)
                    .valuationFairCount(fair)
                    .valuationRichCount(rich)
                    .mainlineMatchCount(mainline)
                    .nextDayAvgPct(avg)
                    .stance(Objects.nonNull(snap) ? snap.getStance() : null)
                    .dataLevel(Objects.nonNull(snap) ? snap.getDataLevel() : null)
                    .note(Objects.nonNull(avg)
                            ? ("买入建议次日均涨跌 " + avg + "%")
                            : "暂无次日收益（缺日线或未到下一交易日）")
                    .build());
        }
        return out;
    }

    /**
     * 决策复盘归因（按策略/共振/主线/立场）
     *
     * @param days 回溯天数
     * @return 归因
     */
    @Override
    public DecisionAttributionResp attribution(Integer days) {
        int size = Objects.isNull(days) || days <= 0 ? 20 : Math.min(days, 60);
        List<DailyAction> dateRows = dailyActionMapper.selectList(Wrappers.<DailyAction>lambdaQuery()
                .select(DailyAction::getActionDate)
                .eq(DailyAction::getUserId, userContext.currentUserId())
                .isNotNull(DailyAction::getActionDate)
                .groupBy(DailyAction::getActionDate)
                .orderByDesc(DailyAction::getActionDate)
                .last("LIMIT " + size));
        if (CollUtil.isEmpty(dateRows)) {
            return DecisionAttributionResp.builder()
                    .days(size)
                    .byStrategy(List.of())
                    .byConfluence(List.of())
                    .byMainline(List.of())
                    .byStance(List.of())
                    .bySellStrategy(List.of())
                    .matureStrategyPerformance(List.of())
                    .message("暂无决策记录，请先启动后台生成决策")
                    .build();
        }
        List<LocalDate> dates = new ArrayList<>();
        for (DailyAction row : dateRows) {
            if (Objects.nonNull(row.getActionDate())) {
                dates.add(row.getActionDate());
            }
        }
        List<DailyAction> buys = dailyActionMapper.selectList(Wrappers.<DailyAction>lambdaQuery()
                .eq(DailyAction::getUserId, userContext.currentUserId())
                .in(DailyAction::getActionDate, dates)
                .eq(DailyAction::getAction, "BUY"));
        List<DailyAction> sells = dailyActionMapper.selectList(Wrappers.<DailyAction>lambdaQuery()
                .eq(DailyAction::getUserId, userContext.currentUserId())
                .in(DailyAction::getActionDate, dates)
                .in(DailyAction::getAction, List.of("SELL", "REDUCE")));

        Map<String, List<BigDecimal>> byStrategy = new HashMap<>();
        Map<String, List<BigDecimal>> byCf = new HashMap<>();
        Map<String, List<BigDecimal>> byMl = new HashMap<>();
        Map<String, List<BigDecimal>> byStance = new HashMap<>();
        Map<String, List<BigDecimal>> bySellStrategy = new HashMap<>();
        Map<LocalDate, String> stanceByDate = new HashMap<>();
        for (LocalDate day : dates) {
            try {
                MarketBriefingSnapshot snap = marketBriefingSnapshotMapper.selectOne(
                        Wrappers.<MarketBriefingSnapshot>lambdaQuery()
                                .eq(MarketBriefingSnapshot::getTradeDate, day)
                                .last("LIMIT 1"));
                if (Objects.nonNull(snap) && StringUtils.isNotBlank(snap.getStance())) {
                    stanceByDate.put(day, snap.getStance());
                }
            } catch (Exception ignored) {
                // ignore
            }
        }

        for (DailyAction row : buys) {
            BigDecimal nextPct = singleNextDayPct(row.getCode(), row.getActionDate());
            String sid = StringUtils.isNotBlank(row.getStrategyId()) ? row.getStrategyId() : "未知";
            putPct(byStrategy, sid, nextPct);
            boolean cf = Objects.nonNull(row.getConfluenceCount()) && row.getConfluenceCount() >= 2;
            putPct(byCf, cf ? "共振" : "非共振", nextPct);
            boolean ml;
            if (Objects.nonNull(row.getMainlineMatch())) {
                ml = row.getMainlineMatch() == 1;
            } else {
                ml = StringUtils.isNotBlank(row.getReason())
                        && (row.getReason().contains("主线「") || row.getReason().contains("主线同向"));
            }
            putPct(byMl, ml ? "主线同向" : "非主线", nextPct);
            String stance = stanceByDate.getOrDefault(row.getActionDate(), "未知立场");
            putPct(byStance, stance, nextPct);
        }
        for (DailyAction row : sells) {
            BigDecimal nextPct = singleNextDayPct(row.getCode(), row.getActionDate());
            String sid = StringUtils.isNotBlank(row.getStrategyId()) ? row.getStrategyId() : "风控/未知";
            putPct(bySellStrategy, sid, nextPct);
        }

        return DecisionAttributionResp.builder()
                .days(dates.size())
                .byStrategy(toBuckets(byStrategy))
                .byConfluence(toBuckets(byCf))
                .byMainline(toBuckets(byMl))
                .byStance(toBuckets(byStance))
                .bySellStrategy(toBuckets(bySellStrategy))
                .matureStrategyPerformance(decisionOutcomeMapper.selectStrategyPerformance(userContext.currentUserId()))
                .message("近 " + dates.size() + " 个决策日 · 买 " + buys.size()
                        + " / 卖 " + sells.size() + " · 按次日涨跌归因（缺日线样本不计入均值）")
                .build();
    }

    /**
     * 建议买入清单的 AI 详细总结
     *
     * @param date      决策日
     * @param groupName 分组
     * @param force     强制刷新
     * @return AI 总结
     */
    @Override
    public DecisionBuyAiResp buyAiSummary(LocalDate date, String groupName, Boolean force) {
        DecisionTodayResp today = today(date, groupName);
        List<DecisionItemResp> buys = Objects.nonNull(today.getBuys()) ? today.getBuys() : List.of();
        LocalDate actionDate = Objects.nonNull(today.getActionDate()) ? today.getActionDate() : LocalDate.now();
        if (CollUtil.isEmpty(buys)) {
            return DecisionBuyAiResp.builder()
                    .configured(kimiChatClient.available())
                    .fromCache(false)
                    .actionDate(actionDate)
                    .buyCount(0)
                    .summary("当前无建议买入标的，暂不生成 AI 总结。请先启动后台生成决策。")
                    .watchPoints(List.of())
                    .stockNotes(List.of())
                    .disclaimer(BUY_AI_DISCLAIMER)
                    .generatedAt(LocalDateTime.now())
                    .build();
        }
        if (!kimiChatClient.available()) {
            return DecisionBuyAiResp.builder()
                    .configured(false)
                    .fromCache(false)
                    .actionDate(actionDate)
                    .buyCount(buys.size())
                    .summary("未配置 apex.ai.api-key，AI 总结不可用。可先查看下方规则评分与理由。")
                    .watchPoints(List.of())
                    .stockNotes(List.of())
                    .disclaimer(BUY_AI_DISCLAIMER)
                    .generatedAt(LocalDateTime.now())
                    .build();
        }

        String cacheKey = buildBuyAiCacheKey(actionDate, buys);
        boolean forceRefresh = Boolean.TRUE.equals(force);
        if (!forceRefresh) {
            DecisionBuyAiResp hit = redisCacheService.get(cacheKey, DecisionBuyAiResp.class);
            if (Objects.nonNull(hit) && StringUtils.isNotBlank(hit.getSummary())) {
                hit.setFromCache(true);
                hit.setConfigured(true);
                return hit;
            }
        }

        MarketBriefingResp briefing = today.getMarketBriefing();
        String system = "你是 A 股交易决策助手。只根据给定的「建议买入」结构化清单做详细总结，禁止编造未提供的财报、公告或新闻。"
                + "输出严格 JSON（不要 markdown）："
                + "{\"stance\":\"可分批试探|精选跟踪|均衡配置|暂缓进攻|防守观望\","
                + "\"summary\":\"200-350字中文详细总结，覆盖市场环境与买入清单结构、主线/估值/风险取舍\","
                + "\"watchPoints\":[\"关注点1\",\"关注点2\",\"关注点3\"],"
                + "\"stockNotes\":[{\"code\":\"代码\",\"name\":\"简称\",\"note\":\"一句话点评\",\"priority\":\"高|中|低|观望\"}],"
                + "\"riskNote\":\"一句话风险\"}."
                + "stockNotes 最多覆盖清单前 8 只，按优先度排序；语气专业克制，强调规则评分与仓位纪律。";

        StringBuilder user = new StringBuilder();
        user.append("决策日：").append(actionDate).append('\n');
        if (StringUtils.isNotBlank(today.getGroupName())) {
            user.append("自选分组：").append(today.getGroupName()).append('\n');
        }
        if (Objects.nonNull(briefing)) {
            user.append("市场立场：").append(briefing.getStance())
                    .append(" 得分：").append(briefing.getStanceScore())
                    .append(" 仓位系数：").append(briefing.getBuyWeightFactor()).append('\n');
            if (StringUtils.isNotBlank(briefing.getStanceReason())) {
                user.append("立场理由：").append(briefing.getStanceReason()).append('\n');
            }
            if (StringUtils.isNotBlank(briefing.getPositionAdvice())) {
                user.append("仓位建议：").append(briefing.getPositionAdvice()).append('\n');
            }
            if (CollUtil.isNotEmpty(briefing.getHotThemes())) {
                user.append("主线题材：").append(String.join("、", briefing.getHotThemes())).append('\n');
            }
        }
        user.append("买入条数：").append(buys.size())
                .append(" 可执行：").append(today.getExecutableCount())
                .append(" 低估：").append(today.getValuationCheapCount())
                .append(" 主线匹配：").append(today.getMainlineMatchCount()).append('\n');
        user.append("清单明细：\n");
        int limit = Math.min(buys.size(), 12);
        for (int i = 0; i < limit; i++) {
            DecisionItemResp item = buys.get(i);
            user.append(i + 1).append(". ")
                    .append(item.getCode()).append(' ')
                    .append(nullToEmpty(item.getName()))
                    .append(" 策略=").append(nullToEmpty(item.getStrategyId()))
                    .append(" 评分=").append(item.getScore())
                    .append(" 仓位=").append(item.getSuggestedWeight())
                    .append(" 估值=").append(nullToEmpty(item.getValuationLabel()))
                    .append(" 主线=").append(Boolean.TRUE.equals(item.getMainlineMatch())
                            ? nullToEmpty(item.getMainlineName()) : "否")
                    .append(" 可执行=").append(Boolean.TRUE.equals(item.getExecutableHint()))
                    .append('\n');
            if (StringUtils.isNotBlank(item.getScoreExplain())) {
                user.append("   拆解：").append(item.getScoreExplain()).append('\n');
            }
            if (StringUtils.isNotBlank(item.getReason())) {
                user.append("   理由：").append(item.getReason()).append('\n');
            }
            if (StringUtils.isNotBlank(item.getLinkHint())) {
                user.append("   联动：").append(item.getLinkHint()).append('\n');
            }
            if (CollUtil.isNotEmpty(item.getRiskFlags())) {
                user.append("   风险：").append(String.join("、", item.getRiskFlags())).append('\n');
            }
        }
        user.append("请结合以上事实给出 JSON 详细总结。");

        String raw = kimiChatClient.chat(system, user.toString(), 1200);
        DecisionBuyAiResp ai = parseBuyAiPayload(raw);
        ai.setConfigured(true);
        ai.setFromCache(false);
        ai.setModel(aiChatProperties.getModel());
        ai.setActionDate(actionDate);
        ai.setBuyCount(buys.size());
        ai.setGeneratedAt(LocalDateTime.now());
        ai.setDisclaimer(BUY_AI_DISCLAIMER);
        if (StringUtils.isBlank(ai.getSummary())) {
            ai.setSummary("大模型暂无有效输出，请稍后重试；可先阅读清单中的评分拆解与理由。");
            ai.setStance(Objects.nonNull(briefing) ? briefing.getStance() : "均衡配置");
        }
        // 补全未返回的 code/name
        if (CollUtil.isNotEmpty(ai.getStockNotes())) {
            Map<String, DecisionItemResp> buyMap = new HashMap<>();
            for (DecisionItemResp item : buys) {
                if (StringUtils.isNotBlank(item.getCode())) {
                    buyMap.put(item.getCode(), item);
                }
            }
            for (DecisionBuyAiStockNote note : ai.getStockNotes()) {
                if (Objects.isNull(note) || StringUtils.isBlank(note.getCode())) {
                    continue;
                }
                DecisionItemResp src = buyMap.get(note.getCode());
                if (Objects.nonNull(src) && StringUtils.isBlank(note.getName())) {
                    note.setName(src.getName());
                }
            }
        }
        redisCacheService.put(cacheKey, ai, Duration.ofSeconds(Math.max(60, aiChatProperties.getSummaryCacheSeconds())));
        log.info("决策买入AI总结完成，日期={}，买入数量={}，是否来自缓存=false", actionDate, buys.size());
        return ai;
    }

    private String buildBuyAiCacheKey(LocalDate actionDate, List<DecisionItemResp> buys) {
        StringBuilder sb = new StringBuilder();
        sb.append(BUY_AI_CACHE_PREFIX).append(userContext.currentUserId()).append('|')
                .append(actionDate).append('|');
        int n = Math.min(buys.size(), 12);
        for (int i = 0; i < n; i++) {
            DecisionItemResp item = buys.get(i);
            sb.append(item.getCode()).append(':')
                    .append(item.getScore()).append(':')
                    .append(item.getStrategyId()).append(';');
        }
        return sb.toString();
    }

    private DecisionBuyAiResp parseBuyAiPayload(String raw) {
        DecisionBuyAiResp empty = DecisionBuyAiResp.builder()
                .watchPoints(List.of())
                .stockNotes(List.of())
                .build();
        if (StringUtils.isBlank(raw)) {
            return empty;
        }
        String text = raw.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return DecisionBuyAiResp.builder().summary(text).watchPoints(List.of()).stockNotes(List.of()).build();
        }
        try {
            JsonNode root = JsonUtils.getObjectMapper().readTree(text.substring(start, end + 1));
            List<String> points = new ArrayList<>();
            JsonNode wp = root.get("watchPoints");
            if (Objects.nonNull(wp) && wp.isArray()) {
                for (JsonNode node : wp) {
                    if (points.size() >= 5) {
                        break;
                    }
                    if (Objects.nonNull(node) && StringUtils.isNotBlank(node.asText())) {
                        points.add(node.asText().trim());
                    }
                }
            }
            List<DecisionBuyAiStockNote> notes = new ArrayList<>();
            JsonNode sn = root.get("stockNotes");
            if (Objects.nonNull(sn) && sn.isArray()) {
                for (JsonNode node : sn) {
                    if (notes.size() >= 8 || Objects.isNull(node) || !node.isObject()) {
                        continue;
                    }
                    String code = node.has("code") ? node.get("code").asText() : null;
                    if (StringUtils.isBlank(code)) {
                        continue;
                    }
                    notes.add(DecisionBuyAiStockNote.builder()
                            .code(code.trim())
                            .name(node.has("name") ? node.get("name").asText() : null)
                            .note(node.has("note") ? node.get("note").asText() : null)
                            .priority(node.has("priority") ? node.get("priority").asText() : null)
                            .build());
                }
            }
            return DecisionBuyAiResp.builder()
                    .stance(root.has("stance") ? root.get("stance").asText() : null)
                    .summary(root.has("summary") ? root.get("summary").asText() : null)
                    .watchPoints(points)
                    .stockNotes(notes)
                    .riskNote(root.has("riskNote") ? root.get("riskNote").asText() : null)
                    .build();
        } catch (Exception ex) {
            log.debug("买入AI JSON 解析失败，回退纯文本: {}", ex.getMessage());
            return DecisionBuyAiResp.builder().summary(text).watchPoints(List.of()).stockNotes(List.of()).build();
        }
    }

    private List<String> parseStrategiesCsv(String csv) {
        if (StringUtils.isBlank(csv)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : csv.split(",")) {
            if (StringUtils.isNotBlank(part)) {
                out.add(part.trim());
            }
        }
        return out;
    }

    private void putPct(Map<String, List<BigDecimal>> map, String key, BigDecimal pct) {
        map.computeIfAbsent(key, k -> new ArrayList<>()).add(pct);
    }

    private List<DecisionAttrBucket> toBuckets(Map<String, List<BigDecimal>> map) {
        List<DecisionAttrBucket> list = new ArrayList<>();
        for (Map.Entry<String, List<BigDecimal>> e : map.entrySet()) {
            List<BigDecimal> all = e.getValue();
            List<BigDecimal> measured = new ArrayList<>();
            int wins = 0;
            for (BigDecimal p : all) {
                if (Objects.nonNull(p)) {
                    measured.add(p);
                    if (p.compareTo(BigDecimal.ZERO) > 0) {
                        wins++;
                    }
                }
            }
            BigDecimal avg = null;
            BigDecimal winRate = null;
            if (!measured.isEmpty()) {
                BigDecimal sum = BigDecimal.ZERO;
                for (BigDecimal p : measured) {
                    sum = sum.add(p);
                }
                avg = sum.divide(BigDecimal.valueOf(measured.size()), 2, RoundingMode.HALF_UP);
                winRate = BigDecimal.valueOf(wins * 100.0 / measured.size())
                        .setScale(1, RoundingMode.HALF_UP);
            }
            list.add(DecisionAttrBucket.builder()
                    .key(e.getKey())
                    .label(e.getKey())
                    .sampleCount(all.size())
                    .measuredCount(measured.size())
                    .avgNextPct(avg)
                    .winRate(winRate)
                    .build());
        }
        list.sort(Comparator.comparing(DecisionAttrBucket::getSampleCount,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return list;
    }

    private BigDecimal singleNextDayPct(String code, LocalDate actionDate) {
        if (StringUtils.isBlank(code) || Objects.isNull(actionDate)) {
            return null;
        }
        LocalDate nextTrade = TradingCalendar.nextTradingDay(actionDate);
        BarDaily next = barDailyMapper.selectOne(Wrappers.<BarDaily>lambdaQuery()
                .eq(BarDaily::getCode, code)
                .eq(BarDaily::getTradeDate, nextTrade)
                .last("LIMIT 1"));
        if (Objects.isNull(next)) {
            next = barDailyMapper.selectOne(Wrappers.<BarDaily>lambdaQuery()
                    .eq(BarDaily::getCode, code)
                    .gt(BarDaily::getTradeDate, actionDate)
                    .orderByAsc(BarDaily::getTradeDate)
                    .last("LIMIT 1"));
        }
        return Objects.nonNull(next) ? next.getPctChg() : null;
    }

    private BigDecimal avgNextDayPct(List<String> codes, LocalDate actionDate) {
        if (CollUtil.isEmpty(codes) || Objects.isNull(actionDate)) {
            return null;
        }
        LocalDate nextTrade = TradingCalendar.nextTradingDay(actionDate);
        List<BarDaily> bars = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                .in(BarDaily::getCode, codes)
                .eq(BarDaily::getTradeDate, nextTrade));
        if (CollUtil.isEmpty(bars)) {
            // 无精确下一交易日时，取 actionDate 之后最近一根
            bars = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                    .in(BarDaily::getCode, codes)
                    .gt(BarDaily::getTradeDate, actionDate)
                    .orderByAsc(BarDaily::getTradeDate)
                    .last("LIMIT " + Math.min(codes.size() * 3, 200)));
            Map<String, BarDaily> first = new HashMap<>();
            for (BarDaily bar : bars) {
                first.putIfAbsent(bar.getCode(), bar);
            }
            bars = new ArrayList<>(first.values());
        }
        BigDecimal sum = BigDecimal.ZERO;
        int n = 0;
        for (BarDaily bar : bars) {
            if (Objects.nonNull(bar.getPctChg())) {
                sum = sum.add(bar.getPctChg());
                n++;
            }
        }
        if (n == 0) {
            return null;
        }
        return sum.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
    }

    /**
     * 观察池买入候选：近端涨幅过大则降权并标注「勿追高」；低估+S2 再提权
     */
    private DecisionItemResp toObserveBuyCandidate(DecisionItemResp item, StockBasic basic) {
        BigDecimal score = Objects.nonNull(item.getScore()) ? item.getScore() : new BigDecimal("50");
        String reason = nullToEmpty(item.getReason());
        String explain = nullToEmpty(item.getScoreExplain());
        List<String> riskFlags = Objects.nonNull(item.getRiskFlags())
                ? new ArrayList<>(item.getRiskFlags()) : new ArrayList<>();
        Boolean executableHint = item.getExecutableHint();
        BigDecimal pct = Objects.nonNull(basic) ? basic.getPctChg() : null;
        if (Objects.nonNull(pct) && pct.compareTo(new BigDecimal("5")) >= 0) {
            score = score.subtract(new BigDecimal("12")).max(new BigDecimal("42"));
            reason = trimReason(reason + " · 今日涨幅偏大勿追高，等回踩再评估");
            explain = trimReason(explain + " · 近端强势降权，避免买在短期高点");
            riskFlags.add("勿追高·等回踩");
            executableHint = false;
        }
        if ("S3".equalsIgnoreCase(item.getStrategyId())
                && Objects.nonNull(pct) && pct.compareTo(new BigDecimal("3")) >= 0) {
            score = score.subtract(new BigDecimal("6")).max(new BigDecimal("40"));
            reason = trimReason(reason + " · 突破后已走高，优先观察回踩确认");
            explain = trimReason(explain + " · S3突破不追高");
            riskFlags.add("S3突破不追高");
            executableHint = false;
        }
        if ("S2".equalsIgnoreCase(item.getStrategyId())) {
            score = score.add(new BigDecimal("4"));
            explain = trimReason(explain + " · S2回调类优先于追涨");
        }
        // 低估 + S2：观察池配额排序再提权
        boolean cheap = "UNDERVALUED".equals(item.getValuationLevel())
                || "SLIGHTLY_CHEAP".equals(item.getValuationLevel());
        if (cheap && "S2".equalsIgnoreCase(item.getStrategyId())) {
            score = score.add(new BigDecimal("3"));
            explain = trimReason(explain + " · 低估回调优先入池");
        }
        // 高估 + S3：禁止可执行
        boolean rich = "OVERVALUED".equals(item.getValuationLevel())
                || "SLIGHTLY_EXPENSIVE".equals(item.getValuationLevel());
        if (rich && "S3".equalsIgnoreCase(item.getStrategyId())) {
            executableHint = false;
            if (!riskFlags.contains("高估突破降权")) {
                riskFlags.add("高估突破降权");
            }
        }
        return DecisionItemResp.builder()
                .actionDate(item.getActionDate())
                .code(item.getCode())
                .name(item.getName())
                .action(item.getAction())
                .strategyId(item.getStrategyId())
                .reason(reason)
                .score(score)
                .suggestedWeight(item.getSuggestedWeight())
                .exitRule(item.getExitRule())
                .confluenceCount(item.getConfluenceCount())
                .confluence(item.getConfluence())
                .strategies(item.getStrategies())
                .fundNote(item.getFundNote())
                .signalId(item.getSignalId())
                .mainlineMatch(item.getMainlineMatch())
                .mainlineName(item.getMainlineName())
                .scoreExplain(explain)
                .valuationLevel(item.getValuationLevel())
                .valuationLabel(item.getValuationLabel())
                .valuationScore(item.getValuationScore())
                .valuationSummary(item.getValuationSummary())
                .riskFlags(riskFlags)
                .executableHint(executableHint)
                .linkHint(item.getLinkHint())
                .build();
    }

    /**
     * 观察池配额：情绪与买入分开；买入侧限制突破追涨占比，保证回调/趋势有位子
     */
    private List<DecisionItemResp> balanceObserveCandidates(List<DecisionItemResp> raw,
                                                            Map<String, StockBasic> basicMap) {
        if (CollUtil.isEmpty(raw)) {
            return List.of();
        }
        List<DecisionItemResp> moods = new ArrayList<>();
        List<DecisionItemResp> s2 = new ArrayList<>();
        List<DecisionItemResp> s1 = new ArrayList<>();
        List<DecisionItemResp> s3 = new ArrayList<>();
        List<DecisionItemResp> other = new ArrayList<>();
        for (DecisionItemResp item : raw) {
            if (Objects.isNull(item)) {
                continue;
            }
            if ("MOOD".equalsIgnoreCase(item.getAction()) || "MOOD".equalsIgnoreCase(item.getStrategyId())) {
                moods.add(item);
                continue;
            }
            String sid = StringUtils.isNotBlank(item.getStrategyId()) ? item.getStrategyId().toUpperCase() : "";
            if ("S2".equals(sid)) {
                s2.add(item);
            } else if ("S1".equals(sid)) {
                s1.add(item);
            } else if ("S3".equals(sid)) {
                s3.add(item);
            } else {
                other.add(item);
            }
        }
        Comparator<DecisionItemResp> byScore = Comparator.comparing(
                (DecisionItemResp x) -> Objects.nonNull(x.getScore()) ? x.getScore() : BigDecimal.ZERO).reversed();
        // 低估/偏低 + S2 优先进配额
        Comparator<DecisionItemResp> byS2Priority = Comparator
                .<DecisionItemResp>comparingInt(x -> {
                    String lv = x.getValuationLevel();
                    if ("UNDERVALUED".equals(lv) || "SLIGHTLY_CHEAP".equals(lv)) {
                        return 0;
                    }
                    return 1;
                })
                .thenComparing(byScore);
        s2.sort(byS2Priority);
        s1.sort(byScore);
        s3.sort(byScore);
        other.sort(byScore);
        moods.sort(byScore);

        List<DecisionItemResp> buys = new ArrayList<>();
        // 优先回调(S2，低估优先) → 趋势(S1) → 其它 → 突破(S3，且过滤极端追高)
        for (DecisionItemResp item : s2) {
            if (buys.size() >= 12) {
                break;
            }
            buys.add(item);
        }
        for (DecisionItemResp item : s1) {
            if (buys.size() >= 20) {
                break;
            }
            buys.add(item);
        }
        for (DecisionItemResp item : other) {
            if (buys.size() >= 24) {
                break;
            }
            buys.add(item);
        }
        int s3Added = 0;
        for (DecisionItemResp item : s3) {
            if (buys.size() >= 30 || s3Added >= 8) {
                break;
            }
            StockBasic basic = basicMap.get(item.getCode());
            BigDecimal pct = Objects.nonNull(basic) ? basic.getPctChg() : null;
            // 今日大涨的突破票：仍可观察，但名额更紧
            if (Objects.nonNull(pct) && pct.compareTo(new BigDecimal("7")) >= 0 && s3Added >= 3) {
                continue;
            }
            buys.add(item);
            s3Added++;
        }
        List<DecisionItemResp> out = new ArrayList<>(buys);
        int moodCap = 15;
        for (DecisionItemResp item : moods) {
            if (moodCap-- <= 0) {
                break;
            }
            out.add(item);
        }
        log.info("观察池候选再平衡，买入候选数={}（第二阶段优先），情绪候选数={}",
                buys.size(), Math.min(moods.size(), 15));
        return out;
    }

    private String nullToEmpty(String s) {
        return StringUtils.isNotBlank(s) ? s : "";
    }

    private StockBasic resolveBasic(String code, Map<String, StockBasic> basicMap) {
        StockBasic basic = basicMap.get(code);
        if (Objects.nonNull(basic)) {
            return basic;
        }
        return stockBasicMapper.selectOne(Wrappers.<StockBasic>lambdaQuery()
                .eq(StockBasic::getCode, code)
                .last("LIMIT 1"));
    }

    private boolean usableBasic(StockBasic basic) {
        if (Objects.isNull(basic) || Objects.isNull(basic.getLatestPrice()) || basic.getLatestPrice().signum() <= 0) {
            return false;
        }
        return Objects.isNull(basic.getStFlag()) || basic.getStFlag() != 1;
    }

    private Map<String, StockBasic> loadBasics(Set<String> codes) {
        Map<String, StockBasic> map = new HashMap<>();
        if (CollUtil.isEmpty(codes)) {
            return map;
        }
        List<StockBasic> list = stockBasicMapper.selectList(Wrappers.<StockBasic>lambdaQuery()
                .in(StockBasic::getCode, codes));
        for (StockBasic basic : list) {
            map.put(basic.getCode(), basic);
        }
        return map;
    }

    void applyReplayPrices(Map<String, StockBasic> basicMap, LocalDate actionDate) {
        if (basicMap.isEmpty()) {
            return;
        }
        for (StockBasic basic : basicMap.values()) {
            basic.setLatestPrice(null);
            basic.setPctChg(null);
            basic.setPeTtm(null);
            basic.setPb(null);
            basic.setCircMv(null);
            basic.setIndustry(null);
            basic.setStFlag(null);
        }
        List<BarDaily> bars = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                .in(BarDaily::getCode, basicMap.keySet())
                .eq(BarDaily::getTradeDate, actionDate));
        for (BarDaily bar : bars) {
            StockBasic basic = basicMap.get(bar.getCode());
            if (Objects.nonNull(basic)) {
                basic.setLatestPrice(bar.getClosePrice());
                basic.setPctChg(bar.getPctChg());
            }
        }
    }

    private Map<String, FundSnapshot> loadFunds(Set<String> codes) {
        Map<String, FundSnapshot> map = new HashMap<>();
        if (CollUtil.isEmpty(codes)) {
            return map;
        }
        List<String> codeList = new ArrayList<>(codes);
        // 1. 批量拉摘要，内存取每 code 最新报告期
        List<StockFinAbstract> abstracts = stockFinAbstractMapper.selectList(Wrappers.<StockFinAbstract>lambdaQuery()
                .in(StockFinAbstract::getCode, codeList)
                .orderByDesc(StockFinAbstract::getReportDate));
        Map<String, StockFinAbstract> latestAbs = new HashMap<>();
        for (StockFinAbstract row : abstracts) {
            if (StringUtils.isBlank(row.getCode())) {
                continue;
            }
            latestAbs.putIfAbsent(row.getCode(), row);
        }
        Set<String> missing = new HashSet<>();
        for (String code : codeList) {
            StockFinAbstract abs = latestAbs.get(code);
            if (Objects.nonNull(abs)) {
                map.put(code, new FundSnapshot(abs.getReportDate(), abs.getRoe(), abs.getDebtRatio(), abs.getNetMargin()));
            } else {
                missing.add(code);
            }
        }
        if (CollUtil.isEmpty(missing)) {
            return map;
        }
        // 2. 摘要缺失时用指标表兜底
        List<StockFinIndicator> indicators = stockFinIndicatorMapper.selectList(Wrappers.<StockFinIndicator>lambdaQuery()
                .in(StockFinIndicator::getCode, missing)
                .orderByDesc(StockFinIndicator::getReportDate));
        Map<String, StockFinIndicator> latestInd = new HashMap<>();
        for (StockFinIndicator row : indicators) {
            if (StringUtils.isBlank(row.getCode())) {
                continue;
            }
            latestInd.putIfAbsent(row.getCode(), row);
        }
        for (String code : missing) {
            StockFinIndicator ind = latestInd.get(code);
            if (Objects.nonNull(ind)) {
                map.put(code, new FundSnapshot(ind.getReportDate(), ind.getRoe(), ind.getDebtRatio(), ind.getNetMargin()));
            }
        }
        return map;
    }

    private FundGate evaluateFund(FundSnapshot fund) {
        FundGate gate = new FundGate();
        if (Objects.isNull(fund)) {
            gate.note = null;
            return gate;
        }
        gate.note = fundNoteOf(fund);
        if (Objects.nonNull(fund.roe) && fund.roe.compareTo(ROE_EXCLUDE) < 0) {
            gate.exclude = true;
            gate.note = gate.note + " · ROE过低已剔除买入";
            return gate;
        }
        if (Objects.nonNull(fund.debtRatio) && fund.debtRatio.compareTo(DEBT_EXCLUDE) > 0) {
            gate.exclude = true;
            gate.note = gate.note + " · 负债率过高已剔除买入";
            return gate;
        }
        if ((Objects.nonNull(fund.roe) && fund.roe.compareTo(ROE_WEAK) < 0)
                || (Objects.nonNull(fund.debtRatio) && fund.debtRatio.compareTo(DEBT_WEAK) > 0)) {
            gate.weak = true;
            gate.note = gate.note + " · 基本面偏弱已降权";
        }
        return gate;
    }

    private String fundNoteOf(FundSnapshot fund) {
        if (Objects.isNull(fund)) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (Objects.nonNull(fund.reportDate)) {
            sb.append(fund.reportDate);
        }
        if (Objects.nonNull(fund.roe)) {
            if (!sb.isEmpty()) {
                sb.append(" · ");
            }
            sb.append("ROE ").append(fund.roe.setScale(2, RoundingMode.HALF_UP)).append("%");
        }
        if (Objects.nonNull(fund.debtRatio)) {
            if (!sb.isEmpty()) {
                sb.append(" · ");
            }
            sb.append("负债率 ").append(fund.debtRatio.setScale(2, RoundingMode.HALF_UP)).append("%");
        }
        if (Objects.nonNull(fund.netMargin)) {
            if (!sb.isEmpty()) {
                sb.append(" · ");
            }
            sb.append("净利率 ").append(fund.netMargin.setScale(2, RoundingMode.HALF_UP)).append("%");
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private BigDecimal baseScore(BigDecimal score) {
        return Objects.nonNull(score) ? score : new BigDecimal("60");
    }

    private String humanReason(StrategySignalEntity signal, SignalConfluenceItem cf, FundGate gate,
                               String actionLabel, HotConfluenceItem hot) {
        String rule = extractRule(signal.getReasonJson());
        StringBuilder sb = new StringBuilder();
        sb.append(actionLabel).append("：");
        if (StringUtils.isNotBlank(signal.getStrategyId())) {
            sb.append(strategyName(signal.getStrategyId()));
        }
        if (StringUtils.isNotBlank(rule)) {
            sb.append(" · ").append(rule);
        }
        if (Objects.nonNull(cf) && Objects.nonNull(cf.getStrategyCount()) && cf.getStrategyCount() >= 2) {
            sb.append(" · ").append(cf.getStrategyCount()).append("策略共振");
            if (CollUtil.isNotEmpty(cf.getStrategies())) {
                sb.append("(").append(String.join("/", cf.getStrategies())).append(")");
            }
        }
        if (Objects.nonNull(hot) && Objects.nonNull(hot.getSourceCount()) && hot.getSourceCount() >= 2) {
            sb.append(" · 热点共振");
            if (CollUtil.isNotEmpty(hot.getSources())) {
                sb.append("(").append(String.join("/", hot.getSources())).append(")");
            }
        }
        if (Objects.nonNull(gate) && gate.weak) {
            sb.append(" · 基本面偏弱降权");
        }
        return trimReason(sb.toString());
    }

    private String extractRule(String reasonJson) {
        if (StringUtils.isBlank(reasonJson)) {
            return null;
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(reasonJson);
            if (node.hasNonNull("rule")) {
                return node.get("rule").asText();
            }
        } catch (Exception ignored) {
            // 非 JSON 则原样截取
            return reasonJson.length() > 80 ? reasonJson.substring(0, 80) : reasonJson;
        }
        return null;
    }

    private String strategyName(String strategyId) {
        if ("S1".equals(strategyId)) {
            return "S1均线趋势";
        }
        if ("S2".equals(strategyId)) {
            return "S2 RSI回调";
        }
        if ("S3".equals(strategyId)) {
            return "S3突破放量";
        }
        return strategyId;
    }

    private String exitRuleOf(String strategyId) {
        if ("S1".equals(strategyId)) {
            return "跌破MA" + strategyParams.s1FastMa() + "离场";
        }
        if ("S2".equals(strategyId)) {
            return "RSI>" + strategyParams.s2RsiOverbought().toPlainString()
                    + "或跌破MA" + strategyParams.s2Ma() + "离场";
        }
        if ("S3".equals(strategyId)) {
            return "跌破突破日低点离场";
        }
        if ("RISK".equals(strategyId)) {
            return "止损/止盈触价";
        }
        return "按策略离场";
    }

    /**
     * 买入离场规则：策略离场 + 止损/止盈价（持仓已有则用持仓，否则按现价生成建议）
     */
    private String buyExitRule(String strategyId, String code, BigDecimal price,
                               MyHolding holding, LocalDate asOfDate,
                               BigDecimal atrStopMultiplier, BigDecimal atrTakeMultiplier) {
        String base = exitRuleOf(strategyId);
        if (Objects.nonNull(holding)
                && Objects.nonNull(holding.getStopLoss()) && holding.getStopLoss().signum() > 0
                && Objects.nonNull(holding.getTakeProfit()) && holding.getTakeProfit().signum() > 0) {
            return mergeExitRule(base, holding);
        }
        BigDecimal[] levels = suggestStopTake(code, price, asOfDate, atrStopMultiplier, atrTakeMultiplier);
        if (Objects.isNull(levels[0]) || Objects.isNull(levels[1])) {
            return base;
        }
        return base + "；建议止损 " + moneyText(levels[0]) + " / 止盈 " + moneyText(levels[1]);
    }

    private String mergeExitRule(String strategyExit, MyHolding holding) {
        if (Objects.isNull(holding)) {
            return strategyExit;
        }
        String holdingRule = holdingExitRule(holding);
        if (StringUtils.isBlank(holdingRule) || "止损 - / 止盈 -".equals(holdingRule)) {
            return strategyExit;
        }
        if (StringUtils.isBlank(strategyExit)) {
            return holdingRule;
        }
        return strategyExit + "；" + holdingRule;
    }

    private String holdingExitRule(MyHolding holding) {
        if (Objects.isNull(holding)) {
            return "止损 - / 止盈 -";
        }
        String stop = Objects.nonNull(holding.getStopLoss()) && holding.getStopLoss().signum() > 0
                ? moneyText(holding.getStopLoss()) : "-";
        String take = Objects.nonNull(holding.getTakeProfit()) && holding.getTakeProfit().signum() > 0
                ? moneyText(holding.getTakeProfit()) : "-";
        return "止损 " + stop + " / 止盈 " + take;
    }

    /**
     * 持仓缺止损或止盈时自动生成并回写（已有值不覆盖）
     */
    private boolean ensureHoldingStopTake(MyHolding holding, BigDecimal price, LocalDate asOfDate,
                                          BigDecimal atrStopMultiplier, BigDecimal atrTakeMultiplier) {
        if (Objects.isNull(holding)) {
            return false;
        }
        boolean needStop = Objects.isNull(holding.getStopLoss()) || holding.getStopLoss().signum() <= 0;
        boolean needTake = Objects.isNull(holding.getTakeProfit()) || holding.getTakeProfit().signum() <= 0;
        if (!needStop && !needTake) {
            return false;
        }
        BigDecimal base = null;
        if (Objects.nonNull(holding.getCostPrice()) && holding.getCostPrice().signum() > 0) {
            base = holding.getCostPrice();
        } else if (Objects.nonNull(price) && price.signum() > 0) {
            base = price;
        } else if (Objects.nonNull(holding.getMarketPrice()) && holding.getMarketPrice().signum() > 0) {
            base = holding.getMarketPrice();
        }
        BigDecimal[] levels = suggestStopTake(
                holding.getCode(), base, asOfDate, atrStopMultiplier, atrTakeMultiplier);
        if (needStop && Objects.nonNull(levels[0])) {
            holding.setStopLoss(levels[0]);
        }
        if (needTake && Objects.nonNull(levels[1])) {
            holding.setTakeProfit(levels[1]);
        }
        return (needStop && Objects.nonNull(holding.getStopLoss()))
                || (needTake && Objects.nonNull(holding.getTakeProfit()));
    }

    /**
     * 建议止损/止盈：优先 ATR14×倍数，否则固定比例（默认 -8% / +20%）
     *
     * @return [stop, take]，算不出则为 null
     */
    private BigDecimal[] suggestStopTake(String code, BigDecimal basePrice, LocalDate asOfDate,
                                         BigDecimal atrStopMultiplier, BigDecimal atrTakeMultiplier) {
        BigDecimal[] result = new BigDecimal[]{null, null};
        if (Objects.isNull(basePrice) || basePrice.signum() <= 0) {
            return result;
        }
        if (Objects.isNull(atrStopMultiplier) || Objects.isNull(atrTakeMultiplier)) {
            throw new BusinessException("决策组合快照缺少冻结ATR参数");
        }
        BigDecimal atr = calcAtr14(code, asOfDate);
        BigDecimal stop;
        BigDecimal take;
        if (Objects.nonNull(atr) && atr.signum() > 0) {
            stop = basePrice.subtract(atr.multiply(atrStopMultiplier)).setScale(2, RoundingMode.HALF_UP);
            take = basePrice.add(atr.multiply(atrTakeMultiplier)).setScale(2, RoundingMode.HALF_UP);
            if (stop.signum() <= 0) {
                stop = basePrice.multiply(BigDecimal.ONE.subtract(FALLBACK_STOP_PCT))
                        .setScale(2, RoundingMode.HALF_UP);
            }
        } else {
            stop = basePrice.multiply(BigDecimal.ONE.subtract(FALLBACK_STOP_PCT))
                    .setScale(2, RoundingMode.HALF_UP);
            take = basePrice.multiply(BigDecimal.ONE.add(FALLBACK_TAKE_PCT))
                    .setScale(2, RoundingMode.HALF_UP);
        }
        result[0] = stop;
        result[1] = take;
        return result;
    }

    private BigDecimal calcAtr14(String code, LocalDate asOfDate) {
        if (StringUtils.isBlank(code)) {
            return BigDecimal.ZERO;
        }
        String normalized = MarketCodeUtils.normalizeHoldingCode(code);
        List<BarDaily> bars = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                .eq(BarDaily::getCode, normalized)
                .le(Objects.nonNull(asOfDate), BarDaily::getTradeDate, asOfDate)
                .orderByDesc(BarDaily::getTradeDate)
                .last("limit 20"));
        if (CollUtil.isEmpty(bars) || bars.size() < 15) {
            return BigDecimal.ZERO;
        }
        List<BarDaily> asc = new ArrayList<>(bars);
        asc.sort(Comparator.comparing(BarDaily::getTradeDate));
        BigDecimal sum = BigDecimal.ZERO;
        int n = 0;
        for (int i = 1; i < asc.size() && n < 14; i++) {
            BarDaily cur = asc.get(i);
            BarDaily prev = asc.get(i - 1);
            if (Objects.isNull(cur.getHighPrice()) || Objects.isNull(cur.getLowPrice())
                    || Objects.isNull(prev.getClosePrice())) {
                continue;
            }
            BigDecimal tr1 = cur.getHighPrice().subtract(cur.getLowPrice());
            BigDecimal tr2 = cur.getHighPrice().subtract(prev.getClosePrice()).abs();
            BigDecimal tr3 = cur.getLowPrice().subtract(prev.getClosePrice()).abs();
            BigDecimal tr = tr1.max(tr2).max(tr3);
            sum = sum.add(tr);
            n++;
        }
        if (n == 0) {
            return BigDecimal.ZERO;
        }
        return sum.divide(BigDecimal.valueOf(n), 4, RoundingMode.HALF_UP);
    }

    private String moneyText(BigDecimal price) {
        if (Objects.isNull(price)) {
            return "-";
        }
        return price.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String stopTakeReason(MyHolding holding, BigDecimal price) {
        if (Objects.isNull(price)) {
            return null;
        }
        if (Objects.nonNull(holding.getStopLoss()) && holding.getStopLoss().signum() > 0
                && price.compareTo(holding.getStopLoss()) <= 0) {
            return "现价触及止损 " + moneyText(holding.getStopLoss());
        }
        if (Objects.nonNull(holding.getTakeProfit()) && holding.getTakeProfit().signum() > 0
                && price.compareTo(holding.getTakeProfit()) >= 0) {
            return "现价触及止盈 " + moneyText(holding.getTakeProfit());
        }
        return null;
    }

    private String trimReason(String reason) {
        if (StringUtils.isBlank(reason)) {
            return reason;
        }
        return reason.length() > 500 ? reason.substring(0, 500) : reason;
    }

    private String pctText(BigDecimal ratio) {
        if (Objects.isNull(ratio)) {
            return "-";
        }
        return ratio.multiply(new BigDecimal("100")).setScale(1, RoundingMode.HALF_UP) + "%";
    }

    private void saveBriefingSnapshot(LocalDate actionDate, MarketBriefingResp briefing) {
        if (Objects.isNull(actionDate) || Objects.isNull(briefing)) {
            return;
        }
        try {
            String json = OBJECT_MAPPER.writeValueAsString(briefing);
            MarketBriefingSnapshot existing = marketBriefingSnapshotMapper.selectOne(
                    Wrappers.<MarketBriefingSnapshot>lambdaQuery()
                            .eq(MarketBriefingSnapshot::getTradeDate, actionDate)
                            .last("LIMIT 1"));
            LocalDateTime now = LocalDateTime.now();
            if (Objects.nonNull(existing)) {
                existing.setStance(briefing.getStance());
                existing.setStanceScore(briefing.getStanceScore());
                existing.setDataLevel(briefing.getDataLevel());
                existing.setPayloadJson(json);
                existing.setUpdateTime(now);
                marketBriefingSnapshotMapper.updateById(existing);
            } else {
                marketBriefingSnapshotMapper.insert(MarketBriefingSnapshot.builder()
                        .tradeDate(actionDate)
                        .stance(briefing.getStance())
                        .stanceScore(briefing.getStanceScore())
                        .dataLevel(briefing.getDataLevel())
                        .payloadJson(json)
                        .createTime(now)
                        .updateTime(now)
                        .deleted(0)
                        .build());
            }
        } catch (Exception ex) {
            // 快照失败不影响决策主流程（表未建时也容错）
        }
    }

    MarketBriefingResp resolveRunBriefing(DecisionContext context) {
        if (context.getMode() != DecisionMode.REPLAY) {
            return marketBriefingService.briefing();
        }
        MarketBriefingResp snapshot = loadBriefingSnapshot(context.getActionDate());
        if (Objects.isNull(snapshot)) {
            throw new BusinessException("历史回放缺少市场简报快照: " + context.getActionDate());
        }
        snapshot.setDataLevel("YELLOW");
        snapshot.setDataSufficient(false);
        snapshot.setMessage("历史回放已禁用无时点快照的持仓、估值、基本面和证券元数据");
        return snapshot;
    }

    private MarketBriefingResp unavailableBriefing(LocalDate actionDate) {
        return MarketBriefingResp.builder()
                .asOf(actionDate)
                .stance("数据不足")
                .stanceScore(0)
                .buyWeightFactor(BigDecimal.ZERO)
                .dataLevel("RED")
                .dataSufficient(false)
                .hotThemes(List.of())
                .factors(List.of())
                .tips(List.of())
                .message("该日期缺少市场简报快照，未使用当前数据回填")
                .build();
    }

    private MarketBriefingResp loadBriefingSnapshot(LocalDate actionDate) {
        if (Objects.isNull(actionDate)) {
            return null;
        }
        try {
            MarketBriefingSnapshot row = marketBriefingSnapshotMapper.selectOne(
                    Wrappers.<MarketBriefingSnapshot>lambdaQuery()
                            .eq(MarketBriefingSnapshot::getTradeDate, actionDate)
                            .last("LIMIT 1"));
            if (Objects.isNull(row) || StringUtils.isBlank(row.getPayloadJson())) {
                return null;
            }
            return OBJECT_MAPPER.readValue(row.getPayloadJson(), MarketBriefingResp.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private List<String> resolveMainlineNames(MarketBriefingResp briefing, boolean allowCurrentFallback) {
        List<String> names = new ArrayList<>();
        if (Objects.nonNull(briefing) && CollUtil.isNotEmpty(briefing.getHotThemeItems())) {
            for (MarketHotThemeItem item : briefing.getHotThemeItems()) {
                if (Objects.nonNull(item)
                        && MainlineBoardRules.isConceptBoard(item.getBoardType(), item.getName())
                        && names.size() < 8) {
                    names.add(item.getName().trim());
                }
            }
        } else if (Objects.nonNull(briefing) && CollUtil.isNotEmpty(briefing.getHotThemes())) {
            for (String theme : briefing.getHotThemes()) {
                if (MainlineBoardRules.isConceptBoard("CONCEPT", theme)
                        && names.size() < 8) {
                    names.add(theme.trim());
                }
            }
        }
        if (CollUtil.isEmpty(names) && allowCurrentFallback) {
            try {
                List<SectorBoardItem> mainline = sectorBoardService.mainline(null, 8);
                for (SectorBoardItem item : mainline) {
                    if (Objects.nonNull(item)
                            && MainlineBoardRules.isConceptBoard(item.getBoardType(), item.getName())
                            && names.size() < 8) {
                        names.add(item.getName().trim());
                    }
                }
            } catch (Exception ignored) {
                // 主线不可用时跳过加分
            }
        }
        return names;
    }

    private static final class FundSnapshot {
        private final LocalDate reportDate;
        private final BigDecimal roe;
        private final BigDecimal debtRatio;
        private final BigDecimal netMargin;

        private FundSnapshot(LocalDate reportDate, BigDecimal roe, BigDecimal debtRatio, BigDecimal netMargin) {
            this.reportDate = reportDate;
            this.roe = roe;
            this.debtRatio = debtRatio;
            this.netMargin = netMargin;
        }
    }

    private static final class FundGate {
        private boolean exclude;
        private boolean weak;
        private String note;
    }
}
