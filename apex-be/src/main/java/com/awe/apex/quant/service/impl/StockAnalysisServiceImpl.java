package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.ai.AiChatProperties;
import com.awe.apex.quant.ai.KimiChatClient;
import com.awe.apex.quant.domain.dto.DecisionItemResp;
import com.awe.apex.quant.domain.dto.DecisionTodayResp;
import com.awe.apex.quant.domain.dto.HotConfluenceItem;
import com.awe.apex.quant.domain.dto.ObserveTechSignal;
import com.awe.apex.quant.domain.dto.SectorBoardItem;
import com.awe.apex.quant.domain.dto.SectorBoardResp;
import com.awe.apex.quant.domain.dto.SignalItemResp;
import com.awe.apex.quant.domain.dto.SignalRunReq;
import com.awe.apex.quant.domain.dto.BarSyncReq;
import com.awe.apex.quant.domain.dto.StockAnalysisAiResp;
import com.awe.apex.quant.domain.dto.StockAnalysisCapitalResp;
import com.awe.apex.quant.domain.dto.StockAnalysisFreshnessResp;
import com.awe.apex.quant.domain.dto.StockAnalysisResp;
import com.awe.apex.quant.domain.dto.StockAnalysisTechResp;
import com.awe.apex.quant.domain.dto.StockDetailResp;
import com.awe.apex.quant.domain.dto.TechRegimeResult;
import com.awe.apex.quant.domain.dto.ValuationResp;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.entity.MarketNews;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.domain.entity.StrategySignalEntity;
import com.awe.apex.quant.indicator.IndicatorUtils;
import com.awe.apex.quant.indicator.TechRegimeEvaluator;
import com.awe.apex.quant.indicator.TechSignalEvaluator;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.MarketNewsMapper;
import com.awe.apex.quant.market.TradingCalendar;
import com.awe.apex.quant.strategy.BarSeries;
import com.awe.apex.quant.service.IBarDailyService;
import com.awe.apex.quant.service.IDecisionService;
import com.awe.apex.quant.service.IHotService;
import com.awe.apex.quant.service.ISectorBoardService;
import com.awe.apex.quant.service.ISignalService;
import com.awe.apex.quant.service.IStockAnalysisService;
import com.awe.apex.quant.service.IStockService;
import com.awe.apex.quant.service.IValuationService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 个股综合研判：聚合技术、估值、板块资金/热点与策略信号
 */
@Slf4j
@Service
public class StockAnalysisServiceImpl implements IStockAnalysisService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final String AI_DISCLAIMER = "AI 解读基于本地规则结果与当日快照，非实时全市场资讯；不构成投资建议。";

    private final ConcurrentHashMap<String, CachedAi> aiCache = new ConcurrentHashMap<>();

    @Resource
    private IStockService stockService;

    @Resource
    private IValuationService valuationService;

    @Resource
    private ISignalService signalService;

    @Resource
    private IDecisionService decisionService;

    @Resource
    private IHotService hotService;

    @Resource
    private ISectorBoardService sectorBoardService;

    @Resource
    private TechSignalEvaluator techSignalEvaluator;

    @Resource
    private TechRegimeEvaluator techRegimeEvaluator;

    @Resource
    private KimiChatClient kimiChatClient;

    @Resource
    private AiChatProperties aiChatProperties;

    @Resource
    private MarketNewsMapper marketNewsMapper;

    @Resource
    private IBarDailyService barDailyService;

    @Resource
    private BarDailyMapper barDailyMapper;

    /**
     * 一页汇总：技术面 + 估值 + 资金情绪 + 策略结论
     *
     * @param code     证券代码
     * @param side     BUY/SELL，默认 BUY
     * @param barLimit K 线条数
     * @return 综合研判
     */
    @Override
    public StockAnalysisResp analyze(String code, String side, Integer barLimit) {
        return analyze(code, side, barLimit, false, false);
    }

    /**
     * 一页汇总，可附加 AI 实时解读
     *
     * @param code     证券代码
     * @param side     BUY/SELL
     * @param barLimit K 线条数
     * @param withAi   是否调用大模型
     * @param forceAi  是否忽略缓存强制重跑 AI
     * @return 综合研判
     */
    @Override
    public StockAnalysisResp analyze(String code, String side, Integer barLimit, boolean withAi, boolean forceAi) {
        if (StringUtils.isBlank(code)) {
            throw new BusinessException("证券代码不能为空");
        }
        String pure = code.trim().toUpperCase().replaceAll("^(SH|SZ|BJ)", "");
        String evalSide = StringUtils.isNotBlank(side) ? side.trim().toUpperCase() : "BUY";
        if (!"BUY".equals(evalSide) && !"SELL".equals(evalSide)) {
            evalSide = "BUY";
        }
        int limit = Objects.nonNull(barLimit) ? Math.max(60, Math.min(barLimit, 250)) : 120;
        LocalDate expectedTradeDate = TradingCalendar.latestTradingDayOnOrBefore(LocalDate.now());

        boolean quoteRefreshed = false;
        boolean barsSynced = false;
        // 规则研判也会尽量补日线；AI 路径额外刷现价
        barsSynced = ensureRecentBars(pure, expectedTradeDate);
        if (withAi) {
            try {
                stockService.syncBasic(pure);
                quoteRefreshed = true;
            } catch (Exception ex) {
                log.debug("综合研判刷新行情失败 code={}: {}", pure, ex.getMessage());
            }
        } else {
            try {
                stockService.syncBasic(pure);
                quoteRefreshed = true;
            } catch (Exception ex) {
                log.debug("综合研判轻量刷价失败 code={}: {}", pure, ex.getMessage());
            }
        }

        // 1. 行情与日线
        StockDetailResp detail = stockService.detail(pure, limit, false);
        if (Objects.isNull(detail) || Objects.isNull(detail.getBasic())) {
            throw new BusinessException("未找到股票 " + pure);
        }
        StockBasic basic = detail.getBasic();
        List<BarDaily> bars = detail.getBars();
        StockAnalysisFreshnessResp freshness = buildFreshness(bars, expectedTradeDate, barsSynced, quoteRefreshed);

        // 2. 技术面
        StockAnalysisTechResp tech = buildTech(evalSide, bars, detail);

        // 3. 估值
        ValuationResp valuation = null;
        try {
            valuation = valuationService.evaluate(pure);
        } catch (Exception ex) {
            log.warn("综合分析估值失败 code={}: {}", pure, ex.getMessage());
        }

        // 4. 资金与情绪（板块资金 + 热点；暂无个股主力流水）
        StockAnalysisCapitalResp capital = buildCapital(basic, detail.getVolumeRatio());

        // 5. 策略信号（单票）
        List<SignalItemResp> signals = runSignals(pure);

        // 6. 今日决策（若有）
        DecisionItemResp decision = findTodayDecision(pure);

        // 7. 综合结论
        StockAnalysisResp resp = buildConclusion(basic, tech, valuation, capital, signals, decision, detail);
        resp.setFreshness(freshness);
        String baseNote = resp.getDataNote();
        resp.setDataNote((StringUtils.isBlank(baseNote) ? "" : baseNote + " ")
                + (StringUtils.isBlank(freshness.getNote()) ? "" : freshness.getNote()));
        if (withAi) {
            resp.setAi(buildAiBrief(resp, quoteRefreshed, forceAi));
        } else if (kimiChatClient.available()) {
            resp.setAi(StockAnalysisAiResp.builder()
                    .configured(true)
                    .disclaimer(AI_DISCLAIMER)
                    .build());
        } else {
            resp.setAi(StockAnalysisAiResp.builder()
                    .configured(false)
                    .disclaimer("未配置 apex.ai.api-key，AI 解读不可用。")
                    .build());
        }
        return resp;
    }

    /**
     * 日线滞后则补近约 90 个自然日
     */
    private boolean ensureRecentBars(String code, LocalDate expectedTradeDate) {
        try {
            BarDaily latest = barDailyMapper.selectOne(Wrappers.<BarDaily>lambdaQuery()
                    .eq(BarDaily::getCode, code)
                    .orderByDesc(BarDaily::getTradeDate)
                    .last("LIMIT 1"));
            Long cnt = barDailyMapper.selectCount(Wrappers.<BarDaily>lambdaQuery()
                    .eq(BarDaily::getCode, code));
            boolean need = Objects.isNull(latest)
                    || Objects.isNull(latest.getTradeDate())
                    || latest.getTradeDate().isBefore(expectedTradeDate)
                    || Objects.isNull(cnt)
                    || cnt < 35;
            if (!need) {
                return false;
            }
            BarSyncReq syncReq = new BarSyncReq();
            syncReq.setCodes(List.of(code));
            syncReq.setBeginDate(LocalDate.now().minusDays(120).toString());
            barDailyService.syncBars(syncReq);
            log.info("综合研判已补日线 code={} expected={}", code, expectedTradeDate);
            return true;
        } catch (Exception ex) {
            log.warn("综合研判补日线失败 code={}: {}", code, ex.getMessage());
            return false;
        }
    }

    private StockAnalysisFreshnessResp buildFreshness(List<BarDaily> bars,
                                                      LocalDate expectedTradeDate,
                                                      boolean barsSynced,
                                                      boolean quoteRefreshed) {
        LocalDate lastBar = null;
        int barCount = 0;
        if (CollUtil.isNotEmpty(bars)) {
            barCount = bars.size();
            BarDaily last = bars.get(bars.size() - 1);
            if (Objects.nonNull(last)) {
                lastBar = last.getTradeDate();
            }
        }
        boolean stale = Objects.isNull(lastBar)
                || Objects.isNull(expectedTradeDate)
                || lastBar.isBefore(expectedTradeDate);
        String note;
        if (Objects.isNull(lastBar)) {
            note = "本地暂无日线，技术结构可能不完整。";
        } else if (stale) {
            note = "日线最后交易日 " + lastBar + "，期望 " + expectedTradeDate + "，仍有滞后。";
        } else if (barsSynced) {
            note = "已补齐日线至 " + lastBar + "。";
        } else {
            note = "日线新鲜（至 " + lastBar + "）。";
        }
        if (quoteRefreshed) {
            note = note + " 现价已刷新快照。";
        }
        return StockAnalysisFreshnessResp.builder()
                .lastBarDate(lastBar)
                .expectedTradeDate(expectedTradeDate)
                .barCount(barCount)
                .barsSynced(barsSynced)
                .quoteRefreshed(quoteRefreshed)
                .barsStale(stale)
                .note(note)
                .build();
    }

    private StockAnalysisTechResp buildTech(String side, List<BarDaily> bars, StockDetailResp detail) {
        // 结构状态机以多头结构为主；SELL 侧仍附带对应雷达明细供展示
        TechRegimeResult regime = techRegimeEvaluator.evaluate(
                bars, detail.getRs20VsHs300(), detail.getRs60VsHs300());
        List<ObserveTechSignal> signals = "SELL".equals(side)
                ? techSignalEvaluator.evaluate("SELL", bars)
                : regime.getRadarSignals();
        int hit = 0;
        if (CollUtil.isNotEmpty(signals)) {
            for (ObserveTechSignal s : signals) {
                if (Boolean.TRUE.equals(s.getHit())) {
                    hit++;
                }
            }
        }
        int total = CollUtil.isEmpty(signals) ? 0 : signals.size();
        BigDecimal hitRate = total > 0
                ? BigDecimal.valueOf(hit * 100.0 / total).setScale(1, RoundingMode.HALF_UP)
                : ZERO;

        BigDecimal rsi14 = null;
        BigDecimal ma5 = null;
        BigDecimal ma20 = null;
        BigDecimal atr14 = techSignalEvaluator.calcAtr14(bars);
        BigDecimal atrPct = null;
        if (CollUtil.isNotEmpty(bars) && bars.size() >= 35) {
            BarSeries series = BarSeries.from(bars);
            int i = series.size() - 1;
            List<BigDecimal> closes = series.getCloses();
            rsi14 = IndicatorUtils.rsi(closes, 14, i);
            ma5 = IndicatorUtils.ma(closes, 5, i);
            ma20 = IndicatorUtils.ma(closes, 20, i);
            BigDecimal close = closes.get(i);
            if (Objects.nonNull(atr14) && Objects.nonNull(close) && close.signum() > 0) {
                atrPct = atr14.divide(close, 6, RoundingMode.HALF_UP)
                        .multiply(HUNDRED)
                        .setScale(2, RoundingMode.HALF_UP);
            }
        }

        String summary = regime.getSummary();
        if (TechRegimeEvaluator.REGIME_INSUFFICIENT.equals(regime.getRegime())) {
            summary = "日线不足，技术结构暂无法完整评估（建议同步日线）";
        }

        return StockAnalysisTechResp.builder()
                .side(side)
                .signals(signals)
                .hitCount(hit)
                .total(total)
                .hitRate(hitRate)
                .summary(summary)
                .regime(regime.getRegime())
                .regimeLabel(regime.getRegimeLabel())
                .grade(regime.getGrade())
                .rsi14(Objects.nonNull(rsi14) ? rsi14.setScale(1, RoundingMode.HALF_UP) : null)
                .atr14(atr14)
                .atrPct(atrPct)
                .ma5(ma5)
                .ma20(ma20)
                .volumeRatio(detail.getVolumeRatio())
                .rs20VsHs300(detail.getRs20VsHs300())
                .rs60VsHs300(detail.getRs60VsHs300())
                .build();
    }

    private StockAnalysisCapitalResp buildCapital(StockBasic basic, BigDecimal volumeRatio) {
        String volumeNote;
        if (Objects.isNull(volumeRatio)) {
            volumeNote = "量比暂缺";
        } else if (volumeRatio.compareTo(new BigDecimal("1.5")) >= 0) {
            volumeNote = "放量（量比≥1.5），短线资金参与度上升";
        } else if (volumeRatio.compareTo(new BigDecimal("0.8")) <= 0) {
            volumeNote = "缩量（量比≤0.8），观望资金偏多";
        } else {
            volumeNote = "量能大致正常";
        }

        SectorBoardItem sector = matchSector(basic.getIndustry());
        String sectorNote = "未匹配到行业板块资金数据";
        if (Objects.nonNull(sector)) {
            BigDecimal main = sector.getMainNetInflow();
            if (Objects.nonNull(main) && main.signum() > 0) {
                sectorNote = "所属板块主力净流入为正，板块资金环境偏积极";
            } else if (Objects.nonNull(main) && main.signum() < 0) {
                sectorNote = "所属板块主力净流出，板块资金偏谨慎";
            } else {
                sectorNote = "板块资金中性";
            }
        }

        HotConfluenceItem hot = null;
        try {
            Map<String, HotConfluenceItem> map = hotService.confluenceMap(30);
            if (Objects.nonNull(map)) {
                hot = map.get(basic.getCode());
            }
        } catch (Exception ex) {
            log.debug("热点共振查询失败: {}", ex.getMessage());
        }

        StringBuilder summary = new StringBuilder();
        summary.append(volumeNote);
        if (Objects.nonNull(sector)) {
            summary.append("；").append(sectorNote);
        }
        if (Objects.nonNull(hot) && Objects.nonNull(hot.getSourceCount()) && hot.getSourceCount() > 0) {
            summary.append("；多源热点共振 ").append(hot.getSourceCount()).append(" 平台");
        } else {
            summary.append("；暂未进入多源热点榜");
        }
        summary.append("。说明：系统暂无个股主力流水，资金维以量能+板块资金+热点代理。");

        return StockAnalysisCapitalResp.builder()
                .volumeRatio(volumeRatio)
                .volumeNote(volumeNote)
                .sectorName(Objects.nonNull(sector) ? sector.getName() : basic.getIndustry())
                .sectorType(Objects.nonNull(sector) ? sector.getBoardType() : null)
                .sectorPctChg(Objects.nonNull(sector) ? sector.getPctChg() : null)
                .sectorNetInflow(Objects.nonNull(sector) ? sector.getNetInflow() : null)
                .sectorMainNetInflow(Objects.nonNull(sector) ? sector.getMainNetInflow() : null)
                .sectorNote(sectorNote)
                .hotHit(Objects.nonNull(hot))
                .hotSourceCount(Objects.nonNull(hot) ? hot.getSourceCount() : 0)
                .hotSources(Objects.nonNull(hot) ? hot.getSources() : List.of())
                .hotBestRank(Objects.nonNull(hot) ? hot.getBestRank() : null)
                .summary(summary.toString())
                .build();
    }

    private SectorBoardItem matchSector(String industry) {
        if (StringUtils.isBlank(industry)) {
            return null;
        }
        try {
            SectorBoardResp board = sectorBoardService.board("INDUSTRY", "pctChg", "desc", 80, null);
            if (Objects.isNull(board) || CollUtil.isEmpty(board.getItems())) {
                return null;
            }
            String key = industry.trim();
            SectorBoardItem best = null;
            for (SectorBoardItem item : board.getItems()) {
                if (Objects.isNull(item) || StringUtils.isBlank(item.getName())) {
                    continue;
                }
                String name = item.getName();
                if (key.equals(name) || key.contains(name) || name.contains(key)) {
                    best = item;
                    break;
                }
            }
            return best;
        } catch (Exception ex) {
            log.debug("匹配行业板块失败 industry={}: {}", industry, ex.getMessage());
            return null;
        }
    }

    private List<SignalItemResp> runSignals(String code) {
        try {
            SignalRunReq req = new SignalRunReq();
            req.setCodes(List.of(code));
            req.setUseUniverse(false);
            List<StrategySignalEntity> entities = signalService.run(req);
            if (CollUtil.isEmpty(entities)) {
                return List.of();
            }
            List<StrategySignalEntity> mine = new ArrayList<>();
            for (StrategySignalEntity e : entities) {
                if (Objects.nonNull(e) && code.equals(e.getCode())) {
                    mine.add(e);
                }
            }
            return signalService.toItemRespList(mine);
        } catch (Exception ex) {
            log.warn("综合分析跑信号失败 code={}: {}", code, ex.getMessage());
            return List.of();
        }
    }

    private DecisionItemResp findTodayDecision(String code) {
        try {
            DecisionTodayResp today = decisionService.today(null, null);
            if (Objects.isNull(today)) {
                return null;
            }
            List<DecisionItemResp> all = new ArrayList<>();
            if (CollUtil.isNotEmpty(today.getItems())) {
                all.addAll(today.getItems());
            } else {
                if (CollUtil.isNotEmpty(today.getBuys())) {
                    all.addAll(today.getBuys());
                }
                if (CollUtil.isNotEmpty(today.getSells())) {
                    all.addAll(today.getSells());
                }
                if (CollUtil.isNotEmpty(today.getHolds())) {
                    all.addAll(today.getHolds());
                }
            }
            for (DecisionItemResp item : all) {
                if (Objects.nonNull(item) && code.equals(item.getCode())) {
                    return item;
                }
            }
        } catch (Exception ex) {
            log.debug("读取今日决策失败: {}", ex.getMessage());
        }
        return null;
    }

    private StockAnalysisResp buildConclusion(StockBasic basic,
                                              StockAnalysisTechResp tech,
                                              ValuationResp valuation,
                                              StockAnalysisCapitalResp capital,
                                              List<SignalItemResp> signals,
                                              DecisionItemResp decision,
                                              StockDetailResp detail) {
        List<String> scoreExplain = new ArrayList<>();
        List<String> bull = new ArrayList<>();
        List<String> bear = new ArrayList<>();
        List<String> risks = new ArrayList<>();

        // 技术 0~35：以结构状态机 grade 为主，雷达命中率作附录说明
        BigDecimal techScore = ZERO;
        if (Objects.nonNull(tech) && StringUtils.isNotBlank(tech.getRegime())
                && !TechRegimeEvaluator.REGIME_INSUFFICIENT.equals(tech.getRegime())) {
            BigDecimal gradeScore;
            if (TechRegimeEvaluator.GRADE_STRONG.equals(tech.getGrade())) {
                gradeScore = new BigDecimal("78");
            } else if (TechRegimeEvaluator.GRADE_WEAK.equals(tech.getGrade())) {
                gradeScore = new BigDecimal("28");
            } else {
                gradeScore = new BigDecimal("52");
            }
            techScore = gradeScore.multiply(new BigDecimal("0.35")).setScale(1, RoundingMode.HALF_UP);
            scoreExplain.add("技术结构 " + tech.getRegimeLabel()
                    + "（雷达 " + tech.getHitCount() + "/" + tech.getTotal() + "）→ " + techScore + " 分");
            if (TechRegimeEvaluator.GRADE_STRONG.equals(tech.getGrade())
                    || TechRegimeEvaluator.REGIME_TREND_HOLD.equals(tech.getRegime())
                    || TechRegimeEvaluator.REGIME_PULLBACK_WATCH.equals(tech.getRegime())) {
                bull.add(tech.getSummary());
            } else if (TechRegimeEvaluator.GRADE_WEAK.equals(tech.getGrade())
                    || TechRegimeEvaluator.REGIME_BREAKDOWN_CUT.equals(tech.getRegime())) {
                bear.add(tech.getSummary());
            }
            if (Objects.nonNull(tech.getRs20VsHs300()) && tech.getRs20VsHs300().compareTo(ZERO) > 0) {
                bull.add("近20日相对沪深300超额 " + tech.getRs20VsHs300() + "pp");
            } else if (Objects.nonNull(tech.getRs20VsHs300()) && tech.getRs20VsHs300().compareTo(new BigDecimal("-3")) < 0) {
                bear.add("近20日相对大盘偏弱 " + tech.getRs20VsHs300() + "pp");
            }
        } else {
            scoreExplain.add("技术面数据不足 → 0 分");
            risks.add("日线不足，技术评估降权");
        }

        // 估值 0~35
        BigDecimal valScore = ZERO;
        if (Objects.nonNull(valuation) && Objects.nonNull(valuation.getScore())) {
            valScore = valuation.getScore().multiply(new BigDecimal("0.35")).setScale(1, RoundingMode.HALF_UP);
            scoreExplain.add("估值评分 " + valuation.getScore() + " → " + valScore + " 分");
            if (CollUtil.isNotEmpty(valuation.getBullPoints())) {
                for (String p : valuation.getBullPoints()) {
                    if (bull.size() < 8) {
                        bull.add(p);
                    }
                }
            }
            if (CollUtil.isNotEmpty(valuation.getBearPoints())) {
                for (String p : valuation.getBearPoints()) {
                    if (bear.size() < 8) {
                        bear.add(p);
                    }
                }
            }
            if ("OVERVALUED".equals(valuation.getLevel()) || "SLIGHTLY_EXPENSIVE".equals(valuation.getLevel())) {
                risks.add("估值偏贵：" + valuation.getLevelLabel());
            }
        } else {
            scoreExplain.add("估值暂不可用 → 0 分");
            risks.add("估值数据不足");
        }

        // 策略信号 0~15：买入加分、卖出扣分，口径与要点一致
        BigDecimal signalScore = ZERO;
        BigDecimal bestBuy = null;
        BigDecimal bestSell = null;
        if (CollUtil.isNotEmpty(signals)) {
            for (SignalItemResp s : signals) {
                if (Objects.isNull(s) || Objects.isNull(s.getScore())) {
                    continue;
                }
                if ("BUY".equalsIgnoreCase(s.getSide())) {
                    if (Objects.isNull(bestBuy) || s.getScore().compareTo(bestBuy) > 0) {
                        bestBuy = s.getScore();
                    }
                    bull.add("策略 " + s.getStrategyId() + " 给出 BUY（分 " + s.getScore() + "）");
                } else if ("SELL".equalsIgnoreCase(s.getSide())) {
                    if (Objects.isNull(bestSell) || s.getScore().compareTo(bestSell) > 0) {
                        bestSell = s.getScore();
                    }
                    bear.add("策略 " + s.getStrategyId() + " 给出 SELL（分 " + s.getScore() + "）");
                }
            }
            if (Objects.nonNull(bestBuy) && Objects.isNull(bestSell)) {
                signalScore = bestBuy.multiply(new BigDecimal("0.15")).setScale(1, RoundingMode.HALF_UP);
            } else if (Objects.nonNull(bestSell) && Objects.isNull(bestBuy)) {
                // 仅卖出：从中性档往下扣，避免「要点写卖出、得分却为 0」的口径分裂
                signalScore = new BigDecimal("7.5")
                        .subtract(bestSell.multiply(new BigDecimal("0.08")))
                        .max(ZERO)
                        .min(new BigDecimal("15"))
                        .setScale(1, RoundingMode.HALF_UP);
            } else if (Objects.nonNull(bestBuy)) {
                signalScore = bestBuy.multiply(new BigDecimal("0.15"))
                        .subtract(bestSell.multiply(new BigDecimal("0.06")))
                        .max(ZERO)
                        .min(new BigDecimal("15"))
                        .setScale(1, RoundingMode.HALF_UP);
            }
            scoreExplain.add("策略信号 → " + signalScore + " 分"
                    + (Objects.nonNull(bestSell) ? "（含卖出压力）" : ""));
        } else {
            scoreExplain.add("当日无策略信号 → 0 分");
        }

        // 资金/情绪 0~15
        BigDecimal capitalScore = new BigDecimal("5");
        if (Objects.nonNull(capital)) {
            if (Objects.nonNull(capital.getVolumeRatio())
                    && capital.getVolumeRatio().compareTo(new BigDecimal("1.5")) >= 0
                    && Objects.nonNull(basic.getPctChg())
                    && basic.getPctChg().signum() >= 0) {
                capitalScore = capitalScore.add(new BigDecimal("4"));
                bull.add(capital.getVolumeNote());
            } else if (Objects.nonNull(capital.getVolumeRatio())
                    && capital.getVolumeRatio().compareTo(new BigDecimal("1.5")) >= 0
                    && Objects.nonNull(basic.getPctChg())
                    && basic.getPctChg().signum() < 0) {
                capitalScore = capitalScore.subtract(new BigDecimal("2"));
                bear.add("放量下跌，抛压需警惕");
                risks.add("放量下跌");
            }
            if (Objects.nonNull(capital.getSectorMainNetInflow())
                    && capital.getSectorMainNetInflow().signum() > 0) {
                capitalScore = capitalScore.add(new BigDecimal("3"));
            } else if (Objects.nonNull(capital.getSectorMainNetInflow())
                    && capital.getSectorMainNetInflow().signum() < 0) {
                capitalScore = capitalScore.subtract(new BigDecimal("2"));
            }
            if (Boolean.TRUE.equals(capital.getHotHit())
                    && Objects.nonNull(capital.getHotSourceCount())
                    && capital.getHotSourceCount() >= 2) {
                capitalScore = capitalScore.add(new BigDecimal("3"));
                bull.add("热点共振 " + capital.getHotSourceCount() + " 源");
                risks.add("情绪拥挤时注意分歧回撤");
            }
        }
        if (capitalScore.compareTo(ZERO) < 0) {
            capitalScore = ZERO;
        }
        if (capitalScore.compareTo(new BigDecimal("15")) > 0) {
            capitalScore = new BigDecimal("15");
        }
        capitalScore = capitalScore.setScale(1, RoundingMode.HALF_UP);
        scoreExplain.add("资金/情绪 → " + capitalScore + " 分");

        if (Objects.nonNull(decision)) {
            bull.add("今日决策清单含该股：" + decision.getAction()
                    + (Objects.nonNull(decision.getScore()) ? ("（决策分 " + decision.getScore() + "）") : ""));
        }
        if (Boolean.TRUE.equals(detail.getNeedSyncBars())) {
            risks.add("本地日线偏少，建议先同步日线");
        }

        BigDecimal composite = techScore.add(valScore).add(signalScore).add(capitalScore)
                .setScale(1, RoundingMode.HALF_UP);
        if (composite.compareTo(HUNDRED) > 0) {
            composite = HUNDRED;
        }

        String stance;
        String actionHint;
        if (composite.compareTo(new BigDecimal("72")) >= 0) {
            stance = "积极关注";
            actionHint = "多维偏积极：可纳入观察/小仓验证，仍需设退出规则";
        } else if (composite.compareTo(new BigDecimal("58")) >= 0) {
            stance = "可跟踪";
            actionHint = "结构尚可：等待更好买点或共振确认，控制仓位";
        } else if (composite.compareTo(new BigDecimal("42")) >= 0) {
            stance = "中性观望";
            actionHint = "多空交织：不宜追涨，优先观察量价与估值边际";
        } else if (composite.compareTo(new BigDecimal("28")) >= 0) {
            stance = "谨慎";
            actionHint = "偏弱：若持仓关注风控；新建仓需更高赔率";
        } else {
            stance = "回避";
            actionHint = "多维偏空：优先回避或减仓，等待结构修复";
        }

        String summary = basic.getName() + "（" + basic.getCode() + "）综合分 "
                + composite + "，立场「" + stance + "」。"
                + (Objects.nonNull(tech) ? tech.getSummary() : "")
                + (Objects.nonNull(valuation) && StringUtils.isNotBlank(valuation.getSummary())
                ? (" 估值：" + valuation.getSummary()) : "");

        List<BigDecimal> closes = new ArrayList<>();
        if (CollUtil.isNotEmpty(detail.getBars())) {
            List<BarDaily> asc = new ArrayList<>(detail.getBars());
            asc.sort((a, b) -> {
                if (Objects.isNull(a.getTradeDate()) || Objects.isNull(b.getTradeDate())) {
                    return 0;
                }
                return a.getTradeDate().compareTo(b.getTradeDate());
            });
            for (BarDaily bar : asc) {
                if (Objects.nonNull(bar) && Objects.nonNull(bar.getClosePrice())) {
                    closes.add(bar.getClosePrice());
                }
            }
        }

        return StockAnalysisResp.builder()
                .code(basic.getCode())
                .name(basic.getName())
                .latestPrice(basic.getLatestPrice())
                .pctChg(basic.getPctChg())
                .pctChg3(periodReturnPct(closes, 3))
                .pctChg5(periodReturnPct(closes, 5))
                .pctChg20(periodReturnPct(closes, 20))
                .industry(basic.getIndustry())
                .compositeScore(composite)
                .stance(stance)
                .summary(summary)
                .actionHint(actionHint)
                .bullPoints(bull)
                .bearPoints(bear)
                .riskFlags(risks)
                .scoreExplain(scoreExplain)
                .tech(tech)
                .valuation(valuation)
                .capital(capital)
                .signals(signals)
                .decision(decision)
                .dataNote("资金维暂以量能+所属板块资金+热点代理；非投资建议，仅供研究。")
                .build();
    }

    /**
     * 近 N 日收盘涨跌幅%
     */
    private BigDecimal periodReturnPct(List<BigDecimal> closes, int lookback) {
        if (CollUtil.isEmpty(closes) || closes.size() <= lookback) {
            return null;
        }
        BigDecimal end = closes.get(closes.size() - 1);
        BigDecimal start = closes.get(closes.size() - 1 - lookback);
        if (Objects.isNull(end) || Objects.isNull(start) || start.signum() <= 0) {
            return null;
        }
        return end.subtract(start).divide(start, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    private StockAnalysisAiResp buildAiBrief(StockAnalysisResp resp, boolean quoteRefreshed, boolean forceAi) {
        if (!kimiChatClient.available()) {
            return StockAnalysisAiResp.builder()
                    .configured(false)
                    .quoteRefreshed(quoteRefreshed)
                    .disclaimer("未配置 apex.ai.api-key，AI 解读不可用。")
                    .build();
        }
        String cacheKey = buildAiCacheKey(resp);
        int ttl = Math.max(60, aiChatProperties.getSummaryCacheSeconds());
        if (!forceAi) {
            CachedAi cached = aiCache.get(cacheKey);
            if (Objects.nonNull(cached)
                    && cached.at.plusSeconds(ttl).isAfter(LocalDateTime.now())
                    && Objects.nonNull(cached.payload)
                    && StringUtils.isNotBlank(cached.payload.getBrief())) {
                StockAnalysisAiResp hit = cached.payload;
                hit.setFromCache(true);
                hit.setConfigured(true);
                hit.setQuoteRefreshed(quoteRefreshed);
                return hit;
            }
        }

        List<String> newsLines = loadRelatedNews(resp.getCode(), resp.getName());
        String system = "你是 A 股个股研究助手。只根据给定结构化事实做盘面解读，禁止编造未提供的财报数字、公告或新闻。"
                + "输出严格 JSON（不要 markdown）："
                + "{\"stance\":\"积极关注|可跟踪|中性观望|谨慎|回避\","
                + "\"brief\":\"120-180字中文解读\","
                + "\"watchPoints\":[\"关注点1\",\"关注点2\",\"关注点3\"],"
                + "\"riskNote\":\"一句话风险\"}."
                + "语气专业克制；强调本地日线/财务可能滞后，现价以快照为准。";
        StringBuilder user = new StringBuilder();
        user.append("代码：").append(resp.getCode()).append(" 名称：").append(resp.getName()).append('\n');
        user.append("现价快照：").append(resp.getLatestPrice())
                .append(" 涨跌幅%：").append(resp.getPctChg())
                .append(" 行业：").append(resp.getIndustry()).append('\n');
        if (Objects.nonNull(resp.getFreshness())) {
            user.append("数据新鲜度：日线最后=")
                    .append(resp.getFreshness().getLastBarDate())
                    .append(" 期望=")
                    .append(resp.getFreshness().getExpectedTradeDate())
                    .append(" 条数=")
                    .append(resp.getFreshness().getBarCount())
                    .append(" 滞后=")
                    .append(resp.getFreshness().getBarsStale())
                    .append('\n');
        }
        user.append("规则综合分：").append(resp.getCompositeScore())
                .append(" 规则立场：").append(resp.getStance()).append('\n');
        user.append("规则摘要：").append(resp.getSummary()).append('\n');
        user.append("行动提示：").append(resp.getActionHint()).append('\n');
        if (Objects.nonNull(resp.getTech())) {
            user.append("技术：").append(resp.getTech().getSummary())
                    .append(" RS20=").append(resp.getTech().getRs20VsHs300())
                    .append(" RSI=").append(resp.getTech().getRsi14()).append('\n');
        }
        if (Objects.nonNull(resp.getValuation())) {
            user.append("估值：").append(resp.getValuation().getSummary()).append('\n');
        }
        if (Objects.nonNull(resp.getCapital())) {
            user.append("资金/情绪：").append(resp.getCapital().getVolumeNote());
            if (StringUtils.isNotBlank(resp.getCapital().getSummary())) {
                user.append("；").append(resp.getCapital().getSummary());
            } else if (StringUtils.isNotBlank(resp.getCapital().getSectorNote())) {
                user.append("；").append(resp.getCapital().getSectorNote());
            }
            user.append('\n');
        }
        if (CollUtil.isNotEmpty(resp.getBullPoints())) {
            user.append("多头要点：").append(String.join("；", resp.getBullPoints())).append('\n');
        }
        if (CollUtil.isNotEmpty(resp.getBearPoints())) {
            user.append("空头要点：").append(String.join("；", resp.getBearPoints())).append('\n');
        }
        if (CollUtil.isNotEmpty(resp.getSignals())) {
            user.append("策略信号：");
            for (SignalItemResp s : resp.getSignals()) {
                user.append(s.getStrategyId()).append(' ').append(s.getSide())
                        .append('(').append(s.getScore()).append(") ");
            }
            user.append('\n');
        }
        user.append("相关新闻标题（可能不全/滞后）：\n");
        if (CollUtil.isEmpty(newsLines)) {
            user.append("- （本地库暂无匹配标题）\n");
        } else {
            for (String line : newsLines) {
                user.append("- ").append(line).append('\n');
            }
        }
        user.append("请结合以上事实给出 JSON 解读。");

        String raw = kimiChatClient.chat(system, user.toString(), 700);
        StockAnalysisAiResp ai = parseAiPayload(raw);
        ai.setConfigured(true);
        ai.setFromCache(false);
        ai.setModel(aiChatProperties.getModel());
        ai.setQuoteRefreshed(quoteRefreshed);
        ai.setGeneratedAt(LocalDateTime.now());
        ai.setDisclaimer(AI_DISCLAIMER);
        if (StringUtils.isBlank(ai.getBrief())) {
            ai.setBrief("大模型暂无有效输出，请稍后重试或查看上方规则研判。");
            ai.setStance(resp.getStance());
        }
        aiCache.put(cacheKey, new CachedAi(ai, LocalDateTime.now()));
        return ai;
    }

    private String buildAiCacheKey(StockAnalysisResp resp) {
        return LocalDate.now() + "|" + resp.getCode() + "|"
                + resp.getLatestPrice() + "|" + resp.getPctChg() + "|"
                + resp.getCompositeScore() + "|"
                + (Objects.nonNull(resp.getTech()) ? resp.getTech().getRegime() : "");
    }

    private List<String> loadRelatedNews(String code, String name) {
        List<String> lines = new ArrayList<>();
        try {
            List<MarketNews> rows = marketNewsMapper.selectList(Wrappers.<MarketNews>lambdaQuery()
                    .and(w -> {
                        w.like(MarketNews::getTitle, code);
                        if (StringUtils.isNotBlank(name) && name.length() >= 2) {
                            w.or().like(MarketNews::getTitle, name);
                        }
                    })
                    .orderByDesc(MarketNews::getPublishedAt)
                    .last("LIMIT 5"));
            if (CollUtil.isEmpty(rows)) {
                return lines;
            }
            for (MarketNews row : rows) {
                if (Objects.isNull(row) || StringUtils.isBlank(row.getTitle())) {
                    continue;
                }
                String line = row.getTitle();
                if (Objects.nonNull(row.getPublishedAt())) {
                    line = row.getPublishedAt().toLocalDate() + " " + line;
                }
                lines.add(line);
            }
        } catch (Exception ex) {
            log.debug("综合研判加载相关新闻失败: {}", ex.getMessage());
        }
        return lines;
    }

    private StockAnalysisAiResp parseAiPayload(String raw) {
        StockAnalysisAiResp empty = StockAnalysisAiResp.builder().build();
        if (StringUtils.isBlank(raw)) {
            return empty;
        }
        String text = raw.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            try {
                JSONObject obj = JSONUtil.parseObj(text.substring(start, end + 1));
                List<String> points = new ArrayList<>();
                JSONArray arr = obj.getJSONArray("watchPoints");
                if (Objects.nonNull(arr)) {
                    for (int i = 0; i < arr.size() && points.size() < 5; i++) {
                        String p = arr.getStr(i);
                        if (StringUtils.isNotBlank(p)) {
                            points.add(p.trim());
                        }
                    }
                }
                return StockAnalysisAiResp.builder()
                        .stance(obj.getStr("stance"))
                        .brief(obj.getStr("brief"))
                        .watchPoints(points)
                        .riskNote(obj.getStr("riskNote"))
                        .build();
            } catch (Exception ex) {
                log.debug("AI JSON 解析失败，回退纯文本: {}", ex.getMessage());
            }
        }
        return StockAnalysisAiResp.builder().brief(text).build();
    }

    private static final class CachedAi {
        private final StockAnalysisAiResp payload;
        private final LocalDateTime at;

        private CachedAi(StockAnalysisAiResp payload, LocalDateTime at) {
            this.payload = payload;
            this.at = at;
        }
    }
}
