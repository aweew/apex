package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.decision.MainlineMatcher;
import com.awe.apex.quant.domain.dto.DecisionAttrBucket;
import com.awe.apex.quant.domain.dto.DecisionAttributionResp;
import com.awe.apex.quant.domain.dto.DecisionHistoryItem;
import com.awe.apex.quant.domain.dto.DecisionItemResp;
import com.awe.apex.quant.domain.dto.DecisionRunReq;
import com.awe.apex.quant.domain.dto.DecisionTodayResp;
import com.awe.apex.quant.domain.dto.HotConfluenceItem;
import com.awe.apex.quant.domain.dto.MarketBriefingResp;
import com.awe.apex.quant.domain.dto.RiskOverviewResp;
import com.awe.apex.quant.domain.dto.SectorBoardItem;
import com.awe.apex.quant.domain.dto.SignalConfluenceItem;
import com.awe.apex.quant.domain.dto.SignalConfluenceResp;
import com.awe.apex.quant.domain.dto.SignalRunReq;
import com.awe.apex.quant.domain.dto.UniverseRefreshReq;
import com.awe.apex.quant.domain.dto.UniverseRefreshResp;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.entity.DailyAction;
import com.awe.apex.quant.domain.entity.MarketBriefingSnapshot;
import com.awe.apex.quant.domain.entity.MyHolding;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.domain.entity.StockFinAbstract;
import com.awe.apex.quant.domain.entity.StockFinIndicator;
import com.awe.apex.quant.domain.entity.StrategySignalEntity;
import com.awe.apex.quant.domain.entity.UniverseSnapshot;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.DailyActionMapper;
import com.awe.apex.quant.mapper.MarketBriefingSnapshotMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.mapper.StockFinAbstractMapper;
import com.awe.apex.quant.mapper.StockFinIndicatorMapper;
import com.awe.apex.quant.market.TradingCalendar;
import com.awe.apex.quant.service.IDecisionService;
import com.awe.apex.quant.service.IHotService;
import com.awe.apex.quant.service.IMarketBriefingService;
import com.awe.apex.quant.service.IMyHoldingService;
import com.awe.apex.quant.service.IPaperService;
import com.awe.apex.quant.service.IRiskService;
import com.awe.apex.quant.service.ISectorBoardService;
import com.awe.apex.quant.service.ISignalService;
import com.awe.apex.quant.service.IUniverseService;
import com.awe.apex.quant.strategy.StrategyParams;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

/**
 * 智能决策：编排股票池 / 策略信号 / 共振 / 热点 / 基本面 / 风控
 */
@Service
public class DecisionServiceImpl implements IDecisionService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final BigDecimal BASE_WEIGHT = new BigDecimal("0.10");
    private static final BigDecimal CONFLUENCE_WEIGHT = new BigDecimal("0.12");
    private static final BigDecimal ROE_EXCLUDE = new BigDecimal("3");
    private static final BigDecimal ROE_WEAK = new BigDecimal("8");
    private static final BigDecimal DEBT_EXCLUDE = new BigDecimal("80");
    private static final BigDecimal DEBT_WEAK = new BigDecimal("70");
    private static final BigDecimal SCORE_BOOST_CONFLUENCE = new BigDecimal("12");
    private static final BigDecimal SCORE_BOOST_HOT = new BigDecimal("8");
    private static final BigDecimal SCORE_BOOST_HOT_TRIPLE = new BigDecimal("4");
    private static final BigDecimal SCORE_PENALTY_FUND = new BigDecimal("8");
    private static final BigDecimal SCORE_BOOST_MAINLINE = new BigDecimal("10");
    private static final BigDecimal SCORE_PENALTY_OFF_MAINLINE = new BigDecimal("5");

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

    /**
     * 一键生成今日决策：刷新股票池 → 跑策略 → 共振/基本面/风控 → 落库
     *
     * @param req 请求
     * @return 今日决策
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DecisionTodayResp run(DecisionRunReq req) {
        DecisionRunReq safe = Objects.nonNull(req) ? req : new DecisionRunReq();
        String groupName = StringUtils.isNotBlank(safe.getGroupName()) ? safe.getGroupName().trim() : "我的自选";
        LocalDate actionDate = Objects.nonNull(safe.getDate()) ? safe.getDate() : LocalDate.now();

        // 0. 市场简报（大盘/风格/量能/涨停/主线）→ 调节买入仓位
        MarketBriefingResp briefing = marketBriefingService.briefing();
        BigDecimal buyFactor = Objects.nonNull(briefing.getBuyWeightFactor())
                ? briefing.getBuyWeightFactor() : BigDecimal.ONE;
        List<String> mainlineNames = resolveMainlineNames(briefing);

        // 1. 我的持仓（卖出/持有聚焦这里；买入不局限于此）
        List<MyHolding> holdings = myHoldingService.listHoldings();
        Map<String, MyHolding> posMap = new HashMap<>();
        for (MyHolding holding : holdings) {
            posMap.put(holding.getCode(), holding);
        }

        // 2. 刷新自选股票池（买入候选主池）
        UniverseRefreshReq universeReq = new UniverseRefreshReq();
        universeReq.setGroupName(groupName);
        UniverseRefreshResp universeResp = universeService.refresh(universeReq);
        int universeCount = Objects.nonNull(universeResp.getCount()) ? universeResp.getCount() : 0;

        // 3. 跑 S1/S2/S3：股票池 + 持仓 + 热点共振（热点不在自选也纳入扫描）
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
        for (MyHolding holding : holdings) {
            if (StringUtils.isNotBlank(holding.getCode()) && signalCodeSet.add(holding.getCode())) {
                signalCodes.add(holding.getCode());
            }
        }
        Map<String, HotConfluenceItem> hotMap = hotService.confluenceMap(50);
        int hotScanCount = 0;
        for (String hotCode : hotMap.keySet()) {
            if (StringUtils.isNotBlank(hotCode) && signalCodeSet.add(hotCode)) {
                signalCodes.add(hotCode);
                hotScanCount++;
            }
        }
        SignalRunReq signalReq = new SignalRunReq();
        if (CollUtil.isNotEmpty(signalCodes)) {
            signalReq.setCodes(signalCodes);
        } else {
            signalReq.setUseUniverse(true);
        }
        List<StrategySignalEntity> signals = signalService.run(signalReq);

        // 4. 多策略共振（近 5 日 ≥2）
        SignalConfluenceResp confluenceResp = signalService.confluence(5, 2);
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
        Long accountId = paperService.defaultAccount().getId();
        RiskOverviewResp risk = riskService.overview(accountId);
        BigDecimal singleLimit = Objects.nonNull(risk.getSingleLimit()) ? risk.getSingleLimit() : new BigDecimal("0.15");

        Set<String> codesNeeded = new HashSet<>(signalCodeSet);
        for (StrategySignalEntity signal : signals) {
            codesNeeded.add(signal.getCode());
        }
        Map<String, StockBasic> basicMap = loadBasics(codesNeeded);
        Map<String, FundSnapshot> fundMap = loadFunds(codesNeeded);

        // 6. 组装决策：买入=股票池机会；卖出=仅我的持仓
        List<DecisionItemResp> buys = new ArrayList<>();
        List<DecisionItemResp> sells = new ArrayList<>();
        List<DecisionItemResp> holds = new ArrayList<>();
        Set<String> covered = new HashSet<>();
        LocalDateTime now = LocalDateTime.now();

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
                BigDecimal score = baseScore(signal.getScore());
                if (cfCount >= 2) {
                    score = score.add(SCORE_BOOST_CONFLUENCE);
                }
                HotConfluenceItem hot = hotMap.get(code);
                score = applyHotBoost(score, hot);
                String reason = humanReason(signal, cf, null, "持仓卖出", hot);
                String exitRule = exitRuleOf(signal.getStrategyId());
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
                        .confluence(cfCount >= 2)
                        .strategies(Objects.nonNull(cf) ? cf.getStrategies() : List.of(signal.getStrategyId()))
                        .fundNote(fundNoteOf(fundMap.get(code)))
                        .signalId(signal.getId())
                        .scoreExplain("策略" + signal.getStrategyId() + " 卖出 · " + exitRule
                                + (cfCount >= 2 ? " · 多策略共振卖出" : ""))
                        .build();
                sells.add(item);
                covered.add(code);
                continue;
            }

            // 买入：来自股票池策略机会，不要求已在「我的持仓」
            if (!"BUY".equals(side)) {
                continue;
            }

            FundSnapshot fund = fundMap.get(code);
            FundGate gate = evaluateFund(fund);
            if (gate.exclude) {
                continue;
            }
            SignalConfluenceItem cf = buyConfluence.get(code);
            int cfCount = Objects.nonNull(cf) ? cf.getStrategyCount() : 1;
            BigDecimal score = baseScore(signal.getScore());
            if (cfCount >= 2) {
                score = score.add(SCORE_BOOST_CONFLUENCE);
            }
            HotConfluenceItem hot = hotMap.get(code);
            score = applyHotBoost(score, hot);
            if (gate.weak) {
                score = score.subtract(SCORE_PENALTY_FUND);
            }
            String industry = Objects.nonNull(basic) ? basic.getIndustry() : null;
            MainlineMatcher.Hit mainHit = MainlineMatcher.match(industry, mainlineNames);
            if (mainHit.match) {
                score = score.add(SCORE_BOOST_MAINLINE);
            } else if (CollUtil.isNotEmpty(mainlineNames) && StringUtils.isNotBlank(industry)) {
                score = score.subtract(SCORE_PENALTY_OFF_MAINLINE);
            }
            boolean hotOk = Objects.nonNull(hot) && Objects.nonNull(hot.getSourceCount()) && hot.getSourceCount() >= 2;
            BigDecimal weight = suggestWeight(cfCount >= 2 || hotOk || mainHit.match, !gate.weak, singleLimit);
            if (mainHit.match) {
                weight = weight.multiply(new BigDecimal("1.08"));
            } else if (CollUtil.isNotEmpty(mainlineNames) && StringUtils.isNotBlank(industry)) {
                weight = weight.multiply(new BigDecimal("0.85"));
            }
            weight = weight.multiply(buyFactor).min(singleLimit).setScale(4, RoundingMode.HALF_UP);
            if ("防守".equals(briefing.getStance())) {
                score = score.subtract(new BigDecimal("6"));
            } else if ("进攻".equals(briefing.getStance()) && cfCount >= 2) {
                score = score.add(new BigDecimal("3"));
            }
            boolean alreadyHeld = Objects.nonNull(holdingInMap);
            String buyLabel = alreadyHeld ? "加仓" : "买入";
            String reason = humanReason(signal, cf, gate, buyLabel, hot);
            if (alreadyHeld) {
                reason = trimReason(reason + " · 已在我的持仓");
            }
            if (mainHit.match) {
                reason = trimReason(reason + " · 主线「" + mainHit.name + "」同向");
            } else if (CollUtil.isNotEmpty(mainlineNames) && StringUtils.isNotBlank(industry)) {
                reason = trimReason(reason + " · 逆主线降权");
            }
            if (buyFactor.compareTo(BigDecimal.ONE) != 0) {
                reason = trimReason(reason + " · 市场" + briefing.getStance()
                        + "仓位×" + buyFactor.setScale(2, RoundingMode.HALF_UP));
            }
            boolean offMainline = !mainHit.match && CollUtil.isNotEmpty(mainlineNames)
                    && StringUtils.isNotBlank(industry);
            String scoreExplain = buildBuyScoreExplain(signal.getStrategyId(), signal.getScore(),
                    cfCount, hot, gate, mainHit, offMainline, briefing.getStance(), buyFactor, weight);
            DecisionItemResp item = DecisionItemResp.builder()
                    .actionDate(actionDate)
                    .code(code)
                    .name(name)
                    .action("BUY")
                    .strategyId(signal.getStrategyId())
                    .reason(reason)
                    .score(score)
                    .suggestedWeight(weight)
                    .exitRule(exitRuleOf(signal.getStrategyId()))
                    .confluenceCount(cfCount)
                    .confluence(cfCount >= 2)
                    .strategies(Objects.nonNull(cf) ? cf.getStrategies() : List.of(signal.getStrategyId()))
                    .fundNote(gate.note)
                    .signalId(signal.getId())
                    .mainlineMatch(mainHit.match)
                    .mainlineName(mainHit.name)
                    .scoreExplain(scoreExplain)
                    .build();
            buys.add(item);
            // 已持仓出现买入信号时记入 covered，避免同时出现在「继续持有」
            if (alreadyHeld) {
                covered.add(code);
            }
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
                        .exitRule(stopSell)
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

        // 其余「我的持仓」→ HOLD
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
                    .reason("无新卖出信号，继续持有")
                    .score(null)
                    .suggestedWeight(null)
                    .exitRule("止损 " + holding.getStopLoss() + " / 止盈 " + holding.getTakeProfit())
                    .confluenceCount(0)
                    .confluence(false)
                    .strategies(List.of())
                    .fundNote(fundNoteOf(fundMap.get(code)))
                    .signalId(null)
                    .build();
            holds.add(item);
            covered.add(code);
        }

        buys.sort(Comparator.comparing(DecisionItemResp::getScore, Comparator.nullsLast(Comparator.reverseOrder())));
        sells.sort(Comparator.comparing(DecisionItemResp::getScore, Comparator.nullsLast(Comparator.reverseOrder())));

        // 7. 落库 daily_action（覆盖当日）
        dailyActionMapper.delete(Wrappers.<DailyAction>lambdaQuery().eq(DailyAction::getActionDate, actionDate));
        List<DecisionItemResp> all = new ArrayList<>();
        all.addAll(buys);
        all.addAll(sells);
        all.addAll(holds);
        for (DecisionItemResp item : all) {
            String strategiesCsv = null;
            if (CollUtil.isNotEmpty(item.getStrategies())) {
                strategiesCsv = String.join(",", item.getStrategies());
                if (strategiesCsv.length() > 64) {
                    strategiesCsv = strategiesCsv.substring(0, 64);
                }
            }
            Integer mainlineMatch = null;
            if (Objects.nonNull(item.getMainlineMatch())) {
                mainlineMatch = Boolean.TRUE.equals(item.getMainlineMatch()) ? 1 : 0;
            }
            String scoreExplain = item.getScoreExplain();
            if (StringUtils.isNotBlank(scoreExplain) && scoreExplain.length() > 512) {
                scoreExplain = scoreExplain.substring(0, 512);
            }
            DailyAction row = DailyAction.builder()
                    .actionDate(actionDate)
                    .code(item.getCode())
                    .name(item.getName())
                    .action(item.getAction())
                    .strategyId(item.getStrategyId())
                    .reason(trimReason(item.getReason()))
                    .suggestedWeight(item.getSuggestedWeight())
                    .exitRule(item.getExitRule())
                    .score(item.getScore())
                    .confluenceCount(item.getConfluenceCount())
                    .fundNote(item.getFundNote())
                    .signalId(item.getSignalId())
                    .mainlineMatch(mainlineMatch)
                    .mainlineName(item.getMainlineName())
                    .scoreExplain(scoreExplain)
                    .strategiesCsv(strategiesCsv)
                    .createTime(now)
                    .updateTime(now)
                    .deleted(0)
                    .build();
            dailyActionMapper.insert(row);
            item.setId(row.getId());
        }
        saveBriefingSnapshot(actionDate, briefing);

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

        return DecisionTodayResp.builder()
                .actionDate(actionDate)
                .groupName(groupName)
                .universeCount(universeCount)
                .buys(buys)
                .sells(sells)
                .holds(holds)
                .items(all)
                .riskNote(riskNote)
                .marketBriefing(briefing)
                .message("市场「" + briefing.getStance() + "」· 买入 " + buys.size()
                        + " · 卖出 " + sells.size()
                        + " · 持有 " + holds.size()
                        + " · 股票池 " + universeCount
                        + " · 持仓 " + holdings.size()
                        + " · 热点扩扫 " + hotScanCount
                        + " · 扫描 " + signalCodes.size())
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
        MarketBriefingResp briefing = loadBriefingSnapshot(actionDate);
        if (Objects.isNull(briefing)) {
            briefing = marketBriefingService.briefing();
        }
        List<String> mainlineNames = resolveMainlineNames(briefing);
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
        String message = CollUtil.isEmpty(all)
                ? "今日尚无决策，请点击「一键生成决策」；下方市场简报已可参考"
                : "市场「" + briefing.getStance() + "」· 买 " + buys.size()
                + " / 卖 " + sells.size() + " / 持有 " + holds.size();
        return DecisionTodayResp.builder()
                .actionDate(actionDate)
                .groupName(group)
                .universeCount(null)
                .buys(buys)
                .sells(sells)
                .holds(holds)
                .items(all)
                .riskNote(Objects.nonNull(briefing.getPositionAdvice()) ? briefing.getPositionAdvice() : null)
                .marketBriefing(briefing)
                .message(message)
                .build();
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

    private BigDecimal suggestWeight(boolean confluence, boolean fundOk, BigDecimal singleLimit) {
        BigDecimal weight = BASE_WEIGHT;
        if (confluence && fundOk) {
            weight = singleLimit.min(new BigDecimal("0.15"));
        } else if (confluence) {
            weight = CONFLUENCE_WEIGHT;
        }
        return weight.min(singleLimit).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal baseScore(BigDecimal score) {
        return Objects.nonNull(score) ? score : new BigDecimal("60");
    }

    private BigDecimal applyHotBoost(BigDecimal score, HotConfluenceItem hot) {
        if (Objects.isNull(hot) || Objects.isNull(hot.getSourceCount()) || hot.getSourceCount() < 2) {
            return score;
        }
        BigDecimal result = score.add(SCORE_BOOST_HOT);
        if (hot.getSourceCount() >= 3) {
            result = result.add(SCORE_BOOST_HOT_TRIPLE);
        }
        return result;
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

    private String stopTakeReason(MyHolding holding, BigDecimal price) {
        if (Objects.isNull(price)) {
            return null;
        }
        if (Objects.nonNull(holding.getStopLoss()) && price.compareTo(holding.getStopLoss()) <= 0) {
            return "现价触及止损 " + holding.getStopLoss();
        }
        if (Objects.nonNull(holding.getTakeProfit()) && price.compareTo(holding.getTakeProfit()) >= 0) {
            return "现价触及止盈 " + holding.getTakeProfit();
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

    private String buildBuyScoreExplain(String strategyId, BigDecimal signalScore, int cfCount,
                                        HotConfluenceItem hot, FundGate gate, MainlineMatcher.Hit mainHit,
                                        boolean offMainline, String stance, BigDecimal buyFactor,
                                        BigDecimal weight) {
        StringBuilder sb = new StringBuilder();
        sb.append("策略").append(strategyId)
                .append(" 基分").append(baseScore(signalScore).setScale(0, RoundingMode.HALF_UP));
        if (cfCount >= 2) {
            sb.append(" · 共振+").append(SCORE_BOOST_CONFLUENCE.toPlainString());
        }
        if (Objects.nonNull(hot) && Objects.nonNull(hot.getSourceCount())) {
            if (hot.getSourceCount() >= 3) {
                sb.append(" · 热点+").append(SCORE_BOOST_HOT.add(SCORE_BOOST_HOT_TRIPLE).toPlainString());
            } else if (hot.getSourceCount() >= 2) {
                sb.append(" · 热点+").append(SCORE_BOOST_HOT.toPlainString());
            }
        }
        if (Objects.nonNull(gate) && gate.weak) {
            sb.append(" · 基本面-").append(SCORE_PENALTY_FUND.toPlainString());
        }
        if (Objects.nonNull(mainHit) && mainHit.match) {
            sb.append(" · 主线+").append(SCORE_BOOST_MAINLINE.toPlainString());
        } else if (offMainline) {
            sb.append(" · 逆主线-").append(SCORE_PENALTY_OFF_MAINLINE.toPlainString());
        }
        if ("防守".equals(stance)) {
            sb.append(" · 防守-6");
        } else if ("进攻".equals(stance) && cfCount >= 2) {
            sb.append(" · 进攻共振+3");
        }
        sb.append(" · 仓位").append(pctText(weight));
        if (Objects.nonNull(buyFactor) && buyFactor.compareTo(BigDecimal.ONE) != 0) {
            sb.append("（市场×").append(buyFactor.setScale(2, RoundingMode.HALF_UP)).append("）");
        }
        return sb.toString();
    }

    private List<String> resolveMainlineNames(MarketBriefingResp briefing) {
        List<String> names = new ArrayList<>();
        if (Objects.nonNull(briefing) && CollUtil.isNotEmpty(briefing.getHotThemes())) {
            for (String theme : briefing.getHotThemes()) {
                if (StringUtils.isNotBlank(theme) && names.size() < 8) {
                    names.add(theme.trim());
                }
            }
        }
        if (CollUtil.isEmpty(names)) {
            try {
                List<SectorBoardItem> mainline = sectorBoardService.mainline(null, 8);
                for (SectorBoardItem item : mainline) {
                    if (Objects.nonNull(item) && StringUtils.isNotBlank(item.getName()) && names.size() < 8) {
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
