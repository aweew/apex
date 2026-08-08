package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.awe.apex.common.util.JsonUtils;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.decision.MainlineBoardRules;
import com.awe.apex.quant.domain.dto.MarketBriefingResp;
import com.awe.apex.quant.domain.dto.MarketEffectResp;
import com.awe.apex.quant.domain.dto.MarketFactorItem;
import com.awe.apex.quant.domain.dto.MarketHotThemeItem;
import com.awe.apex.quant.domain.dto.MarketIndexItem;
import com.awe.apex.quant.domain.dto.MarketTipItem;
import com.awe.apex.quant.domain.dto.SectorBoardItem;
import com.awe.apex.quant.domain.entity.IndexBar;
import com.awe.apex.quant.domain.entity.LimitUpPool;
import com.awe.apex.quant.domain.entity.MarketBriefingSnapshot;
import com.awe.apex.quant.domain.entity.SectorQuote;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.mapper.IndexBarMapper;
import com.awe.apex.quant.mapper.LimitUpPoolMapper;
import com.awe.apex.quant.mapper.MarketBriefingSnapshotMapper;
import com.awe.apex.quant.mapper.SectorQuoteMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.market.MarketBriefingMath;
import com.awe.apex.quant.market.MarketCrossSectionClient;
import com.awe.apex.quant.market.TradingCalendar;
import com.awe.apex.quant.service.IMarketBriefingService;
import com.awe.apex.quant.service.ISectorBoardService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 每日市场简报：大盘趋势 + 风格 + 量能 + 涨停情绪 + 主线题材
 */
@Slf4j
@Service
public class MarketBriefingServiceImpl implements IMarketBriefingService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    /** 内存缓存 10 分钟，避免每次进看板都重建 */
    private static final long CACHE_TTL_MS = 600_000L;

    private final Object cacheLock = new Object();
    private final AtomicBoolean rebuildScheduled = new AtomicBoolean(false);
    private MarketBriefingResp cachedBriefing;
    private long cachedAtMs;
    /** 实时三市成交额短缓存，避免每次补全都打外网 */
    private BigDecimal cachedLiveAmount;
    private long cachedLiveAmountAtMs;
    private static final long LIVE_AMOUNT_TTL_MS = 45_000L;
    private static final long LIVE_AMOUNT_MISS_TTL_MS = 15_000L;
    private static final long LIVE_QUOTE_TTL_MS = 60_000L;
    /** 赚钱效应截面较重，3 分钟短缓存（强制刷新仍会清空） */
    private static final long LIVE_EFFECT_TTL_MS = 180_000L;
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Map<String, String> INDEX_CODE_NAME = Map.of(
            "000001", "上证指数",
            "399001", "深证成指",
            "399006", "创业板指",
            "000688", "科创50"
    );
    private static final String[] INDEX_CODE_ORDER = {"000001", "399001", "399006", "000688"};
    private Map<String, LiveIndexQuote> cachedLiveIndexes;
    private long cachedLiveIndexesAtMs;
    private int[] cachedLiveBreadth;
    private long cachedLiveBreadthAtMs;
    /** 涨跌分布中的涨停/跌停档（11 / -11） */
    private Integer cachedFenbuLimitUp;
    private Integer cachedFenbuLimitDown;
    private Integer cachedLiveLimitUp;
    private Integer cachedLiveLimitDown;
    private long cachedLiveLimitsAtMs;
    /** 腾讯自选股市场总览（涨跌停与较上日额变动） */
    private QqMarketOverview cachedQqMarket;
    private long cachedQqMarketAtMs;
    /** 赚钱效应截面短缓存 */
    private MarketEffectResp cachedMarketEffect;
    private long cachedMarketEffectAtMs;
    private final Map<LocalDate, BigDecimal> recentLiveAmountByDate = new java.util.LinkedHashMap<>();

    @Resource
    private IndexBarMapper indexBarMapper;

    @Resource
    private LimitUpPoolMapper limitUpPoolMapper;

    @Resource
    private SectorQuoteMapper sectorQuoteMapper;

    @Resource
    private ISectorBoardService sectorBoardService;

    @Resource
    private StockBasicMapper stockBasicMapper;

    @Resource
    private MarketBriefingSnapshotMapper marketBriefingSnapshotMapper;

    @Resource
    private MarketCrossSectionClient marketCrossSectionClient;

    /**
     * 生成市场简报
     *
     * @return 简报
     */
    @Override
    public MarketBriefingResp briefing() {
        return briefing(false);
    }

    /**
     * 生成市场简报
     *
     * @param forceRebuild true 时跳过快照秒回，同步完整重建
     * @return 简报
     */
    @Override
    public MarketBriefingResp briefing(boolean forceRebuild) {
        if (forceRebuild) {
            invalidateCache();
            synchronized (cacheLock) {
                MarketBriefingResp built = refreshLiveMarketQuotes(buildBriefing());
                cachedBriefing = built;
                cachedAtMs = System.currentTimeMillis();
                persistSnapshot(built);
                return built;
            }
        }
        long now = System.currentTimeMillis();
        synchronized (cacheLock) {
            if (Objects.nonNull(cachedBriefing) && now - cachedAtMs < CACHE_TTL_MS) {
                return refreshLiveMarketQuotes(cachedBriefing);
            }
        }
        MarketBriefingResp snap = loadRecentSnapshot();
        if (Objects.nonNull(snap)) {
            snap = refreshLiveMarketQuotes(snap);
            synchronized (cacheLock) {
                if (Objects.isNull(cachedBriefing) || System.currentTimeMillis() - cachedAtMs >= CACHE_TTL_MS) {
                    cachedBriefing = snap;
                    cachedAtMs = System.currentTimeMillis();
                }
            }
            scheduleRebuild();
            return snap;
        }
        synchronized (cacheLock) {
            if (Objects.nonNull(cachedBriefing) && System.currentTimeMillis() - cachedAtMs < CACHE_TTL_MS) {
                return refreshLiveMarketQuotes(cachedBriefing);
            }
            MarketBriefingResp built = refreshLiveMarketQuotes(buildBriefing());
            cachedBriefing = built;
            cachedAtMs = System.currentTimeMillis();
            persistSnapshot(built);
            return built;
        }
    }

    /**
     * 清除简报内存缓存
     */
    @Override
    public void invalidateCache() {
        synchronized (cacheLock) {
            cachedBriefing = null;
            cachedAtMs = 0L;
            cachedLiveAmount = null;
            cachedLiveAmountAtMs = 0L;
            cachedLiveIndexes = null;
            cachedLiveIndexesAtMs = 0L;
            cachedLiveBreadth = null;
            cachedLiveBreadthAtMs = 0L;
            cachedFenbuLimitUp = null;
            cachedFenbuLimitDown = null;
            cachedLiveLimitUp = null;
            cachedLiveLimitDown = null;
            cachedLiveLimitsAtMs = 0L;
            cachedQqMarket = null;
            cachedQqMarketAtMs = 0L;
            cachedMarketEffect = null;
            cachedMarketEffectAtMs = 0L;
        }
    }

    /**
     * 库内指数成交额缺失时，用实时三市额补全展示（短超时 + 内存缓存）
     */
    private MarketBriefingResp fillMissingIndexVolume(MarketBriefingResp resp) {
        if (Objects.isNull(resp)) {
            return null;
        }
        if (StringUtils.isNotBlank(resp.getIndexVolumeText())) {
            return resp;
        }
        BigDecimal live = fetchLiveThreeMarketAmount();
        if (Objects.isNull(live) || live.signum() <= 0) {
            return resp;
        }
        resp.setIndexVolume(live);
        resp.setIndexVolumeText(formatAmount(live));
        return resp;
    }


    /**
     * 实时覆盖链：指数 → 量能 → 涨跌家数 → 涨跌停 → 赚钱效应 → 主线题材 → 因子/立场对齐
     */
    private MarketBriefingResp refreshLiveMarketQuotes(MarketBriefingResp resp) {
        return reconcileFactorsWithLive(
                overlayHotThemes(
                        overlayLiveEffect(
                                overlayLiveLimits(
                                        overlayLiveBreadth(
                                                overlayLiveVolume(
                                                        overlayLiveIndexes(resp)))))));
    }

    /**
     * 每次出简报时刷新主线（排除结果型板，附涨幅），避免沿用旧快照里的「昨日连板」等
     */
    private MarketBriefingResp overlayHotThemes(MarketBriefingResp resp) {
        if (Objects.isNull(resp)) {
            return null;
        }
        List<MarketHotThemeItem> items = loadHotThemeItems();
        List<String> names = new ArrayList<>();
        for (MarketHotThemeItem item : items) {
            if (Objects.nonNull(item) && StringUtils.isNotBlank(item.getName())) {
                names.add(item.getName());
            }
        }
        resp.setHotThemeItems(items);
        resp.setHotThemes(names);
        return resp;
    }

    /**
     * 实时指数/广度/量能覆盖后，同步改写因子条与立场分，避免「右侧指数绿、下方大盘当日红」撕裂
     */
    private MarketBriefingResp reconcileFactorsWithLive(MarketBriefingResp resp) {
        if (Objects.isNull(resp) || CollUtil.isEmpty(resp.getIndexes())) {
            return resp;
        }
        // 无实时指数时不改写因子，避免把库内昨日数据标成「今日实时」
        Map<String, LiveIndexQuote> live = fetchLiveIndexQuotes();
        if (CollUtil.isEmpty(live)) {
            return resp;
        }
        BigDecimal shPct = findIndexPct(resp.getIndexes(), "上证");
        BigDecimal cybPct = findIndexPct(resp.getIndexes(), "创业");
        BigDecimal shClose = findIndexClose(resp.getIndexes(), "上证");
        if (Objects.isNull(shPct) && Objects.isNull(cybPct)) {
            return resp;
        }

        List<MarketFactorItem> factors = Objects.nonNull(resp.getFactors())
                ? new ArrayList<>(resp.getFactors())
                : new ArrayList<>();
        resp.setFactors(factors);
        resp.setAsOf(resolveSessionDay());

        BigDecimal dayAvg = avgNonNull(shPct, cybPct);
        if (Objects.nonNull(dayAvg)) {
            upsertFactor(factors, "大盘当日",
                    fmtPct(dayAvg) + "（上证" + fmtPct(shPct) + "/创业" + fmtPct(cybPct) + "）",
                    daySignalOf(dayAvg),
                    "综合上证与创业板涨跌（实时）");
        }

        List<IndexBar> shBars = loadBars("CN_SH", 60);
        Boolean aboveMa20 = aboveMaWithLive(shBars, 20, shClose);
        BigDecimal ret5 = cumReturnWithLive(shBars, 5, shClose);
        BigDecimal ret20 = cumReturnWithLive(shBars, 20, shClose);
        if (Objects.nonNull(aboveMa20)) {
            if (Boolean.TRUE.equals(aboveMa20)) {
                upsertFactor(factors, "趋势位置", "上证站上MA20", "偏多",
                        "近5日" + fmtPct(ret5) + " · 近20日" + fmtPct(ret20));
            } else {
                upsertFactor(factors, "趋势位置", "上证低于MA20", "偏空",
                        "近5日" + fmtPct(ret5) + " · 近20日" + fmtPct(ret20));
            }
        }

        if (StringUtils.isNotBlank(resp.getVolumeTrend()) || StringUtils.isNotBlank(resp.getIndexVolumeText())) {
            String volSignal = volumeSignalOf(resp.getVolumeTrend(), shPct);
            String factorValue;
            if (StringUtils.isNotBlank(resp.getIndexVolumeText())) {
                factorValue = resp.getIndexVolumeText()
                        + (StringUtils.isNotBlank(resp.getVolumeTrend()) ? (" · " + resp.getVolumeTrend()) : "")
                        + (Objects.nonNull(resp.getVolumeVsMa5Pct())
                        ? (" · 较前日" + fmtPct(resp.getVolumeVsMa5Pct())) : "");
            } else {
                factorValue = resp.getVolumeTrend()
                        + (Objects.nonNull(resp.getVolumeVsMa5Pct())
                        ? (" · 较前日" + fmtPct(resp.getVolumeVsMa5Pct())) : "");
            }
            upsertFactor(factors, "三市成交", factorValue, volSignal, "上证+深成+北证50 三市成交额合计（实时）");
        }

        if (Objects.nonNull(resp.getBreadthUp()) && Objects.nonNull(resp.getBreadthDown())) {
            int up = resp.getBreadthUp();
            int down = resp.getBreadthDown();
            int flat = Objects.nonNull(resp.getBreadthFlat()) ? resp.getBreadthFlat() : 0;
            String bSignal = "中性";
            if (up > down * 1.5) {
                bSignal = "偏多";
            } else if (down > up * 1.5) {
                bSignal = "偏空";
            }
            upsertFactor(factors, "市场广度",
                    "涨" + up + " / 平" + flat + " / 跌" + down,
                    bSignal,
                    "全市场涨跌家数（平盘单列·实时）");
        }

        if (Objects.nonNull(resp.getLimitUpCount())) {
            int maxBoard = parseMaxLianban(factors);
            String luSignal = limitUpSignalOf(resp.getLimitUpCount(), maxBoard);
            upsertFactor(factors, "涨停情绪",
                    "涨停" + resp.getLimitUpCount() + "家 · 最高" + Math.max(maxBoard, 1) + "板",
                    luSignal,
                    "池日期 " + resp.getAsOf());
        }

        applyStanceFromLiveFactors(resp, dayAvg, aboveMa20, shPct);
        return resp;
    }

    private void applyStanceFromLiveFactors(MarketBriefingResp resp, BigDecimal dayAvg,
                                            Boolean aboveMa20, BigDecimal shPct) {
        int score = 50;
        if (Objects.nonNull(dayAvg)) {
            if (dayAvg.compareTo(new BigDecimal("0.8")) >= 0) {
                score += 12;
            } else if (dayAvg.compareTo(new BigDecimal("-0.8")) <= 0) {
                score -= 14;
            } else if (dayAvg.compareTo(ZERO) > 0) {
                score += 4;
            } else if (dayAvg.compareTo(ZERO) < 0) {
                score -= 5;
            }
        }
        if (Objects.nonNull(aboveMa20)) {
            score += Boolean.TRUE.equals(aboveMa20) ? 10 : -12;
        }
        if ("放量".equals(resp.getVolumeTrend()) && Objects.nonNull(shPct) && shPct.compareTo(ZERO) >= 0) {
            score += 8;
        } else if ("放量".equals(resp.getVolumeTrend()) && Objects.nonNull(shPct) && shPct.compareTo(ZERO) < 0) {
            score -= 10;
        } else if ("缩量".equals(resp.getVolumeTrend()) && Objects.nonNull(shPct) && shPct.compareTo(ZERO) > 0) {
            score -= 2;
        }
        for (MarketFactorItem factor : resp.getFactors()) {
            if (Objects.isNull(factor) || !"最近风格".equals(factor.getName())) {
                continue;
            }
            if ("偏多".equals(factor.getSignal())) {
                score += 5;
            } else if ("提示".equals(factor.getSignal())) {
                score -= 3;
            }
        }
        if (Objects.nonNull(resp.getLimitUpCount())) {
            int maxBoard = parseMaxLianban(resp.getFactors());
            if (resp.getLimitUpCount() >= 80 && maxBoard >= 5) {
                score += 3;
            } else if (resp.getLimitUpCount() <= 30) {
                score -= 6;
            } else {
                score += 2;
            }
        }
        if (Objects.nonNull(resp.getBreadthUp()) && Objects.nonNull(resp.getBreadthDown())) {
            if (resp.getBreadthUp() > resp.getBreadthDown() * 1.5) {
                score += 6;
            } else if (resp.getBreadthDown() > resp.getBreadthUp() * 1.5) {
                score -= 8;
            }
        }
        String dataLevel = StringUtils.isNotBlank(resp.getDataLevel()) ? resp.getDataLevel() : "YELLOW";
        boolean dataSufficient = !"RED".equals(dataLevel);
        if ("RED".equals(dataLevel)) {
            score = Math.min(score, 35);
        }
        score = Math.max(0, Math.min(100, score));

        String stance;
        BigDecimal buyFactor;
        String positionAdvice;
        if (!dataSufficient) {
            stance = "防守";
            buyFactor = new BigDecimal("0.40");
            positionAdvice = "数据不足：建议空仓观望或极低仓，先补齐同步";
        } else if (score >= 65) {
            stance = "进攻";
            buyFactor = new BigDecimal("1.10");
            positionAdvice = "建议总仓 6–8 成，可对共振/主线标的正常/略抬仓";
        } else if (score <= 40) {
            stance = "防守";
            buyFactor = new BigDecimal("0.55");
            positionAdvice = "建议总仓 2–4 成，新建仓降权，优先处理卖出/止损";
        } else {
            stance = "均衡";
            buyFactor = BigDecimal.ONE;
            positionAdvice = "建议总仓 4–6 成，精选共振/主线，避免无脑铺开";
        }
        resp.setStance(stance);
        resp.setStanceScore(score);
        resp.setBuyWeightFactor(buyFactor);
        resp.setPositionAdvice(positionAdvice);
        resp.setDataSufficient(dataSufficient);
        resp.setStanceReason("评分 " + score + "/100 · 综合大盘、趋势、量能、风格、广度与涨停情绪");
        if (Objects.nonNull(resp.getAsOf())) {
            resp.setMessage("市场简报 · " + resp.getAsOf() + " · 立场「" + stance + "」· 数据" + dataLevelLabel(dataLevel));
        }
        // 去掉与实时大盘矛盾的旧提示，并重写立场首条
        List<MarketTipItem> tips = Objects.nonNull(resp.getTips()) ? new ArrayList<>(resp.getTips()) : new ArrayList<>();
        tips.removeIf(t -> Objects.isNull(t) || StringUtils.isBlank(t.getText())
                || t.getText().contains("综合评分")
                || t.getText().contains("数据门禁")
                || (Objects.nonNull(dayAvg) && dayAvg.compareTo(ZERO) > 0
                && (t.getText().contains("明显调整") || t.getText().contains("放量下跌")))
                || (Objects.nonNull(dayAvg) && dayAvg.compareTo(ZERO) < 0
                && t.getText().contains("强势上涨")));
        if (!dataSufficient) {
            tips.add(0, tip("danger", "数据门禁生效：强制防守，买入仓位已大幅降权。"));
        } else if (score >= 65) {
            tips.add(0, tip("info", "综合评分偏进攻：可执行买入计划，但仍控制单票上限。"));
        } else if (score <= 40) {
            tips.add(0, tip("danger", "综合评分偏防守：今日买入建议已自动降权，优先风控与持仓体检。"));
        } else {
            tips.add(0, tip("info", "市场中性偏均衡：有信号再做，仓位中等、纪律优先。"));
        }
        if (tips.size() > 10) {
            tips = new ArrayList<>(tips.subList(0, 10));
        }
        resp.setTips(tips);
    }

    private String daySignalOf(BigDecimal dayAvg) {
        if (dayAvg.compareTo(new BigDecimal("0.8")) >= 0) {
            return "偏多";
        }
        if (dayAvg.compareTo(new BigDecimal("-0.8")) <= 0) {
            return "偏空";
        }
        return "中性";
    }

    private String volumeSignalOf(String trend, BigDecimal shPct) {
        if ("放量".equals(trend) && Objects.nonNull(shPct) && shPct.compareTo(ZERO) >= 0) {
            return "偏多";
        }
        if ("放量".equals(trend) && Objects.nonNull(shPct) && shPct.compareTo(ZERO) < 0) {
            return "偏空";
        }
        if ("缩量".equals(trend)) {
            return "提示";
        }
        return "中性";
    }

    private String limitUpSignalOf(int count, int maxBoard) {
        if (count >= 80 && maxBoard >= 5) {
            return "提示";
        }
        if (count <= 30) {
            return "偏空";
        }
        return "中性";
    }

    private int parseMaxLianban(List<MarketFactorItem> factors) {
        if (CollUtil.isEmpty(factors)) {
            return 1;
        }
        Pattern p = Pattern.compile("最高(\\d+)板");
        for (MarketFactorItem factor : factors) {
            if (Objects.isNull(factor) || !"涨停情绪".equals(factor.getName()) || StringUtils.isBlank(factor.getValue())) {
                continue;
            }
            Matcher m = p.matcher(factor.getValue());
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        }
        return 1;
    }

    private void upsertFactor(List<MarketFactorItem> factors, String name, String value, String signal, String note) {
        MarketFactorItem item = MarketFactorItem.builder()
                .name(name)
                .value(value)
                .signal(signal)
                .note(note)
                .build();
        for (int i = 0; i < factors.size(); i++) {
            MarketFactorItem old = factors.get(i);
            if (Objects.nonNull(old) && name.equals(old.getName())) {
                factors.set(i, item);
                return;
            }
        }
        factors.add(item);
    }

    private BigDecimal findIndexPct(List<MarketIndexItem> indexes, String namePart) {
        if (CollUtil.isEmpty(indexes)) {
            return null;
        }
        for (MarketIndexItem item : indexes) {
            if (Objects.nonNull(item) && StringUtils.isNotBlank(item.getName())
                    && item.getName().contains(namePart)) {
                return item.getPctChg();
            }
        }
        return null;
    }

    private BigDecimal findIndexClose(List<MarketIndexItem> indexes, String namePart) {
        if (CollUtil.isEmpty(indexes)) {
            return null;
        }
        for (MarketIndexItem item : indexes) {
            if (Objects.nonNull(item) && StringUtils.isNotBlank(item.getName())
                    && item.getName().contains(namePart)) {
                return item.getClose();
            }
        }
        return null;
    }

    private Boolean aboveMaWithLive(List<IndexBar> bars, int n, BigDecimal liveClose) {
        if (Objects.isNull(liveClose)) {
            return aboveMa(bars, n);
        }
        if (CollUtil.isEmpty(bars) || bars.size() < n) {
            return null;
        }
        BigDecimal sum = ZERO;
        int cnt = 0;
        for (int i = bars.size() - n; i < bars.size(); i++) {
            BigDecimal c = bars.get(i).getClosePrice();
            if (Objects.isNull(c)) {
                continue;
            }
            sum = sum.add(c);
            cnt++;
        }
        if (cnt < n) {
            return null;
        }
        BigDecimal ma = sum.divide(BigDecimal.valueOf(n), 6, RoundingMode.HALF_UP);
        return liveClose.compareTo(ma) >= 0;
    }

    private BigDecimal cumReturnWithLive(List<IndexBar> bars, int days, BigDecimal liveClose) {
        if (Objects.isNull(liveClose)) {
            return cumReturn(bars, days);
        }
        if (CollUtil.isEmpty(bars) || bars.size() < days) {
            return null;
        }
        BigDecimal start = bars.get(bars.size() - days).getClosePrice();
        if (Objects.isNull(start) || start.signum() == 0) {
            return null;
        }
        return liveClose.subtract(start).divide(start, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    private MarketBriefingResp overlayLiveIndexes(MarketBriefingResp resp) {
        if (Objects.isNull(resp)) {
            return null;
        }
        Map<String, LiveIndexQuote> live = fetchLiveIndexQuotes();
        if (CollUtil.isEmpty(live)) {
            return resp;
        }
        List<String> indexLines = new ArrayList<>();
        List<MarketIndexItem> indexes = new ArrayList<>();
        for (String code : INDEX_CODE_ORDER) {
            LiveIndexQuote quote = live.get(code);
            if (Objects.isNull(quote)) {
                continue;
            }
            indexLines.add(lineOf(quote.name, quote.pctChg, quote.close));
            indexes.add(indexItemOf(quote.name, quote.pctChg, quote.close));
        }
        if (CollUtil.isNotEmpty(indexes)) {
            resp.setIndexLines(indexLines);
            resp.setIndexes(indexes);
        }
        return resp;
    }

    private MarketBriefingResp overlayLiveVolume(MarketBriefingResp resp) {
        if (Objects.isNull(resp)) {
            return null;
        }
        VolumeStat vol = threeMarketAmountStat(
                loadBars("CN_SH", 20),
                loadBars("CN_SZ", 20),
                loadBars("CN_BJ50", 20),
                5);
        if (Objects.isNull(vol)) {
            resp.setVolumeTrend(null);
            resp.setVolumeVsMa5Pct(null);
            resp.setVolumeLabel(null);
            resp.setIndexVolume(null);
            resp.setIndexVolumeChange(null);
            resp.setIndexVolumeText(null);
            return resp;
        }
        resp.setVolumeTrend(vol.trend);
        resp.setVolumeVsMa5Pct(vol.vsMa5Pct);
        resp.setVolumeLabel(vol.label);
        resp.setIndexVolume(vol.volume);
        resp.setIndexVolumeChange(vol.change);
        resp.setIndexVolumeText(formatAmount(vol.volume));
        return resp;
    }

    private MarketBriefingResp overlayLiveBreadth(MarketBriefingResp resp) {
        if (Objects.isNull(resp)) {
            return null;
        }
        int[] breadth = fetchLiveMarketBreadth();
        if (breadth[0] <= 0 && breadth[1] <= 0) {
            return resp;
        }
        resp.setBreadthUp(breadth[0]);
        resp.setBreadthDown(breadth[1]);
        resp.setBreadthFlat(breadth[2]);
        return resp;
    }

    private MarketBriefingResp overlayLiveLimits(MarketBriefingResp resp) {
        if (Objects.isNull(resp)) {
            return null;
        }
        // 涨跌停优先腾讯口径（对齐同花顺市场总览），东财 fenbu/涨停池作兜底
        QqMarketOverview qq = fetchQqMarketOverview();
        Integer up = Objects.nonNull(qq) ? qq.upLimitCount : null;
        Integer down = Objects.nonNull(qq) ? qq.downLimitCount : null;
        if (Objects.isNull(up) || Objects.isNull(down)) {
            fetchLiveMarketBreadth();
            if (Objects.isNull(up)) {
                up = cachedFenbuLimitUp;
            }
            if (Objects.isNull(down)) {
                down = cachedFenbuLimitDown;
            }
        }
        if (Objects.isNull(up) || Objects.isNull(down)) {
            Integer poolUp = fetchLiveLimitUpCount();
            Integer poolDown = fetchLiveLimitDownCount();
            if (Objects.isNull(up)) {
                up = poolUp;
            }
            if (Objects.isNull(down)) {
                down = poolDown;
            }
        }
        if (Objects.nonNull(up)) {
            resp.setLimitUpCount(up);
        }
        if (Objects.nonNull(down)) {
            resp.setLimitDownCount(down);
        }
        return resp;
    }

    /**
     * 覆盖赚钱效应：平均股价800005 / 中位数880009口径 / 等权800010 / 微盘800007 / 沪深300
     */
    private MarketBriefingResp overlayLiveEffect(MarketBriefingResp resp) {
        if (Objects.isNull(resp)) {
            return null;
        }
        MarketEffectResp effect = fetchLiveMarketEffect();
        if (Objects.nonNull(effect)) {
            resp.setEffect(effect);
        }
        return resp;
    }

    /**
     * 实时赚钱效应（短缓存）
     */
    private MarketEffectResp fetchLiveMarketEffect() {
        long now = System.currentTimeMillis();
        synchronized (cacheLock) {
            if (Objects.nonNull(cachedMarketEffect)
                    && now - cachedMarketEffectAtMs < LIVE_EFFECT_TTL_MS) {
                return cachedMarketEffect;
            }
        }
        // 1. 四指数：平均股价 / 全A等权 / 微盘 / 沪深300
        Map<String, LiveIndexQuote> indexMap = fetchLiveIndexBatch(
                "47.800005,47.800010,47.800007,1.000300");
        LiveIndexQuote avgPriceIdx = indexMap.get("800005");
        LiveIndexQuote equalWeightIdx = indexMap.get("800010");
        LiveIndexQuote microIdx = indexMap.get("800007");
        LiveIndexQuote hs300Idx = indexMap.get("000300");

        BigDecimal avgPrice = Objects.nonNull(avgPriceIdx) ? avgPriceIdx.close : null;
        BigDecimal avgPct = Objects.nonNull(avgPriceIdx) ? avgPriceIdx.pctChg : null;
        BigDecimal equalWeightPct = Objects.nonNull(equalWeightIdx) ? equalWeightIdx.pctChg : null;
        BigDecimal microPct = Objects.nonNull(microIdx) ? microIdx.pctChg : null;
        BigDecimal hsPct = Objects.nonNull(hs300Idx) ? hs300Idx.pctChg : null;

        // 2. 中位数：全A截面（880009 口径）
        BigDecimal medianPct = null;
        BigDecimal crossSectionAvgPct = null;
        Integer sampleSize = null;
        Integer strongUp = null;
        Integer strongDown = null;
        String source = "eastmoney-ulist";
        try {
            MarketCrossSectionClient.CrossSectionStats stats = marketCrossSectionClient.fetchHsjAStats();
            if (Objects.nonNull(stats)) {
                medianPct = stats.medianPct;
                crossSectionAvgPct = stats.avgPct;
                sampleSize = stats.sampleSize;
                strongUp = stats.strongUpCount;
                strongDown = stats.strongDownCount;
                if (Objects.isNull(avgPct)) {
                    avgPct = stats.avgPct;
                }
                if (Objects.isNull(avgPrice)) {
                    avgPrice = stats.avgPrice;
                }
                source = "eastmoney-ulist+clist";
            }
        } catch (Exception ex) {
            log.debug("东财截面中位数失败: {}", ex.getMessage());
        }
        if (Objects.isNull(medianPct)) {
            MarketEffectResp local = effectFromStockBasic();
            if (Objects.nonNull(local)) {
                medianPct = local.getMedianPctChg();
                crossSectionAvgPct = local.getAvgPctChg();
                sampleSize = local.getSampleSize();
                strongUp = local.getStrongUpCount();
                strongDown = local.getStrongDownCount();
                if (Objects.isNull(avgPct)) {
                    avgPct = local.getAvgPctChg();
                }
                if (Objects.isNull(avgPrice)) {
                    avgPrice = local.getAvgStockPrice();
                }
                source = source + "+stock_basic";
            }
        }

        equalWeightPct = MarketBriefingMath.equalWeightPct(equalWeightPct, crossSectionAvgPct);

        BigDecimal microVsLarge = MarketBriefingMath.microVsLarge(microPct, hsPct);
        String hint = MarketBriefingMath.effectHint(medianPct, microVsLarge, microPct);
        if (Objects.isNull(avgPct) && Objects.isNull(medianPct) && Objects.isNull(equalWeightPct)
                && Objects.isNull(microPct) && Objects.isNull(hsPct)) {
            return null;
        }
        MarketEffectResp effect = MarketEffectResp.builder()
                .avgStockPrice(avgPrice)
                .avgPctChg(avgPct)
                .medianPctChg(medianPct)
                .equalWeightPctChg(equalWeightPct)
                .microPctChg(microPct)
                .sampleSize(sampleSize)
                .csi2000PctChg(microPct)
                .csi2000Close(Objects.nonNull(microIdx) ? microIdx.close : null)
                .hs300PctChg(hsPct)
                .microVsLargePct(microVsLarge)
                .strongUpCount(strongUp)
                .strongDownCount(strongDown)
                .hint(hint)
                .source(source)
                .build();
        synchronized (cacheLock) {
            cachedMarketEffect = effect;
            cachedMarketEffectAtMs = System.currentTimeMillis();
        }
        return effect;
    }

    /**
     * 本地 stock_basic 兜底截面
     */
    private MarketEffectResp effectFromStockBasic() {
        try {
            List<StockBasic> rows = stockBasicMapper.selectList(Wrappers.<StockBasic>lambdaQuery()
                    .gt(StockBasic::getLatestPrice, 0)
                    .isNotNull(StockBasic::getPctChg)
                    .select(StockBasic::getLatestPrice, StockBasic::getPctChg)
                    .last("LIMIT 8000"));
            if (CollUtil.isEmpty(rows)) {
                return null;
            }
            List<BigDecimal> prices = new ArrayList<>(rows.size());
            List<BigDecimal> pcts = new ArrayList<>(rows.size());
            int strongUp = 0;
            int strongDown = 0;
            BigDecimal five = new BigDecimal("5");
            BigDecimal negFive = new BigDecimal("-5");
            for (StockBasic row : rows) {
                if (Objects.nonNull(row.getLatestPrice()) && row.getLatestPrice().signum() > 0) {
                    prices.add(row.getLatestPrice());
                }
                if (Objects.nonNull(row.getPctChg())) {
                    pcts.add(row.getPctChg());
                    if (row.getPctChg().compareTo(five) >= 0) {
                        strongUp++;
                    } else if (row.getPctChg().compareTo(negFive) <= 0) {
                        strongDown++;
                    }
                }
            }
            return MarketEffectResp.builder()
                    .avgStockPrice(MarketBriefingMath.average(prices, 2))
                    .avgPctChg(MarketBriefingMath.average(pcts, 2))
                    .medianPctChg(MarketBriefingMath.median(pcts, 2))
                    .sampleSize(Math.max(prices.size(), pcts.size()))
                    .strongUpCount(strongUp)
                    .strongDownCount(strongDown)
                    .source("stock_basic")
                    .build();
        } catch (Exception ex) {
            log.debug("stock_basic 赚钱效应兜底失败: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * 批量指数实时行情，key=代码（如 800005 / 000300）
     */
    private Map<String, LiveIndexQuote> fetchLiveIndexBatch(String secIds) {
        Map<String, LiveIndexQuote> result = new HashMap<>();
        if (StringUtils.isBlank(secIds)) {
            return result;
        }
        String url = "https://push2delay.eastmoney.com/api/qt/ulist.np/get"
                + "?fltt=2&secids=" + secIds + "&fields=f2,f3,f12,f14";
        try (HttpResponse response = HttpRequest.get(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://quote.eastmoney.com/")
                .header("Accept", "application/json,text/plain,*/*")
                .timeout(5000)
                .execute()) {
            if (!response.isOk()) {
                return result;
            }
            JSONObject data = JSONUtil.parseObj(response.body()).getJSONObject("data");
            JSONArray diff = Objects.nonNull(data) ? data.getJSONArray("diff") : null;
            if (Objects.isNull(diff) || diff.isEmpty()) {
                return result;
            }
            for (int i = 0; i < diff.size(); i++) {
                JSONObject row = diff.getJSONObject(i);
                if (Objects.isNull(row)) {
                    continue;
                }
                String code = row.getStr("f12");
                BigDecimal close = row.getBigDecimal("f2");
                BigDecimal pct = row.getBigDecimal("f3");
                if (StringUtils.isBlank(code) || Objects.isNull(close)) {
                    continue;
                }
                LiveIndexQuote quote = new LiveIndexQuote();
                quote.name = row.getStr("f14");
                quote.close = close.setScale(2, RoundingMode.HALF_UP);
                quote.pctChg = Objects.nonNull(pct) ? pct.setScale(2, RoundingMode.HALF_UP) : null;
                result.put(code, quote);
            }
        } catch (Exception ex) {
            log.debug("批量指数实时拉取失败: {}", ex.getMessage());
        }
        return result;
    }

    /**
     * 单指数实时行情
     */
    private LiveIndexQuote fetchLiveSingleIndex(String secId, String name) {
        String url = "https://push2delay.eastmoney.com/api/qt/ulist.np/get"
                + "?fltt=2&secids=" + secId + "&fields=f2,f3,f12,f14";
        try (HttpResponse response = HttpRequest.get(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://quote.eastmoney.com/")
                .header("Accept", "application/json,text/plain,*/*")
                .timeout(4000)
                .execute()) {
            if (!response.isOk()) {
                return null;
            }
            JSONObject data = JSONUtil.parseObj(response.body()).getJSONObject("data");
            JSONArray diff = Objects.nonNull(data) ? data.getJSONArray("diff") : null;
            if (Objects.isNull(diff) || diff.isEmpty()) {
                return null;
            }
            JSONObject row = diff.getJSONObject(0);
            if (Objects.isNull(row)) {
                return null;
            }
            BigDecimal close = row.getBigDecimal("f2");
            BigDecimal pct = row.getBigDecimal("f3");
            if (Objects.isNull(close)) {
                return null;
            }
            LiveIndexQuote quote = new LiveIndexQuote();
            quote.name = name;
            quote.close = close.setScale(2, RoundingMode.HALF_UP);
            quote.pctChg = Objects.nonNull(pct) ? pct.setScale(2, RoundingMode.HALF_UP) : null;
            return quote;
        } catch (Exception ex) {
            log.debug("单指数实时拉取失败 secId={}: {}", secId, ex.getMessage());
            return null;
        }
    }

    private void persistSnapshot(MarketBriefingResp briefing) {
        if (Objects.isNull(briefing) || Objects.isNull(briefing.getAsOf())) {
            return;
        }
        try {
            String json = JsonUtils.toJsonString(briefing);
            MarketBriefingSnapshot existing = marketBriefingSnapshotMapper.selectOne(
                    Wrappers.<MarketBriefingSnapshot>lambdaQuery()
                            .eq(MarketBriefingSnapshot::getTradeDate, briefing.getAsOf())
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
                        .tradeDate(briefing.getAsOf())
                        .stance(briefing.getStance())
                        .stanceScore(briefing.getStanceScore())
                        .dataLevel(briefing.getDataLevel())
                        .payloadJson(json)
                        .createTime(now)
                        .updateTime(now)
                        .build());
            }
        } catch (Exception ex) {
            log.debug("持久化简报快照失败: {}", ex.getMessage());
        }
    }

    private Map<String, LiveIndexQuote> fetchLiveIndexQuotes() {
        long now = System.currentTimeMillis();
        synchronized (cacheLock) {
            if (Objects.nonNull(cachedLiveIndexes)
                    && now - cachedLiveIndexesAtMs < LIVE_QUOTE_TTL_MS) {
                return cachedLiveIndexes;
            }
        }
        Map<String, LiveIndexQuote> result = new HashMap<>();
        String url = "https://push2delay.eastmoney.com/api/qt/ulist.np/get"
                + "?fltt=2&secids=1.000001,0.399001,0.399006,1.000688&fields=f2,f3,f12";
        try (HttpResponse response = HttpRequest.get(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://quote.eastmoney.com/")
                .header("Accept", "application/json,text/plain,*/*")
                .timeout(4000)
                .execute()) {
            if (response.isOk()) {
                JSONObject data = JSONUtil.parseObj(response.body()).getJSONObject("data");
                JSONArray diff = Objects.nonNull(data) ? data.getJSONArray("diff") : null;
                if (Objects.nonNull(diff)) {
                    for (int i = 0; i < diff.size(); i++) {
                        JSONObject row = diff.getJSONObject(i);
                        if (Objects.isNull(row)) {
                            continue;
                        }
                        String code = String.valueOf(row.get("f12"));
                        String name = INDEX_CODE_NAME.get(code);
                        if (StringUtils.isBlank(name)) {
                            continue;
                        }
                        BigDecimal close = row.getBigDecimal("f2");
                        BigDecimal pct = row.getBigDecimal("f3");
                        if (Objects.isNull(close)) {
                            continue;
                        }
                        LiveIndexQuote quote = new LiveIndexQuote();
                        quote.name = name;
                        quote.close = close.setScale(2, RoundingMode.HALF_UP);
                        quote.pctChg = Objects.nonNull(pct) ? pct.setScale(2, RoundingMode.HALF_UP) : null;
                        result.put(code, quote);
                    }
                }
            }
        } catch (Exception ex) {
            log.debug("东财指数实时拉取失败: {}", ex.getMessage());
        }
        synchronized (cacheLock) {
            cachedLiveIndexes = result;
            cachedLiveIndexesAtMs = System.currentTimeMillis();
        }
        return result;
    }

    private Integer fetchLiveLimitUpCount() {
        return fetchLiveLimitPoolCount(true);
    }

    private Integer fetchLiveLimitDownCount() {
        return fetchLiveLimitPoolCount(false);
    }

    private Integer fetchLiveLimitPoolCount(boolean limitUp) {
        long now = System.currentTimeMillis();
        synchronized (cacheLock) {
            if (now - cachedLiveLimitsAtMs < LIVE_QUOTE_TTL_MS
                    && Objects.nonNull(cachedLiveLimitUp)
                    && Objects.nonNull(cachedLiveLimitDown)) {
                return limitUp ? cachedLiveLimitUp : cachedLiveLimitDown;
            }
        }
        LocalDate sessionDay = resolveSessionDay();
        String day = sessionDay.format(DAY_FMT);
        String path = limitUp ? "/getTopicZTPool" : "/getTopicDTPool";
        String[] hosts = {
                "https://push2ex.eastmoney.com",
                "https://push2delay.eastmoney.com"
        };
        Integer count = null;
        String sort = limitUp ? "fbt:asc" : "fund:asc";
        for (String host : hosts) {
            String url = host + path
                    + "?ut=7eea3edcaed734bea9cbfc24409ed989&dpt=wz.ztzt"
                    + "&Pageindex=0&pagesize=1&sort=" + sort
                    + "&date=" + day;
            try (HttpResponse response = HttpRequest.get(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Referer", "https://quote.eastmoney.com/ztb/detail")
                    .header("Accept", "application/json,text/plain,*/*")
                    .timeout(4000)
                    .execute()) {
                if (!response.isOk()) {
                    continue;
                }
                JSONObject data = JSONUtil.parseObj(response.body()).getJSONObject("data");
                if (Objects.isNull(data)) {
                    continue;
                }
                count = data.getInt("tc");
                if (Objects.nonNull(count)) {
                    break;
                }
            } catch (Exception ex) {
                log.debug("eastmoney limit pool fail up={} host={}: {}", limitUp, host, ex.getMessage());
            }
        }
        synchronized (cacheLock) {
            if (limitUp) {
                cachedLiveLimitUp = count;
            } else {
                cachedLiveLimitDown = count;
            }
            cachedLiveLimitsAtMs = System.currentTimeMillis();
        }
        return count;
    }

    private String lineOf(String label, BigDecimal pct, BigDecimal close) {
        return label + " " + fmtPct(pct)
                + (Objects.nonNull(close) ? (" · " + close.setScale(2, RoundingMode.HALF_UP)) : "");
    }

    private MarketIndexItem indexItemOf(String label, BigDecimal pct, BigDecimal close) {
        String direction = "flat";
        if (Objects.nonNull(pct)) {
            if (pct.compareTo(ZERO) > 0) {
                direction = "up";
            } else if (pct.compareTo(ZERO) < 0) {
                direction = "down";
            }
        }
        return MarketIndexItem.builder()
                .name(label)
                .pctChg(pct)
                .close(close)
                .direction(direction)
                .build();
    }

    /**
     * 后台单飞重建简报，刷新内存缓存
     */
    private void scheduleRebuild() {
        if (!rebuildScheduled.compareAndSet(false, true)) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                MarketBriefingResp built = refreshLiveMarketQuotes(buildBriefing());
                synchronized (cacheLock) {
                    cachedBriefing = built;
                    cachedAtMs = System.currentTimeMillis();
                }
            } catch (Exception ex) {
                log.warn("后台重建市场简报失败: {}", ex.getMessage());
            } finally {
                rebuildScheduled.set(false);
            }
        });
    }

    /**
     * 优先读近 3 日简报快照，供看板秒开
     */
    private MarketBriefingResp loadRecentSnapshot() {
        try {
            MarketBriefingSnapshot row = marketBriefingSnapshotMapper.selectOne(
                    Wrappers.<MarketBriefingSnapshot>lambdaQuery()
                            .ge(MarketBriefingSnapshot::getTradeDate, LocalDate.now().minusDays(3))
                            .orderByDesc(MarketBriefingSnapshot::getTradeDate)
                            .last("LIMIT 1"));
            if (Objects.isNull(row) || StringUtils.isBlank(row.getPayloadJson())) {
                return null;
            }
            return JsonUtils.parseObject(row.getPayloadJson(), MarketBriefingResp.class);
        } catch (Exception ex) {
            log.debug("读取简报快照失败: {}", ex.getMessage());
            return null;
        }
    }

    private MarketBriefingResp buildBriefing() {
        List<IndexBar> sh = loadBars("CN_SH", 60);
        List<IndexBar> sz = loadBars("CN_SZ", 60);
        List<IndexBar> cyb = loadBars("CN_CYB", 30);
        List<IndexBar> kc = loadBars("CN_KC50", 30);
        List<IndexBar> bj = loadBars("CN_BJ50", 60);

        LocalDate asOf = latestDate(sh, sz, cyb);
        List<MarketFactorItem> factors = new ArrayList<>();
        List<MarketTipItem> tips = new ArrayList<>();
        List<String> indexLines = new ArrayList<>();
        List<MarketIndexItem> indexes = new ArrayList<>();
        int score = 50;

        // 有实时报价时，当日涨跌/收盘优先用实时，避免因子条落后于右侧大盘
        Map<String, LiveIndexQuote> liveQuotes = fetchLiveIndexQuotes();
        LiveIndexQuote liveSh = liveQuotes.get("000001");
        LiveIndexQuote liveSz = liveQuotes.get("399001");
        LiveIndexQuote liveCyb = liveQuotes.get("399006");
        LiveIndexQuote liveKc = liveQuotes.get("000688");
        if (CollUtil.isNotEmpty(liveQuotes)) {
            asOf = resolveSessionDay();
        }

        // —— 大盘当日 ——
        BigDecimal shPct = Objects.nonNull(liveSh) && Objects.nonNull(liveSh.pctChg) ? liveSh.pctChg : lastPct(sh);
        BigDecimal szPct = Objects.nonNull(liveSz) && Objects.nonNull(liveSz.pctChg) ? liveSz.pctChg : lastPct(sz);
        BigDecimal cybPct = Objects.nonNull(liveCyb) && Objects.nonNull(liveCyb.pctChg) ? liveCyb.pctChg : lastPct(cyb);
        BigDecimal kcPct = Objects.nonNull(liveKc) && Objects.nonNull(liveKc.pctChg) ? liveKc.pctChg : lastPct(kc);
        if (Objects.nonNull(liveSh)) {
            indexLines.add(lineOf("上证指数", liveSh.pctChg, liveSh.close));
            indexes.add(indexItemOf("上证指数", liveSh.pctChg, liveSh.close));
        } else {
            indexLines.add(lineOf("上证指数", sh));
            indexes.add(indexItemOf("上证指数", sh));
        }
        if (Objects.nonNull(liveSz)) {
            indexLines.add(lineOf("深证成指", liveSz.pctChg, liveSz.close));
            indexes.add(indexItemOf("深证成指", liveSz.pctChg, liveSz.close));
        } else {
            indexLines.add(lineOf("深证成指", sz));
            indexes.add(indexItemOf("深证成指", sz));
        }
        if (Objects.nonNull(liveCyb)) {
            indexLines.add(lineOf("创业板指", liveCyb.pctChg, liveCyb.close));
            indexes.add(indexItemOf("创业板指", liveCyb.pctChg, liveCyb.close));
        } else {
            indexLines.add(lineOf("创业板指", cyb));
            indexes.add(indexItemOf("创业板指", cyb));
        }
        if (Objects.nonNull(liveKc)) {
            indexLines.add(lineOf("科创50", liveKc.pctChg, liveKc.close));
            indexes.add(indexItemOf("科创50", liveKc.pctChg, liveKc.close));
        } else if (CollUtil.isNotEmpty(kc)) {
            indexLines.add(lineOf("科创50", kc));
            indexes.add(indexItemOf("科创50", kc));
        }

        BigDecimal dayAvg = avgNonNull(shPct, cybPct);
        if (Objects.nonNull(dayAvg)) {
            String daySignal = "中性";
            if (dayAvg.compareTo(new BigDecimal("0.8")) >= 0) {
                daySignal = "偏多";
                score += 12;
            } else if (dayAvg.compareTo(new BigDecimal("-0.8")) <= 0) {
                daySignal = "偏空";
                score -= 14;
            } else if (dayAvg.compareTo(ZERO) > 0) {
                score += 4;
            } else if (dayAvg.compareTo(ZERO) < 0) {
                score -= 5;
            }
            factors.add(MarketFactorItem.builder()
                    .name("大盘当日")
                    .value(fmtPct(dayAvg) + "（上证" + fmtPct(shPct) + "/创业" + fmtPct(cybPct) + "）")
                    .signal(daySignal)
                    .note("综合上证与创业板涨跌")
                    .build());
            if (dayAvg.compareTo(new BigDecimal("-1.5")) <= 0) {
                tips.add(tip("danger", "主要指数明显调整，控制开仓与追高，优先处理持仓风险。"));
            } else if (dayAvg.compareTo(new BigDecimal("1.5")) >= 0) {
                tips.add(tip("info", "指数强势上涨，可积极跟踪主线，但仍需避免尾盘情绪透支。"));
            }
        } else {
            factors.add(MarketFactorItem.builder()
                    .name("大盘当日")
                    .value("暂无指数数据")
                    .signal("提示")
                    .note("请先在「大盘」页刷新指数")
                    .build());
            tips.add(tip("warn", "本地缺少指数日线，市场立场仅供参考；请先同步指数数据。"));
        }

        // —— 趋势（相对 MA20）——
        BigDecimal shCloseLive = Objects.nonNull(liveSh) ? liveSh.close : null;
        Boolean aboveMa20 = aboveMaWithLive(sh, 20, shCloseLive);
        BigDecimal ret5 = cumReturnWithLive(sh, 5, shCloseLive);
        BigDecimal ret20 = cumReturnWithLive(sh, 20, shCloseLive);
        if (Objects.nonNull(aboveMa20)) {
            if (Boolean.TRUE.equals(aboveMa20)) {
                score += 10;
                factors.add(MarketFactorItem.builder()
                        .name("趋势位置")
                        .value("上证站上MA20")
                        .signal("偏多")
                        .note("近5日" + fmtPct(ret5) + " · 近20日" + fmtPct(ret20))
                        .build());
            } else {
                score -= 12;
                factors.add(MarketFactorItem.builder()
                        .name("趋势位置")
                        .value("上证低于MA20")
                        .signal("偏空")
                        .note("近5日" + fmtPct(ret5) + " · 近20日" + fmtPct(ret20))
                        .build());
                tips.add(tip("warn", "指数仍在均线下方，趋势未修复前宜降低进攻仓位。"));
            }
        }

        // —— 沪深京三市成交额与量能（对齐同花顺市场总览） ——
        VolumeStat vol = threeMarketAmountStat(sh, sz, bj, 5);
        String volumeTrend = null;
        BigDecimal volumeVsMa5Pct = null;
        BigDecimal indexVolume = null;
        BigDecimal indexVolumeChange = null;
        String indexVolumeText = null;
        if (Objects.nonNull(vol)) {
            volumeTrend = vol.trend;
            volumeVsMa5Pct = vol.vsMa5Pct;
            indexVolume = vol.volume;
            indexVolumeChange = vol.change;
            if (Objects.isNull(indexVolume) || indexVolume.signum() <= 0) {
                indexVolume = fetchLiveThreeMarketAmount();
            }
            if (Objects.isNull(vol.label)) {
                vol.label = MarketBriefingMath.volumeLabel(vol.vsMa5Pct, vol.usedLive);
            }
            indexVolumeText = formatAmount(indexVolume);
            String volSignal = "中性";
            if ("放量".equals(vol.trend) && Objects.nonNull(shPct) && shPct.compareTo(ZERO) >= 0) {
                volSignal = "偏多";
                score += 8;
                tips.add(tip("info", "三市放量上涨，资金参与度上升，可关注强度板块与持仓加仓机会。"));
            } else if ("放量".equals(vol.trend) && Objects.nonNull(shPct) && shPct.compareTo(ZERO) < 0) {
                volSignal = "偏空";
                score -= 10;
                tips.add(tip("danger", "三市放量下跌，抛压较重，谨慎抄底，优先减仓弱势持仓。"));
            } else if ("缩量".equals(vol.trend) && Objects.nonNull(shPct) && shPct.compareTo(ZERO) > 0) {
                volSignal = "提示";
                score -= 2;
                tips.add(tip("warn", "三市缩量上涨，上攻动能不足，追高性价比偏低。"));
            } else if ("缩量".equals(vol.trend)) {
                volSignal = "提示";
                tips.add(tip("info", "三市成交缩量，观望资金较多，等待放量确认方向。"));
            }
            String factorValue = Objects.nonNull(indexVolumeText)
                    ? (indexVolumeText + " · " + vol.trend + " · 较前日" + fmtPct(vol.vsMa5Pct))
                    : (vol.trend + " · 较前日" + fmtPct(vol.vsMa5Pct));
            factors.add(MarketFactorItem.builder()
                    .name("三市成交")
                    .value(factorValue)
                    .signal(volSignal)
                    .note(vol.usedAmount ? "上证+深成+北证50 三市成交额合计" : "本地成交额缺失，量能用成交量合计近似；总额优先东财实时")
                    .build());
        }

        // —— 风格（创业板/科创 vs 上证，近5日）——
        BigDecimal style5 = styleSpread(cyb, sh, 5);
        BigDecimal styleKc = styleSpread(kc, sh, 5);
        if (Objects.nonNull(style5)) {
            String styleName;
            String styleSignal;
            if (style5.compareTo(new BigDecimal("2")) >= 0) {
                styleName = "成长/小盘占优";
                styleSignal = "偏多";
                score += 5;
                tips.add(tip("info", "近5日创业板显著强于主板，风格偏向成长；选股可优先科技/题材弹性。"));
            } else if (style5.compareTo(new BigDecimal("-2")) <= 0) {
                styleName = "权重/防御占优";
                styleSignal = "提示";
                score -= 3;
                tips.add(tip("info", "近5日主板相对更强，风格偏权重/防御；追题材需更严格。"));
            } else {
                styleName = "风格均衡";
                styleSignal = "中性";
            }
            String extra = Objects.nonNull(styleKc) ? " · 科创相对主板" + fmtPct(styleKc) : "";
            factors.add(MarketFactorItem.builder()
                    .name("最近风格")
                    .value(styleName + "（创业-上证5日" + fmtPct(style5) + "）")
                    .signal(styleSignal)
                    .note("相对强弱" + extra)
                    .build());
        }

        // —— 涨停情绪 ——
        LimitUpStat lu = limitUpStat(asOf);
        if (Objects.nonNull(lu)) {
            String luSignal = "中性";
            if (lu.count >= 80 && lu.maxLianban >= 5) {
                luSignal = "提示";
                score += 3;
                tips.add(tip("warn", "涨停活跃且连板高度达" + lu.maxLianban
                        + "板，短线情绪偏热，注意分歧日回撤。"));
            } else if (lu.count <= 30) {
                luSignal = "偏空";
                score -= 6;
                tips.add(tip("warn", "涨停家数偏少（" + lu.count + "），赚钱效应一般，宜精选不强攻。"));
            } else if (lu.maxLianban >= 7) {
                tips.add(tip("warn", "最高连板" + lu.maxLianban + "，主线抱团明显，跟风需确认晋级率。"));
            } else {
                score += 2;
            }
            factors.add(MarketFactorItem.builder()
                    .name("涨停情绪")
                    .value("涨停" + lu.count + "家 · 最高" + lu.maxLianban + "板")
                    .signal(luSignal)
                    .note(lu.asOf != null ? ("池日期 " + lu.asOf) : "涨停池")
                    .build());
        } else {
            factors.add(MarketFactorItem.builder()
                    .name("涨停情绪")
                    .value("暂无涨停池")
                    .signal("提示")
                    .note("可在「涨停复盘」刷新")
                    .build());
        }

        // —— 市场广度（全市场涨/平/跌，平盘单独计、不并入涨）——
        int[] breadth = marketBreadth(asOf);
        Integer breadthUp = breadth[0] > 0 || breadth[1] > 0 ? breadth[0] : null;
        Integer breadthDown = breadth[0] > 0 || breadth[1] > 0 ? breadth[1] : null;
        Integer breadthFlat = Objects.nonNull(breadthUp) ? breadth[2] : null;
        if (Objects.nonNull(breadthUp)) {
            String bSignal = "中性";
            if (breadthUp > breadthDown * 1.5) {
                bSignal = "偏多";
                score += 6;
            } else if (breadthDown > breadthUp * 1.5) {
                bSignal = "偏空";
                score -= 8;
                tips.add(tip("warn", "下跌家数明显多于上涨，市场广度偏弱。"));
            }
            factors.add(MarketFactorItem.builder()
                    .name("市场广度")
                    .value("涨" + breadthUp + " / 平" + breadthFlat + " / 跌" + breadthDown)
                    .signal(bSignal)
                    .note("全市场涨跌家数（平盘单列）")
                    .build());
        }

        // —— 主线题材 ——
        List<MarketHotThemeItem> hotThemeItems = loadHotThemeItems();
        List<String> hotThemes = new ArrayList<>();
        for (MarketHotThemeItem item : hotThemeItems) {
            if (StringUtils.isNotBlank(item.getName())) {
                hotThemes.add(item.getName());
            }
        }
        if (CollUtil.isNotEmpty(hotThemeItems)) {
            int tipN = Math.min(4, hotThemeItems.size());
            List<String> tipLabels = new ArrayList<>();
            for (int i = 0; i < tipN; i++) {
                tipLabels.add(formatThemeWithPct(hotThemeItems.get(i)));
            }
            int factorN = Math.min(3, hotThemeItems.size());
            List<String> factorLabels = new ArrayList<>();
            for (int i = 0; i < factorN; i++) {
                factorLabels.add(formatThemeWithPct(hotThemeItems.get(i)));
            }
            factors.add(MarketFactorItem.builder()
                    .name("主线题材")
                    .value(String.join("、", factorLabels))
                    .signal("提示")
                    .note("资金+持续性为主，附当日涨幅")
                    .build());
            tips.add(tip("info", "今日关注主线：" + String.join("、", tipLabels)
                    + "；买卖优先与主线同向。"));
        }

        // —— 数据新鲜度门禁 ——
        String dataLevel = resolveDataLevel(sh, asOf, lu);
        boolean dataSufficient = !"RED".equals(dataLevel);
        if ("RED".equals(dataLevel)) {
            score = Math.min(score, 35);
            tips.add(0, tip("danger", "关键行情数据不足或过期，禁止进攻立场；请先同步指数/板块/涨停池。"));
        } else if ("YELLOW".equals(dataLevel)) {
            tips.add(tip("warn", "部分数据偏旧，简报仅供参考，建议先刷新大盘与涨停池。"));
        }

        score = Math.max(0, Math.min(100, score));
        String stance;
        BigDecimal buyFactor;
        String positionAdvice;
        if (!dataSufficient) {
            stance = "防守";
            buyFactor = new BigDecimal("0.40");
            positionAdvice = "数据不足：建议空仓观望或极低仓，先补齐同步";
            tips.add(0, tip("danger", "数据门禁生效：强制防守，买入仓位已大幅降权。"));
        } else if (score >= 65) {
            stance = "进攻";
            buyFactor = new BigDecimal("1.10");
            positionAdvice = "建议总仓 6–8 成，可对共振/主线标的正常/略抬仓";
            tips.add(0, tip("info", "综合评分偏进攻：可执行买入计划，但仍控制单票上限。"));
        } else if (score <= 40) {
            stance = "防守";
            buyFactor = new BigDecimal("0.55");
            positionAdvice = "建议总仓 2–4 成，新建仓降权，优先处理卖出/止损";
            tips.add(0, tip("danger", "综合评分偏防守：今日买入建议已自动降权，优先风控与持仓体检。"));
        } else {
            stance = "均衡";
            buyFactor = BigDecimal.ONE;
            positionAdvice = "建议总仓 4–6 成，精选共振/主线，避免无脑铺开";
            tips.add(0, tip("info", "市场中性偏均衡：有信号再做，仓位中等、纪律优先。"));
        }

        String stanceReason = "评分 " + score + "/100 · 综合大盘、趋势、量能、风格、广度与涨停情绪";
        if (tips.size() > 10) {
            tips = new ArrayList<>(tips.subList(0, 10));
        }

        return MarketBriefingResp.builder()
                .asOf(asOf)
                .stance(stance)
                .stanceScore(score)
                .stanceReason(stanceReason)
                .positionAdvice(positionAdvice)
                .buyWeightFactor(buyFactor)
                .factors(factors)
                .tips(tips)
                .indexLines(indexLines)
                .indexes(indexes)
                .volumeTrend(volumeTrend)
                .volumeVsMa5Pct(volumeVsMa5Pct)
                .volumeLabel(Objects.nonNull(vol) ? vol.label : null)
                .indexVolume(indexVolume)
                .indexVolumeChange(indexVolumeChange)
                .indexVolumeText(indexVolumeText)
                .limitUpCount(resolveLimitUpCount(lu))
                .limitDownCount(resolveLimitDownCount())
                .hotThemes(hotThemes)
                .hotThemeItems(hotThemeItems)
                .dataLevel(dataLevel)
                .dataSufficient(dataSufficient)
                .breadthUp(breadthUp)
                .breadthDown(breadthDown)
                .breadthFlat(breadthFlat)
                .message(Objects.nonNull(asOf)
                        ? ("市场简报 · " + asOf + " · 立场「" + stance + "」· 数据" + dataLevelLabel(dataLevel))
                        : "市场简报（指数数据不足）· 数据" + dataLevelLabel(dataLevel))
                .build();
    }

    /** @return [上涨, 下跌, 平盘]，平盘绝不并入上涨 */
    private int[] marketBreadth(LocalDate asOf) {
        int[] live = fetchLiveMarketBreadth();
        if (live[0] > 0 || live[1] > 0) {
            return live;
        }
        int[] fromBasic = breadthFromStockBasic();
        if (fromBasic[0] > 0 || fromBasic[1] > 0) {
            return fromBasic;
        }
        return breadthFromSectorQuote(asOf);
    }

    /**
     * 东财涨跌分布（全市场），失败时用沪深成份涨跌家数相加兜底
     */
    private int[] fetchLiveMarketBreadth() {
        long now = System.currentTimeMillis();
        synchronized (cacheLock) {
            if (Objects.nonNull(cachedLiveBreadth)
                    && now - cachedLiveBreadthAtMs < LIVE_QUOTE_TTL_MS) {
                return cachedLiveBreadth;
            }
        }
        int[] result = fetchBreadthFromZdFenBu();
        if (result[0] <= 0 && result[1] <= 0) {
            result = fetchBreadthFromIndexUlist();
        }
        synchronized (cacheLock) {
            cachedLiveBreadth = result;
            cachedLiveBreadthAtMs = System.currentTimeMillis();
        }
        return result;
    }

    private int[] fetchBreadthFromZdFenBu() {
        String url = "https://push2ex.eastmoney.com/getTopicZDFenBu"
                + "?ut=7eea3edcaed734bea9cbfc24409ed989&dpt=wz.ztzt";
        try (HttpResponse response = HttpRequest.get(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://quote.eastmoney.com/")
                .header("Accept", "application/json,text/plain,*/*")
                .timeout(5000)
                .execute()) {
            if (!response.isOk()) {
                return new int[]{0, 0, 0};
            }
            JSONObject data = JSONUtil.parseObj(response.body()).getJSONObject("data");
            if (Objects.isNull(data)) {
                return new int[]{0, 0, 0};
            }
            JSONArray fenbu = data.getJSONArray("fenbu");
            if (Objects.isNull(fenbu) || fenbu.isEmpty()) {
                return new int[]{0, 0, 0};
            }
            // 档位：>0 上涨，<0 下跌，0 平盘（绝不并入涨）
            int up = 0;
            int down = 0;
            int flat = 0;
            for (int i = 0; i < fenbu.size(); i++) {
                Object raw = fenbu.get(i);
                if (Objects.isNull(raw)) {
                    continue;
                }
                JSONObject bucket = raw instanceof JSONObject
                        ? (JSONObject) raw
                        : JSONUtil.parseObj(raw);
                for (Map.Entry<String, Object> entry : bucket.entrySet()) {
                    int level;
                    try {
                        level = Integer.parseInt(String.valueOf(entry.getKey()).trim());
                    } catch (Exception ignored) {
                        continue;
                    }
                    int count;
                    try {
                        count = new BigDecimal(String.valueOf(entry.getValue())).intValue();
                    } catch (Exception ignored) {
                        continue;
                    }
                    if (level == 11) {
                        synchronized (cacheLock) {
                            cachedFenbuLimitUp = Math.max(0, count);
                        }
                    } else if (level == -11) {
                        synchronized (cacheLock) {
                            cachedFenbuLimitDown = Math.max(0, count);
                        }
                    }
                    if (level > 0) {
                        up += count;
                    } else if (level < 0) {
                        down += count;
                    } else {
                        flat += count;
                    }
                }
            }
            return new int[]{up, down, flat};
        } catch (Exception ex) {
            log.debug("东财涨跌分布失败: {}", ex.getMessage());
            return new int[]{0, 0, 0};
        }
    }

    private int[] fetchBreadthFromIndexUlist() {
        // f104 上涨 / f105 下跌 / f106 平盘（分列，不合并）
        String url = "https://push2delay.eastmoney.com/api/qt/ulist.np/get"
                + "?fltt=2&secids=1.000001,0.399001&fields=f104,f105,f106,f12";
        try (HttpResponse response = HttpRequest.get(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://quote.eastmoney.com/")
                .header("Accept", "application/json,text/plain,*/*")
                .timeout(5000)
                .execute()) {
            if (!response.isOk()) {
                return new int[]{0, 0, 0};
            }
            JSONObject data = JSONUtil.parseObj(response.body()).getJSONObject("data");
            if (Objects.isNull(data)) {
                return new int[]{0, 0, 0};
            }
            JSONArray diff = data.getJSONArray("diff");
            if (Objects.isNull(diff) || diff.isEmpty()) {
                return new int[]{0, 0, 0};
            }
            int up = 0;
            int down = 0;
            int flat = 0;
            for (int i = 0; i < diff.size(); i++) {
                JSONObject row = diff.getJSONObject(i);
                if (Objects.isNull(row)) {
                    continue;
                }
                up += row.getInt("f104", 0);
                down += row.getInt("f105", 0);
                flat += row.getInt("f106", 0);
            }
            return new int[]{up, down, flat};
        } catch (Exception ex) {
            log.debug("东财指数涨跌家数失败: {}", ex.getMessage());
            return new int[]{0, 0, 0};
        }
    }

    private int[] breadthFromStockBasic() {
        try {
            Long up = stockBasicMapper.selectCount(Wrappers.<StockBasic>lambdaQuery()
                    .isNotNull(StockBasic::getPctChg)
                    .gt(StockBasic::getPctChg, ZERO));
            Long down = stockBasicMapper.selectCount(Wrappers.<StockBasic>lambdaQuery()
                    .isNotNull(StockBasic::getPctChg)
                    .lt(StockBasic::getPctChg, ZERO));
            Long flat = stockBasicMapper.selectCount(Wrappers.<StockBasic>lambdaQuery()
                    .isNotNull(StockBasic::getPctChg)
                    .eq(StockBasic::getPctChg, ZERO));
            return new int[]{
                    Objects.nonNull(up) ? up.intValue() : 0,
                    Objects.nonNull(down) ? down.intValue() : 0,
                    Objects.nonNull(flat) ? flat.intValue() : 0
            };
        } catch (Exception ex) {
            return new int[]{0, 0, 0};
        }
    }

    private int[] breadthFromSectorQuote(LocalDate asOf) {
        int up = 0;
        int down = 0;
        LocalDate day = asOf;
        List<SectorQuote> rows = new ArrayList<>();
        if (Objects.nonNull(day)) {
            rows = sectorQuoteMapper.selectList(Wrappers.<SectorQuote>lambdaQuery()
                    .eq(SectorQuote::getBoardType, "INDUSTRY")
                    .eq(SectorQuote::getTradeDate, day));
        }
        if (CollUtil.isEmpty(rows)) {
            SectorQuote latest = sectorQuoteMapper.selectOne(Wrappers.<SectorQuote>lambdaQuery()
                    .eq(SectorQuote::getBoardType, "INDUSTRY")
                    .orderByDesc(SectorQuote::getTradeDate)
                    .last("LIMIT 1"));
            if (Objects.isNull(latest)) {
                return new int[]{0, 0, 0};
            }
            day = latest.getTradeDate();
            rows = sectorQuoteMapper.selectList(Wrappers.<SectorQuote>lambdaQuery()
                    .eq(SectorQuote::getBoardType, "INDUSTRY")
                    .eq(SectorQuote::getTradeDate, day));
        }
        for (SectorQuote row : rows) {
            if (Objects.nonNull(row.getUpCount())) {
                up += row.getUpCount();
            }
            if (Objects.nonNull(row.getDownCount())) {
                down += row.getDownCount();
            }
        }
        // 板块汇总无平盘字段，且存在重复计票，仅作兜底
        return new int[]{up, down, 0};
    }

    /**
     * 数据充分性中文标签
     */
    private static String dataLevelLabel(String level) {
        if ("GREEN".equals(level)) {
            return "正常";
        }
        if ("YELLOW".equals(level)) {
            return "预警";
        }
        if ("RED".equals(level)) {
            return "异常";
        }
        return StringUtils.isBlank(level) ? "-" : level;
    }

    private String resolveDataLevel(List<IndexBar> sh, LocalDate asOf, LimitUpStat lu) {
        LocalDate latestTrade = TradingCalendar.latestTradingDayOnOrBefore(LocalDate.now());
        // 允许落后最近 3 个交易日
        List<LocalDate> recent3 = TradingCalendar.recentTradingDays(latestTrade, 3);
        LocalDate oldestOk = recent3.get(0);

        boolean hasIndex = CollUtil.isNotEmpty(sh) && Objects.nonNull(asOf);
        boolean indexFresh = false;
        if (hasIndex) {
            LocalDate latest = sh.get(sh.size() - 1).getTradeDate();
            indexFresh = Objects.nonNull(latest) && !latest.isBefore(oldestOk);
        }
        boolean hasLu = Objects.nonNull(lu)
                && Objects.nonNull(lu.asOf)
                && !lu.asOf.isBefore(oldestOk);
        SectorQuote sectorLatest = sectorQuoteMapper.selectOne(Wrappers.<SectorQuote>lambdaQuery()
                .eq(SectorQuote::getBoardType, "INDUSTRY")
                .orderByDesc(SectorQuote::getTradeDate)
                .last("LIMIT 1"));
        boolean hasSector = Objects.nonNull(sectorLatest)
                && Objects.nonNull(sectorLatest.getTradeDate())
                && !sectorLatest.getTradeDate().isBefore(oldestOk);
        if (!hasIndex || !indexFresh) {
            return "RED";
        }
        if (!hasSector || !hasLu) {
            return "YELLOW";
        }
        return "GREEN";
    }

    private List<IndexBar> loadBars(String code, int limit) {
        List<IndexBar> desc = indexBarMapper.selectList(Wrappers.<IndexBar>lambdaQuery()
                .eq(IndexBar::getCode, code)
                .orderByDesc(IndexBar::getTradeDate)
                .last("LIMIT " + Math.max(limit, 5)));
        Collections.reverse(desc);
        return desc;
    }

    private LocalDate latestDate(List<IndexBar> a, List<IndexBar> b, List<IndexBar> c) {
        LocalDate d = null;
        for (List<IndexBar> list : List.of(a, b, c)) {
            if (CollUtil.isEmpty(list)) {
                continue;
            }
            LocalDate t = list.get(list.size() - 1).getTradeDate();
            if (Objects.isNull(d) || (Objects.nonNull(t) && t.isAfter(d))) {
                d = t;
            }
        }
        return d;
    }

    private BigDecimal lastPct(List<IndexBar> bars) {
        if (CollUtil.isEmpty(bars)) {
            return null;
        }
        return bars.get(bars.size() - 1).getPctChg();
    }

    private Boolean aboveMa(List<IndexBar> bars, int n) {
        if (CollUtil.isEmpty(bars) || bars.size() < n) {
            return null;
        }
        BigDecimal close = bars.get(bars.size() - 1).getClosePrice();
        if (Objects.isNull(close)) {
            return null;
        }
        BigDecimal sum = ZERO;
        int cnt = 0;
        for (int i = bars.size() - n; i < bars.size(); i++) {
            BigDecimal c = bars.get(i).getClosePrice();
            if (Objects.isNull(c)) {
                continue;
            }
            sum = sum.add(c);
            cnt++;
        }
        if (cnt < n) {
            return null;
        }
        BigDecimal ma = sum.divide(BigDecimal.valueOf(n), 6, RoundingMode.HALF_UP);
        return close.compareTo(ma) >= 0;
    }

    private BigDecimal cumReturn(List<IndexBar> bars, int days) {
        if (CollUtil.isEmpty(bars) || bars.size() <= days) {
            return null;
        }
        BigDecimal end = bars.get(bars.size() - 1).getClosePrice();
        BigDecimal start = bars.get(bars.size() - 1 - days).getClosePrice();
        if (Objects.isNull(end) || Objects.isNull(start) || start.signum() == 0) {
            return null;
        }
        return end.subtract(start).divide(start, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal styleSpread(List<IndexBar> a, List<IndexBar> b, int days) {
        BigDecimal ra = cumReturn(a, days);
        BigDecimal rb = cumReturn(b, days);
        if (Objects.isNull(ra) || Objects.isNull(rb)) {
            return null;
        }
        return ra.subtract(rb).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 三市成交额序列：优先 amount；量能对比上一交易日（对齐同花顺较前日）
     */
    private VolumeStat threeMarketAmountStat(List<IndexBar> sh, List<IndexBar> sz, List<IndexBar> bj, int maN) {
        Map<LocalDate, BigDecimal> amountByDate = new HashMap<>();
        mergeMetric(amountByDate, sh, true);
        mergeMetric(amountByDate, sz, true);
        mergeMetric(amountByDate, bj, true);
        mergeAmountHistoryFromSnapshots(amountByDate);

        LocalDate sessionDay = resolveSessionDay();
        BigDecimal live = fetchLiveThreeMarketAmount();
        boolean usedLive = Objects.nonNull(live) && live.signum() > 0;
        if (usedLive) {
            rememberLiveAmount(sessionDay, live);
            amountByDate.put(sessionDay, live);
        }
        BigDecimal last = usedLive ? live : amountByDate.get(sessionDay);
        if (Objects.isNull(last) || last.signum() <= 0) {
            List<LocalDate> datesFallback = new ArrayList<>(amountByDate.keySet());
            datesFallback.sort(LocalDate::compareTo);
            if (!datesFallback.isEmpty()) {
                last = amountByDate.get(datesFallback.get(datesFallback.size() - 1));
            }
        }
        if (Objects.isNull(last) || last.signum() <= 0) {
            return null;
        }

        List<LocalDate> dates = new ArrayList<>(amountByDate.keySet());
        dates.sort(LocalDate::compareTo);
        BigDecimal prevAmount = null;
        for (int i = dates.size() - 1; i >= 0; i--) {
            LocalDate d = dates.get(i);
            if (!d.isBefore(sessionDay)) {
                continue;
            }
            BigDecimal v = amountByDate.get(d);
            if (Objects.nonNull(v) && v.signum() > 0) {
                prevAmount = v;
                break;
            }
        }
        // 库内缺上日三市额时，用腾讯两市较上日变动比例近似（与同花顺「较前日」同向同量级）
        if (Objects.isNull(prevAmount)) {
            QqMarketOverview qq = fetchQqMarketOverview();
            if (Objects.nonNull(qq)
                    && Objects.nonNull(qq.amount)
                    && qq.amount.signum() > 0
                    && Objects.nonNull(qq.amountChange)) {
                BigDecimal qqPrev = qq.amount.subtract(qq.amountChange);
                if (qqPrev.signum() > 0) {
                    BigDecimal ratio = last.divide(qq.amount, 8, RoundingMode.HALF_UP);
                    prevAmount = qqPrev.multiply(ratio);
                }
            }
        }
        VolumeStat stat = new VolumeStat();
        stat.volume = last;
        stat.usedAmount = true;
        stat.usedLive = usedLive;
        if (Objects.isNull(prevAmount) || prevAmount.signum() <= 0) {
            stat.trend = null;
            stat.vsMa5Pct = null;
            stat.label = MarketBriefingMath.volumeLabel(null, usedLive);
            return stat;
        }
        BigDecimal vs = last.subtract(prevAmount).divide(prevAmount, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
        stat.change = MarketBriefingMath.volumeChange(last, prevAmount);
        stat.trend = vs.compareTo(ZERO) >= 0 ? "放量" : "缩量";
        // 字段名沿用 volumeVsMa5Pct，语义改为较上一交易日
        stat.vsMa5Pct = vs;
        stat.label = MarketBriefingMath.volumeLabel(vs, usedLive);
        return stat;
    }

    private void mergeMetric(Map<LocalDate, BigDecimal> target, List<IndexBar> bars, boolean useAmount) {
        if (CollUtil.isEmpty(bars)) {
            return;
        }
        for (IndexBar bar : bars) {
            if (Objects.isNull(bar) || Objects.isNull(bar.getTradeDate())) {
                continue;
            }
            BigDecimal v = useAmount ? bar.getAmount() : bar.getVolume();
            if (Objects.isNull(v) || v.signum() <= 0) {
                continue;
            }
            target.merge(bar.getTradeDate(), v, BigDecimal::add);
        }
    }

    /**
     * 实时三市成交额：上证 + 深成 + 北证50（元）
     */
    private BigDecimal fetchLiveThreeMarketAmount() {
        long now = System.currentTimeMillis();
        synchronized (cacheLock) {
            // 成功/失败都短缓存，避免看板反复打外网
            long ttl = Objects.nonNull(cachedLiveAmount) && cachedLiveAmount.signum() > 0
                    ? LIVE_AMOUNT_TTL_MS : LIVE_AMOUNT_MISS_TTL_MS;
            if (cachedLiveAmountAtMs > 0 && now - cachedLiveAmountAtMs < ttl) {
                return cachedLiveAmount;
            }
        }
        // 东财 ulist f6 优先；新浪 GBK bodyBytes 兜底
        BigDecimal live = fetchThreeMarketAmountFromEastMoney();
        if (Objects.isNull(live) || live.signum() <= 0) {
            live = fetchThreeMarketAmountFromSina();
        }
        if (Objects.nonNull(live) && live.signum() <= 0) {
            live = null;
        }
        synchronized (cacheLock) {
            cachedLiveAmount = live;
            cachedLiveAmountAtMs = System.currentTimeMillis();
        }
        return live;
    }

    private BigDecimal fetchThreeMarketAmountFromEastMoney() {
        // 指数成交额用 ulist f6（f48 对指数常为 0）；含北证50 对齐同花顺三市
        String url = "https://push2delay.eastmoney.com/api/qt/ulist.np/get"
                + "?fltt=2&secids=1.000001,0.399001,0.899050&fields=f6,f12";
        try (HttpResponse response = HttpRequest.get(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://quote.eastmoney.com/")
                .header("Accept", "application/json,text/plain,*/*")
                .timeout(3000)
                .execute()) {
            if (!response.isOk()) {
                return null;
            }
            JSONObject data = JSONUtil.parseObj(response.body()).getJSONObject("data");
            if (Objects.isNull(data)) {
                return null;
            }
            JSONArray diff = data.getJSONArray("diff");
            if (Objects.isNull(diff) || diff.isEmpty()) {
                return null;
            }
            BigDecimal sum = ZERO;
            int hit = 0;
            for (int i = 0; i < diff.size(); i++) {
                JSONObject row = diff.getJSONObject(i);
                if (Objects.isNull(row)) {
                    continue;
                }
                BigDecimal amt = row.getBigDecimal("f6");
                if (Objects.nonNull(amt) && amt.signum() > 0) {
                    sum = sum.add(amt);
                    hit++;
                }
            }
            return hit > 0 ? sum : null;
        } catch (Exception ex) {
            log.debug("东财 ulist 三市成交额失败: {}", ex.getMessage());
            return null;
        }
    }

    private BigDecimal fetchThreeMarketAmountFromSina() {
        // hq 字段：index9=成交量，index10=成交额
        String url = "https://hq.sinajs.cn/list=sh000001,sz399001,bj899050";
        try (HttpResponse response = HttpRequest.get(url)
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", "https://finance.sina.com.cn/")
                .timeout(2000)
                .execute()) {
            if (!response.isOk()) {
                return null;
            }
            String body = new String(response.bodyBytes(), Charset.forName("GBK"));
            if (StringUtils.isBlank(body)) {
                return null;
            }
            BigDecimal sum = ZERO;
            int hit = 0;
            String[] lines = body.split(";");
            for (String line : lines) {
                int q1 = line.indexOf('"');
                int q2 = line.lastIndexOf('"');
                if (q1 < 0 || q2 <= q1) {
                    continue;
                }
                String[] parts = line.substring(q1 + 1, q2).split(",");
                if (parts.length < 10) {
                    continue;
                }
                try {
                    BigDecimal amount = new BigDecimal(parts[9].trim());
                    if (amount.signum() > 0) {
                        sum = sum.add(amount);
                        hit++;
                    }
                } catch (Exception ignored) {
                    // skip
                }
            }
            return hit > 0 ? sum : null;
        } catch (Exception ex) {
            log.debug("新浪三市成交额失败: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * ????????????????????????????? token?
     */
    private QqMarketOverview fetchQqMarketOverview() {
        long now = System.currentTimeMillis();
        synchronized (cacheLock) {
            if (Objects.nonNull(cachedQqMarket)
                    && now - cachedQqMarketAtMs < LIVE_QUOTE_TTL_MS) {
                return cachedQqMarket;
            }
        }
        String url = "https://proxy.finance.qq.com/cgi/cgi-bin/market/hs/index?type=0";
        QqMarketOverview overview = null;
        try (HttpResponse response = HttpRequest.get(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://stockapp.finance.qq.com/")
                .header("Accept", "application/json,text/plain,*/*")
                .timeout(4000)
                .execute()) {
            if (response.isOk()) {
                JSONObject root = JSONUtil.parseObj(response.body());
                JSONObject data = root.getJSONObject("data");
                if (Objects.nonNull(data)) {
                    overview = new QqMarketOverview();
                    JSONObject ups = data.getJSONObject("ups_downs_dsb");
                    if (Objects.nonNull(ups)) {
                        overview.upLimitCount = ups.getInt("up_limit_count");
                        overview.downLimitCount = ups.getInt("down_limit_count");
                        overview.upCount = ups.getInt("up_count");
                        overview.downCount = ups.getInt("down_count");
                        overview.flatCount = ups.getInt("flat_count");
                    }
                    JSONObject turnover = data.getJSONObject("turnover_dsb");
                    if (Objects.nonNull(turnover)) {
                        JSONObject all = turnover.getJSONObject("all");
                        if (Objects.nonNull(all)) {
                            overview.amount = all.getBigDecimal("amount");
                            overview.amountChange = all.getBigDecimal("amount_change");
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.debug("??????????: {}", ex.getMessage());
        }
        synchronized (cacheLock) {
            cachedQqMarket = overview;
            cachedQqMarketAtMs = System.currentTimeMillis();
        }
        return overview;
    }

    private Integer resolveLimitUpCount(LimitUpStat lu) {
        QqMarketOverview qq = fetchQqMarketOverview();
        if (Objects.nonNull(qq) && Objects.nonNull(qq.upLimitCount)) {
            return qq.upLimitCount;
        }
        if (Objects.nonNull(cachedFenbuLimitUp)) {
            return cachedFenbuLimitUp;
        }
        Integer pool = fetchLiveLimitUpCount();
        if (Objects.nonNull(pool)) {
            return pool;
        }
        return Objects.nonNull(lu) ? lu.count : null;
    }

    private Integer resolveLimitDownCount() {
        QqMarketOverview qq = fetchQqMarketOverview();
        if (Objects.nonNull(qq) && Objects.nonNull(qq.downLimitCount)) {
            return qq.downLimitCount;
        }
        return countLimitDown();
    }

    private Integer countLimitDown() {
        Integer live = fetchLiveLimitDownCount();
        if (Objects.nonNull(live)) {
            return live;
        }
        if (Objects.nonNull(cachedFenbuLimitDown)) {
            return cachedFenbuLimitDown;
        }
        return null;
    }


    private LocalDate resolveSessionDay() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate day = now.toLocalDate();
        if (now.getHour() < 9 || (now.getHour() == 9 && now.getMinute() < 15)) {
            day = day.minusDays(1);
        }
        while (day.getDayOfWeek().getValue() >= 6) {
            day = day.minusDays(1);
        }
        return day;
    }

    private void rememberLiveAmount(LocalDate day, BigDecimal amount) {
        if (Objects.isNull(day) || Objects.isNull(amount) || amount.signum() <= 0) {
            return;
        }
        synchronized (cacheLock) {
            recentLiveAmountByDate.put(day, amount);
            while (recentLiveAmountByDate.size() > 12) {
                LocalDate first = recentLiveAmountByDate.keySet().iterator().next();
                recentLiveAmountByDate.remove(first);
            }
        }
    }

    private void mergeAmountHistoryFromSnapshots(Map<LocalDate, BigDecimal> amountByDate) {
        synchronized (cacheLock) {
            for (Map.Entry<LocalDate, BigDecimal> e : recentLiveAmountByDate.entrySet()) {
                if (Objects.nonNull(e.getValue()) && e.getValue().signum() > 0) {
                    amountByDate.putIfAbsent(e.getKey(), e.getValue());
                }
            }
        }
        try {
            List<MarketBriefingSnapshot> snaps = marketBriefingSnapshotMapper.selectList(
                    Wrappers.<MarketBriefingSnapshot>lambdaQuery()
                            .ge(MarketBriefingSnapshot::getTradeDate, LocalDate.now().minusDays(20))
                            .orderByDesc(MarketBriefingSnapshot::getTradeDate)
                            .last("LIMIT 12"));
            if (CollUtil.isEmpty(snaps)) {
                return;
            }
            for (MarketBriefingSnapshot snap : snaps) {
                if (Objects.isNull(snap) || Objects.isNull(snap.getTradeDate())
                        || StringUtils.isBlank(snap.getPayloadJson())) {
                    continue;
                }
                BigDecimal exist = amountByDate.get(snap.getTradeDate());
                if (Objects.nonNull(exist) && exist.signum() > 0) {
                    continue;
                }
                MarketBriefingResp payload = JsonUtils.parseObject(snap.getPayloadJson(), MarketBriefingResp.class);
                if (Objects.isNull(payload) || Objects.isNull(payload.getIndexVolume())
                        || payload.getIndexVolume().signum() <= 0) {
                    continue;
                }
                amountByDate.put(snap.getTradeDate(), payload.getIndexVolume());
            }
        } catch (Exception ex) {
            log.debug("从简报快照补成交额失败: {}", ex.getMessage());
        }
    }

    private String formatAmount(BigDecimal amount) {
        if (Objects.isNull(amount) || amount.signum() <= 0) {
            return null;
        }
        BigDecimal yi = new BigDecimal("100000000");
        BigDecimal wan = new BigDecimal("10000");
        // 三市成交额通常 1~3 万亿：统一用「亿」展示（如 20113亿），避免「2.01万亿」难对照
        if (amount.compareTo(yi) >= 0) {
            return amount.divide(yi, 0, RoundingMode.HALF_UP).toPlainString() + "亿";
        }
        if (amount.compareTo(wan) >= 0) {
            return amount.divide(wan, 1, RoundingMode.HALF_UP).toPlainString() + "万";
        }
        return amount.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private LimitUpStat limitUpStat(LocalDate preferDate) {
        LocalDate day = preferDate;
        if (Objects.isNull(day)) {
            LimitUpPool latest = limitUpPoolMapper.selectOne(Wrappers.<LimitUpPool>lambdaQuery()
                    .orderByDesc(LimitUpPool::getTradeDate)
                    .last("LIMIT 1"));
            if (Objects.isNull(latest)) {
                return null;
            }
            day = latest.getTradeDate();
        }
        List<LimitUpPool> rows = limitUpPoolMapper.selectList(Wrappers.<LimitUpPool>lambdaQuery()
                .eq(LimitUpPool::getTradeDate, day));
        if (CollUtil.isEmpty(rows) && Objects.nonNull(preferDate)) {
            LimitUpPool latest = limitUpPoolMapper.selectOne(Wrappers.<LimitUpPool>lambdaQuery()
                    .orderByDesc(LimitUpPool::getTradeDate)
                    .last("LIMIT 1"));
            if (Objects.isNull(latest)) {
                return null;
            }
            day = latest.getTradeDate();
            rows = limitUpPoolMapper.selectList(Wrappers.<LimitUpPool>lambdaQuery()
                    .eq(LimitUpPool::getTradeDate, day));
        }
        if (CollUtil.isEmpty(rows)) {
            return null;
        }
        int max = 1;
        for (LimitUpPool row : rows) {
            if (Objects.nonNull(row.getLianban()) && row.getLianban() > max) {
                max = row.getLianban();
            }
        }
        LimitUpStat stat = new LimitUpStat();
        stat.asOf = day;
        stat.count = rows.size();
        stat.maxLianban = max;
        return stat;
    }

    private List<MarketHotThemeItem> loadHotThemeItems() {
        List<MarketHotThemeItem> themes = new ArrayList<>();
        try {
            List<SectorBoardItem> mainline = sectorBoardService.mainline(null, 6);
            if (CollUtil.isNotEmpty(mainline)) {
                for (SectorBoardItem item : mainline) {
                    if (StringUtils.isNotBlank(item.getName()) && !MainlineBoardRules.isOutcomeBoard(item.getName())) {
                        themes.add(MarketHotThemeItem.builder()
                                .name(item.getName())
                                .pctChg(scalePct(item.getPctChg()))
                                .boardType(item.getBoardType())
                                .build());
                    }
                }
            }
        } catch (Exception ex) {
            log.debug("主线识别失败: {}", ex.getMessage());
        }
        if (CollUtil.isNotEmpty(themes)) {
            return themes;
        }
        // 退化：题材/概念按净流入+3日涨幅，排除结果型板
        SectorQuote latest = sectorQuoteMapper.selectOne(Wrappers.<SectorQuote>lambdaQuery()
                .in(SectorQuote::getBoardType, List.of("THEME", "CONCEPT", "INDUSTRY"))
                .orderByDesc(SectorQuote::getTradeDate)
                .last("LIMIT 1"));
        if (Objects.isNull(latest) || Objects.isNull(latest.getTradeDate())) {
            return themes;
        }
        List<SectorQuote> tops = sectorQuoteMapper.selectList(Wrappers.<SectorQuote>lambdaQuery()
                .eq(SectorQuote::getTradeDate, latest.getTradeDate())
                .in(SectorQuote::getBoardType, List.of("INDUSTRY", "CONCEPT", "THEME"))
                .orderByDesc(SectorQuote::getNetInflow)
                .orderByDesc(SectorQuote::getPctChg3d)
                .orderByDesc(SectorQuote::getPctChg)
                .last("LIMIT 30"));
        Map<String, Boolean> seen = new HashMap<>();
        for (SectorQuote q : tops) {
            if (StringUtils.isBlank(q.getName())
                    || MainlineBoardRules.isOutcomeBoard(q.getName())
                    || seen.containsKey(q.getName())) {
                continue;
            }
            seen.put(q.getName(), Boolean.TRUE);
            themes.add(MarketHotThemeItem.builder()
                    .name(q.getName())
                    .pctChg(scalePct(q.getPctChg()))
                    .boardType(q.getBoardType())
                    .build());
            if (themes.size() >= 5) {
                break;
            }
        }
        return themes;
    }

    private static BigDecimal scalePct(BigDecimal pct) {
        if (Objects.isNull(pct)) {
            return null;
        }
        return pct.setScale(2, RoundingMode.HALF_UP);
    }

    private static String formatThemeWithPct(MarketHotThemeItem item) {
        if (Objects.isNull(item) || StringUtils.isBlank(item.getName())) {
            return "";
        }
        if (Objects.isNull(item.getPctChg())) {
            return item.getName();
        }
        BigDecimal pct = item.getPctChg();
        String sign = "";
        if (pct.compareTo(BigDecimal.ZERO) > 0) {
            sign = "+";
        } else if (pct.compareTo(BigDecimal.ZERO) < 0) {
            sign = "\u2212";
        }
        String abs = pct.abs().setScale(2, RoundingMode.HALF_UP).toPlainString();
        return item.getName() + " " + sign + abs + "%";
    }

    private String lineOf(String label, List<IndexBar> bars) {
        if (CollUtil.isEmpty(bars)) {
            return label + " -";
        }
        IndexBar last = bars.get(bars.size() - 1);
        return label + " " + fmtPct(last.getPctChg())
                + (Objects.nonNull(last.getClosePrice())
                ? (" · " + last.getClosePrice().setScale(2, RoundingMode.HALF_UP))
                : "");
    }

    private MarketIndexItem indexItemOf(String label, List<IndexBar> bars) {
        if (CollUtil.isEmpty(bars)) {
            return MarketIndexItem.builder()
                    .name(label)
                    .direction("flat")
                    .build();
        }
        IndexBar last = bars.get(bars.size() - 1);
        BigDecimal pct = last.getPctChg();
        String direction = "flat";
        if (Objects.nonNull(pct)) {
            if (pct.compareTo(ZERO) > 0) {
                direction = "up";
            } else if (pct.compareTo(ZERO) < 0) {
                direction = "down";
            }
        }
        return MarketIndexItem.builder()
                .name(label)
                .pctChg(Objects.nonNull(pct) ? pct.setScale(2, RoundingMode.HALF_UP) : null)
                .close(Objects.nonNull(last.getClosePrice())
                        ? last.getClosePrice().setScale(2, RoundingMode.HALF_UP) : null)
                .direction(direction)
                .build();
    }

    private BigDecimal avgNonNull(BigDecimal a, BigDecimal b) {
        if (Objects.nonNull(a) && Objects.nonNull(b)) {
            return a.add(b).divide(new BigDecimal("2"), 4, RoundingMode.HALF_UP);
        }
        return Objects.nonNull(a) ? a : b;
    }

    private String fmtPct(BigDecimal pct) {
        if (Objects.isNull(pct)) {
            return "-";
        }
        String sign = pct.compareTo(ZERO) > 0 ? "+" : "";
        return sign + pct.setScale(2, RoundingMode.HALF_UP) + "%";
    }

    private MarketTipItem tip(String level, String text) {
        return MarketTipItem.builder().level(level).text(text).build();
    }

    private static final class VolumeStat {
        private String trend;
        private BigDecimal vsMa5Pct;
        private String label;
        /** 三市成交额（元） */
        private BigDecimal volume;
        /** 较上一交易日成交额增减值（元） */
        private BigDecimal change;
        private boolean usedAmount;
        private boolean usedLive;
    }

    private static final class QqMarketOverview {
        private Integer upLimitCount;
        private Integer downLimitCount;
        private Integer upCount;
        private Integer downCount;
        private Integer flatCount;
        private BigDecimal amount;
        private BigDecimal amountChange;
    }

    private static final class LiveIndexQuote {
        private String name;
        private BigDecimal close;
        private BigDecimal pctChg;
    }

    private static final class LimitUpStat {
        private LocalDate asOf;
        private int count;
        private int maxLianban;
    }
}
