package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.util.JsonUtils;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.ai.KimiChatClient;
import com.awe.apex.quant.cache.RedisCacheService;
import com.awe.apex.quant.domain.dto.MarketBriefingResp;
import com.awe.apex.quant.domain.dto.MarketHotThemeItem;
import com.awe.apex.quant.domain.dto.WeekendFridaySnapshotResp;
import com.awe.apex.quant.domain.dto.WeekendIndexPerformanceResp;
import com.awe.apex.quant.domain.dto.WeekendMarketReportResp;
import com.awe.apex.quant.domain.dto.WeekendMarketScenarioResp;
import com.awe.apex.quant.domain.dto.WeekendNewsItemResp;
import com.awe.apex.quant.domain.dto.WeekendOpinionItemResp;
import com.awe.apex.quant.domain.dto.WeekendTradingThemeResp;
import com.awe.apex.quant.domain.entity.IndexBar;
import com.awe.apex.quant.domain.entity.MarketNews;
import com.awe.apex.quant.domain.entity.MarketOpinion;
import com.awe.apex.quant.mapper.IndexBarMapper;
import com.awe.apex.quant.mapper.MarketNewsMapper;
import com.awe.apex.quant.mapper.MarketOpinionMapper;
import com.awe.apex.quant.market.TradingCalendar;
import com.awe.apex.quant.service.IMarketBriefingService;
import com.awe.apex.quant.service.IMarketOpinionService;
import com.awe.apex.quant.service.INewsService;
import com.awe.apex.quant.service.IWeekendMarketReportService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 全市场周末消息面专题研报实现。
 */
@Slf4j
@Service
public class WeekendMarketReportServiceImpl implements IWeekendMarketReportService {

    static final String CACHE_KEY = "apex:weekend-market-report:latest:v2";
    static final Duration CACHE_TTL = Duration.ofDays(14);
    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalTime WEEKEND_REPORT_VISIBLE_TIME = LocalTime.of(21, 0);
    private static final LocalTime MARKET_OPEN_TIME = LocalTime.of(9, 30);
    private static final int MAX_NEWS_COUNT = 8;
    private static final List<String> INDEX_CODES = List.of("CN_SH", "CN_SZ", "CN_CYB", "CN_KC50", "CN_BJ50");
    private static final List<String> NEWS_SOURCES = List.of("eastmoney", "cls", "ths", "sina");
    private static final List<String> IRRELEVANT_NEWS_KEYWORDS = List.of(
            "足球", "中足联", "足协", "餐饮门店", "消杀", "采访资格", "居民告别", "拉闸限电");
    private static final List<String> COMPANY_EVENT_KEYWORDS = List.of(
            "半年报", "年报", "季报", "业绩", "营收", "净利润", "扭亏", "预增", "预减", "中标", "订单",
            "签署", "收购", "并购", "重组", "回购", "增持", "减持", "停牌", "复牌", "定增", "获批", "出货量");
    private static final List<String> POLICY_AUTHORITIES = List.of(
            "国务院", "央行", "证监会", "财政部", "商务部", "发改委", "工信部", "金融监管总局");
    private static final List<String> POLICY_ACTION_KEYWORDS = List.of(
            "政策", "监管", "降准", "降息", "利率", "关税", "出口", "进口", "制裁", "补贴", "税率", "指导意见");
    private static final List<String> INDUSTRY_KEYWORDS = List.of(
            "半导体", "芯片", "DRAM", "NAND", "GPU", "SoC", "先进封装", "测试设备", "长鑫存储", "电子气体",
            "商业航天", "卫星", "宇航", "并购重组", "原油", "天然气", "油气", "创新药", "医药", "临床",
            "农业", "粮食", "转基因", "种业", "种植", "新能源汽车", "新能源车");

    @Resource
    private MarketNewsMapper marketNewsMapper;

    @Resource
    private MarketOpinionMapper marketOpinionMapper;

    @Resource
    private IndexBarMapper indexBarMapper;

    @Resource
    private IMarketBriefingService marketBriefingService;

    @Resource
    private INewsService newsService;

    @Resource
    private IMarketOpinionService marketOpinionService;

    @Resource
    private RedisCacheService redisCacheService;

    @Resource
    private KimiChatClient kimiChatClient;

    /**
     * 读取最新周末研报。
     *
     * @param forceRefresh 是否强制重新生成
     * @return 周末研报
     */
    @Override
    public WeekendMarketReportResp latest(boolean forceRefresh) {
        return latest(forceRefresh, LocalDateTime.now(SHANGHAI_ZONE));
    }

    WeekendMarketReportResp latest(boolean forceRefresh, LocalDateTime currentTime) {
        if (!isVisibleWindow(currentTime)) {
            return null;
        }
        if (!forceRefresh) {
            WeekendMarketReportResp cachedReport = redisCacheService.get(CACHE_KEY, WeekendMarketReportResp.class);
            LocalDate expectedLastTradeDate = resolveLastCompletedTradeDate(currentTime.toLocalDate());
            if (Objects.nonNull(cachedReport) && expectedLastTradeDate.equals(cachedReport.getWeekEndDate())) {
                return cachedReport;
            }
        }
        return generate();
    }

    /**
     * 生成最新周末研报。
     *
     * @return 周末研报
     */
    @Override
    public WeekendMarketReportResp generate() {
        return generate(List.of());
    }

    private WeekendMarketReportResp generate(List<String> initialMissingData) {
        LocalDateTime generatedAt = LocalDateTime.now(SHANGHAI_ZONE);
        LocalDate lastTradeDate = resolveLastCompletedTradeDate(generatedAt.toLocalDate());
        LocalDateTime reportCutoff = resolveReportCutoff(lastTradeDate, generatedAt);
        List<LocalDate> weekDates = resolveWeekDates(lastTradeDate);
        List<String> missingData = new ArrayList<>(initialMissingData);

        List<WeekendIndexPerformanceResp> indexPerformance = loadIndexPerformance(weekDates.get(0),
                lastTradeDate, missingData);
        WeekendFridaySnapshotResp fridaySnapshot = loadFridaySnapshot(lastTradeDate, missingData);
        List<WeekendNewsItemResp> weekendNews = loadWeekendNews(lastTradeDate, reportCutoff, missingData);
        List<WeekendOpinionItemResp> marketOpinions = loadMarketOpinions(reportCutoff, missingData);
        List<WeekendTradingThemeResp> tradingThemes = buildTradingThemes(weekendNews);
        List<WeekendMarketScenarioResp> scenarios = buildScenarios(fridaySnapshot);

        String marketStatus = resolveMarketStatus(fridaySnapshot, indexPerformance);
        String coreView = buildCoreView(marketStatus, weekendNews, tradingThemes);
        String maxRisk = buildMaxRisk(fridaySnapshot, missingData);
        WeekendMarketReportResp context = WeekendMarketReportResp.builder()
                .reportDate(reportCutoff.toLocalDate())
                .weekStartDate(weekDates.get(0))
                .weekEndDate(lastTradeDate)
                .lastTradeDate(lastTradeDate)
                .generatedAt(generatedAt)
                .dataAsOf(resolveDataAsOf(fridaySnapshot, weekendNews, marketOpinions, reportCutoff))
                .dataLevel(resolveDataLevel(indexPerformance, fridaySnapshot, weekendNews, marketOpinions, missingData))
                .reportSource("RULE")
                .coreView(coreView)
                .marketStatus(marketStatus)
                .maxRisk(maxRisk)
                .indexPerformance(indexPerformance)
                .fridaySnapshot(fridaySnapshot)
                .weekendNews(weekendNews)
                .marketOpinions(marketOpinions)
                .tradingThemes(tradingThemes)
                .scenarios(scenarios)
                .missingData(missingData)
                .build();

        String content = buildRuleReport(context);
        String reportSource = "RULE";
        if (kimiChatClient.available()) {
            try {
                String userPrompt = "以下 JSON 是本次研报唯一允许使用的事实与证据上下文：\n"
                        + JsonUtils.toJsonString(context);
                String modelContent = normalizeModelContent(kimiChatClient.chat(loadSystemPrompt(), userPrompt, 2600));
                if (isCompleteReport(modelContent, context)) {
                    content = modelContent;
                    reportSource = "AI";
                } else {
                    log.warn("周末研报模型输出不完整，使用规则版，统计周={}至{}", weekDates.get(0), lastTradeDate);
                }
            } catch (Exception ex) {
                log.warn("周末研报模型生成失败，使用规则版，最后交易日={}，原因={}", lastTradeDate, ex.getMessage());
            }
        }
        WeekendMarketReportResp report = context.toBuilder().reportSource(reportSource).content(content).build();
        List<String> qualityWarnings = new ArrayList<>();
        boolean fullContent = "AI".equals(reportSource)
                || (CollUtil.isNotEmpty(context.getTradingThemes())
                && !"本周末无新增主线".equals(context.getTradingThemes().get(0).getTheme()));
        if (!fullContent) {
            qualityWarnings.add(CollUtil.isEmpty(context.getTradingThemes())
                    || "本周末无新增主线".equals(context.getTradingThemes().get(0).getTheme())
                    ? "周末证据未形成板块级新增主线"
                    : "AI 正文未通过事实校验，已切换为证据版正文");
        }
        report = report.toBuilder()
                .contentLevel(fullContent ? "FULL" : "DEGRADED")
                .qualityWarnings(qualityWarnings)
                .build();
        redisCacheService.put(CACHE_KEY, report, CACHE_TTL);
        log.info("周末研报生成完成，报告日期={}，最后交易日={}，来源={}，数据等级={}，新闻数={}，观点数={}",
                report.getReportDate(), lastTradeDate, reportSource, report.getDataLevel(), weekendNews.size(), marketOpinions.size());
        return report;
    }

    /**
     * 刷新资讯与观点后生成周末研报。
     *
     * @return 周末研报
     */
    @Override
    public WeekendMarketReportResp refresh() {
        return refresh(LocalDateTime.now(SHANGHAI_ZONE));
    }

    WeekendMarketReportResp refresh(LocalDateTime currentTime) {
        if (!isVisibleWindow(currentTime)) {
            log.info("周末研报刷新跳过：当前不在周日 21:00 至周一 09:30 可见窗口");
            return null;
        }
        List<String> refreshMissingData = new ArrayList<>();
        try {
            newsService.refresh("eastmoney,cls,ths,sina", 80);
        } catch (Exception ex) {
            refreshMissingData.add("周末资讯刷新失败");
            log.warn("周末研报资讯刷新失败，继续使用本地数据，原因={}", ex.getMessage());
        }
        try {
            marketOpinionService.refresh();
        } catch (Exception ex) {
            refreshMissingData.add("市场观点刷新失败");
            log.warn("周末研报市场观点刷新失败，继续使用本地快照，原因={}", ex.getMessage());
        }
        return generate(refreshMissingData);
    }

    private List<LocalDate> resolveWeekDates(LocalDate lastTradeDate) {
        LocalDate weekStart = lastTradeDate.minusDays(lastTradeDate.getDayOfWeek().getValue() - 1L);
        List<LocalDate> weekDates = new ArrayList<>();
        LocalDate currentDate = weekStart;
        while (!currentDate.isAfter(lastTradeDate)) {
            if (TradingCalendar.isTradingDay(currentDate)) {
                weekDates.add(currentDate);
            }
            currentDate = currentDate.plusDays(1);
        }
        return weekDates;
    }

    private List<WeekendIndexPerformanceResp> loadIndexPerformance(LocalDate weekStartDate,
                                                                     LocalDate lastTradeDate,
                                                                     List<String> missingData) {
        List<WeekendIndexPerformanceResp> performances = new ArrayList<>();
        LocalDate baselineDate = TradingCalendar.prevTradingDay(weekStartDate);
        for (String indexCode : INDEX_CODES) {
            List<IndexBar> bars = indexBarMapper.selectList(Wrappers.<IndexBar>lambdaQuery()
                    .eq(IndexBar::getCode, indexCode)
                    .le(IndexBar::getTradeDate, lastTradeDate)
                    .ge(IndexBar::getTradeDate, baselineDate)
                    .orderByAsc(IndexBar::getTradeDate)
                    .orderByAsc(IndexBar::getId));
            if (CollUtil.isEmpty(bars)) {
                missingData.add(indexCode + "指数周度数据");
                continue;
            }
            IndexBar baseline = null;
            IndexBar first = null;
            IndexBar last = null;
            for (IndexBar bar : bars) {
                if (Objects.isNull(bar) || Objects.isNull(bar.getTradeDate())
                        || !indexCode.equals(bar.getCode())
                        || bar.getTradeDate().isAfter(lastTradeDate)) {
                    continue;
                }
                if (bar.getTradeDate().isBefore(weekStartDate)) {
                    baseline = bar;
                    continue;
                }
                if (Objects.isNull(first)) {
                    first = bar;
                }
                last = bar;
            }
            if (Objects.isNull(first) || Objects.isNull(last) || Objects.isNull(first.getClosePrice())
                    || Objects.isNull(last.getClosePrice())) {
                missingData.add(indexCode + "指数周度收盘价");
                continue;
            }
            BigDecimal weeklyReturn = null;
            if (Objects.nonNull(baseline) && Objects.nonNull(baseline.getClosePrice())
                    && baseline.getClosePrice().compareTo(BigDecimal.ZERO) != 0) {
                weeklyReturn = last.getClosePrice().subtract(baseline.getClosePrice())
                        .divide(baseline.getClosePrice(), 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            } else {
                missingData.add(indexCode + "指数周初基准收盘价");
            }
            performances.add(WeekendIndexPerformanceResp.builder()
                    .code(indexCode)
                    .name(StringUtils.isNotBlank(last.getName()) ? last.getName() : indexCode)
                    .weekStartClose(Objects.nonNull(baseline) ? baseline.getClosePrice() : null)
                    .weekEndClose(last.getClosePrice())
                    .weeklyReturn(weeklyReturn)
                    .fridayPctChg(last.getPctChg())
                    .build());
        }
        return performances;
    }

    private WeekendFridaySnapshotResp loadFridaySnapshot(LocalDate lastTradeDate, List<String> missingData) {
        MarketBriefingResp briefing = marketBriefingService.loadCachedBriefing();
        if (Objects.isNull(briefing)) {
            missingData.add("周五市场简报");
            return null;
        }
        if (!lastTradeDate.equals(briefing.getAsOf())) {
            missingData.add("周五市场简报（数据截至 " + briefing.getAsOf() + "）");
            return null;
        }
        List<String> hotThemes = new ArrayList<>();
        if (CollUtil.isNotEmpty(briefing.getHotThemes())) {
            hotThemes.addAll(briefing.getHotThemes());
        } else if (CollUtil.isNotEmpty(briefing.getHotThemeItems())) {
            for (MarketHotThemeItem item : briefing.getHotThemeItems()) {
                if (Objects.nonNull(item) && StringUtils.isNotBlank(item.getName())) {
                    hotThemes.add(item.getName());
                }
            }
        }
        return WeekendFridaySnapshotResp.builder()
                .asOf(briefing.getAsOf())
                .stance(briefing.getStance())
                .indexVolume(briefing.getIndexVolume())
                .indexVolumeChange(briefing.getIndexVolumeChange())
                .volumeLabel(briefing.getVolumeLabel())
                .breadthUp(briefing.getBreadthUp())
                .breadthDown(briefing.getBreadthDown())
                .breadthFlat(briefing.getBreadthFlat())
                .limitUpCount(briefing.getLimitUpCount())
                .limitDownCount(briefing.getLimitDownCount())
                .hotThemes(hotThemes)
                .build();
    }

    private List<WeekendNewsItemResp> loadWeekendNews(LocalDate lastTradeDate, LocalDateTime generatedAt,
                                                       List<String> missingData) {
        LocalDateTime startTime = lastTradeDate.atTime(LocalTime.of(15, 0));
        List<MarketNews> rows = marketNewsMapper.selectList(Wrappers.<MarketNews>lambdaQuery()
                .in(MarketNews::getSource, NEWS_SOURCES)
                .ge(MarketNews::getPublishedAt, startTime)
                .le(MarketNews::getPublishedAt, generatedAt)
                .orderByDesc(MarketNews::getPublishedAt)
                .orderByDesc(MarketNews::getId)
                .last("LIMIT 120"));
        if (CollUtil.isEmpty(rows)) {
            missingData.add("周五收盘后至周日的四源资讯");
            return List.of();
        }
        List<WeekendNewsItemResp> newsItems = new ArrayList<>();
        Set<String> seenTitles = new HashSet<>();
        Set<String> seenExternalIds = new HashSet<>();
        for (MarketNews row : rows) {
            if (!isQualifiedNews(row)) {
                continue;
            }
            String titleKey = normalizeTitle(row.getTitle());
            String externalKey = StringUtils.isNotBlank(row.getSource()) && StringUtils.isNotBlank(row.getExternalId())
                    ? row.getSource().trim().toLowerCase() + ":" + row.getExternalId().trim() : "";
            boolean duplicateTitle = false;
            for (String seenTitle : seenTitles) {
                if (titleKey.equals(seenTitle) || (Math.min(titleKey.length(), seenTitle.length()) >= 12
                        && (titleKey.contains(seenTitle) || seenTitle.contains(titleKey)))) {
                    duplicateTitle = true;
                    break;
                }
            }
            if (duplicateTitle || (StringUtils.isNotBlank(externalKey) && !seenExternalIds.add(externalKey))) {
                continue;
            }
            seenTitles.add(titleKey);
            if (StringUtils.isBlank(row.getUrl())
                    && !missingData.contains("部分消息缺少原文链接，仅保留来源索引")) {
                missingData.add("部分消息缺少原文链接，仅保留来源索引");
            }
            newsItems.add(WeekendNewsItemResp.builder()
                    .id(row.getId())
                    .source(row.getSource())
                    .externalId(row.getExternalId())
                    .title(row.getTitle())
                    .summary(StringUtils.isNotBlank(row.getSummary()) ? row.getSummary() : row.getContent())
                    .publishedAt(row.getPublishedAt())
                    .sentiment(row.getSentiment())
                    .relatedCodes(splitCodes(row.getRelatedCodes()))
                    .url(row.getUrl())
                    .importance(resolveImportance(row))
                    .build());
        }
        if (newsItems.isEmpty()) {
            missingData.add("周五收盘后至周日的有效四源资讯");
        }
        newsItems.sort(Comparator.comparing(WeekendNewsItemResp::getImportance,
                Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(WeekendNewsItemResp::getPublishedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return newsItems.size() > MAX_NEWS_COUNT
                ? new ArrayList<>(newsItems.subList(0, MAX_NEWS_COUNT)) : newsItems;
    }

    private List<WeekendOpinionItemResp> loadMarketOpinions(LocalDateTime generatedAt, List<String> missingData) {
        LocalDateTime startTime = generatedAt.minusDays(5);
        List<MarketOpinion> rows = marketOpinionMapper.selectList(Wrappers.<MarketOpinion>lambdaQuery()
                .in(MarketOpinion::getOpinionType, List.of("INSTITUTION", "ACTIVE_SEAT", "KOL"))
                .ge(MarketOpinion::getPublishedAt, startTime)
                .le(MarketOpinion::getPublishedAt, generatedAt)
                .orderByDesc(MarketOpinion::getPublishedAt)
                .orderByDesc(MarketOpinion::getId)
                .last("LIMIT 60"));
        if (CollUtil.isEmpty(rows)) {
            missingData.add("近5日机构、活跃席位和已核验大V观点");
            return List.of();
        }
        List<WeekendOpinionItemResp> opinions = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (MarketOpinion row : rows) {
            if (!isQualifiedOpinion(row)) {
                continue;
            }
            String key = row.getOpinionType() + ":" + (StringUtils.isNotBlank(row.getExternalId())
                    ? row.getExternalId() : row.getSubjectName() + ":" + row.getTitle());
            if (!seen.add(key)) {
                continue;
            }
            opinions.add(WeekendOpinionItemResp.builder()
                    .opinionType(row.getOpinionType())
                    .subjectName(row.getSubjectName())
                    .source(row.getSource())
                    .title(row.getTitle())
                    .summary(row.getSummary())
                    .direction(row.getDirection())
                    .relatedCode(row.getRelatedCode())
                    .relatedName(row.getRelatedName())
                    .topic(row.getTopic())
                    .netAmount(row.getNetAmount())
                    .publishedAt(row.getPublishedAt())
                    .url(row.getUrl())
                    .build());
            if (opinions.size() >= 12) {
                break;
            }
        }
        if (opinions.isEmpty()) {
            missingData.add("近5日有效机构、活跃席位和已核验大V观点");
        }
        return opinions;
    }

    private List<WeekendTradingThemeResp> buildTradingThemes(List<WeekendNewsItemResp> news) {
        List<WeekendTradingThemeResp> themes = new ArrayList<>();
        List<String> candidateThemes = new ArrayList<>();
        for (WeekendNewsItemResp newsItem : news) {
            String theme = resolveNewsTheme(newsItem);
            if (StringUtils.isNotBlank(theme) && !candidateThemes.contains(theme)) {
                candidateThemes.add(theme);
            }
        }
        for (String candidateTheme : candidateThemes) {
            List<WeekendNewsItemResp> themeNews = new ArrayList<>();
            for (WeekendNewsItemResp newsItem : news) {
                if (candidateTheme.equals(resolveNewsTheme(newsItem))) {
                    themeNews.add(newsItem);
                }
            }
            boolean hasMajorSignal = false;
            for (WeekendNewsItemResp newsItem : themeNews) {
                if (Objects.nonNull(newsItem.getImportance()) && newsItem.getImportance() >= 3) {
                    hasMajorSignal = true;
                    break;
                }
            }
            if (themeNews.size() < 2 && !hasMajorSignal) {
                continue;
            }
            List<String> catalysts = new ArrayList<>();
            Set<String> relatedCodes = new LinkedHashSet<>();
            for (WeekendNewsItemResp newsItem : themeNews) {
                if (catalysts.size() < 2) {
                    catalysts.add(newsItem.getTitle());
                }
                if (CollUtil.isNotEmpty(newsItem.getRelatedCodes())) {
                    relatedCodes.addAll(newsItem.getRelatedCodes());
                }
            }
            themes.add(WeekendTradingThemeResp.builder()
                    .theme(candidateTheme)
                    .catalyst(String.join("；", catalysts))
                    .confirmation("周一前30分钟板块成交额进入全市场前3，上涨家数占比不低于60%，且核心标的不跌破开盘价")
                    .invalidation("周一前30分钟板块成交额跌出前10，上涨家数占比低于40%，或核心标的跌破周五收盘价")
                    .relatedCodes(String.join("、", relatedCodes))
                    .build());
            if (themes.size() >= 3) {
                break;
            }
        }
        if (themes.isEmpty()) {
            themes.add(WeekendTradingThemeResp.builder().theme("本周末无新增主线")
                    .catalyst("有效消息以个股事件为主，未形成可核验的板块级集中催化")
                    .confirmation("周一前30分钟出现成交额前3、上涨家数占比不低于60%的新方向")
                    .invalidation("任一方向满足量价和市场宽度确认条件后，本判断失效").build());
        }
        return themes;
    }

    private List<WeekendMarketScenarioResp> buildScenarios(WeekendFridaySnapshotResp snapshot) {
        String stance = Objects.nonNull(snapshot) ? snapshot.getStance() : null;
        String strongTrigger = "两市成交额较周五放大至少10%，上涨家数/下跌家数不低于1.5，且候选方向成交额进入前3";
        String weakTrigger = "两市成交额较周五缩减至少10%，下跌家数/上涨家数不低于1.5，或跌停家数达到10家";
        if (StringUtils.contains(stance, "防守")) {
            strongTrigger = "两市成交额较周五放大至少15%，上涨家数超过下跌家数1.5倍，并收复周五高点";
        }
        return List.of(
                WeekendMarketScenarioResp.builder().scenario("强势").trigger(strongTrigger).action("只跟随有成交和证据的主线，分批验证").build(),
                WeekendMarketScenarioResp.builder().scenario("震荡").trigger("成交额较周五变化在正负10%以内，上涨与下跌家数比处于0.67至1.5之间").action("控制仓位，等待主线确认后再提高暴露").build(),
                WeekendMarketScenarioResp.builder().scenario("转弱").trigger(weakTrigger).action("优先防守，暂停追涨并检查既有方向的失效条件").build());
    }

    private String resolveMarketStatus(WeekendFridaySnapshotResp snapshot, List<WeekendIndexPerformanceResp> indexes) {
        if (CollUtil.isEmpty(indexes)) {
            return Objects.nonNull(snapshot) && StringUtils.isNotBlank(snapshot.getStance())
                    ? snapshot.getStance() : "数据不足，暂不定性";
        }
        int positive = 0;
        WeekendIndexPerformanceResp strongestIndex = null;
        BigDecimal lowestReturn = null;
        for (WeekendIndexPerformanceResp item : indexes) {
            if (Objects.nonNull(item.getWeeklyReturn()) && item.getWeeklyReturn().compareTo(BigDecimal.ZERO) > 0) {
                positive++;
            }
            if (Objects.nonNull(item.getWeeklyReturn()) && (Objects.isNull(strongestIndex)
                    || item.getWeeklyReturn().compareTo(strongestIndex.getWeeklyReturn()) > 0)) {
                strongestIndex = item;
            }
            if (Objects.nonNull(item.getWeeklyReturn())
                    && (Objects.isNull(lowestReturn) || item.getWeeklyReturn().compareTo(lowestReturn) < 0)) {
                lowestReturn = item.getWeeklyReturn();
            }
        }
        if (Objects.nonNull(strongestIndex) && Objects.nonNull(lowestReturn)
                && positive * 5 >= indexes.size() * 4
                && strongestIndex.getWeeklyReturn().compareTo(BigDecimal.valueOf(3)) >= 0
                && strongestIndex.getWeeklyReturn().subtract(lowestReturn).compareTo(BigDecimal.valueOf(2)) >= 0) {
            return "结构性偏强，" + strongestIndex.getName() + "领涨";
        }
        return positive * 2 >= indexes.size() ? "震荡偏强" : "震荡偏弱";
    }

    private String buildCoreView(String marketStatus, List<WeekendNewsItemResp> news,
                                 List<WeekendTradingThemeResp> tradingThemes) {
        if (CollUtil.isEmpty(tradingThemes) || "本周末无新增主线".equals(tradingThemes.get(0).getTheme())) {
            return "上周市场" + marketStatus + "；周末筛得 " + news.size()
                    + " 条有效消息，但未形成板块级新增主线。周一不预设方向，只观察量价和市场宽度确认。";
        }
        List<String> themeNames = new ArrayList<>();
        for (WeekendTradingThemeResp tradingTheme : tradingThemes) {
            themeNames.add(tradingTheme.getTheme());
        }
        return "上周市场" + marketStatus + "；周末筛得 " + news.size() + " 条有效消息，优先观察"
                + String.join("、", themeNames) + "。仅在量价和市场宽度同时确认后跟随。";
    }

    private String buildMaxRisk(WeekendFridaySnapshotResp snapshot, List<String> missingData) {
        boolean criticalDataMissing = false;
        for (String missingItem : missingData) {
            if (StringUtils.contains(missingItem, "指数") || StringUtils.contains(missingItem, "周五市场简报")
                    || StringUtils.contains(missingItem, "刷新失败")) {
                criticalDataMissing = true;
                break;
            }
        }
        if (criticalDataMissing) {
            return "关键数据缺口可能放大判断误差，尤其是周五市场宽度或消息源未完整同步。";
        }
        if (Objects.nonNull(snapshot) && StringUtils.contains(snapshot.getVolumeLabel(), "缩量")) {
            return "周五缩量后的热点持续性不足，周一若无量能承接容易高开低走。";
        }
        return "周末消息的真实传导强度仍需周一量价和市场宽度确认。";
    }

    private LocalDateTime resolveDataAsOf(WeekendFridaySnapshotResp snapshot, List<WeekendNewsItemResp> news,
                                           List<WeekendOpinionItemResp> opinions, LocalDateTime generatedAt) {
        LocalDateTime latest = Objects.nonNull(snapshot) && Objects.nonNull(snapshot.getAsOf())
                ? snapshot.getAsOf().atTime(LocalTime.MAX) : null;
        for (WeekendNewsItemResp item : news) {
            if (Objects.nonNull(item.getPublishedAt()) && (Objects.isNull(latest) || item.getPublishedAt().isAfter(latest))) {
                latest = item.getPublishedAt();
            }
        }
        for (WeekendOpinionItemResp item : opinions) {
            if (Objects.nonNull(item.getPublishedAt()) && (Objects.isNull(latest) || item.getPublishedAt().isAfter(latest))) {
                latest = item.getPublishedAt();
            }
        }
        return Objects.nonNull(latest) ? latest : generatedAt;
    }

    private String resolveDataLevel(List<WeekendIndexPerformanceResp> indexes, WeekendFridaySnapshotResp snapshot,
                                    List<WeekendNewsItemResp> news, List<WeekendOpinionItemResp> opinions,
                                    List<String> missingData) {
        if (CollUtil.isEmpty(indexes) || Objects.isNull(snapshot)) {
            return "RED";
        }
        if (CollUtil.isNotEmpty(missingData) || CollUtil.isEmpty(news) || CollUtil.isEmpty(opinions)) {
            return "YELLOW";
        }
        return "GREEN";
    }

    private String buildRuleReport(WeekendMarketReportResp report) {
        StringBuilder content = new StringBuilder();
        content.append("周末消息面专题｜").append(report.getWeekStartDate()).append("至").append(report.getWeekEndDate()).append('\n');
        content.append("数据截至：").append(report.getDataAsOf()).append("｜数据等级：").append(report.getDataLevel()).append('\n');
        content.append("核心观点：").append(report.getCoreView()).append('\n');
        content.append("最大风险：").append(report.getMaxRisk()).append('\n');
        content.append("01｜上周走势\n");
        for (WeekendIndexPerformanceResp item : report.getIndexPerformance()) {
            content.append(item.getName()).append("：周度 ").append(formatPercent(item.getWeeklyReturn()))
                    .append("，周五 ").append(formatPercent(item.getFridayPctChg())).append('\n');
        }
        content.append("02｜周五收盘\n").append(formatFridaySnapshot(report.getFridaySnapshot())).append('\n');
        content.append("03｜周末消息\n");
        if (CollUtil.isEmpty(report.getWeekendNews())) {
            content.append("暂无满足时间窗口且可核验的四源资讯。\n");
        } else {
            for (WeekendNewsItemResp item : report.getWeekendNews()) {
                content.append("- ").append(item.getTitle()).append("｜").append(item.getSource())
                        .append("｜").append(item.getPublishedAt()).append("｜")
                        .append(formatNewsReference(item)).append('\n');
            }
        }
        content.append("04｜机构与大 V 观点\n");
        if (CollUtil.isEmpty(report.getMarketOpinions())) {
            content.append("暂无近5日可核验观点。\n");
        } else {
            for (WeekendOpinionItemResp item : report.getMarketOpinions()) {
                content.append("- ").append(item.getSubjectName()).append("｜").append(formatOpinionTarget(item))
                        .append("｜").append(item.getDirection()).append("｜").append(item.getTitle())
                        .append("｜").append(item.getPublishedAt()).append("｜").append(item.getUrl()).append('\n');
            }
        }
        content.append("05｜下周交易主线\n");
        for (WeekendTradingThemeResp item : report.getTradingThemes()) {
            content.append("- ").append(item.getTheme()).append("｜催化：").append(item.getCatalyst())
                    .append("｜确认：").append(item.getConfirmation()).append("｜失效：").append(item.getInvalidation()).append('\n');
        }
        content.append("06｜市场剧本与风险\n");
        for (WeekendMarketScenarioResp item : report.getScenarios()) {
            content.append(item.getScenario()).append("｜触发：").append(item.getTrigger()).append("｜应对：").append(item.getAction()).append('\n');
        }
        content.append("仅供研究，不构成投资建议。");
        return content.toString();
    }

    private boolean isCompleteReport(String content, WeekendMarketReportResp context) {
        if (StringUtils.isBlank(content) || content.length() > 6000 || content.contains("未提供事实")
                || content.contains("无法核验") || content.contains("编造")
                || content.contains("建议关注") || content.contains("有望受益")
                || content.contains("保持强势")) {
            return false;
        }
        for (String section : List.of("01｜上周走势", "02｜周五收盘", "03｜周末消息", "04｜机构与大 V 观点",
                "05｜下周交易主线", "06｜市场剧本与风险", "核心观点：", "最大风险：")) {
            if (!content.contains(section)) {
                return false;
            }
        }
        if (CollUtil.isNotEmpty(context.getWeekendNews())) {
            boolean matched = false;
            for (WeekendNewsItemResp item : context.getWeekendNews()) {
                if (StringUtils.isNotBlank(item.getTitle()) && content.contains(item.getTitle())) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        if (CollUtil.isNotEmpty(context.getMarketOpinions())) {
            boolean matched = false;
            for (WeekendOpinionItemResp item : context.getMarketOpinions()) {
                if (StringUtils.isNotBlank(item.getSubjectName()) && content.contains(item.getSubjectName())) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        if (!hasOnlyAllowedNews(content, context.getWeekendNews())
                || !hasOnlyAllowedOpinions(content, context.getMarketOpinions())) {
            return false;
        }
        for (WeekendTradingThemeResp tradingTheme : context.getTradingThemes()) {
            if (StringUtils.isBlank(tradingTheme.getTheme()) || !content.contains(tradingTheme.getTheme())) {
                return false;
            }
        }
        Set<String> catalysts = new HashSet<>();
        for (WeekendTradingThemeResp tradingTheme : context.getTradingThemes()) {
            if (StringUtils.isNotBlank(tradingTheme.getCatalyst()) && !catalysts.add(tradingTheme.getCatalyst())) {
                return false;
            }
        }
        return hasOnlyAllowedThemes(content, context.getTradingThemes());
    }

    private boolean hasOnlyAllowedThemes(String content, List<WeekendTradingThemeResp> tradingThemes) {
        List<String> themeLines = extractBulletLines(content, "05｜下周交易主线", "06｜市场剧本与风险");
        if (themeLines.size() != tradingThemes.size()) {
            return false;
        }
        Set<String> matchedThemes = new HashSet<>();
        for (String themeLine : themeLines) {
            boolean matched = false;
            for (WeekendTradingThemeResp tradingTheme : tradingThemes) {
                String expectedLine = "- " + tradingTheme.getTheme() + "｜催化：" + tradingTheme.getCatalyst()
                        + "｜确认：" + tradingTheme.getConfirmation() + "｜失效：" + tradingTheme.getInvalidation();
                if (expectedLine.equals(themeLine)) {
                    matchedThemes.add(tradingTheme.getTheme());
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return matchedThemes.size() == tradingThemes.size();
    }

    private boolean hasOnlyAllowedNews(String content, List<WeekendNewsItemResp> news) {
        List<String> newsLines = extractBulletLines(content, "03｜周末消息", "04｜机构与大 V 观点");
        if (newsLines.size() != news.size()) {
            return false;
        }
        Set<String> matchedTitles = new HashSet<>();
        for (String newsLine : newsLines) {
            boolean matched = false;
            for (WeekendNewsItemResp newsItem : news) {
                String expectedLine = "- " + newsItem.getTitle() + "｜" + newsItem.getSource() + "｜"
                        + newsItem.getPublishedAt() + "｜" + formatNewsReference(newsItem);
                if (expectedLine.equals(newsLine)) {
                    matchedTitles.add(newsItem.getTitle());
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return matchedTitles.size() == news.size();
    }

    private boolean hasOnlyAllowedOpinions(String content, List<WeekendOpinionItemResp> opinions) {
        List<String> opinionLines = extractBulletLines(content, "04｜机构与大 V 观点", "05｜下周交易主线");
        if (opinionLines.size() != opinions.size()) {
            return false;
        }
        Set<String> matchedOpinions = new HashSet<>();
        for (String opinionLine : opinionLines) {
            boolean matched = false;
            for (WeekendOpinionItemResp opinion : opinions) {
                String opinionKey = opinion.getSubjectName() + "|" + opinion.getTitle();
                String expectedLine = "- " + opinion.getSubjectName() + "｜" + formatOpinionTarget(opinion)
                        + "｜" + opinion.getDirection() + "｜" + opinion.getTitle() + "｜"
                        + opinion.getPublishedAt() + "｜" + opinion.getUrl();
                if (expectedLine.equals(opinionLine)) {
                    matchedOpinions.add(opinionKey);
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return matchedOpinions.size() == opinions.size();
    }

    private List<String> extractBulletLines(String content, String sectionStart, String sectionEnd) {
        int startIndex = content.indexOf(sectionStart);
        int endIndex = content.indexOf(sectionEnd, startIndex);
        if (startIndex < 0 || endIndex <= startIndex) {
            return List.of();
        }
        List<String> bulletLines = new ArrayList<>();
        for (String line : content.substring(startIndex, endIndex).split("\\R")) {
            String normalizedLine = line.trim();
            if (normalizedLine.startsWith("•")) {
                normalizedLine = "- " + normalizedLine.substring(1).trim();
            }
            if (normalizedLine.startsWith("-")) {
                bulletLines.add(normalizedLine.startsWith("- ")
                        ? normalizedLine : "- " + normalizedLine.substring(1).trim());
            }
        }
        return bulletLines;
    }

    private String formatFridaySnapshot(WeekendFridaySnapshotResp snapshot) {
        if (Objects.isNull(snapshot)) {
            return "周五收盘市场简报缺失";
        }
        return "量能=" + valueOrMissing(snapshot.getVolumeLabel()) + "，上涨=" + valueOrMissing(snapshot.getBreadthUp())
                + "，下跌=" + valueOrMissing(snapshot.getBreadthDown()) + "，涨停=" + valueOrMissing(snapshot.getLimitUpCount())
                + "，跌停=" + valueOrMissing(snapshot.getLimitDownCount()) + "，热点="
                + (CollUtil.isEmpty(snapshot.getHotThemes()) ? "数据暂缺" : String.join("、", snapshot.getHotThemes()));
    }

    private Integer resolveImportance(MarketNews row) {
        String content = buildNewsEvidence(row);
        boolean marketPolicy = containsAny(content, POLICY_AUTHORITIES) && containsAny(content, POLICY_ACTION_KEYWORDS);
        boolean marketWideMerger = StringUtils.contains(content, "A股")
                && containsAny(content, List.of("收购", "并购", "重组"));
        return marketPolicy || marketWideMerger ? 3 : 2;
    }

    private String normalizeTitle(String title) {
        return title.replaceFirst("^(财联社|新浪财经|新浪)(\\d{1,2}月\\d{1,2}日)?电[，,:：]?", "")
                .replaceAll("\\s+", "").replaceAll("[【】\\[\\]（）()：:，,。.!！?？]", "").trim();
    }

    private boolean isQualifiedNews(MarketNews row) {
        if (Objects.isNull(row) || StringUtils.isBlank(row.getTitle()) || StringUtils.isBlank(row.getSource())
                || Objects.isNull(row.getPublishedAt()) || !hasTraceableNewsReference(row)) {
            return false;
        }
        String title = row.getTitle().trim();
        String content = buildNewsEvidence(row);
        if (containsAny(content, IRRELEVANT_NEWS_KEYWORDS)
                || title.matches(".*(?:涨|跌)(?:超|逾)?0(?:\\.0+)?%.*")
                || title.matches(".*(?:同比|环比)(?:增长|下降)?\\d+(?:\\.\\d+)?$")
                || title.endsWith("（") || title.endsWith("(") || title.endsWith("：") || title.endsWith(":")) {
            return false;
        }
        boolean hasRelatedCode = StringUtils.isNotBlank(row.getRelatedCodes());
        boolean companyEvent = containsAny(title, COMPANY_EVENT_KEYWORDS)
                && (title.contains("：") || title.contains(":") || StringUtils.contains(content, "A股") || hasRelatedCode);
        boolean industryResearch = containsAny(content, INDUSTRY_KEYWORDS)
                && containsAny(content, List.of("研报", "研究", "机构", "策略"));
        boolean namedIndustrySignal = containsAny(content, INDUSTRY_KEYWORDS)
                && (title.contains("：") || title.contains(":")) && StringUtils.contains(title, "公司");
        boolean marketPolicy = containsAny(content, POLICY_AUTHORITIES) && containsAny(content, POLICY_ACTION_KEYWORDS);
        boolean commoditySignal = containsAny(content, List.of("原油", "黄金", "铜", "铝", "天然气"))
                && containsAny(content, List.of("突破", "涨幅", "跌幅", "上涨", "下跌", "收涨", "收跌"));
        return hasRelatedCode || companyEvent || industryResearch || namedIndustrySignal || marketPolicy || commoditySignal;
    }

    private boolean hasTraceableNewsReference(MarketNews row) {
        if (StringUtils.isNotBlank(row.getUrl()) && row.getUrl().trim().matches("https?://.+")) {
            return true;
        }
        return StringUtils.isNotBlank(row.getExternalId())
                && ("cls".equalsIgnoreCase(row.getSource()) || "sina".equalsIgnoreCase(row.getSource()));
    }

    private boolean isQualifiedOpinion(MarketOpinion row) {
        if (Objects.isNull(row) || StringUtils.isBlank(row.getOpinionType())
                || StringUtils.isBlank(row.getSubjectName()) || StringUtils.isBlank(row.getTitle())
                || StringUtils.isBlank(row.getDirection()) || StringUtils.isBlank(row.getUrl())
                || Objects.isNull(row.getPublishedAt())) {
            return false;
        }
        boolean hasTarget = StringUtils.isNotBlank(row.getRelatedName())
                || StringUtils.isNotBlank(row.getRelatedCode()) || StringUtils.isNotBlank(row.getTopic());
        return hasTarget && (StringUtils.isNotBlank(row.getSummary()) || row.getTitle().trim().length() >= 8);
    }

    private String buildNewsEvidence(MarketNews row) {
        return String.join(" ", row.getTitle(), StringUtils.isNotBlank(row.getSummary()) ? row.getSummary() : "",
                StringUtils.isNotBlank(row.getContent()) ? row.getContent() : "");
    }

    private boolean containsAny(String content, List<String> keywords) {
        if (StringUtils.isBlank(content)) {
            return false;
        }
        for (String keyword : keywords) {
            if (StringUtils.contains(content, keyword)) {
                return true;
            }
        }
        return false;
    }

    private String resolveNewsTheme(WeekendNewsItemResp newsItem) {
        String content = String.join(" ", StringUtils.isNotBlank(newsItem.getTitle()) ? newsItem.getTitle() : "",
                StringUtils.isNotBlank(newsItem.getSummary()) ? newsItem.getSummary() : "");
        if (containsAny(content, List.of("半导体", "芯片", "DRAM", "NAND", "GPU", "SoC", "先进封装", "测试设备", "长鑫存储", "电子气体"))) {
            return "半导体";
        }
        if (containsAny(content, List.of("商业航天", "卫星", "宇航"))) {
            return "商业航天";
        }
        if (containsAny(content, List.of("并购重组", "收购", "并购"))) {
            return "并购重组";
        }
        if (containsAny(content, List.of("原油", "天然气", "油气"))) {
            return "油气";
        }
        if (containsAny(content, List.of("创新药", "医药", "临床"))) {
            return "医药";
        }
        if (containsAny(content, List.of("农业", "粮食", "转基因", "种业", "种植"))) {
            return "农业";
        }
        if (containsAny(content, List.of("新能源汽车", "新能源车"))) {
            return "新能源汽车";
        }
        Matcher emergingThemeMatcher = Pattern.compile(
                "(?:发布|推动|支持|加快发展|布局)([\\p{IsHan}A-Za-z0-9]{2,12})(?:产业|行业)(?:支持|发展|监管|政策|规划|指导)")
                .matcher(content);
        if (emergingThemeMatcher.find()) {
            return emergingThemeMatcher.group(1);
        }
        return null;
    }

    private String formatNewsReference(WeekendNewsItemResp newsItem) {
        if (StringUtils.isNotBlank(newsItem.getUrl())) {
            return newsItem.getUrl();
        }
        return "来源索引 " + newsItem.getSource() + "#" + newsItem.getExternalId();
    }

    private String formatOpinionTarget(WeekendOpinionItemResp opinion) {
        if (StringUtils.isNotBlank(opinion.getRelatedName())) {
            return opinion.getRelatedName();
        }
        return StringUtils.isNotBlank(opinion.getTopic()) ? opinion.getTopic() : opinion.getRelatedCode();
    }

    private List<String> splitCodes(String codes) {
        if (StringUtils.isBlank(codes)) {
            return List.of();
        }
        List<String> relatedCodes = new ArrayList<>();
        for (String code : codes.split("[,，;；\\s]+")) {
            String relatedCode = code.trim();
            if (StringUtils.isNotBlank(relatedCode) && !relatedCodes.contains(relatedCode)) {
                relatedCodes.add(relatedCode);
            }
        }
        return relatedCodes;
    }

    private String formatPercent(BigDecimal value) {
        return Objects.isNull(value) ? "数据暂缺" : value.setScale(2, RoundingMode.HALF_UP) + "%";
    }

    private String valueOrMissing(Object value) {
        return Objects.isNull(value) ? "数据暂缺" : String.valueOf(value);
    }

    private LocalDate resolveLastCompletedTradeDate(LocalDate reportDate) {
        if (reportDate.getDayOfWeek().getValue() >= 6) {
            return TradingCalendar.latestTradingDayOnOrBefore(reportDate);
        }
        LocalDate currentWeekStart = reportDate;
        while (currentWeekStart.getDayOfWeek().getValue() != 1) {
            currentWeekStart = currentWeekStart.minusDays(1);
        }
        return TradingCalendar.latestTradingDayOnOrBefore(currentWeekStart.minusDays(1));
    }

    private LocalDateTime resolveReportCutoff(LocalDate lastTradeDate, LocalDateTime generatedAt) {
        LocalDate sunday = lastTradeDate;
        while (sunday.getDayOfWeek().getValue() != 7) {
            sunday = sunday.plusDays(1);
        }
        LocalDateTime sundayCutoff = sunday.atTime(21, 0);
        return generatedAt.isBefore(sundayCutoff) ? generatedAt : sundayCutoff;
    }

    static boolean isVisibleWindow(LocalDateTime currentTime) {
        if (Objects.isNull(currentTime)) {
            return false;
        }
        DayOfWeek dayOfWeek = currentTime.getDayOfWeek();
        LocalTime time = currentTime.toLocalTime();
        return (dayOfWeek == DayOfWeek.SUNDAY && !time.isBefore(WEEKEND_REPORT_VISIBLE_TIME))
                || (dayOfWeek == DayOfWeek.MONDAY && time.isBefore(MARKET_OPEN_TIME));
    }

    private String loadSystemPrompt() {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/weekend-market-report.txt");
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            log.warn("周末研报提示词读取失败，原因={}", ex.getMessage());
            return "只根据输入 JSON 的事实和证据生成周末研报，必须包含六个章节，不得编造或引用 JSON 外的消息。";
        }
    }

    private String normalizeModelContent(String modelContent) {
        if (StringUtils.isBlank(modelContent)) {
            return null;
        }
        String content = modelContent.trim();
        if (content.startsWith("```")) {
            int firstLineEnd = content.indexOf('\n');
            int lastFence = content.lastIndexOf("```");
            if (firstLineEnd >= 0 && lastFence > firstLineEnd) {
                content = content.substring(firstLineEnd + 1, lastFence).trim();
            }
        }
        return content;
    }

}
