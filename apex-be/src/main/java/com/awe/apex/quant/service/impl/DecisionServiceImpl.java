package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.DecisionItemResp;
import com.awe.apex.quant.domain.dto.DecisionRunReq;
import com.awe.apex.quant.domain.dto.DecisionTodayResp;
import com.awe.apex.quant.domain.dto.HotConfluenceItem;
import com.awe.apex.quant.domain.dto.MarketBriefingResp;
import com.awe.apex.quant.domain.dto.RiskOverviewResp;
import com.awe.apex.quant.domain.dto.SignalConfluenceItem;
import com.awe.apex.quant.domain.dto.SignalConfluenceResp;
import com.awe.apex.quant.domain.dto.SignalRunReq;
import com.awe.apex.quant.domain.dto.UniverseRefreshReq;
import com.awe.apex.quant.domain.dto.UniverseRefreshResp;
import com.awe.apex.quant.domain.entity.DailyAction;
import com.awe.apex.quant.domain.entity.MyHolding;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.domain.entity.StockFinAbstract;
import com.awe.apex.quant.domain.entity.StockFinIndicator;
import com.awe.apex.quant.domain.entity.StrategySignalEntity;
import com.awe.apex.quant.domain.entity.UniverseSnapshot;
import com.awe.apex.quant.mapper.DailyActionMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.mapper.StockFinAbstractMapper;
import com.awe.apex.quant.mapper.StockFinIndicatorMapper;
import com.awe.apex.quant.service.IDecisionService;
import com.awe.apex.quant.service.IHotService;
import com.awe.apex.quant.service.IMarketBriefingService;
import com.awe.apex.quant.service.IMyHoldingService;
import com.awe.apex.quant.service.IPaperService;
import com.awe.apex.quant.service.IRiskService;
import com.awe.apex.quant.service.ISignalService;
import com.awe.apex.quant.service.IUniverseService;
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
                DecisionItemResp item = DecisionItemResp.builder()
                        .actionDate(actionDate)
                        .code(code)
                        .name(name)
                        .action("SELL")
                        .strategyId(signal.getStrategyId())
                        .reason(reason)
                        .score(score)
                        .suggestedWeight(null)
                        .exitRule("信号卖出")
                        .confluenceCount(cfCount)
                        .confluence(cfCount >= 2)
                        .strategies(Objects.nonNull(cf) ? cf.getStrategies() : List.of(signal.getStrategyId()))
                        .fundNote(fundNoteOf(fundMap.get(code)))
                        .signalId(signal.getId())
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
            boolean hotOk = Objects.nonNull(hot) && Objects.nonNull(hot.getSourceCount()) && hot.getSourceCount() >= 2;
            BigDecimal weight = suggestWeight(cfCount >= 2 || hotOk, !gate.weak, singleLimit);
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
            if (buyFactor.compareTo(BigDecimal.ONE) != 0) {
                reason = trimReason(reason + " · 市场" + briefing.getStance()
                        + "仓位×" + buyFactor.setScale(2, RoundingMode.HALF_UP));
            }
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
                        .strategyId(null)
                        .reason(stopSell)
                        .score(new BigDecimal("90"))
                        .suggestedWeight(null)
                        .exitRule(stopSell)
                        .confluenceCount(0)
                        .confluence(false)
                        .strategies(List.of())
                        .fundNote(fundNoteOf(fundMap.get(code)))
                        .signalId(null)
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
                    .createTime(now)
                    .updateTime(now)
                    .deleted(0)
                    .build();
            dailyActionMapper.insert(row);
            item.setId(row.getId());
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
        List<DecisionItemResp> buys = new ArrayList<>();
        List<DecisionItemResp> sells = new ArrayList<>();
        List<DecisionItemResp> holds = new ArrayList<>();
        List<DecisionItemResp> all = new ArrayList<>();
        for (DailyAction row : rows) {
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
                    .strategies(StringUtils.isNotBlank(row.getStrategyId()) ? List.of(row.getStrategyId()) : List.of())
                    .fundNote(row.getFundNote())
                    .signalId(row.getSignalId())
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
        MarketBriefingResp briefing = marketBriefingService.briefing();
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
        for (String code : codes) {
            StockFinAbstract abs = stockFinAbstractMapper.selectOne(Wrappers.<StockFinAbstract>lambdaQuery()
                    .eq(StockFinAbstract::getCode, code)
                    .orderByDesc(StockFinAbstract::getReportDate)
                    .last("LIMIT 1"));
            StockFinIndicator ind = null;
            if (Objects.isNull(abs)) {
                ind = stockFinIndicatorMapper.selectOne(Wrappers.<StockFinIndicator>lambdaQuery()
                        .eq(StockFinIndicator::getCode, code)
                        .orderByDesc(StockFinIndicator::getReportDate)
                        .last("LIMIT 1"));
            }
            if (Objects.nonNull(abs)) {
                map.put(code, new FundSnapshot(abs.getReportDate(), abs.getRoe(), abs.getDebtRatio(), abs.getNetMargin()));
            } else if (Objects.nonNull(ind)) {
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
            return "跌破MA20离场";
        }
        if ("S2".equals(strategyId)) {
            return "RSI>70或跌破MA60离场";
        }
        if ("S3".equals(strategyId)) {
            return "跌破突破日低点离场";
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
