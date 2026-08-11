package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.JsonUtils;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.ai.AiChatProperties;
import com.awe.apex.quant.ai.KimiChatClient;
import com.awe.apex.quant.decision.DecisionContext;
import com.awe.apex.quant.decision.DecisionActionPublisher;
import com.awe.apex.quant.decision.DecisionFeature;
import com.awe.apex.quant.decision.DecisionFeatureBuilder;
import com.awe.apex.quant.decision.DecisionFeatureInput;
import com.awe.apex.quant.decision.DecisionFeatureSource;
import com.awe.apex.quant.decision.DecisionMode;
import com.awe.apex.quant.decision.DecisionRunManager;
import com.awe.apex.quant.decision.DecisionScoreReq;
import com.awe.apex.quant.decision.DecisionScoreResp;
import com.awe.apex.quant.decision.DecisionScorer;
import com.awe.apex.quant.decision.MainlineBoardRules;
import com.awe.apex.quant.decision.MainlineMatcher;
import com.awe.apex.quant.domain.dto.DecisionAttrBucket;
import com.awe.apex.quant.domain.dto.DecisionAttributionResp;
import com.awe.apex.quant.domain.dto.DecisionBuyAiResp;
import com.awe.apex.quant.domain.dto.DecisionBuyAiStockNote;
import com.awe.apex.quant.domain.dto.DecisionHistoryItem;
import com.awe.apex.quant.domain.dto.DecisionItemResp;
import com.awe.apex.quant.domain.dto.DecisionRunReq;
import com.awe.apex.quant.domain.dto.DecisionTodayResp;
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
import com.awe.apex.quant.domain.dto.UniverseRefreshReq;
import com.awe.apex.quant.domain.dto.UniverseRefreshResp;
import com.awe.apex.quant.domain.dto.ValuationBriefResp;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.entity.DailyAction;
import com.awe.apex.quant.domain.entity.DecisionRun;
import com.awe.apex.quant.domain.entity.MarketBriefingSnapshot;
import com.awe.apex.quant.domain.entity.MarketHot;
import com.awe.apex.quant.domain.entity.MyHolding;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.domain.entity.StockFinAbstract;
import com.awe.apex.quant.domain.entity.StockFinIndicator;
import com.awe.apex.quant.domain.entity.StrategySignalEntity;
import com.awe.apex.quant.domain.entity.UniverseSnapshot;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.DailyActionMapper;
import com.awe.apex.quant.mapper.DecisionRunMapper;
import com.awe.apex.quant.mapper.MarketBriefingSnapshotMapper;
import com.awe.apex.quant.mapper.MyHoldingMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.mapper.StockFinAbstractMapper;
import com.awe.apex.quant.mapper.StockFinIndicatorMapper;
import com.awe.apex.quant.market.MarketCodeUtils;
import com.awe.apex.quant.market.TradingCalendar;
import com.awe.apex.quant.service.IConfigService;
import com.awe.apex.quant.service.IDecisionService;
import com.awe.apex.quant.service.IHotService;
import com.awe.apex.quant.service.ILimitUpLadderService;
import com.awe.apex.quant.service.IMarketBriefingService;
import com.awe.apex.quant.service.IMyHoldingService;
import com.awe.apex.quant.service.IObservePoolService;
import com.awe.apex.quant.service.IPaperService;
import com.awe.apex.quant.service.IRiskService;
import com.awe.apex.quant.service.ISectorBoardService;
import com.awe.apex.quant.service.ISignalService;
import com.awe.apex.quant.service.IUniverseService;
import com.awe.apex.quant.service.IValuationService;
import com.awe.apex.quant.strategy.StrategyParams;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
import java.util.concurrent.ConcurrentHashMap;

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
    private IPaperService paperService;

    @Resource
    private DailyActionMapper dailyActionMapper;

    @Resource
    private DecisionRunMapper decisionRunMapper;

    @Resource
    private IMyHoldingService myHoldingService;

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
    private ILimitUpLadderService limitUpLadderService;

    @Resource
    private IValuationService valuationService;

    @Resource
    private DecisionScorer decisionScorer;

    @Resource
    private DecisionFeatureBuilder decisionFeatureBuilder;

    @Resource
    private DecisionRunManager decisionRunManager;

    @Resource
    private DecisionActionPublisher decisionActionPublisher;

    @Resource
    private MyHoldingMapper myHoldingMapper;

    @Resource
    private IConfigService configService;

    @Resource
    private KimiChatClient kimiChatClient;

    @Resource
    private AiChatProperties aiChatProperties;

    private static final BigDecimal FALLBACK_STOP_PCT = new BigDecimal("0.08");
    private static final BigDecimal FALLBACK_TAKE_PCT = new BigDecimal("0.20");
    private static final String BUY_AI_DISCLAIMER = "AI 总结仅供研究参考，不构成投资建议；请结合本地规则评分与风控自行决策。";
    private final Map<String, CachedBuyAi> buyAiCache = new ConcurrentHashMap<>();

    /**
     * 一键生成今日决策：刷新股票池 → 跑策略 → 共振/基本面/风控 → 落库 → 同步观察池
     *
     * @param req 请求
     * @return 今日决策
     */
    @Override
    public DecisionTodayResp run(DecisionRunReq req) {
        DecisionRunReq safe = Objects.nonNull(req) ? req : new DecisionRunReq();
        String groupName = StringUtils.isNotBlank(safe.getGroupName()) ? safe.getGroupName().trim() : "我的自选";
        DecisionContext context = DecisionContext.from(safe);
        DecisionRun decisionRun = decisionRunManager.start(context, groupName, decisionConfigSnapshot());
        try {
            DecisionTodayResp response = executeRun(safe, groupName, context, decisionRun);
            String dataLevel = Objects.nonNull(response.getMarketBriefing())
                    ? response.getMarketBriefing().getDataLevel() : null;
            finishRun(context, decisionRun, response, dataLevel);
            return response;
        } catch (RuntimeException ex) {
            decisionRunManager.fail(decisionRun, ex);
            throw ex;
        }
    }

    void finishRun(DecisionContext context, DecisionRun run,
                   DecisionTodayResp response, String dataLevel) {
        if (context.getMode() == DecisionMode.SHADOW) {
            decisionRunManager.completeUnpublished(run, dataLevel, response.getMessage());
            return;
        }
        decisionActionPublisher.publish(run, response.getItems(), dataLevel, response.getMessage());
    }

    private DecisionTodayResp executeRun(DecisionRunReq safe, String groupName,
                                         DecisionContext context, DecisionRun decisionRun) {
        LocalDate actionDate = context.getActionDate();

        // 0. 市场简报（大盘/风格/量能/涨停/主线）→ 调节买入仓位
        MarketBriefingResp briefing = resolveRunBriefing(context);
        BigDecimal buyFactor = Objects.nonNull(briefing.getBuyWeightFactor())
                ? briefing.getBuyWeightFactor() : BigDecimal.ONE;
        List<String> mainlineNames = resolveMainlineNames(briefing, context.getMode() != DecisionMode.REPLAY);

        // 1. 我的持仓（仅用于卖出/持有决策；买入候选不从持仓里挑）
        List<MyHolding> holdings = context.getMode() == DecisionMode.REPLAY
                ? List.of() : myHoldingService.listHoldings();
        Map<String, MyHolding> posMap = new HashMap<>();
        for (MyHolding holding : holdings) {
            posMap.put(holding.getCode(), holding);
        }

        // 2. 刷新全A股票池：本地日线≥60 的全市场（不截断市值 TopN）；宽松质量不因估值硬踢
        // 默认不含北交所（京市），仅 includeBj=true 时纳入
        boolean includeBj = Boolean.TRUE.equals(safe.getIncludeBj());
        UniverseRefreshReq universeReq = new UniverseRefreshReq();
        universeReq.setLooseFilter(true);
        universeReq.setScope("MARKET");
        universeReq.setIncludeBj(includeBj);
        if (context.getMode() == DecisionMode.REPLAY) {
            universeReq.setAsOfDate(actionDate);
        }
        UniverseRefreshResp universeResp;
        try {
            universeResp = universeService.refresh(universeReq);
        } catch (BusinessException ex) {
            if (context.getMode() == DecisionMode.REPLAY) {
                throw ex;
            }
            log.warn("全A股票池为空，回退自选分组 group={} err={}", groupName, ex.getMessage());
            UniverseRefreshReq fallbackReq = new UniverseRefreshReq();
            fallbackReq.setLooseFilter(true);
            fallbackReq.setGroupName(groupName);
            fallbackReq.setIncludeBj(includeBj);
            universeResp = universeService.refresh(fallbackReq);
        }
        int universeCount = Objects.nonNull(universeResp.getCount()) ? universeResp.getCount() : 0;

        // 3. 跑 S1/S2/S3：全A质量池 + 热点；持仓仅附加以便产出卖出信号（观察池不同步卖出）
        List<String> signalCodes = new ArrayList<>();
        Set<String> signalCodeSet = new HashSet<>();
        List<UniverseSnapshot> universeList = universeService.latest();
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
        for (MyHolding holding : holdings) {
            if (StringUtils.isNotBlank(holding.getCode()) && signalCodeSet.add(holding.getCode())) {
                signalCodes.add(holding.getCode());
            }
        }
        SignalRunReq signalReq = new SignalRunReq();
        signalReq.setAsOfDate(actionDate);
        if (CollUtil.isNotEmpty(signalCodes)) {
            signalReq.setCodes(signalCodes);
        } else {
            signalReq.setUseUniverse(true);
        }
        List<StrategySignalEntity> signals = signalService.run(signalReq);

        // 4. 多策略共振（窗口/最少策略数可配置）
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

        // 5. 风控参数（仓位上限仍读模拟盘风控配置）
        RiskOverviewResp risk = context.getMode() == DecisionMode.REPLAY
                ? replayRiskOverview() : riskService.overview(paperService.defaultAccount().getId());
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

        // 持仓缺止损/止盈时先补全（供卖出/持有/加仓离场规则使用）
        for (MyHolding holding : holdings) {
            String code = holding.getCode();
            StockBasic basic = basicMap.get(code);
            if (Objects.isNull(basic) && StringUtils.isNotBlank(code)) {
                basic = basicMap.get(MarketCodeUtils.normalizeHoldingCode(code));
            }
            BigDecimal price = Objects.nonNull(basic) ? basic.getLatestPrice() : holding.getMarketPrice();
            ensureHoldingStopTake(holding, price, actionDate);
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

        // 6. 组装决策：买入=全A机会；卖出=仅我的持仓；另收集「值得观察」候选（不要求马上买）
        List<DecisionItemResp> buys = new ArrayList<>();
        List<DecisionItemResp> sells = new ArrayList<>();
        List<DecisionItemResp> holds = new ArrayList<>();
        List<DecisionItemResp> observeCandidates = new ArrayList<>();
        Map<String, DecisionFeatureInput> featureInputs = new HashMap<>();
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

            // 卖出：只处理「我的持仓」；池内未持仓的卖出信号忽略
            if ("SELL".equals(side)) {
                if (Objects.isNull(holdingInMap)) {
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
                featureInputs.put(featureKey(code, "SELL"), new DecisionFeatureInput(
                        baseScore(signal.getScore()), false, false, false, null,
                        buyFactor, singleLimit, false));
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
            featureInputs.put(featureKey(code, "BUY"), new DecisionFeatureInput(
                    scoreReq.getSignalScore(), scoreReq.isFundExclude(), scoreReq.isFundWeak(),
                    scoreReq.isOffMainline(), Objects.nonNull(valBrief) ? valBrief.getScoreDelta() : null,
                    scoreReq.getBuyWeightFactor(), scoreReq.getSingleLimit(), scoreReq.isObserveOnly()));
            if (StringUtils.isNotBlank(scored.getLinkHint())) {
                reason = trimReason(reason + " · " + scored.getLinkHint());
            }

            // 基本面硬剔除：不进「今日买入」，但仍可进观察池盯信号
            BigDecimal buyPrice = Objects.nonNull(basic) ? basic.getLatestPrice() : null;
            String buyExitRule = buyExitRule(signal.getStrategyId(), code, buyPrice, holdingInMap, actionDate);

            if (gate.exclude) {
                if (!alreadyHeld && observeCodes.add(code)) {
                    observeCandidates.add(DecisionItemResp.builder()
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
                            .linkHint(scored.getLinkHint())
                            .build());
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
                    .linkHint(scored.getLinkHint())
                    .build();
            buys.add(item);
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

        List<DecisionItemResp> all = new ArrayList<>();
        all.addAll(buys);
        all.addAll(sells);
        all.addAll(holds);
        List<DecisionFeature> features = new ArrayList<>();
        for (DecisionItemResp item : all) {
            HotConfluenceItem hot = hotMap.get(item.getCode());
            int hotSourceCount = Objects.nonNull(hot) && Objects.nonNull(hot.getSourceCount())
                    ? hot.getSourceCount() : 0;
            DecisionFeatureInput input = featureInputs.get(featureKey(item.getCode(), item.getAction()));
            if (Objects.isNull(input)) {
                input = new DecisionFeatureInput(item.getScore(), false, false, false, null,
                        briefing.getBuyWeightFactor(), singleLimit, false);
            }
            features.add(decisionFeatureBuilder.build(item,
                    new DecisionFeatureSource(input, hotSourceCount, briefing)));
        }
        decisionRunManager.saveFeatures(decisionRun, features);
        if (context.getMode() == DecisionMode.LIVE) {
            saveBriefingSnapshot(actionDate, briefing);
        }

        // 8. 自动写入观察池：多策略分散 + 抑制追高（不要求马上买）
        int observeCreated = 0;
        int observeUpdated = 0;
        int observeUpserted = 0;
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

        String riskNote = "单票上限 " + pctText(singleLimit)
                + " · 总仓 " + pctText(risk.getPositionRatio())
                + "/" + pctText(risk.getTotalLimit());
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
                        + " · 扫描 " + signalCodes.size()
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
        return config;
    }

    private RiskOverviewResp replayRiskOverview() {
        return RiskOverviewResp.builder()
                .positionValue(BigDecimal.ZERO)
                .positionRatio(BigDecimal.ZERO)
                .totalLimit(new BigDecimal("0.80"))
                .singleLimit(new BigDecimal("0.15"))
                .industryLimit(new BigDecimal("0.30"))
                .warnings(List.of("历史回放未使用当前账户持仓"))
                .alerts(List.of())
                .criticalCount(0)
                .warnCount(0)
                .build();
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
        LocalDate actionDate = Objects.nonNull(date) ? date : LocalDate.now();
        String group = StringUtils.isNotBlank(groupName) ? groupName.trim() : "我的自选";
        List<DailyAction> rows = dailyActionMapper.selectList(Wrappers.<DailyAction>lambdaQuery()
                .eq(DailyAction::getActionDate, actionDate)
                .orderByAsc(DailyAction::getAction)
                .orderByDesc(DailyAction::getScore));
        DecisionRun storedRun = loadDecisionRun(rows);
        MarketBriefingResp briefing;
        if (actionDate.equals(LocalDate.now())) {
            // 今日大盘与看板对齐：走实时简报（指数/量能/涨跌家数会覆盖）
            briefing = marketBriefingService.briefing();
        } else {
            briefing = loadBriefingSnapshot(actionDate);
            if (Objects.isNull(briefing)) {
                briefing = unavailableBriefing(actionDate);
            }
        }
        List<String> mainlineNames = resolveMainlineNames(briefing, actionDate.equals(LocalDate.now()));
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
            } else if ("SELL".equalsIgnoreCase(row.getAction())) {
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
                ? "今日尚无决策，请点击「一键生成决策」；下方市场简报已可参考"
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
                return decisionRunMapper.selectById(row.getRunId());
            }
        }
        return null;
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
                } else if ("SELL".equalsIgnoreCase(row.getAction())) {
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
                    .message("暂无决策记录，请先一键生成决策")
                    .build();
        }
        List<LocalDate> dates = new ArrayList<>();
        for (DailyAction row : dateRows) {
            if (Objects.nonNull(row.getActionDate())) {
                dates.add(row.getActionDate());
            }
        }
        List<DailyAction> buys = dailyActionMapper.selectList(Wrappers.<DailyAction>lambdaQuery()
                .in(DailyAction::getActionDate, dates)
                .eq(DailyAction::getAction, "BUY"));
        List<DailyAction> sells = dailyActionMapper.selectList(Wrappers.<DailyAction>lambdaQuery()
                .in(DailyAction::getActionDate, dates)
                .eq(DailyAction::getAction, "SELL"));

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
                    .summary("当前无建议买入标的，暂不生成 AI 总结。请先一键生成决策。")
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
        int ttl = Math.max(60, aiChatProperties.getSummaryCacheSeconds());
        boolean forceRefresh = Boolean.TRUE.equals(force);
        if (!forceRefresh) {
            CachedBuyAi cached = buyAiCache.get(cacheKey);
            if (Objects.nonNull(cached)
                    && cached.at.plusSeconds(ttl).isAfter(LocalDateTime.now())
                    && Objects.nonNull(cached.payload)
                    && StringUtils.isNotBlank(cached.payload.getSummary())) {
                DecisionBuyAiResp hit = cached.payload;
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
        buyAiCache.put(cacheKey, new CachedBuyAi(ai, LocalDateTime.now()));
        log.info("决策买入AI总结完成 date={} buys={} fromCache=false", actionDate, buys.size());
        return ai;
    }

    private String buildBuyAiCacheKey(LocalDate actionDate, List<DecisionItemResp> buys) {
        StringBuilder sb = new StringBuilder();
        sb.append(actionDate).append('|');
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

    private static final class CachedBuyAi {
        private final DecisionBuyAiResp payload;
        private final LocalDateTime at;

        private CachedBuyAi(DecisionBuyAiResp payload, LocalDateTime at) {
            this.payload = payload;
            this.at = at;
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
        log.info("观察池候选再平衡 buy={} (s2优先) mood={}", buys.size(), Math.min(moods.size(), 15));
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
                               MyHolding holding, LocalDate asOfDate) {
        String base = exitRuleOf(strategyId);
        if (Objects.nonNull(holding)
                && Objects.nonNull(holding.getStopLoss()) && holding.getStopLoss().signum() > 0
                && Objects.nonNull(holding.getTakeProfit()) && holding.getTakeProfit().signum() > 0) {
            return mergeExitRule(base, holding);
        }
        BigDecimal[] levels = suggestStopTake(code, price, asOfDate);
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
    private void ensureHoldingStopTake(MyHolding holding, BigDecimal price, LocalDate asOfDate) {
        if (Objects.isNull(holding) || Objects.isNull(holding.getId())) {
            return;
        }
        boolean needStop = Objects.isNull(holding.getStopLoss()) || holding.getStopLoss().signum() <= 0;
        boolean needTake = Objects.isNull(holding.getTakeProfit()) || holding.getTakeProfit().signum() <= 0;
        if (!needStop && !needTake) {
            return;
        }
        BigDecimal base = null;
        if (Objects.nonNull(holding.getCostPrice()) && holding.getCostPrice().signum() > 0) {
            base = holding.getCostPrice();
        } else if (Objects.nonNull(price) && price.signum() > 0) {
            base = price;
        } else if (Objects.nonNull(holding.getMarketPrice()) && holding.getMarketPrice().signum() > 0) {
            base = holding.getMarketPrice();
        }
        BigDecimal[] levels = suggestStopTake(holding.getCode(), base, asOfDate);
        if (needStop && Objects.nonNull(levels[0])) {
            holding.setStopLoss(levels[0]);
        }
        if (needTake && Objects.nonNull(levels[1])) {
            holding.setTakeProfit(levels[1]);
        }
        if ((needStop && Objects.nonNull(holding.getStopLoss()))
                || (needTake && Objects.nonNull(holding.getTakeProfit()))) {
            MyHolding patch = new MyHolding();
            patch.setId(holding.getId());
            patch.setStopLoss(holding.getStopLoss());
            patch.setTakeProfit(holding.getTakeProfit());
            patch.setUpdateTime(LocalDateTime.now());
            myHoldingMapper.updateById(patch);
            log.info("持仓止损止盈已自动补全 code={} stop={} take={}",
                    holding.getCode(), holding.getStopLoss(), holding.getTakeProfit());
        }
    }

    /**
     * 建议止损/止盈：优先 ATR14×倍数，否则固定比例（默认 -8% / +20%）
     *
     * @return [stop, take]，算不出则为 null
     */
    private BigDecimal[] suggestStopTake(String code, BigDecimal basePrice, LocalDate asOfDate) {
        BigDecimal[] result = new BigDecimal[]{null, null};
        if (Objects.isNull(basePrice) || basePrice.signum() <= 0) {
            return result;
        }
        BigDecimal stopMult = configService.getDecimal("atr_stop_mult", new BigDecimal("2.0"));
        BigDecimal takeMult = configService.getDecimal("atr_take_mult", new BigDecimal("3.0"));
        BigDecimal atr = calcAtr14(code, asOfDate);
        BigDecimal stop;
        BigDecimal take;
        if (Objects.nonNull(atr) && atr.signum() > 0) {
            stop = basePrice.subtract(atr.multiply(stopMult)).setScale(2, RoundingMode.HALF_UP);
            take = basePrice.add(atr.multiply(takeMult)).setScale(2, RoundingMode.HALF_UP);
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
