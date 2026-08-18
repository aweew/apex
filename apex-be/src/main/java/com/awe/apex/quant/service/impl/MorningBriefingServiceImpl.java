package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.bot.config.ApexBotProperties;
import com.awe.apex.quant.cache.RedisCacheService;
import com.awe.apex.quant.domain.dto.MorningBriefingResp;
import com.awe.apex.quant.domain.dto.NewsPulseCardResp;
import com.awe.apex.quant.domain.dto.NewsPulseResp;
import com.awe.apex.quant.domain.dto.OvernightMarketQuote;
import com.awe.apex.quant.domain.dto.OvernightMarketTheme;
import com.awe.apex.quant.market.MarketBriefingMath;
import com.awe.apex.quant.market.TradingCalendar;
import com.awe.apex.quant.market.UsMarketQuoteClient;
import com.awe.apex.quant.service.IMorningBriefingService;
import com.awe.apex.quant.service.INewsPulseService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 盘前晨报服务实现。
 */
@Slf4j
@Service
public class MorningBriefingServiceImpl implements IMorningBriefingService {

    private static final String BRIEFING_CACHE_KEY = "apex:morning-briefing:latest";
    private static final Duration BRIEFING_CACHE_TTL = Duration.ofHours(30);

    private final Object cacheLock = new Object();
    private MorningBriefingResp cachedBriefing;
    private long cachedAtMs;

    @Resource
    private ApexBotProperties properties;

    @Resource
    private UsMarketQuoteClient usMarketQuoteClient;

    @Resource
    private INewsPulseService newsPulseService;

    @Resource
    private RedisCacheService redisCacheService;

    /**
     * 汇总隔夜美股和夜间新闻。
     *
     * @return 盘前晨报
     */
    @Override
    public MorningBriefingResp generate() {
        LocalDateTime generatedAt = LocalDateTime.now();
        LocalDate generatedDate = generatedAt.toLocalDate();
        LocalDate tradeDate = TradingCalendar.isTradingDay(generatedDate)
                ? generatedDate : TradingCalendar.nextTradingDay(generatedDate);

        // 1. 合并旧清单、展示分组和主题清单，确保新增配置真正进入一次性行情请求
        String[] configuredSymbolGroups = {
                properties.getMorningBriefing().getSymbols(),
                properties.getMorningBriefing().getIndexSymbols(),
                properties.getMorningBriefing().getStarSymbols(),
                properties.getMorningBriefing().getTechnologyGiantsSymbols(),
                properties.getMorningBriefing().getAiChipSymbols(),
                properties.getMorningBriefing().getStorageSymbols(),
                properties.getMorningBriefing().getWaferManufacturingSymbols(),
                properties.getMorningBriefing().getSemiconductorEquipmentSymbols(),
                properties.getMorningBriefing().getEdaIpSymbols(),
                properties.getMorningBriefing().getAnalogAutomotiveChipSymbols(),
                properties.getMorningBriefing().getChinaConceptSymbols()
        };
        List<String> symbols = new ArrayList<>();
        for (String configuredSymbolGroup : configuredSymbolGroups) {
            for (String symbol : parseSymbols(configuredSymbolGroup)) {
                if (!symbols.contains(symbol)) {
                    symbols.add(symbol);
                }
            }
        }
        List<OvernightMarketQuote> marketQuotes = usMarketQuoteClient.fetch(symbols);
        if (Objects.isNull(marketQuotes)) {
            marketQuotes = List.of();
        }
        List<OvernightMarketQuote> indexQuotes = selectQuotes(marketQuotes,
                parseSymbols(properties.getMorningBriefing().getIndexSymbols()));
        List<OvernightMarketTheme> marketThemes = buildMarketThemes(marketQuotes);
        List<OvernightMarketQuote> starQuotes = buildStarQuotes(marketQuotes);

        // 2. 汇总夜间新闻标题和完整摘要快照
        NewsPulseResp newsPulse = loadNewsPulse();
        List<String> newsTitles = new ArrayList<>();
        if (Objects.nonNull(newsPulse) && CollUtil.isNotEmpty(newsPulse.getCards())) {
            for (NewsPulseCardResp card : newsPulse.getCards()) {
                if (StringUtils.isNotBlank(card.getTitle())) {
                    newsTitles.add(card.getTitle().trim());
                }
                if (newsTitles.size() >= 3) {
                    break;
                }
            }
        }

        // 3. 输出市场温度、主题强弱和明星异动三层结果
        String summary = buildSummary(indexQuotes, marketThemes, starQuotes, newsPulse);
        List<String> validQuoteSymbols = new ArrayList<>();
        for (OvernightMarketQuote marketQuote : marketQuotes) {
            if (StringUtils.isNotBlank(marketQuote.getSymbol())
                    && symbols.contains(marketQuote.getSymbol())
                    && !validQuoteSymbols.contains(marketQuote.getSymbol())) {
                validQuoteSymbols.add(marketQuote.getSymbol());
            }
        }
        boolean quoteDataIncomplete = CollUtil.isEmpty(symbols) || validQuoteSymbols.size() < symbols.size();
        MorningBriefingResp briefing = MorningBriefingResp.builder()
                .tradeDate(tradeDate)
                .generatedAt(generatedAt)
                .marketQuotes(marketQuotes)
                .indexQuotes(indexQuotes)
                .starQuotes(starQuotes)
                .marketThemes(marketThemes)
                .newsTitles(newsTitles)
                .newsPulse(newsPulse)
                .summary(summary)
                .dataLevel(quoteDataIncomplete ? "YELLOW" : "GREEN")
                .build();
        synchronized (cacheLock) {
            cachedBriefing = briefing;
            cachedAtMs = System.currentTimeMillis();
        }
        redisCacheService.put(BRIEFING_CACHE_KEY, briefing, BRIEFING_CACHE_TTL);
        return briefing;
    }

    /**
     * 读取最近一次盘前晨报，缓存未命中时生成。
     *
     * @return 最近一次盘前晨报
     */
    @Override
    public MorningBriefingResp latest() {
        long now = System.currentTimeMillis();
        synchronized (cacheLock) {
            if (Objects.nonNull(cachedBriefing)
                    && now - cachedAtMs < BRIEFING_CACHE_TTL.toMillis()) {
                return cachedBriefing;
            }
        }
        MorningBriefingResp sharedCached = redisCacheService.get(BRIEFING_CACHE_KEY, MorningBriefingResp.class);
        if (Objects.nonNull(sharedCached)) {
            synchronized (cacheLock) {
                cachedBriefing = sharedCached;
                cachedAtMs = System.currentTimeMillis();
            }
            return sharedCached;
        }
        synchronized (cacheLock) {
            if (Objects.nonNull(cachedBriefing)
                    && System.currentTimeMillis() - cachedAtMs < BRIEFING_CACHE_TTL.toMillis()) {
                return cachedBriefing;
            }
            return generate();
        }
    }

    /**
     * 清除盘前晨报缓存。
     */
    @Override
    public void invalidateCache() {
        synchronized (cacheLock) {
            cachedBriefing = null;
            cachedAtMs = 0L;
        }
        redisCacheService.evict(BRIEFING_CACHE_KEY);
    }

    private List<String> parseSymbols(String configuredSymbols) {
        List<String> symbols = new ArrayList<>();
        if (StringUtils.isBlank(configuredSymbols)) {
            return symbols;
        }
        for (String symbol : configuredSymbols.split(",")) {
            if (StringUtils.isNotBlank(symbol)) {
                symbols.add(symbol.trim());
            }
        }
        return symbols;
    }

    private List<OvernightMarketQuote> selectQuotes(List<OvernightMarketQuote> marketQuotes,
                                                     List<String> selectedSymbols) {
        List<OvernightMarketQuote> selectedQuotes = new ArrayList<>();
        if (CollUtil.isEmpty(marketQuotes) || CollUtil.isEmpty(selectedSymbols)) {
            return selectedQuotes;
        }
        for (String selectedSymbol : selectedSymbols) {
            for (OvernightMarketQuote marketQuote : marketQuotes) {
                if (selectedSymbol.equals(marketQuote.getSymbol())) {
                    selectedQuotes.add(marketQuote);
                    break;
                }
            }
        }
        return selectedQuotes;
    }

    private List<OvernightMarketTheme> buildMarketThemes(List<OvernightMarketQuote> marketQuotes) {
        List<OvernightMarketTheme> marketThemes = new ArrayList<>();
        marketThemes.add(buildTheme("TECHNOLOGY_GIANTS", "科技巨头",
                properties.getMorningBriefing().getTechnologyGiantsSymbols(), marketQuotes));
        marketThemes.add(buildTheme("AI_CHIP", "AI芯片",
                properties.getMorningBriefing().getAiChipSymbols(), marketQuotes));
        marketThemes.add(buildTheme("STORAGE", "存储",
                properties.getMorningBriefing().getStorageSymbols(), marketQuotes));
        marketThemes.add(buildTheme("WAFER_MANUFACTURING", "晶圆制造",
                properties.getMorningBriefing().getWaferManufacturingSymbols(), marketQuotes));
        marketThemes.add(buildTheme("SEMICONDUCTOR_EQUIPMENT", "半导体设备",
                properties.getMorningBriefing().getSemiconductorEquipmentSymbols(), marketQuotes));
        marketThemes.add(buildTheme("EDA_IP", "EDA与IP",
                properties.getMorningBriefing().getEdaIpSymbols(), marketQuotes));
        marketThemes.add(buildTheme("ANALOG_AUTOMOTIVE_CHIP", "模拟与汽车芯片",
                properties.getMorningBriefing().getAnalogAutomotiveChipSymbols(), marketQuotes));
        marketThemes.add(buildTheme("CHINA_CONCEPT", "中概风向",
                properties.getMorningBriefing().getChinaConceptSymbols(), marketQuotes));
        marketThemes.sort((left, right) -> {
            if (Objects.isNull(left.getMedianPctChg()) && Objects.isNull(right.getMedianPctChg())) {
                return 0;
            }
            if (Objects.isNull(left.getMedianPctChg())) {
                return 1;
            }
            if (Objects.isNull(right.getMedianPctChg())) {
                return -1;
            }
            return right.getMedianPctChg().compareTo(left.getMedianPctChg());
        });
        return marketThemes;
    }

    private OvernightMarketTheme buildTheme(String code, String name, String configuredSymbols,
                                             List<OvernightMarketQuote> marketQuotes) {
        List<OvernightMarketQuote> themeQuotes = selectQuotes(marketQuotes, parseSymbols(configuredSymbols));
        List<BigDecimal> pctChanges = new ArrayList<>();
        int upCount = 0;
        int downCount = 0;
        int flatCount = 0;
        OvernightMarketQuote leaderQuote = null;
        OvernightMarketQuote laggardQuote = null;
        for (OvernightMarketQuote themeQuote : themeQuotes) {
            if (Objects.isNull(themeQuote.getPctChg())) {
                continue;
            }
            pctChanges.add(themeQuote.getPctChg());
            if (themeQuote.getPctChg().signum() > 0) {
                upCount++;
            } else if (themeQuote.getPctChg().signum() < 0) {
                downCount++;
            } else {
                flatCount++;
            }
            if (Objects.isNull(leaderQuote)
                    || themeQuote.getPctChg().compareTo(leaderQuote.getPctChg()) > 0) {
                leaderQuote = themeQuote;
            }
            if (Objects.isNull(laggardQuote)
                    || themeQuote.getPctChg().compareTo(laggardQuote.getPctChg()) < 0) {
                laggardQuote = themeQuote;
            }
        }
        return OvernightMarketTheme.builder()
                .code(code)
                .name(name)
                .quoteCount(pctChanges.size())
                .upCount(upCount)
                .downCount(downCount)
                .flatCount(flatCount)
                .medianPctChg(MarketBriefingMath.median(pctChanges, 2))
                .leaderQuote(leaderQuote)
                .laggardQuote(laggardQuote)
                .build();
    }

    private List<OvernightMarketQuote> buildStarQuotes(List<OvernightMarketQuote> marketQuotes) {
        List<OvernightMarketQuote> configuredStarQuotes = selectQuotes(marketQuotes,
                parseSymbols(properties.getMorningBriefing().getStarSymbols()));
        List<OvernightMarketQuote> sortedStarQuotes = new ArrayList<>();
        for (OvernightMarketQuote configuredStarQuote : configuredStarQuotes) {
            if (Objects.nonNull(configuredStarQuote.getPctChg())) {
                sortedStarQuotes.add(configuredStarQuote);
            }
        }
        sortedStarQuotes.sort((left, right) -> right.getPctChg().abs().compareTo(left.getPctChg().abs()));
        int starQuoteLimit = Math.max(properties.getMorningBriefing().getStarQuoteLimit(), 0);
        if (sortedStarQuotes.size() <= starQuoteLimit) {
            return sortedStarQuotes;
        }
        return new ArrayList<>(sortedStarQuotes.subList(0, starQuoteLimit));
    }

    private String buildSummary(List<OvernightMarketQuote> indexQuotes,
                                List<OvernightMarketTheme> marketThemes,
                                List<OvernightMarketQuote> starQuotes,
                                NewsPulseResp newsPulse) {
        StringBuilder summary = new StringBuilder("隔夜美股：");
        if (CollUtil.isEmpty(indexQuotes)) {
            summary.append("美股行情暂未获取。");
        } else {
            appendQuotes(summary, indexQuotes, indexQuotes.size());
            summary.append("。");
        }

        summary.append("\n主题情绪：");
        OvernightMarketTheme strongestTheme = null;
        OvernightMarketTheme weakestTheme = null;
        for (OvernightMarketTheme marketTheme : marketThemes) {
            if (Objects.nonNull(marketTheme.getMedianPctChg())) {
                if (Objects.isNull(strongestTheme)) {
                    strongestTheme = marketTheme;
                }
                weakestTheme = marketTheme;
            }
        }
        if (Objects.isNull(strongestTheme)) {
            summary.append("主题行情暂未获取。");
        } else {
            summary.append("最强").append(strongestTheme.getName()).append(" ")
                    .append(formatPercent(strongestTheme.getMedianPctChg()));
            if (!strongestTheme.getCode().equals(weakestTheme.getCode())) {
                summary.append("；最弱").append(weakestTheme.getName()).append(" ")
                        .append(formatPercent(weakestTheme.getMedianPctChg()));
            }
            summary.append("。");
        }

        summary.append("\n明星异动：");
        if (CollUtil.isEmpty(starQuotes)) {
            summary.append("明星行情暂未获取。");
        } else {
            appendQuotes(summary, starQuotes, Math.min(starQuotes.size(), 3));
            summary.append("。");
        }
        summary.append("\n夜间新闻：");
        if (Objects.nonNull(newsPulse) && StringUtils.isNotBlank(newsPulse.getExecutiveSummary())) {
            summary.append(newsPulse.getExecutiveSummary());
        } else {
            summary.append("暂未形成有效摘要。");
        }
        summary.append("\n仅供研究，不构成投资建议。");
        return summary.toString();
    }

    private void appendQuotes(StringBuilder summary, List<OvernightMarketQuote> quotes, int limit) {
        for (int index = 0; index < limit; index++) {
            if (index > 0) {
                summary.append("；");
            }
            OvernightMarketQuote quote = quotes.get(index);
            summary.append(quote.getName()).append(" ").append(formatPercent(quote.getPctChg()));
        }
    }

    private NewsPulseResp loadNewsPulse() {
        try {
            return newsPulseService.pulse(6, true);
        } catch (Exception ex) {
            log.warn("盘前晨报夜间新闻摘要失败，原因={}", ex.getMessage());
            return null;
        }
    }

    private String formatPercent(BigDecimal pctChg) {
        if (Objects.isNull(pctChg)) {
            return "--";
        }
        return (pctChg.signum() > 0 ? "+" : "")
                + pctChg.setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }
}
