package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.util.JsonUtils;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.ai.KimiChatClient;
import com.awe.apex.quant.bot.config.ApexBotProperties;
import com.awe.apex.quant.cache.RedisCacheService;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.bo.DailyPreMarketReportContextBO;
import com.awe.apex.quant.domain.bo.PreMarketStockPickBO;
import com.awe.apex.quant.domain.dto.DailyPreMarketReportResp;
import com.awe.apex.quant.domain.dto.CommandDirectionItemResp;
import com.awe.apex.quant.domain.dto.DashboardCommandResp;
import com.awe.apex.quant.domain.dto.DashboardHomeResp;
import com.awe.apex.quant.domain.dto.DecisionItemResp;
import com.awe.apex.quant.domain.dto.DecisionTodayResp;
import com.awe.apex.quant.domain.dto.ExternalMarketItemResp;
import com.awe.apex.quant.domain.dto.MarketHotThemeItem;
import com.awe.apex.quant.domain.dto.MorningBriefingResp;
import com.awe.apex.quant.domain.dto.NewsPulseResp;
import com.awe.apex.quant.domain.dto.OvernightMarketQuote;
import com.awe.apex.quant.domain.dto.OvernightMarketTheme;
import com.awe.apex.quant.domain.dto.ObservePoolResp;
import com.awe.apex.quant.domain.dto.PortfolioBriefResp;
import com.awe.apex.quant.domain.dto.PortfolioSummaryResp;
import com.awe.apex.quant.domain.dto.PreMarketEventImpactResp;
import com.awe.apex.quant.domain.entity.PortfolioHolding;
import com.awe.apex.quant.market.TradingCalendar;
import com.awe.apex.quant.service.IDailyPreMarketReportService;
import com.awe.apex.quant.service.IDashboardService;
import com.awe.apex.quant.service.IDecisionService;
import com.awe.apex.quant.service.IPortfolioService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Apex 每日盘前研报服务实现。
 */
@Slf4j
@Service
public class DailyPreMarketReportServiceImpl implements IDailyPreMarketReportService {

    private static final String CACHE_KEY_PREFIX = "apex:daily-pre-market-report:latest:v14:";
    private static final Duration CACHE_TTL = Duration.ofDays(7);
    private static final String DEFAULT_WATCHLIST_GROUP = "我的自选";
    private static final String DATA_MISSING = "数据暂缺";

    @Resource
    private ApexUserContext userContext;

    @Resource
    private IDashboardService dashboardService;

    @Resource
    private IDecisionService decisionService;

    @Resource
    private IPortfolioService portfolioService;

    @Resource
    private KimiChatClient kimiChatClient;

    @Resource
    private RedisCacheService redisCacheService;

    @Resource
    private ApexBotProperties botProperties;

    /**
     * 读取当前用户的当日盘前研报。
     *
     * @param forceRefresh 是否强制重新生成
     * @return 每日盘前研报
     */
    @Override
    public DailyPreMarketReportResp latest(boolean forceRefresh) {
        Long currentUserId = userContext.currentUserId();
        LocalDate currentDate = LocalDate.now();
        LocalDate tradeDate = TradingCalendar.isTradingDay(currentDate)
                ? currentDate : TradingCalendar.nextTradingDay(currentDate);
        String cacheKey = buildCacheKey(currentUserId, tradeDate);
        if (!forceRefresh) {
            DailyPreMarketReportResp cachedReport = redisCacheService.get(cacheKey, DailyPreMarketReportResp.class);
            if (Objects.nonNull(cachedReport)) {
                return cachedReport;
            }
        }
        return generateCurrentUserReport(currentUserId);
    }

    /**
     * 使用指定用户身份生成当日盘前研报。
     *
     * @param userId Apex 用户ID
     * @return 每日盘前研报
     */
    @Override
    public DailyPreMarketReportResp generateForUser(Long userId) {
        return userContext.runAsUser(userId, () -> generateCurrentUserReport(userId));
    }

    private DailyPreMarketReportResp generateCurrentUserReport(Long userId) {
        LocalDateTime generatedAt = LocalDateTime.now();
        DashboardHomeResp dashboard = dashboardService.home(null, resolveWatchlistGroup(), false);
        MorningBriefingResp morningBriefing = Objects.nonNull(dashboard) ? dashboard.getMorningBriefing() : null;
        LocalDate tradeDate = resolveTradeDate(morningBriefing, generatedAt.toLocalDate());
        List<String> missingData = collectMissingData(dashboard);
        List<PortfolioSummaryResp> portfolios = loadPortfolios(missingData);
        int holdingCount = countHoldings(portfolios);
        Integer sentimentScore = calculateSentimentScore(dashboard);
        List<PortfolioSummaryResp> focusPortfolios = selectFocusPortfolios(portfolios, dashboard);
        List<PreMarketStockPickBO> stockPicks = buildStockPicks(dashboard, tradeDate);
        DailyPreMarketReportContextBO reportContext = DailyPreMarketReportContextBO.builder()
                .tradeDate(tradeDate)
                .generatedAt(generatedAt)
                .sentimentScore(sentimentScore)
                .market(Objects.nonNull(dashboard) ? dashboard.getMarket() : null)
                .morningBriefing(morningBriefing)
                .breadthForecast(Objects.nonNull(dashboard) ? dashboard.getBreadthForecast() : null)
                .command(Objects.nonNull(dashboard) ? dashboard.getCommand() : null)
                .decision(Objects.nonNull(dashboard) ? dashboard.getDecision() : null)
                .observePool(Objects.nonNull(dashboard) && CollUtil.isNotEmpty(dashboard.getObserveAlerts())
                        ? dashboard.getObserveAlerts() : List.of())
                .stockPicks(stockPicks)
                .portfolios(focusPortfolios)
                .missingData(missingData)
                .build();
        String marketJudgement = buildMarketJudgement(reportContext);

        String reportContent = null;
        String reportSource = "RULE";
        if (kimiChatClient.available()) {
            try {
                DailyPreMarketReportContextBO aiReportContext = buildAiReportContext(reportContext);
                String userPrompt = "以下 JSON 是本次研报唯一允许使用的事实与数据上下文：\n"
                        + JsonUtils.toJsonString(aiReportContext);
                String modelContent = kimiChatClient.chat(loadSystemPrompt(), userPrompt, 2600);
                modelContent = normalizeModelContent(modelContent);
                if (isCompleteReport(modelContent, reportContext)) {
                    reportContent = modelContent;
                    reportSource = "AI";
                } else {
                    log.warn("盘前研报模型输出不完整，用户编号={}，使用规则版", userId);
                }
            } catch (Exception ex) {
                log.warn("盘前研报模型生成失败，用户编号={}，原因={}", userId, ex.getMessage());
            }
        }
        if (StringUtils.isBlank(reportContent)) {
            reportContent = buildRuleReport(reportContext, marketJudgement);
        }

        DashboardHomeResp.MarketBlock market = Objects.nonNull(dashboard) ? dashboard.getMarket() : null;
        String dataLevel = resolveDataLevel(dashboard, missingData);
        LocalDate previousTradeDate = TradingCalendar.prevTradingDay(tradeDate);
        DailyPreMarketReportResp previousReport = redisCacheService.get(
                buildCacheKey(userId, previousTradeDate), DailyPreMarketReportResp.class);
        List<String> focusChanges = resolveFocusChanges(previousReport, reportContent, tradeDate);
        DailyPreMarketReportResp report = DailyPreMarketReportResp.builder()
                .tradeDate(tradeDate)
                .generatedAt(generatedAt)
                .marketDataAsOf(Objects.nonNull(market) ? market.getAsOf() : null)
                .marketStatus(sentimentLabel(sentimentScore))
                .sentimentScore(sentimentScore)
                .marketJudgement(marketJudgement)
                .dataLevel(dataLevel)
                .reportSource(reportSource)
                .portfolioCount(portfolios.size())
                .holdingCount(holdingCount)
                .focusChanges(focusChanges)
                .missingData(missingData)
                .content(reportContent)
                .build();
        redisCacheService.put(buildCacheKey(userId, tradeDate), report, CACHE_TTL);
        log.info("盘前研报生成完成，用户编号={}，交易日={}，来源={}，数据等级={}，个股观点数={}，组合数={}，持仓数={}",
                userId, tradeDate, reportSource, dataLevel, stockPicks.size(), portfolios.size(), holdingCount);
        return report;
    }

    private List<PreMarketStockPickBO> buildStockPicks(DashboardHomeResp dashboard, LocalDate tradeDate) {
        List<PreMarketStockPickBO> stockPicks = new ArrayList<>();

        // 1. 目标交易日决策优先；尚未生成时，只允许前一交易日决策作为盘前观察排序。
        DashboardHomeResp.DecisionBlock decision = Objects.nonNull(dashboard) ? dashboard.getDecision() : null;
        boolean currentDecision = Objects.nonNull(decision) && Boolean.TRUE.equals(decision.getHasToday())
                && Objects.nonNull(decision.getActionDate()) && decision.getActionDate().equals(tradeDate);
        boolean referenceDecision = false;
        if (!currentDecision) {
            LocalDate marketDataDate = Objects.nonNull(dashboard) && Objects.nonNull(dashboard.getMarket())
                    ? dashboard.getMarket().getAsOf() : null;
            boolean latestCompletedTradingDay = Objects.nonNull(marketDataDate)
                    && marketDataDate.equals(TradingCalendar.prevTradingDay(tradeDate));
            if (latestCompletedTradingDay) {
                try {
                    DecisionTodayResp previousDecision = decisionService.today(marketDataDate, resolveWatchlistGroup());
                    if (Objects.nonNull(previousDecision) && Boolean.TRUE.equals(previousDecision.getGenerated())
                            && marketDataDate.equals(previousDecision.getActionDate())
                            && CollUtil.isNotEmpty(previousDecision.getBuys())) {
                        List<DashboardHomeResp.HomeActionItem> referenceBuys = new ArrayList<>();
                        for (DecisionItemResp buyItem : previousDecision.getBuys()) {
                            if (Objects.isNull(buyItem) || !"BUY".equalsIgnoreCase(buyItem.getAction())
                                    || StringUtils.isBlank(buyItem.getCode())
                                    || StringUtils.isBlank(buyItem.getName())) {
                                continue;
                            }
                            referenceBuys.add(DashboardHomeResp.HomeActionItem.builder()
                                    .code(buyItem.getCode())
                                    .name(buyItem.getName())
                                    .action(buyItem.getAction())
                                    .strategyId(buyItem.getStrategyId())
                                    .score(buyItem.getScore())
                                    .suggestedWeight(buyItem.getSuggestedWeight())
                                    .mainlineMatch(buyItem.getMainlineMatch())
                                    .mainlineName(buyItem.getMainlineName())
                                    .valuationLevel(buyItem.getValuationLevel())
                                    .valuationLabel(buyItem.getValuationLabel())
                                    .executableHint(buyItem.getExecutableHint())
                                    .linkHint(buyItem.getLinkHint())
                                    .reason(buyItem.getReason())
                                    .exitRule(buyItem.getExitRule())
                                    .build());
                            if (referenceBuys.size() >= 3) {
                                break;
                            }
                        }
                        if (CollUtil.isNotEmpty(referenceBuys)) {
                            decision = DashboardHomeResp.DecisionBlock.builder()
                                    .actionDate(marketDataDate)
                                    .hasToday(true)
                                    .topBuys(referenceBuys)
                                    .build();
                            referenceDecision = true;
                        }
                    }
                } catch (Exception ex) {
                    log.warn("盘前研报读取上一交易日决策失败，决策日={}，原因={}", marketDataDate, ex.getMessage());
                }
            }
        }
        if ((currentDecision || referenceDecision) && CollUtil.isNotEmpty(decision.getTopBuys())) {
            for (DashboardHomeResp.HomeActionItem buyItem : decision.getTopBuys()) {
                if (Objects.isNull(buyItem) || !"BUY".equalsIgnoreCase(buyItem.getAction())
                        || StringUtils.isBlank(buyItem.getCode()) || StringUtils.isBlank(buyItem.getName())) {
                    continue;
                }
                String opinion;
                boolean usefulValuation = isUsefulText(buyItem.getValuationLabel())
                        && !buyItem.getValuationLabel().contains("数据不足");
                if (Boolean.TRUE.equals(buyItem.getMainlineMatch())
                        && usefulValuation) {
                    opinion = defaultText(buyItem.getMainlineName(), "当日主线") + "与"
                            + buyItem.getValuationLabel() + "形成共振，优先于仅靠短线反弹的候选";
                } else if (Boolean.TRUE.equals(buyItem.getMainlineMatch())) {
                    opinion = defaultText(buyItem.getMainlineName(), "当日主线")
                            + "内的买入候选，优先看板块强度能否向个股扩散";
                } else if (usefulValuation) {
                    opinion = buyItem.getValuationLabel() + "提供安全边际，但只执行量价确认后的机会";
                } else if (referenceDecision) {
                    opinion = "量价信号领先，只看强势延续，不接低开转弱";
                } else {
                    opinion = "当日买入候选中优先级靠前，只做确认后的强势延续，不提前赌开盘方向";
                }

                List<String> evidenceItems = new ArrayList<>();
                if (referenceDecision) {
                    evidenceItems.add("决策日 " + decision.getActionDate());
                }
                evidenceItems.add(buyItem.getReason());
                if (Objects.nonNull(buyItem.getScore())) {
                    evidenceItems.add("综合评分 " + formatNumber(buyItem.getScore()));
                }
                if (Boolean.TRUE.equals(buyItem.getMainlineMatch())) {
                    evidenceItems.add(defaultText(buyItem.getMainlineName(), "当日主线") + "主线匹配");
                }
                if (usefulValuation) {
                    evidenceItems.add(buyItem.getValuationLabel());
                }
                evidenceItems.add(buyItem.getLinkHint());
                if (CollUtil.isEmpty(distinctTexts(evidenceItems))) {
                    evidenceItems.add("当日买入候选排名靠前");
                }

                boolean executable = !referenceDecision && Boolean.TRUE.equals(buyItem.getExecutableHint());
                String trigger = executable
                        ? "9:45 后仍不破开盘价且持续强于沪深 300"
                        : "9:45 后仍不破开盘价且持续强于沪深 300，再纳入开仓候选";
                String invalidation = StringUtils.isNotBlank(buyItem.getExitRule())
                        ? buyItem.getExitRule() : "9:45 前跌破开盘价且相对沪深 300 转弱";
                BigDecimal suggestedWeight = Objects.nonNull(buyItem.getSuggestedWeight())
                        && buyItem.getSuggestedWeight().compareTo(BigDecimal.ZERO) > 0 && !referenceDecision
                        ? buyItem.getSuggestedWeight() : null;
                stockPicks.add(PreMarketStockPickBO.builder()
                        .rank(stockPicks.size() + 1)
                        .level(referenceDecision
                                ? (stockPicks.isEmpty() ? "观察首选" : "观察备选")
                                : (stockPicks.isEmpty() ? "首选" : "备选"))
                        .code(buyItem.getCode())
                        .name(buyItem.getName())
                        .direction(Boolean.TRUE.equals(buyItem.getMainlineMatch()) ? buyItem.getMainlineName() : null)
                        .opinion(opinion)
                        .evidence(String.join("，", distinctTexts(evidenceItems)))
                        .trigger(trigger)
                        .invalidation(invalidation)
                        .suggestedWeight(suggestedWeight)
                        .build());
                if (stockPicks.size() >= 3) {
                    return stockPicks;
                }
            }
        }

        // 2. 当日决策不足三只时，只用已触发或接近触发的 BUY 观察候选补充。
        List<ObservePoolResp> observeAlerts = Objects.nonNull(dashboard) ? dashboard.getObserveAlerts() : List.of();
        if (CollUtil.isEmpty(observeAlerts)) {
            return stockPicks;
        }
        for (ObservePoolResp observeAlert : observeAlerts) {
            if (Objects.isNull(observeAlert) || !"BUY".equalsIgnoreCase(observeAlert.getSide())
                    || (!"TRIGGERED".equals(observeAlert.getStatus()) && !"NEAR".equals(observeAlert.getStatus()))
                    || StringUtils.isBlank(observeAlert.getCode()) || StringUtils.isBlank(observeAlert.getName())
                    || Objects.isNull(observeAlert.getDecisionUpdatedAt())
                    || !tradeDate.equals(observeAlert.getDecisionUpdatedAt().toLocalDate())
                    || (StringUtils.isBlank(observeAlert.getReason())
                    && StringUtils.isBlank(observeAlert.getGuideText()))) {
                continue;
            }
            boolean duplicated = false;
            for (PreMarketStockPickBO stockPick : stockPicks) {
                if (observeAlert.getCode().equals(stockPick.getCode())) {
                    duplicated = true;
                    break;
                }
            }
            if (duplicated) {
                continue;
            }

            List<String> evidenceItems = new ArrayList<>();
            evidenceItems.add(observeAlert.getReason());
            evidenceItems.add(observeAlert.getGuideText());
            evidenceItems.add(observeAlert.getTechSummary());
            evidenceItems.add(observeAlert.getValuationLabel());
            evidenceItems.add(observeAlert.getStatusHint());
            String setupStyle = defaultText(observeAlert.getSetupStyle(), "策略观察");
            String opinion = "TRIGGERED".equals(observeAlert.getStatus())
                    ? setupStyle + "条件已经触发，优先等待开盘确认而不是追高"
                    : setupStyle + "接近触发，只有条件兑现才进入交易候选";
            String trigger = "9:45 前确认：" + (StringUtils.isNotBlank(observeAlert.getTriggerLabel())
                    ? observeAlert.getTriggerLabel() : defaultText(observeAlert.getGuideText(), "观察条件转为已触发"));
            String invalidation = Objects.nonNull(observeAlert.getStopLoss())
                    ? "跌破止损价 " + formatNumber(observeAlert.getStopLoss()) : "触发条件撤销或相对强度转弱";
            stockPicks.add(PreMarketStockPickBO.builder()
                    .rank(stockPicks.size() + 1)
                    .level("TRIGGERED".equals(observeAlert.getStatus()) ? "触发" : "观察")
                    .code(observeAlert.getCode())
                    .name(observeAlert.getName())
                    .opinion(opinion)
                    .evidence(String.join("，", distinctTexts(evidenceItems)))
                    .trigger(trigger)
                    .invalidation(invalidation)
                    .build());
            if (stockPicks.size() >= 3) {
                break;
            }
        }
        return stockPicks;
    }

    private List<PortfolioSummaryResp> loadPortfolios(List<String> missingData) {
        List<PortfolioSummaryResp> portfolioItems = portfolioService.listPortfolios(false);
        if (CollUtil.isEmpty(portfolioItems)) {
            missingData.add("Apex 当前没有可分析的活跃组合");
            return new ArrayList<>();
        }
        List<PortfolioSummaryResp> portfolios = new ArrayList<>();
        for (PortfolioSummaryResp portfolioItem : portfolioItems) {
            if (!Boolean.TRUE.equals(portfolioItem.getEditable())) {
                continue;
            }
            try {
                PortfolioSummaryResp portfolio = portfolioService.detail(portfolioItem.getId());
                portfolios.add(portfolio);
                if (Objects.nonNull(portfolio.getMissingQuoteCount()) && portfolio.getMissingQuoteCount() > 0) {
                    missingData.add("组合“" + portfolio.getName() + "”有 "
                            + portfolio.getMissingQuoteCount() + " 只持仓缺少行情");
                }
            } catch (Exception ex) {
                missingData.add("组合“" + defaultText(portfolioItem.getName(), String.valueOf(portfolioItem.getId()))
                        + "”详情读取失败");
                log.warn("盘前研报组合详情读取失败，组合编号={}，原因={}", portfolioItem.getId(), ex.getMessage());
            }
        }
        if (CollUtil.isEmpty(portfolios)) {
            missingData.add("当前用户没有可分析的活跃组合");
        }
        return portfolios;
    }

    private List<String> collectMissingData(DashboardHomeResp dashboard) {
        List<String> missingData = new ArrayList<>();
        if (Objects.isNull(dashboard) || Objects.isNull(dashboard.getMarket())) {
            missingData.add("昨日 A 股市场简报");
        } else {
            DashboardHomeResp.MarketBlock market = dashboard.getMarket();
            if (Objects.isNull(market.getAsOf())) {
                missingData.add("A 股市场数据截至日");
            }
            if (CollUtil.isEmpty(market.getIndexes())) {
                missingData.add("A 股主要指数");
            }
            if (Objects.isNull(market.getIndexVolume())) {
                missingData.add("A 股市场成交额");
            }
            if (Objects.isNull(market.getBreadthUp()) || Objects.isNull(market.getBreadthDown())) {
                missingData.add("A 股上涨/下跌家数");
            }
            if (Objects.isNull(market.getLimitUpCount()) || Objects.isNull(market.getLimitDownCount())) {
                missingData.add("A 股涨停/跌停家数");
            }
        }
        MorningBriefingResp morning = Objects.nonNull(dashboard) ? dashboard.getMorningBriefing() : null;
        if (Objects.isNull(morning)) {
            missingData.add("隔夜全球市场晨报");
        } else {
            if (CollUtil.isEmpty(morning.getIndexQuotes())) {
                missingData.add("隔夜美股主要指数");
            }
            if (CollUtil.isEmpty(morning.getAsiaQuotes())) {
                missingData.add("亚太主要指数");
            }
            if (Objects.isNull(morning.getFtseA50Future())) {
                missingData.add("富时 A50 期指");
            }
            if (Objects.isNull(morning.getChinaGoldenDragon())) {
                missingData.add("纳斯达克中国金龙指数");
            }
            int configuredChinaConceptCount = 0;
            String configuredChinaConceptSymbols = botProperties.getMorningBriefing().getChinaConceptSymbols();
            if (StringUtils.isNotBlank(configuredChinaConceptSymbols)) {
                for (String configuredChinaConceptSymbol : configuredChinaConceptSymbols.split(",")) {
                    if (StringUtils.isNotBlank(configuredChinaConceptSymbol)) {
                        configuredChinaConceptCount++;
                    }
                }
            }
            int availableChinaConceptCount = 0;
            if (CollUtil.isNotEmpty(morning.getChinaConceptQuotes())) {
                for (OvernightMarketQuote chinaConceptQuote : morning.getChinaConceptQuotes()) {
                    if (Objects.nonNull(chinaConceptQuote)
                            && StringUtils.isNotBlank(chinaConceptQuote.getSymbol())) {
                        availableChinaConceptCount++;
                    }
                }
            }
            if (configuredChinaConceptCount > 0 && availableChinaConceptCount < configuredChinaConceptCount) {
                missingData.add("中概股代表行情");
            }
            if (CollUtil.isEmpty(morning.getExternalMarketItems())) {
                missingData.add("黄金、原油、人民币、美债和美元");
            } else {
                for (ExternalMarketItemResp item : morning.getExternalMarketItems()) {
                    if (Objects.nonNull(item) && !item.isAvailable()) {
                        missingData.add(item.getName());
                    }
                }
            }
            if (Objects.isNull(morning.getNewsPulse()) || CollUtil.isEmpty(morning.getNewsPulse().getEventImpacts())) {
                missingData.add("已分类的盘前重要消息");
            }
        }
        if (Objects.isNull(dashboard) || Objects.isNull(dashboard.getCommand())) {
            missingData.add("盘前市场判断与验证条件");
        }
        return distinctTexts(missingData);
    }

    private Integer calculateSentimentScore(DashboardHomeResp dashboard) {
        if (Objects.isNull(dashboard) || Objects.isNull(dashboard.getMarket())) {
            return null;
        }
        DashboardHomeResp.MarketBlock market = dashboard.getMarket();
        int score = 50;
        int evidenceCount = 0;
        if (Objects.nonNull(market.getBreadthUp()) && Objects.nonNull(market.getBreadthDown())) {
            int breadthTotal = market.getBreadthUp() + market.getBreadthDown();
            if (breadthTotal > 0) {
                score += BigDecimal.valueOf(market.getBreadthUp() - market.getBreadthDown())
                        .multiply(BigDecimal.valueOf(30))
                        .divide(BigDecimal.valueOf(breadthTotal), 0, RoundingMode.HALF_UP)
                        .intValue();
                evidenceCount++;
            }
        }
        if (Objects.nonNull(market.getLimitUpCount()) && Objects.nonNull(market.getLimitDownCount())) {
            int limitTotal = market.getLimitUpCount() + market.getLimitDownCount();
            if (limitTotal > 0) {
                score += BigDecimal.valueOf(market.getLimitUpCount() - market.getLimitDownCount())
                        .multiply(BigDecimal.TEN)
                        .divide(BigDecimal.valueOf(limitTotal), 0, RoundingMode.HALF_UP)
                        .intValue();
                evidenceCount++;
            }
        }
        MorningBriefingResp morning = dashboard.getMorningBriefing();
        if (Objects.nonNull(morning) && Objects.nonNull(morning.getNewsPulse())) {
            Integer bullCount = morning.getNewsPulse().getBullCount();
            Integer bearCount = morning.getNewsPulse().getBearCount();
            Integer neutralCount = morning.getNewsPulse().getNeutralCount();
            if (Objects.nonNull(bullCount) && Objects.nonNull(bearCount) && Objects.nonNull(neutralCount)) {
                int newsTotal = bullCount + bearCount + neutralCount;
                if (newsTotal > 0) {
                    score += BigDecimal.valueOf(bullCount - bearCount)
                            .multiply(BigDecimal.TEN)
                            .divide(BigDecimal.valueOf(newsTotal), 0, RoundingMode.HALF_UP)
                            .intValue();
                    evidenceCount++;
                }
            }
        }
        return evidenceCount > 0 ? Math.max(0, Math.min(100, score)) : null;
    }

    private String buildRuleReport(DailyPreMarketReportContextBO context, String marketJudgement) {
        StringBuilder report = new StringBuilder();
        List<String> focusDirectionNames = collectFocusDirectionNames(context);
        if (CollUtil.isNotEmpty(context.getStockPicks())) {
            List<String> stockDirectionNames = new ArrayList<>();
            for (PreMarketStockPickBO stockPick : context.getStockPicks()) {
                addDirectionName(stockDirectionNames, stockPick.getDirection());
            }
            if (CollUtil.isNotEmpty(stockDirectionNames)) {
                focusDirectionNames = stockDirectionNames;
            }
        }
        report.append(buildReportTitle(context, focusDirectionNames)).append("\n")
                .append("日期：").append(context.getTradeDate()).append("\n")
                .append("核心观点：").append(marketJudgement).append("\n");
        if (CollUtil.isNotEmpty(context.getStockPicks())) {
            report.append("首选个股：");
            List<String> stockIdentities = new ArrayList<>();
            for (PreMarketStockPickBO stockPick : context.getStockPicks()) {
                stockIdentities.add(stockPick.getName() + " " + stockPick.getCode());
            }
            report.append(String.join("、", stockIdentities)).append("。\n");
        }
        if (CollUtil.isEmpty(context.getStockPicks()) && CollUtil.isNotEmpty(focusDirectionNames)) {
            report.append("优先看：").append(String.join("、", focusDirectionNames)).append("。\n");
        }
        report.append("最大风险：").append(resolvePrimaryRisk(context)).append("\n");

        appendMarketState(report, context);
        appendCapitalStyle(report, context, focusDirectionNames);
        if (CollUtil.isNotEmpty(context.getStockPicks())) {
            appendStockPicks(report, context.getStockPicks());
        } else {
            appendInvestmentOpportunities(report, context, focusDirectionNames);
        }
        appendPortfolioSection(report, context);
        report.append("\n05｜开盘剧本\n")
                .append("偏强｜核心方向放量、上涨家数继续扩大，核心股保住大部分高开涨幅。\n")
                .append("震荡｜指数横盘、量能没有放大，资金在两三个方向间快速轮动。\n")
                .append("转弱｜核心方向高开低走、下跌家数持续扩大，跌停数量同步增加。\n")
                .append("仅供研究，不构成投资建议。");
        return report.toString();
    }

    private String buildReportTitle(DailyPreMarketReportContextBO context, List<String> focusDirectionNames) {
        if (isShrinkingVolume(context.getMarket())) {
            return "今日投资机会｜缩量轮动 · 强势延续";
        }
        if (CollUtil.isNotEmpty(focusDirectionNames)) {
            String primaryDirection = focusDirectionNames.get(0);
            PreMarketEventImpactResp event = findDirectionEvent(context, primaryDirection);
            if (Objects.nonNull(event)) {
                return "今日投资机会｜" + primaryDirection + "获事件催化，开盘承接决定持续性";
            }
            if (isShrinkingVolume(context.getMarket())) {
                return "今日投资机会｜" + primaryDirection + "保持活跃，缩量环境不追高";
            }
            return "今日投资机会｜" + primaryDirection + "居前，等待量价确认主线强度";
        }
        if (Objects.nonNull(context.getSentimentScore()) && context.getSentimentScore() <= 40) {
            return "今日投资机会｜弱势先控仓，只做逆势走强方向";
        }
        return "今日投资机会｜震荡轮动，先等主线走出来";
    }

    private void appendStockPicks(StringBuilder report, List<PreMarketStockPickBO> stockPicks) {
        report.append("\n03｜个股推荐\n");
        for (PreMarketStockPickBO stockPick : stockPicks) {
            report.append(stockPick.getRank()).append(". ")
                    .append(stockPick.getName()).append(" ").append(stockPick.getCode())
                    .append("｜级别：").append(reportField(stockPick.getLevel()))
                    .append("；观点：").append(reportField(stockPick.getOpinion()))
                    .append("；依据：").append(reportField(stockPick.getEvidence()))
                    .append("；触发：").append(reportField(stockPick.getTrigger()))
                    .append("；失效：").append(reportField(stockPick.getInvalidation()));
            if (Objects.nonNull(stockPick.getSuggestedWeight())) {
                report.append("；仓位：")
                        .append(formatPercent(stockPick.getSuggestedWeight().multiply(BigDecimal.valueOf(100))));
            }
            report.append("。\n");
        }
    }

    private void appendMarketState(StringBuilder report, DailyPreMarketReportContextBO context) {
        DashboardHomeResp.MarketBlock market = context.getMarket();
        report.append("\n01｜市场状态\n");
        StringBuilder marketEvidence = new StringBuilder();
        if (Objects.nonNull(market) && StringUtils.isNotBlank(market.getVolumeLabel())) {
            marketEvidence.append("量能 ").append(market.getVolumeLabel());
        }
        if (Objects.nonNull(market) && Objects.nonNull(market.getBreadthUp())
                && Objects.nonNull(market.getBreadthDown())) {
            appendEvidenceSeparator(marketEvidence);
            marketEvidence.append("上涨 ").append(market.getBreadthUp())
                    .append(" / 下跌 ").append(market.getBreadthDown());
        }
        if (Objects.nonNull(market) && Objects.nonNull(market.getLimitUpCount())
                && Objects.nonNull(market.getLimitDownCount())) {
            appendEvidenceSeparator(marketEvidence);
            marketEvidence.append("涨停 ").append(market.getLimitUpCount())
                    .append(" / 跌停 ").append(market.getLimitDownCount());
        }
        if (Objects.nonNull(context.getSentimentScore())) {
            appendEvidenceSeparator(marketEvidence);
            marketEvidence.append("情绪 ").append(context.getSentimentScore())
                    .append(" / 100（").append(sentimentLabel(context.getSentimentScore())).append("）");
        }
        if (marketEvidence.length() > 0) {
            report.append("盘面：").append(marketEvidence).append("。\n");
        }
        if (Objects.nonNull(market) && StringUtils.isNotBlank(market.getStanceReason())) {
            report.append("含义：").append(market.getStanceReason()).append("。\n");
        }
    }

    private void appendEvidenceSeparator(StringBuilder evidence) {
        if (evidence.length() > 0) {
            evidence.append("，");
        }
    }

    private void appendCapitalStyle(StringBuilder report, DailyPreMarketReportContextBO context,
                                    List<String> focusDirectionNames) {
        List<String> riskDirectionNames = collectRiskDirectionNames(context);
        DashboardHomeResp.MarketBlock market = context.getMarket();
        boolean hasVolumeEvidence = Objects.nonNull(market)
                && (StringUtils.isNotBlank(market.getVolumeLabel())
                || StringUtils.isNotBlank(market.getVolumeTrend()));
        if (CollUtil.isEmpty(focusDirectionNames) && CollUtil.isEmpty(riskDirectionNames)
                && !hasVolumeEvidence) {
            return;
        }
        report.append("\n02｜资金风格\n");
        if (CollUtil.isNotEmpty(focusDirectionNames)) {
            report.append("偏强：").append(String.join("、", focusDirectionNames)).append("。\n");
        }
        if (CollUtil.isNotEmpty(riskDirectionNames)) {
            report.append("承压：").append(String.join("、", riskDirectionNames)).append("。\n");
        }
        report.append("风格：");
        if (isShrinkingVolume(market)) {
            report.append("存量资金抱团，强势方向容易冲高兑现，开盘只认放量承接。");
        } else if (Objects.nonNull(context.getSentimentScore()) && context.getSentimentScore() <= 40) {
            report.append("弱势资金偏防守，放量下跌时先控制回撤，只关注逆势走强方向。");
        } else if (Objects.nonNull(context.getSentimentScore()) && context.getSentimentScore() >= 60) {
            report.append("风险偏好正在修复，核心方向若带动涨跌家数扩散，可继续提高优先级。");
        } else {
            report.append("资金仍在轮动，板块成交与个股扩散同步增强后才提高优先级。");
        }
        report.append("\n");
    }

    private void appendInvestmentOpportunities(StringBuilder report, DailyPreMarketReportContextBO context,
                                               List<String> focusDirectionNames) {
        if (CollUtil.isEmpty(focusDirectionNames)) {
            return;
        }
        report.append("\n03｜投资机会\n");
        int appendedOpportunityCount = 0;
        List<String> usedEventTitles = new ArrayList<>();
        for (String directionName : focusDirectionNames) {
            PreMarketEventImpactResp matchedEvent = findDirectionEvent(context, directionName);
            if (Objects.nonNull(matchedEvent) && StringUtils.isNotBlank(matchedEvent.getTitle())
                    && usedEventTitles.contains(matchedEvent.getTitle())) {
                continue;
            }
            CommandDirectionItemResp matchedCommand = findCommandDirection(context, directionName);
            MarketHotThemeItem matchedTheme = findHotTheme(context, directionName);
            appendedOpportunityCount++;
            report.append(appendedOpportunityCount).append(". ").append(directionName).append("｜催化：");
            if (Objects.nonNull(matchedEvent)) {
                report.append(matchedEvent.getTitle());
                if (StringUtils.isNotBlank(matchedEvent.getTitle())) {
                    usedEventTitles.add(matchedEvent.getTitle());
                }
            } else if (Objects.nonNull(matchedCommand) && StringUtils.isNotBlank(matchedCommand.getReason())) {
                report.append(matchedCommand.getReason());
            } else if (Objects.nonNull(matchedTheme) && Objects.nonNull(matchedTheme.getPctChg())) {
                report.append("昨日涨 ").append(formatPercent(matchedTheme.getPctChg())).append("，具备延续基础");
            } else if (Objects.nonNull(matchedTheme)) {
                report.append("昨日进入活跃方向，具备延续基础");
            } else {
                report.append("盘前机会信号居前");
            }
            report.append("；确认：9:45 前").append(directionName)
                    .append("板块成交额较昨日同期放大 20%，核心股涨幅持续高于沪深 300；失效：9:45 前")
                    .append(directionName).append("冲高回落，核心股跌破开盘价且板块涨幅落后沪深 300。\n");
            if (appendedOpportunityCount >= 3) {
                break;
            }
        }
    }

    private void appendPortfolioSection(StringBuilder report, DailyPreMarketReportContextBO context) {
        if (CollUtil.isEmpty(context.getPortfolios())) {
            return;
        }
        List<String> portfolioRiskLines = collectPortfolioRiskLines(context.getPortfolios());
        boolean hasHolding = false;
        for (PortfolioSummaryResp portfolio : context.getPortfolios()) {
            if (CollUtil.isNotEmpty(portfolio.getHoldings())) {
                hasHolding = true;
                break;
            }
        }
        if (!hasHolding && CollUtil.isEmpty(portfolioRiskLines)) {
            return;
        }
        report.append("\n04｜持仓应对\n");
        int appendedHoldingCount = 0;
        for (PortfolioSummaryResp portfolio : context.getPortfolios()) {
            if (CollUtil.isEmpty(portfolio.getHoldings()) || appendedHoldingCount >= 6) {
                continue;
            }
            for (PortfolioHolding holding : portfolio.getHoldings()) {
                if (Objects.isNull(holding)
                        || (StringUtils.isBlank(holding.getCode()) && StringUtils.isBlank(holding.getName()))) {
                    continue;
                }
                report.append("- ").append(defaultText(holding.getName(), holding.getCode()))
                        .append(" ").append(defaultText(holding.getCode(), ""))
                        .append("｜").append(holdingStatus(holding))
                        .append("｜入选：").append(buildHoldingFocusReason(holding, context));
                if (Objects.nonNull(holding.getWeightPct())) {
                    report.append("｜仓位 ").append(formatPercent(holding.getWeightPct()));
                }
                if (Objects.nonNull(holding.getMarketPrice())) {
                    report.append("｜价格 ").append(formatNumber(holding.getMarketPrice()));
                }
                if (Objects.nonNull(holding.getPnlPct())) {
                    report.append("｜盈亏 ").append(formatPercent(holding.getPnlPct().multiply(BigDecimal.valueOf(100))));
                }
                if (StringUtils.isNotBlank(holding.getTechSummary())) {
                    report.append("｜趋势 ").append(holding.getTechSummary());
                }
                if (StringUtils.isNotBlank(holding.getAdvice())) {
                    report.append("｜处理 ").append(holding.getAdvice());
                }
                report.append("\n");
                appendedHoldingCount++;
                if (appendedHoldingCount >= 6) {
                    break;
                }
            }
        }
        for (String portfolioRiskLine : portfolioRiskLines) {
            report.append("- 组合风险｜").append(portfolioRiskLine).append("\n");
        }
    }

    private List<String> collectPortfolioRiskLines(List<PortfolioSummaryResp> portfolios) {
        List<String> riskLines = new ArrayList<>();
        for (PortfolioSummaryResp portfolio : portfolios) {
            PortfolioBriefResp brief = portfolio.getBrief();
            if (Objects.isNull(brief)) {
                continue;
            }
            if (Objects.nonNull(brief.getMaxWeightPct())
                    && brief.getMaxWeightPct().compareTo(BigDecimal.valueOf(20)) >= 0) {
                riskLines.add(defaultText(portfolio.getName(), "组合") + "单票最高仓位 "
                        + formatPercent(brief.getMaxWeightPct()) + "（"
                        + defaultText(brief.getMaxWeightCode(), "高仓位标的") + "）");
            }
            if (StringUtils.isNotBlank(brief.getTopTheme()) && Objects.nonNull(brief.getTopThemePct())
                    && brief.getTopThemePct().compareTo(BigDecimal.valueOf(35)) >= 0) {
                riskLines.add(defaultText(portfolio.getName(), "组合") + "对“" + brief.getTopTheme()
                        + "”暴露 " + formatPercent(brief.getTopThemePct()));
            }
        }
        return riskLines;
    }

    private List<PortfolioSummaryResp> selectFocusPortfolios(List<PortfolioSummaryResp> portfolios,
                                                              DashboardHomeResp dashboard) {
        List<PortfolioSummaryResp> focusPortfolios = new ArrayList<>();
        for (PortfolioSummaryResp portfolio : portfolios) {
            List<PortfolioHolding> focusHoldings = new ArrayList<>();
            if (CollUtil.isNotEmpty(portfolio.getHoldings())) {
                for (PortfolioHolding holding : portfolio.getHoldings()) {
                    if (isHoldingNotable(holding, dashboard)) {
                        focusHoldings.add(holding);
                    }
                }
            }
            if (CollUtil.isEmpty(focusHoldings) && !hasPortfolioRisk(portfolio.getBrief())) {
                continue;
            }
            focusPortfolios.add(PortfolioSummaryResp.builder()
                    .id(portfolio.getId())
                    .name(portfolio.getName())
                    .positionCount(focusHoldings.size())
                    .brief(portfolio.getBrief())
                    .holdings(focusHoldings)
                    .build());
        }
        return focusPortfolios;
    }

    private boolean isHoldingNotable(PortfolioHolding holding, DashboardHomeResp dashboard) {
        if (Objects.isNull(holding)) {
            return false;
        }
        if (Objects.nonNull(holding.getMarketPrice()) && Objects.nonNull(holding.getStopLoss())
                && holding.getMarketPrice().compareTo(holding.getStopLoss()) <= 0) {
            return true;
        }
        if (Objects.nonNull(holding.getWeightPct())
                && holding.getWeightPct().compareTo(BigDecimal.valueOf(20)) >= 0) {
            return true;
        }
        if (Objects.nonNull(holding.getPctChg())
                && holding.getPctChg().abs().compareTo(BigDecimal.valueOf(3)) >= 0) {
            return true;
        }
        if (containsWeak(holding.getTechSummary()) || "RICH".equalsIgnoreCase(holding.getValuationLevel())) {
            return true;
        }
        return isHoldingLinkedToCurrentVariable(holding, dashboard);
    }

    private boolean isHoldingLinkedToCurrentVariable(PortfolioHolding holding, DashboardHomeResp dashboard) {
        MorningBriefingResp morning = Objects.nonNull(dashboard) ? dashboard.getMorningBriefing() : null;
        for (PreMarketEventImpactResp event : morningEvents(morning)) {
            if (CollUtil.isNotEmpty(event.getRelatedCodes()) && event.getRelatedCodes().contains(holding.getCode())) {
                return true;
            }
            if (matchesHoldingDirection(holding, event.getThemes())) {
                return true;
            }
        }
        DashboardCommandResp command = Objects.nonNull(dashboard) ? dashboard.getCommand() : null;
        if (Objects.nonNull(command) && Objects.nonNull(command.getPreMarketSummary())
                && CollUtil.isNotEmpty(command.getPreMarketSummary().getOpportunityItems())) {
            for (CommandDirectionItemResp direction : command.getPreMarketSummary().getOpportunityItems()) {
                if (StringUtils.isNotBlank(direction.getName())
                        && matchesHoldingDirection(holding, List.of(direction.getName()))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasPortfolioRisk(PortfolioBriefResp brief) {
        if (Objects.isNull(brief)) {
            return false;
        }
        return (Objects.nonNull(brief.getMaxWeightPct())
                && brief.getMaxWeightPct().compareTo(BigDecimal.valueOf(20)) >= 0)
                || (StringUtils.isNotBlank(brief.getTopTheme()) && Objects.nonNull(brief.getTopThemePct())
                && brief.getTopThemePct().compareTo(BigDecimal.valueOf(35)) >= 0);
    }

    private boolean matchesHoldingDirection(PortfolioHolding holding, List<String> directions) {
        if (CollUtil.isEmpty(directions)) {
            return false;
        }
        for (String direction : directions) {
            if (StringUtils.isBlank(direction)) {
                continue;
            }
            if (StringUtils.isNotBlank(holding.getIndustry()) && holding.getIndustry().contains(direction)) {
                return true;
            }
            if (CollUtil.isNotEmpty(holding.getThemeTags())) {
                for (String themeTag : holding.getThemeTags()) {
                    if (StringUtils.isNotBlank(themeTag)
                            && (themeTag.contains(direction) || direction.contains(themeTag))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private String buildHoldingFocusReason(PortfolioHolding holding, DailyPreMarketReportContextBO context) {
        List<String> reasons = new ArrayList<>();
        if (Objects.nonNull(holding.getMarketPrice()) && Objects.nonNull(holding.getStopLoss())
                && holding.getMarketPrice().compareTo(holding.getStopLoss()) <= 0) {
            reasons.add("已触及止损线");
        }
        if (Objects.nonNull(holding.getWeightPct())
                && holding.getWeightPct().compareTo(BigDecimal.valueOf(20)) >= 0) {
            reasons.add("单票仓位较高");
        }
        if (Objects.nonNull(holding.getPctChg())
                && holding.getPctChg().abs().compareTo(BigDecimal.valueOf(3)) >= 0) {
            reasons.add("价格波动超过 3%");
        }
        if (containsWeak(holding.getTechSummary())) {
            reasons.add("技术结构转弱");
        }
        if ("RICH".equalsIgnoreCase(holding.getValuationLevel())) {
            reasons.add("估值偏贵");
        }
        for (PreMarketEventImpactResp event : morningEvents(context.getMorningBriefing())) {
            if ((CollUtil.isNotEmpty(event.getRelatedCodes()) && event.getRelatedCodes().contains(holding.getCode()))
                    || matchesHoldingDirection(holding, event.getThemes())) {
                reasons.add(StringUtils.isNotBlank(event.getTitle())
                        ? "与“" + event.getTitle() + "”直接相关" : "与今日重大事件直接相关");
                break;
            }
        }
        if (CollUtil.isEmpty(reasons)) {
            reasons.add("与今日优先方向相关");
        }
        return String.join("、", reasons.subList(0, Math.min(2, reasons.size())));
    }

    private String buildMarketJudgement(DailyPreMarketReportContextBO context) {
        DashboardHomeResp.MarketBlock market = context.getMarket();
        Integer sentimentScore = context.getSentimentScore();
        if (Objects.nonNull(sentimentScore) && sentimentScore >= 60) {
            return isShrinkingVolume(market)
                    ? "情绪偏强但量能收缩，预计以结构性轮动为主；高开不追，放量承接才算主线确认。"
                    : "情绪与量能共同偏强，市场倾向结构性偏多；优先做有事件或资金确认的方向。";
        }
        if (Objects.nonNull(sentimentScore) && sentimentScore <= 40) {
            return "情绪偏弱，今天先控制回撤；只有缩量止跌并出现明确主线，才考虑提高进攻性。";
        }
        List<PreMarketEventImpactResp> events = reportEvents(context);
        if (CollUtil.isNotEmpty(events) && CollUtil.isNotEmpty(events.get(0).getThemes())) {
            String primaryDirection = events.get(0).getThemes().get(0);
            return primaryDirection + "受隔夜事件催化，但市场仍按结构性机会定价；"
                    + "只有板块成交与个股扩散共振，催化才算主线。";
        }
        String commandJudgement = resolveMarketJudgement(context.getCommand(), market);
        if (isUsefulText(commandJudgement)) {
            return commandJudgement + "；只做开盘后被量价确认的方向。";
        }
        return "今天不预设指数单边方向，只交易开盘后被量价确认的事件线。";
    }

    private String resolvePrimaryRisk(DailyPreMarketReportContextBO context) {
        for (PortfolioSummaryResp portfolio : context.getPortfolios()) {
            PortfolioBriefResp brief = portfolio.getBrief();
            if (Objects.nonNull(brief) && Objects.nonNull(brief.getMaxWeightPct())
                    && brief.getMaxWeightPct().compareTo(BigDecimal.valueOf(20)) >= 0) {
                return defaultText(portfolio.getName(), "组合") + "单票仓位达到 "
                        + formatPercent(brief.getMaxWeightPct()) + "，判断错误会放大组合回撤。";
            }
        }
        if (CollUtil.isNotEmpty(context.getStockPicks())) {
            PreMarketStockPickBO primaryStockPick = context.getStockPicks().get(0);
            return primaryStockPick.getName() + "未满足执行条件，或" + primaryStockPick.getInvalidation() + "。";
        }
        if (isShrinkingVolume(context.getMarket())) {
            return "缩量环境下，昨日强势方向高开低走，情绪分数与价格表现背离。";
        }
        List<PreMarketEventImpactResp> events = reportEvents(context);
        if (CollUtil.isNotEmpty(events) && "待验证".equals(events.get(0).getDirection())) {
            return "最重要事件尚未形成明确交易方向，盘前映射可能被市场反向解读。";
        }
        return "开盘量价与盘前判断背离，核心方向没有形成持续承接。";
    }

    private List<String> collectFocusDirectionNames(DailyPreMarketReportContextBO context) {
        List<String> names = new ArrayList<>();
        for (PreMarketEventImpactResp event : reportEvents(context)) {
            addDirectionNames(names, event.getThemes());
            if (names.size() >= 3) {
                return names;
            }
        }
        DashboardCommandResp command = context.getCommand();
        if (Objects.nonNull(command) && Objects.nonNull(command.getPreMarketSummary())
                && CollUtil.isNotEmpty(command.getPreMarketSummary().getOpportunityItems())) {
            for (CommandDirectionItemResp direction : command.getPreMarketSummary().getOpportunityItems()) {
                addDirectionName(names, direction.getName());
                if (names.size() >= 3) {
                    return names;
                }
            }
        }
        MorningBriefingResp morning = context.getMorningBriefing();
        if (Objects.nonNull(morning) && CollUtil.isNotEmpty(morning.getMarketThemes())) {
            for (OvernightMarketTheme marketTheme : morning.getMarketThemes()) {
                if (Objects.nonNull(marketTheme.getMedianPctChg())
                        && marketTheme.getMedianPctChg().abs().compareTo(BigDecimal.ONE) >= 0) {
                    addDirectionName(names, marketTheme.getName());
                }
                if (names.size() >= 3) {
                    return names;
                }
            }
        }
        DashboardHomeResp.MarketBlock market = context.getMarket();
        if (Objects.nonNull(market) && CollUtil.isNotEmpty(market.getHotThemeItems())) {
            for (MarketHotThemeItem hotTheme : market.getHotThemeItems()) {
                addDirectionName(names, hotTheme.getName());
                if (names.size() >= 3) {
                    return names;
                }
            }
        }
        return names;
    }

    private List<String> collectRiskDirectionNames(DailyPreMarketReportContextBO context) {
        List<String> names = new ArrayList<>();
        DashboardCommandResp command = context.getCommand();
        if (Objects.isNull(command) || Objects.isNull(command.getPreMarketSummary())
                || CollUtil.isEmpty(command.getPreMarketSummary().getRiskItems())) {
            return names;
        }
        for (CommandDirectionItemResp direction : command.getPreMarketSummary().getRiskItems()) {
            addDirectionName(names, direction.getName());
        }
        return names;
    }

    private void addDirectionNames(List<String> names, List<String> candidates) {
        if (CollUtil.isEmpty(candidates)) {
            return;
        }
        for (String candidate : candidates) {
            addDirectionName(names, candidate);
        }
    }

    private void addDirectionName(List<String> names, String candidate) {
        if (StringUtils.isNotBlank(candidate) && !names.contains(candidate) && names.size() < 3) {
            names.add(candidate);
        }
    }

    private PreMarketEventImpactResp findDirectionEvent(DailyPreMarketReportContextBO context, String directionName) {
        for (PreMarketEventImpactResp event : reportEvents(context)) {
            if (CollUtil.isNotEmpty(event.getThemes()) && event.getThemes().contains(directionName)) {
                return event;
            }
        }
        return null;
    }

    private CommandDirectionItemResp findCommandDirection(DailyPreMarketReportContextBO context,
                                                           String directionName) {
        DashboardCommandResp command = context.getCommand();
        if (Objects.isNull(command) || Objects.isNull(command.getPreMarketSummary())
                || CollUtil.isEmpty(command.getPreMarketSummary().getOpportunityItems())) {
            return null;
        }
        for (CommandDirectionItemResp direction : command.getPreMarketSummary().getOpportunityItems()) {
            if (directionName.equals(direction.getName())) {
                return direction;
            }
        }
        return null;
    }

    private MarketHotThemeItem findHotTheme(DailyPreMarketReportContextBO context, String directionName) {
        DashboardHomeResp.MarketBlock market = context.getMarket();
        if (Objects.isNull(market) || CollUtil.isEmpty(market.getHotThemeItems())) {
            return null;
        }
        for (MarketHotThemeItem hotTheme : market.getHotThemeItems()) {
            if (directionName.equals(hotTheme.getName())) {
                return hotTheme;
            }
        }
        return null;
    }

    private List<PreMarketEventImpactResp> morningEvents(MorningBriefingResp morning) {
        if (Objects.isNull(morning) || Objects.isNull(morning.getNewsPulse())
                || CollUtil.isEmpty(morning.getNewsPulse().getEventImpacts())) {
            return List.of();
        }
        List<PreMarketEventImpactResp> investmentEvents = new ArrayList<>();
        for (PreMarketEventImpactResp event : morning.getNewsPulse().getEventImpacts()) {
            if (Objects.isNull(event) || StringUtils.isBlank(event.getTitle())) {
                continue;
            }
            if ("EMERGENCY".equals(event.getEventType())
                    && CollUtil.isEmpty(event.getThemes()) && CollUtil.isEmpty(event.getRelatedCodes())) {
                continue;
            }
            investmentEvents.add(event);
        }
        return investmentEvents;
    }

    private List<PreMarketEventImpactResp> reportEvents(DailyPreMarketReportContextBO context) {
        List<PreMarketEventImpactResp> relevantEvents = new ArrayList<>();
        for (PreMarketEventImpactResp event : morningEvents(context.getMorningBriefing())) {
            if (isReportRelevantEvent(event, context)) {
                relevantEvents.add(event);
            }
        }
        return relevantEvents;
    }

    private DailyPreMarketReportContextBO buildAiReportContext(DailyPreMarketReportContextBO context) {
        MorningBriefingResp morningBriefing = context.getMorningBriefing();
        if (Objects.isNull(morningBriefing) || Objects.isNull(morningBriefing.getNewsPulse())) {
            return context;
        }
        NewsPulseResp newsPulse = morningBriefing.getNewsPulse().toBuilder()
                .eventImpacts(reportEvents(context))
                .build();
        return context.toBuilder()
                .morningBriefing(morningBriefing.toBuilder().newsPulse(newsPulse).build())
                .build();
    }

    private boolean isReportRelevantEvent(PreMarketEventImpactResp event,
                                          DailyPreMarketReportContextBO context) {
        if (CollUtil.isNotEmpty(event.getRelatedCodes())) {
            for (PreMarketStockPickBO stockPick : context.getStockPicks()) {
                if (event.getRelatedCodes().contains(stockPick.getCode())) {
                    return true;
                }
            }
            for (PortfolioSummaryResp portfolio : context.getPortfolios()) {
                if (CollUtil.isEmpty(portfolio.getHoldings())) {
                    continue;
                }
                for (PortfolioHolding holding : portfolio.getHoldings()) {
                    if (Objects.nonNull(holding) && event.getRelatedCodes().contains(holding.getCode())) {
                        return true;
                    }
                }
            }
            return false;
        }
        if (CollUtil.isNotEmpty(event.getThemes())) {
            for (PreMarketStockPickBO stockPick : context.getStockPicks()) {
                if (StringUtils.isNotBlank(stockPick.getDirection())
                        && event.getThemes().contains(stockPick.getDirection())) {
                    return true;
                }
            }
            for (PortfolioSummaryResp portfolio : context.getPortfolios()) {
                if (CollUtil.isEmpty(portfolio.getHoldings())) {
                    continue;
                }
                for (PortfolioHolding holding : portfolio.getHoldings()) {
                    if (Objects.nonNull(holding) && matchesHoldingDirection(holding, event.getThemes())) {
                        return true;
                    }
                }
            }
            return !"ANNOUNCEMENT".equals(event.getEventType())
                    && Objects.nonNull(event.getPriority()) && event.getPriority() >= 4;
        }
        return "MARKET".equals(event.getImpactScope())
                && Objects.nonNull(event.getPriority()) && event.getPriority() >= 4;
    }

    private boolean mentionsIrrelevantEvent(String content, DailyPreMarketReportContextBO context) {
        List<PreMarketEventImpactResp> relevantEvents = reportEvents(context);
        for (PreMarketEventImpactResp event : morningEvents(context.getMorningBriefing())) {
            if (relevantEvents.contains(event)) {
                continue;
            }
            if (StringUtils.isNotBlank(event.getTitle()) && content.contains(event.getTitle())) {
                return true;
            }
        }
        return false;
    }

    private boolean isShrinkingVolume(DashboardHomeResp.MarketBlock market) {
        return Objects.nonNull(market) && ("缩量".equals(market.getVolumeTrend())
                || (StringUtils.isNotBlank(market.getVolumeLabel()) && market.getVolumeLabel().contains("缩量")));
    }

    private boolean isUsefulText(String text) {
        return StringUtils.isNotBlank(text) && !text.contains(DATA_MISSING)
                && !text.contains("未获取") && !text.contains("暂不据此")
                && !text.contains("待补充");
    }

    private String loadSystemPrompt() {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/daily-pre-market-report.txt");
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            log.warn("盘前研报提示词读取失败，原因={}", ex.getMessage());
            return "仅根据输入事实生成明确的盘前投资观点；没有 A 股传导链的新闻直接省略，禁止编造。";
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

    private boolean isCompleteReport(String content, DailyPreMarketReportContextBO context) {
        if (StringUtils.isBlank(content) || content.length() > 3000
                || content.contains(DATA_MISSING) || content.contains("未获取")
                || content.contains("暂不据此") || content.contains("盘前观察排序")
                || content.contains("开仓门禁") || content.contains("决策评分")
                || mentionsIrrelevantEvent(content, context)) {
            return false;
        }
        if (!content.startsWith("今日投资机会｜")
                || content.contains("关键变量")
                || content.contains("S 级") || content.contains("A 级") || content.contains("B 级")) {
            return false;
        }
        List<String> requiredSections = List.of(
                "核心观点：",
                "最大风险：",
                "01｜市场状态",
                "05｜开盘剧本",
                "偏强｜",
                "震荡｜",
                "转弱｜");
        for (String requiredSection : requiredSections) {
            if (!content.contains(requiredSection)) {
                return false;
            }
        }
        String reportTitle = content.lines().findFirst().orElse("");
        String coreView = "";
        for (String contentLine : content.split("\\R")) {
            if (contentLine.startsWith("核心观点：")) {
                coreView = contentLine.substring(5);
                break;
            }
        }
        if (StringUtils.isBlank(coreView)
                || containsKnownStock(reportTitle, context) || containsKnownStock(coreView, context)
                || containsEventHeadline(reportTitle, context) || containsEventHeadline(coreView, context)) {
            return false;
        }
        if (CollUtil.isNotEmpty(context.getStockPicks())) {
            if (!reportTitle.startsWith("今日投资机会｜") || !reportTitle.contains(" · ")
                    || !content.contains("首选个股：")
                    || !content.contains("03｜个股推荐")) {
                return false;
            }
            int stockPickStart = content.indexOf("03｜个股推荐");
            int stockPickEnd = content.indexOf("04｜持仓应对", stockPickStart);
            if (stockPickEnd < 0) {
                stockPickEnd = content.indexOf("05｜开盘剧本", stockPickStart);
            }
            String stockPickContent = content.substring(stockPickStart,
                    stockPickEnd > stockPickStart ? stockPickEnd : content.length());
            for (PreMarketStockPickBO stockPick : context.getStockPicks()) {
                String matchedStockLine = null;
                for (String stockPickLine : stockPickContent.split("\\R")) {
                    String stockPickBody = stockPickLine.replaceFirst("^\\d+[.、]\\s*", "");
                    if (!stockPickBody.equals(stockPickLine)
                            && (stockPickBody.startsWith(stockPick.getName() + " " + stockPick.getCode() + "｜")
                            || stockPickBody.startsWith(stockPick.getName() + " " + stockPick.getCode() + "|"))) {
                        matchedStockLine = stockPickLine;
                        break;
                    }
                }
                if (StringUtils.isBlank(matchedStockLine)
                        || !matchedStockLine.contains("级别：" + reportField(stockPick.getLevel()))
                        || !matchedStockLine.contains("观点：" + reportField(stockPick.getOpinion()))
                        || !matchedStockLine.contains("依据：" + reportField(stockPick.getEvidence()))
                        || !matchedStockLine.contains("触发：" + reportField(stockPick.getTrigger()))
                        || !matchedStockLine.contains("失效：" + reportField(stockPick.getInvalidation()))) {
                    return false;
                }
                if (Objects.nonNull(stockPick.getSuggestedWeight())
                        && !matchedStockLine.contains("仓位：" + formatPercent(
                        stockPick.getSuggestedWeight().multiply(BigDecimal.valueOf(100))))) {
                    return false;
                }
            }
            int stockPickLineCount = 0;
            for (String stockPickLine : stockPickContent.split("\\R")) {
                if (!stockPickLine.matches("^\\d+[.、].+")) {
                    continue;
                }
                stockPickLineCount++;
                boolean knownStock = false;
                for (PreMarketStockPickBO stockPick : context.getStockPicks()) {
                    String stockPickBody = stockPickLine.replaceFirst("^\\d+[.、]\\s*", "");
                    if (stockPickBody.startsWith(stockPick.getName() + " " + stockPick.getCode() + "｜")
                            || stockPickBody.startsWith(stockPick.getName() + " " + stockPick.getCode() + "|")) {
                        knownStock = true;
                        break;
                    }
                }
                if (!knownStock || !stockPickLine.contains("级别：") || !stockPickLine.contains("观点：")
                        || !stockPickLine.contains("依据：") || !stockPickLine.contains("触发：")
                        || !stockPickLine.contains("失效：")) {
                    return false;
                }
            }
            if (stockPickLineCount != context.getStockPicks().size()) {
                return false;
            }
        } else if (CollUtil.isNotEmpty(collectFocusDirectionNames(context))) {
            if (!content.contains("03｜投资机会")
                    || !content.contains("确认：") || !content.contains("失效：")) {
                return false;
            }
            int opportunityStart = content.indexOf("03｜投资机会");
            int opportunityEnd = content.indexOf("04｜持仓应对", opportunityStart);
            if (opportunityEnd < 0) {
                opportunityEnd = content.indexOf("05｜开盘剧本", opportunityStart);
            }
            String opportunityContent = content.substring(opportunityStart,
                    opportunityEnd > opportunityStart ? opportunityEnd : content.length());
            List<String> catalystTexts = new ArrayList<>();
            for (String opportunityLine : opportunityContent.split("\\R")) {
                int catalystStart = opportunityLine.indexOf("催化：");
                int catalystEnd = opportunityLine.indexOf("；确认：");
                if (catalystStart < 0 || catalystEnd <= catalystStart) {
                    continue;
                }
                String catalystText = opportunityLine.substring(catalystStart + 3, catalystEnd).trim();
                if (StringUtils.isNotBlank(catalystText) && catalystTexts.contains(catalystText)) {
                    return false;
                }
                catalystTexts.add(catalystText);
            }
        }
        if (CollUtil.isNotEmpty(context.getPortfolios()) && !content.contains("04｜持仓应对")) {
            return false;
        }
        for (PortfolioSummaryResp portfolio : context.getPortfolios()) {
            if (CollUtil.isEmpty(portfolio.getHoldings())) {
                continue;
            }
            for (PortfolioHolding holding : portfolio.getHoldings()) {
                String holdingIdentity = StringUtils.isNotBlank(holding.getCode())
                        ? holding.getCode() : holding.getName();
                if (StringUtils.isBlank(holdingIdentity) || !content.contains(holdingIdentity)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean containsKnownStock(String reportText, DailyPreMarketReportContextBO context) {
        for (PreMarketStockPickBO stockPick : context.getStockPicks()) {
            if (reportText.contains(stockPick.getName()) || reportText.contains(stockPick.getCode())) {
                return true;
            }
        }
        for (PortfolioSummaryResp portfolio : context.getPortfolios()) {
            if (CollUtil.isEmpty(portfolio.getHoldings())) {
                continue;
            }
            for (PortfolioHolding holding : portfolio.getHoldings()) {
                if (Objects.nonNull(holding)
                        && ((StringUtils.isNotBlank(holding.getName()) && reportText.contains(holding.getName()))
                        || (StringUtils.isNotBlank(holding.getCode()) && reportText.contains(holding.getCode())))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean containsEventHeadline(String reportText, DailyPreMarketReportContextBO context) {
        for (PreMarketEventImpactResp event : reportEvents(context)) {
            if (StringUtils.isNotBlank(event.getTitle()) && reportText.contains(event.getTitle())) {
                return true;
            }
        }
        return false;
    }

    private String resolveDataLevel(DashboardHomeResp dashboard, List<String> missingData) {
        if (Objects.isNull(dashboard) || Objects.isNull(dashboard.getMarket())
                || Objects.isNull(dashboard.getMorningBriefing())) {
            return "RED";
        }
        String dataLevel = worseLevel(dashboard.getMarket().getDataLevel(),
                dashboard.getMorningBriefing().getDataLevel());
        if (Objects.nonNull(dashboard.getCommand())) {
            dataLevel = worseLevel(dataLevel, dashboard.getCommand().getDataLevel());
        }
        if (CollUtil.isNotEmpty(missingData) && "GREEN".equals(dataLevel)) {
            dataLevel = "YELLOW";
        }
        return dataLevel;
    }

    private String worseLevel(String firstLevel, String secondLevel) {
        int firstRank = dataLevelRank(firstLevel);
        int secondRank = dataLevelRank(secondLevel);
        return firstRank >= secondRank ? normalizeDataLevel(firstLevel) : normalizeDataLevel(secondLevel);
    }

    private int dataLevelRank(String dataLevel) {
        return switch (normalizeDataLevel(dataLevel)) {
            case "RED" -> 3;
            case "YELLOW" -> 2;
            default -> 1;
        };
    }

    private String normalizeDataLevel(String dataLevel) {
        if ("RED".equalsIgnoreCase(dataLevel)) {
            return "RED";
        }
        if ("YELLOW".equalsIgnoreCase(dataLevel)) {
            return "YELLOW";
        }
        return "GREEN";
    }

    private String resolveMarketJudgement(DashboardCommandResp command, DashboardHomeResp.MarketBlock market) {
        if (Objects.nonNull(command) && Objects.nonNull(command.getPreMarketSummary())
                && StringUtils.isNotBlank(command.getPreMarketSummary().getHeadline())) {
            return command.getPreMarketSummary().getHeadline();
        }
        if (Objects.nonNull(market) && StringUtils.isNotBlank(market.getStanceReason())) {
            return market.getStanceReason();
        }
        return DATA_MISSING;
    }

    private LocalDate resolveTradeDate(MorningBriefingResp morningBriefing, LocalDate generatedDate) {
        if (Objects.nonNull(morningBriefing) && Objects.nonNull(morningBriefing.getTradeDate())) {
            return morningBriefing.getTradeDate();
        }
        return TradingCalendar.isTradingDay(generatedDate)
                ? generatedDate : TradingCalendar.nextTradingDay(generatedDate);
    }

    private int countHoldings(List<PortfolioSummaryResp> portfolios) {
        int holdingCount = 0;
        for (PortfolioSummaryResp portfolio : portfolios) {
            holdingCount += CollUtil.isNotEmpty(portfolio.getHoldings())
                    ? portfolio.getHoldings().size() : defaultInteger(portfolio.getPositionCount());
        }
        return holdingCount;
    }

    private String buildCacheKey(Long userId, LocalDate tradeDate) {
        return CACHE_KEY_PREFIX + userId + ":" + tradeDate;
    }

    private List<String> resolveFocusChanges(DailyPreMarketReportResp previousReport, String currentContent,
                                             LocalDate currentTradeDate) {
        if (Objects.isNull(previousReport) || Objects.isNull(previousReport.getTradeDate())
                || !previousReport.getTradeDate().isBefore(currentTradeDate)) {
            return List.of();
        }
        List<String> previousDirections = extractOpportunityDirections(previousReport.getContent());
        List<String> currentDirections = extractOpportunityDirections(currentContent);
        if (CollUtil.isEmpty(previousDirections) && CollUtil.isEmpty(currentDirections)) {
            return List.of();
        }

        List<String> newDirections = new ArrayList<>();
        List<String> continuedDirections = new ArrayList<>();
        for (String currentDirection : currentDirections) {
            if (previousDirections.contains(currentDirection)) {
                continuedDirections.add(currentDirection);
            } else {
                newDirections.add(currentDirection);
            }
        }
        List<String> downgradedDirections = new ArrayList<>();
        for (String previousDirection : previousDirections) {
            if (!currentDirections.contains(previousDirection)) {
                downgradedDirections.add(previousDirection);
            }
        }

        List<String> focusChanges = new ArrayList<>();
        boolean currentStockPicks = currentContent.contains("03｜个股推荐")
                || currentContent.contains("03|个股推荐");
        boolean previousStockPicks = StringUtils.isNotBlank(previousReport.getContent())
                && (previousReport.getContent().contains("03｜个股推荐")
                || previousReport.getContent().contains("03|个股推荐"));
        if (CollUtil.isNotEmpty(newDirections)) {
            focusChanges.add((currentStockPicks ? "新增推荐：" : "新增方向：")
                    + String.join("、", newDirections));
        }
        if (CollUtil.isNotEmpty(continuedDirections)) {
            focusChanges.add((currentStockPicks ? "延续推荐：" : "延续方向：")
                    + String.join("、", continuedDirections));
        }
        if (CollUtil.isNotEmpty(downgradedDirections)) {
            focusChanges.add((previousStockPicks ? "退出推荐：" : "降级方向：")
                    + String.join("、", downgradedDirections));
        }
        return focusChanges;
    }

    private List<String> extractOpportunityDirections(String reportContent) {
        if (StringUtils.isBlank(reportContent)) {
            return List.of();
        }
        List<String> directions = new ArrayList<>();
        boolean opportunitySection = false;
        for (String rawLine : reportContent.split("\\R")) {
            String line = rawLine.trim();
            if (line.startsWith("03｜") || line.startsWith("03|")) {
                opportunitySection = true;
                continue;
            }
            if (opportunitySection && line.matches("^\\d{2}[｜|].+")) {
                break;
            }
            if (!opportunitySection || !line.matches("^\\d+[.、].+")) {
                continue;
            }
            int separatorIndex = Math.max(line.indexOf('｜'), line.indexOf('|'));
            if (separatorIndex < 0) {
                continue;
            }
            String direction = line.substring(0, separatorIndex)
                    .replaceFirst("^\\d+[.、]\\s*", "")
                    .trim();
            if (StringUtils.isNotBlank(direction) && !directions.contains(direction)) {
                directions.add(direction);
            }
        }
        return directions;
    }

    private String resolveWatchlistGroup() {
        return StringUtils.isNotBlank(botProperties.getWatchlistGroup())
                ? botProperties.getWatchlistGroup().trim() : DEFAULT_WATCHLIST_GROUP;
    }

    private List<String> distinctTexts(List<String> texts) {
        List<String> distinctTexts = new ArrayList<>();
        for (String text : texts) {
            if (StringUtils.isNotBlank(text) && !distinctTexts.contains(text)) {
                distinctTexts.add(text);
            }
        }
        return distinctTexts;
    }

    private String reportField(String value) {
        if (StringUtils.isBlank(value)) {
            return "当日系统筛选事实";
        }
        return value.replace('\n', ' ')
                .replace('\r', ' ')
                .replace('；', '，')
                .replace(';', '，')
                .replace('｜', '，')
                .replace('|', '，')
                .trim();
    }

    private String holdingStatus(PortfolioHolding holding) {
        if (Objects.nonNull(holding.getMarketPrice()) && Objects.nonNull(holding.getStopLoss())
                && holding.getMarketPrice().compareTo(holding.getStopLoss()) <= 0) {
            return "高风险";
        }
        if ((Objects.nonNull(holding.getPctChg()) && holding.getPctChg().compareTo(BigDecimal.valueOf(-3)) <= 0)
                || "RICH".equalsIgnoreCase(holding.getValuationLevel())) {
            return "风险观察";
        }
        if (Objects.nonNull(holding.getPctChg()) && holding.getPctChg().signum() > 0
                && !containsWeak(holding.getTechSummary())) {
            return "正向关注";
        }
        return "中性观察";
    }

    private boolean containsWeak(String text) {
        return StringUtils.isNotBlank(text) && (text.contains("弱") || text.contains("空头") || text.contains("破位"));
    }

    private String sentimentLabel(Integer score) {
        if (Objects.isNull(score)) {
            return DATA_MISSING;
        }
        if (score < 20) {
            return "极度恐慌";
        }
        if (score < 40) {
            return "偏弱";
        }
        if (score < 60) {
            return "中性";
        }
        if (score < 80) {
            return "偏强";
        }
        return "极度亢奋";
    }

    private String formatPercent(BigDecimal value) {
        return Objects.nonNull(value) ? formatNumber(value) + "%" : DATA_MISSING;
    }

    private String formatNumber(BigDecimal value) {
        return Objects.nonNull(value) ? value.stripTrailingZeros().toPlainString() : DATA_MISSING;
    }

    private int defaultInteger(Integer value) {
        return Objects.nonNull(value) ? value : 0;
    }

    private String defaultText(String value, String defaultValue) {
        if (StringUtils.isNotBlank(value)) {
            return value;
        }
        return StringUtils.isNotBlank(defaultValue) ? defaultValue : DATA_MISSING;
    }
}
