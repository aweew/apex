package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.DecisionItemResp;
import com.awe.apex.quant.domain.dto.DecisionTodayResp;
import com.awe.apex.quant.domain.dto.HotConfluenceItem;
import com.awe.apex.quant.domain.dto.ObserveTechSignal;
import com.awe.apex.quant.domain.dto.SectorBoardItem;
import com.awe.apex.quant.domain.dto.SectorBoardResp;
import com.awe.apex.quant.domain.dto.SignalItemResp;
import com.awe.apex.quant.domain.dto.SignalRunReq;
import com.awe.apex.quant.domain.dto.StockAnalysisCapitalResp;
import com.awe.apex.quant.domain.dto.StockAnalysisResp;
import com.awe.apex.quant.domain.dto.StockAnalysisTechResp;
import com.awe.apex.quant.domain.dto.StockDetailResp;
import com.awe.apex.quant.domain.dto.ValuationResp;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.domain.entity.StrategySignalEntity;
import com.awe.apex.quant.indicator.IndicatorUtils;
import com.awe.apex.quant.indicator.TechSignalEvaluator;
import com.awe.apex.quant.strategy.BarSeries;
import com.awe.apex.quant.service.IDecisionService;
import com.awe.apex.quant.service.IHotService;
import com.awe.apex.quant.service.ISectorBoardService;
import com.awe.apex.quant.service.ISignalService;
import com.awe.apex.quant.service.IStockAnalysisService;
import com.awe.apex.quant.service.IStockService;
import com.awe.apex.quant.service.IValuationService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 个股综合研判：聚合技术、估值、板块资金/热点与策略信号
 */
@Slf4j
@Service
public class StockAnalysisServiceImpl implements IStockAnalysisService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

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
        if (StringUtils.isBlank(code)) {
            throw new BusinessException("证券代码不能为空");
        }
        String pure = code.trim().toUpperCase().replaceAll("^(SH|SZ|BJ)", "");
        String evalSide = StringUtils.isNotBlank(side) ? side.trim().toUpperCase() : "BUY";
        if (!"BUY".equals(evalSide) && !"SELL".equals(evalSide)) {
            evalSide = "BUY";
        }
        int limit = Objects.nonNull(barLimit) ? Math.max(60, Math.min(barLimit, 250)) : 120;

        // 1. 行情与日线
        StockDetailResp detail = stockService.detail(pure, limit, false);
        if (Objects.isNull(detail) || Objects.isNull(detail.getBasic())) {
            throw new BusinessException("未找到股票 " + pure);
        }
        StockBasic basic = detail.getBasic();
        List<BarDaily> bars = detail.getBars();

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
        return buildConclusion(basic, tech, valuation, capital, signals, decision, detail);
    }

    private StockAnalysisTechResp buildTech(String side, List<BarDaily> bars, StockDetailResp detail) {
        List<ObserveTechSignal> signals = techSignalEvaluator.evaluate(side, bars);
        int hit = 0;
        for (ObserveTechSignal s : signals) {
            if (Boolean.TRUE.equals(s.getHit())) {
                hit++;
            }
        }
        int total = signals.size();
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

        String summary;
        if (total == 0) {
            summary = "日线不足，技术雷达暂无法完整评估（建议同步日线）";
        } else if (hitRate.compareTo(new BigDecimal("75")) >= 0) {
            summary = "技术面偏多：均线/MACD/量能等多数指标支持" + ("SELL".equals(side) ? "卖出结构" : "做多结构");
        } else if (hitRate.compareTo(new BigDecimal("45")) >= 0) {
            summary = "技术面中性偏分歧：部分指标共振，需结合估值与仓位";
        } else {
            summary = "技术面偏弱：多数雷达未命中，追高/抄底需更谨慎";
        }

        return StockAnalysisTechResp.builder()
                .side(side)
                .signals(signals)
                .hitCount(hit)
                .total(total)
                .hitRate(hitRate)
                .summary(summary)
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

        // 技术 0~35
        BigDecimal techScore = ZERO;
        if (Objects.nonNull(tech) && Objects.nonNull(tech.getHitRate())) {
            techScore = tech.getHitRate().multiply(new BigDecimal("0.35")).setScale(1, RoundingMode.HALF_UP);
            scoreExplain.add("技术雷达 " + tech.getHitCount() + "/" + tech.getTotal()
                    + " → " + techScore + " 分");
            if (tech.getHitRate().compareTo(new BigDecimal("60")) >= 0) {
                bull.add(tech.getSummary());
            } else if (tech.getHitRate().compareTo(new BigDecimal("40")) < 0) {
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

        // 策略信号 0~15
        BigDecimal signalScore = ZERO;
        BigDecimal bestBuy = null;
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
                    bear.add("策略 " + s.getStrategyId() + " 给出 SELL（分 " + s.getScore() + "）");
                }
            }
            if (Objects.nonNull(bestBuy)) {
                signalScore = bestBuy.multiply(new BigDecimal("0.15")).setScale(1, RoundingMode.HALF_UP);
            }
            scoreExplain.add("策略信号 → " + signalScore + " 分");
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

        return StockAnalysisResp.builder()
                .code(basic.getCode())
                .name(basic.getName())
                .latestPrice(basic.getLatestPrice())
                .pctChg(basic.getPctChg())
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
}
