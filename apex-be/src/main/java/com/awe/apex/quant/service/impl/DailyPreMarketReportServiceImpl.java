package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.util.JsonUtils;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.ai.KimiChatClient;
import com.awe.apex.quant.bot.config.ApexBotProperties;
import com.awe.apex.quant.cache.RedisCacheService;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.bo.DailyPreMarketReportContextBO;
import com.awe.apex.quant.domain.dto.DailyPreMarketReportResp;
import com.awe.apex.quant.domain.dto.DashboardCommandResp;
import com.awe.apex.quant.domain.dto.DashboardHomeResp;
import com.awe.apex.quant.domain.dto.ExternalMarketItemResp;
import com.awe.apex.quant.domain.dto.MorningBriefingResp;
import com.awe.apex.quant.domain.dto.ObservePoolResp;
import com.awe.apex.quant.domain.dto.OvernightMarketQuote;
import com.awe.apex.quant.domain.dto.PortfolioBriefResp;
import com.awe.apex.quant.domain.dto.PortfolioSummaryResp;
import com.awe.apex.quant.domain.dto.PreMarketEventImpactResp;
import com.awe.apex.quant.domain.entity.PortfolioHolding;
import com.awe.apex.quant.market.TradingCalendar;
import com.awe.apex.quant.service.IDailyPreMarketReportService;
import com.awe.apex.quant.service.IDashboardService;
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

    private static final String CACHE_KEY_PREFIX = "apex:daily-pre-market-report:latest:v1:";
    private static final Duration CACHE_TTL = Duration.ofHours(20);
    private static final String DEFAULT_WATCHLIST_GROUP = "我的自选";
    private static final String DATA_MISSING = "数据暂缺";

    @Resource
    private ApexUserContext userContext;

    @Resource
    private IDashboardService dashboardService;

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
                .portfolios(portfolios)
                .missingData(missingData)
                .build();

        String reportContent = null;
        String reportSource = "RULE";
        if (kimiChatClient.available()) {
            try {
                String userPrompt = "以下 JSON 是本次研报唯一允许使用的事实与数据上下文：\n"
                        + JsonUtils.toJsonString(reportContext);
                String modelContent = kimiChatClient.chat(loadSystemPrompt(), userPrompt, 6000);
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
            reportContent = buildRuleReport(reportContext);
        }

        DashboardHomeResp.MarketBlock market = Objects.nonNull(dashboard) ? dashboard.getMarket() : null;
        String dataLevel = resolveDataLevel(dashboard, missingData);
        DailyPreMarketReportResp report = DailyPreMarketReportResp.builder()
                .tradeDate(tradeDate)
                .generatedAt(generatedAt)
                .marketDataAsOf(Objects.nonNull(market) ? market.getAsOf() : null)
                .marketStatus(resolveMarketStatus(dashboard))
                .sentimentScore(sentimentScore)
                .marketJudgement(resolveMarketJudgement(dashboard))
                .dataLevel(dataLevel)
                .reportSource(reportSource)
                .portfolioCount(portfolios.size())
                .holdingCount(holdingCount)
                .missingData(missingData)
                .content(reportContent)
                .build();
        redisCacheService.put(buildCacheKey(userId, tradeDate), report, CACHE_TTL);
        log.info("盘前研报生成完成，用户编号={}，交易日={}，来源={}，数据等级={}，组合数={}，持仓数={}",
                userId, tradeDate, reportSource, dataLevel, portfolios.size(), holdingCount);
        return report;
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

    private String buildRuleReport(DailyPreMarketReportContextBO context) {
        DashboardHomeResp.MarketBlock market = context.getMarket();
        MorningBriefingResp morning = context.getMorningBriefing();
        StringBuilder report = new StringBuilder();
        report.append("Apex 每日盘前研报\n")
                .append("日期：").append(context.getTradeDate()).append("\n")
                .append("发布时间：盘前\n")
                .append("市场状态：").append(resolveMarketStatus(market)).append("\n")
                .append("Apex 情绪指数：")
                .append(Objects.nonNull(context.getSentimentScore()) ? context.getSentimentScore() + " / 100" : DATA_MISSING)
                .append("\n")
                .append("今日市场判断：").append(resolveMarketJudgement(context.getCommand(), market)).append("\n\n")
                .append("01｜昨日 A 股复盘\n");
        if (Objects.isNull(market)) {
            report.append(DATA_MISSING).append("：市场简报未生成。\n");
        } else {
            appendTextItems(report, market.getIndexLines(), "指数");
            report.append("成交量：").append(defaultText(market.getIndexVolumeText(), DATA_MISSING)).append("\n")
                    .append("涨跌家数：上涨 ").append(formatInteger(market.getBreadthUp()))
                    .append("，下跌 ").append(formatInteger(market.getBreadthDown()))
                    .append("，平盘 ").append(formatInteger(market.getBreadthFlat())).append("\n")
                    .append("涨停 / 跌停：").append(formatInteger(market.getLimitUpCount()))
                    .append(" / ").append(formatInteger(market.getLimitDownCount())).append("\n")
                    .append("市场风格：").append(defaultText(market.getStanceReason(), DATA_MISSING)).append("\n")
                    .append("核心题材：").append(joinTexts(market.getHotThemes())).append("\n")
                    .append("昨日市场一句话总结：").append(defaultText(market.getPositionAdvice(), DATA_MISSING)).append("\n");
        }

        report.append("\n02｜隔夜全球市场\n");
        if (Objects.isNull(morning)) {
            report.append(DATA_MISSING).append("：隔夜市场晨报未生成。\n");
        } else {
            appendQuotes(report, "美股", morning.getIndexQuotes());
            appendQuotes(report, "亚太", morning.getAsiaQuotes());
            if (CollUtil.isEmpty(morning.getExternalMarketItems())) {
                report.append("黄金 / 原油 / 人民币 / 美债 / 美元：").append(DATA_MISSING).append("\n");
            } else {
                for (ExternalMarketItemResp item : morning.getExternalMarketItems()) {
                    report.append(defaultText(item.getName(), "外围指标")).append("：");
                    if (item.isAvailable()) {
                        report.append(formatNumber(item.getLatestPrice()))
                                .append("，涨跌 ").append(formatPercent(item.getPctChg()))
                                .append("，报价时间 ").append(defaultText(Objects.toString(item.getQuoteTime(), null), DATA_MISSING));
                    } else {
                        report.append(DATA_MISSING);
                    }
                    report.append("；A 股影响：").append(defaultText(item.getAShareImpact(), DATA_MISSING)).append("\n");
                }
            }
            report.append("隔夜市场对 A 股的影响：").append(defaultText(morning.getSummary(), DATA_MISSING)).append("\n");
        }

        report.append("\n03｜今日重要消息\n");
        List<PreMarketEventImpactResp> events = Objects.nonNull(morning) && Objects.nonNull(morning.getNewsPulse())
                ? morning.getNewsPulse().getEventImpacts() : List.of();
        if (CollUtil.isEmpty(events)) {
            report.append(DATA_MISSING).append("：尚无已分类并可核验的重要消息。\n");
        } else {
            for (PreMarketEventImpactResp event : events) {
                report.append(eventLevel(event)).append("｜")
                        .append(defaultText(event.getTitle(), DATA_MISSING)).append("｜影响方向：")
                        .append(defaultText(event.getDirection(), "待确认")).append("｜影响对象：")
                        .append(resolveEventTargets(event)).append("｜依据：")
                        .append(defaultText(event.getImpactExplanation(), event.getSummary())).append("｜核验状态：")
                        .append(defaultText(event.getVerificationStatus(), "待确认")).append("\n");
            }
        }

        report.append("\n04｜资金面\n")
                .append("市场资金：").append(Objects.nonNull(market) ? defaultText(market.getVolumeLabel(), DATA_MISSING) : DATA_MISSING).append("\n")
                .append("行业资金：").append(DATA_MISSING).append("\n")
                .append("ETF / 两融 / 外资：").append(DATA_MISSING).append("\n")
                .append("资金风格判断：缺少独立资金流数据，不根据单一涨跌推断资金流向。\n")
                .append("\n05｜市场情绪\n")
                .append("Apex 情绪指数：")
                .append(Objects.nonNull(context.getSentimentScore()) ? context.getSentimentScore() + " / 100" : DATA_MISSING)
                .append("\n情绪状态：").append(sentimentLabel(context.getSentimentScore())).append("\n")
                .append("情绪依据：使用已同步的涨跌家数、涨跌停和消息面结构；缺失项不按中性计入。\n");

        report.append("\n06｜今日重点方向 TOP 5\n");
        if (Objects.isNull(market) || CollUtil.isEmpty(market.getHotThemeItems())) {
            report.append(DATA_MISSING).append("：没有足够数据形成高质量方向排序。\n");
        } else {
            int themeRank = 0;
            for (var theme : market.getHotThemeItems()) {
                themeRank++;
                report.append(themeRank).append(". ").append(theme.getName())
                        .append("｜关注级别：").append(themeRank == 1 ? "核心主线" : "重点关注")
                        .append("｜近期涨跌：").append(formatPercent(theme.getPctChg()))
                        .append("｜盘中观察：开盘强度、成交持续性与龙头是否高开低走")
                        .append("｜失效条件：板块放量转弱且核心股失去承接")
                        .append("｜风险：资金与事件催化数据暂缺\n");
                if (themeRank >= 5) {
                    break;
                }
            }
        }

        appendPortfolioSection(report, context.getPortfolios());
        appendObservePoolSection(report, context.getObservePool());
        appendPortfolioRiskSection(report, context.getPortfolios());

        report.append("\n10｜今日策略判断\n")
                .append("市场环境：").append(resolveMarketStatus(market)).append("\n")
                .append("置信度：").append(DATA_MISSING).append("\n")
                .append("更适合：等待开盘量价和主线持续性确认后，按既有风控条件观察。\n")
                .append("不适合：仅凭隔夜单一指标追涨，或在关键数据缺失时扩大仓位。\n")
                .append("盘前核心判断：").append(resolveMarketJudgement(context.getCommand(), market)).append("\n")
                .append("\n11｜开盘后 30 分钟观察\n")
                .append("如果指数、上涨家数和核心方向量价同步增强 → 偏多。\n")
                .append("如果指数震荡、涨跌家数接近且主线轮动 → 中性。\n")
                .append("如果核心方向高开低走、下跌家数扩大且跌停增加 → 转谨慎。\n")
                .append("\nApex 今日结论\n")
                .append("一句话：").append(resolveMarketJudgement(context.getCommand(), market)).append("\n")
                .append("三个最重要的方向：").append(Objects.nonNull(market) ? joinTexts(market.getHotThemes()) : DATA_MISSING).append("\n")
                .append("三个最大风险：数据缺口、开盘量价背离、核心方向高开低走。\n")
                .append("今天最值得观察的一件事：开盘后 30 分钟，市场广度是否与核心方向同步。\n")
                .append("仅供研究，不构成投资建议。");
        return report.toString();
    }

    private void appendPortfolioSection(StringBuilder report, List<PortfolioSummaryResp> portfolios) {
        report.append("\n07｜Apex 当前组合\n");
        if (CollUtil.isEmpty(portfolios)) {
            report.append(DATA_MISSING).append("：没有可分析的活跃组合。\n");
            return;
        }
        for (PortfolioSummaryResp portfolio : portfolios) {
            report.append("组合：").append(defaultText(portfolio.getName(), "未命名组合"))
                    .append("｜持仓 ").append(defaultInteger(portfolio.getPositionCount()))
                    .append(" 只｜总权益 ").append(formatNumber(portfolio.getTotalEquity()))
                    .append("｜今日盈亏 ").append(formatNumber(portfolio.getTodayPnl())).append("\n");
            if (CollUtil.isEmpty(portfolio.getHoldings())) {
                report.append("- 无持仓。\n");
                continue;
            }
            for (PortfolioHolding holding : portfolio.getHoldings()) {
                report.append("- ").append(holdingStatus(holding)).append(" ")
                        .append(defaultText(holding.getName(), holding.getCode())).append(" ")
                        .append(defaultText(holding.getCode(), DATA_MISSING))
                        .append("｜行业 ").append(defaultText(holding.getIndustry(), DATA_MISSING))
                        .append("｜最新可用价格 ").append(formatNumber(holding.getMarketPrice()))
                        .append("｜行情时间 ").append(defaultText(Objects.toString(holding.getQuoteTime(), null), DATA_MISSING))
                        .append("｜成本 ").append(formatNumber(holding.getCostPrice()))
                        .append("｜权重 ").append(formatPercent(holding.getWeightPct()))
                        .append("｜累计盈亏 ").append(formatPercent(Objects.nonNull(holding.getPnlPct())
                                ? holding.getPnlPct().multiply(BigDecimal.valueOf(100)) : null))
                        .append("｜趋势 ").append(defaultText(holding.getTechSummary(), DATA_MISSING))
                        .append("｜主线 ").append(joinTexts(holding.getThemeTags()))
                        .append("｜资金/最新消息/催化 ").append(DATA_MISSING)
                        .append("｜风险 ").append(defaultText(holding.getAdvice(), holding.getValuationSummary())).append("\n");
            }
        }
    }

    private void appendObservePoolSection(StringBuilder report, List<ObservePoolResp> observePool) {
        report.append("\n08｜Apex 今日观察池\n");
        if (CollUtil.isEmpty(observePool)) {
            report.append("当前没有达到可执行或接近触发条件的高质量标的。\n");
        } else {
            int count = 0;
            for (ObservePoolResp item : observePool) {
                count++;
                report.append(count).append(". ").append(defaultText(item.getName(), item.getCode()))
                        .append("｜方向 ").append(defaultText(item.getSetupStyle(), item.getSide()))
                        .append("｜关注理由 ").append(defaultText(item.getReason(), joinTexts(item.getPickReasons())))
                        .append("｜催化/资金 ").append(DATA_MISSING)
                        .append("｜技术位置 ").append(defaultText(item.getTechSummary(), DATA_MISSING))
                        .append("｜今日观察点 ").append(defaultText(item.getTriggerLabel(), item.getGuideText()))
                        .append("｜风险 ").append(joinTexts(item.getRiskFlags())).append("\n");
                if (count >= 15) {
                    break;
                }
            }
        }
        report.append("观察不等于推荐买入。\n");
    }

    private void appendPortfolioRiskSection(StringBuilder report, List<PortfolioSummaryResp> portfolios) {
        report.append("\n09｜组合风险\n");
        String largestRisk = DATA_MISSING;
        BigDecimal largestWeight = null;
        for (PortfolioSummaryResp portfolio : portfolios) {
            PortfolioBriefResp brief = portfolio.getBrief();
            if (Objects.isNull(brief)) {
                continue;
            }
            report.append(defaultText(portfolio.getName(), "组合")).append("：")
                    .append(defaultText(brief.getSummary(), DATA_MISSING))
                    .append("；单票最高仓位 ").append(formatPercent(brief.getMaxWeightPct()))
                    .append("；主题集中 ").append(defaultText(brief.getTopTheme(), DATA_MISSING))
                    .append(" ").append(formatPercent(brief.getTopThemePct())).append("。\n");
            if (Objects.nonNull(brief.getMaxWeightPct())
                    && (Objects.isNull(largestWeight) || brief.getMaxWeightPct().compareTo(largestWeight) > 0)) {
                largestWeight = brief.getMaxWeightPct();
                largestRisk = "组合“" + portfolio.getName() + "”的单票最高仓位为 "
                        + formatPercent(brief.getMaxWeightPct()) + "，代码 "
                        + defaultText(brief.getMaxWeightCode(), DATA_MISSING);
            }
        }
        report.append("行业、规模、成长/价值、高波动和相关性暴露若未在组合简报中提供，则结论边界为数据暂缺。\n")
                .append("Apex 当前最大风险：").append(largestRisk).append("。\n");
    }

    private void appendQuotes(StringBuilder report, String label, List<OvernightMarketQuote> quotes) {
        report.append(label).append("：");
        if (CollUtil.isEmpty(quotes)) {
            report.append(DATA_MISSING).append("\n");
            return;
        }
        List<String> quoteTexts = new ArrayList<>();
        for (OvernightMarketQuote quote : quotes) {
            quoteTexts.add(defaultText(quote.getName(), quote.getSymbol()) + " " + formatPercent(quote.getPctChg())
                    + "（" + defaultText(Objects.toString(quote.getQuoteTime(), null), "时间暂缺") + "）");
        }
        report.append(String.join("；", quoteTexts)).append("\n");
    }

    private void appendTextItems(StringBuilder report, List<String> items, String label) {
        report.append(label).append("：").append(joinTexts(items)).append("\n");
    }

    private String loadSystemPrompt() {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/daily-pre-market-report.txt");
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            log.warn("盘前研报提示词读取失败，原因={}", ex.getMessage());
            return "仅根据输入数据生成 Apex 每日盘前研报；缺失数据写数据暂缺，禁止编造。";
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
        if (StringUtils.isBlank(content)) {
            return false;
        }
        List<String> requiredSections = List.of(
                "Apex 每日盘前研报",
                "01｜昨日 A 股复盘",
                "02｜隔夜全球市场",
                "03｜今日重要消息",
                "04｜资金面",
                "05｜市场情绪",
                "06｜今日重点方向 TOP 5",
                "07｜Apex 当前组合",
                "08｜Apex 今日观察池",
                "09｜组合风险",
                "10｜今日策略判断",
                "11｜开盘后 30 分钟观察",
                "Apex 今日结论");
        for (String requiredSection : requiredSections) {
            if (!content.contains(requiredSection)) {
                return false;
            }
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

    private String resolveMarketStatus(DashboardHomeResp dashboard) {
        return resolveMarketStatus(Objects.nonNull(dashboard) ? dashboard.getMarket() : null);
    }

    private String resolveMarketStatus(DashboardHomeResp.MarketBlock market) {
        return Objects.nonNull(market) ? defaultText(market.getStance(), DATA_MISSING) : DATA_MISSING;
    }

    private String resolveMarketJudgement(DashboardHomeResp dashboard) {
        return resolveMarketJudgement(Objects.nonNull(dashboard) ? dashboard.getCommand() : null,
                Objects.nonNull(dashboard) ? dashboard.getMarket() : null);
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

    private String eventLevel(PreMarketEventImpactResp event) {
        if ("MARKET".equals(event.getImpactScope())) {
            return "S 级";
        }
        if ("THEME".equals(event.getImpactScope())) {
            return "A 级";
        }
        return "B 级";
    }

    private String resolveEventTargets(PreMarketEventImpactResp event) {
        List<String> targets = new ArrayList<>();
        if (CollUtil.isNotEmpty(event.getThemes())) {
            targets.addAll(event.getThemes());
        }
        if (CollUtil.isNotEmpty(event.getRelatedCodes())) {
            targets.addAll(event.getRelatedCodes());
        }
        return joinTexts(targets);
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

    private String joinTexts(List<String> texts) {
        if (CollUtil.isEmpty(texts)) {
            return DATA_MISSING;
        }
        List<String> validTexts = new ArrayList<>();
        for (String text : texts) {
            if (StringUtils.isNotBlank(text)) {
                validTexts.add(text.trim());
            }
        }
        return CollUtil.isNotEmpty(validTexts) ? String.join("、", validTexts) : DATA_MISSING;
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

    private String formatInteger(Integer value) {
        return Objects.nonNull(value) ? value.toString() : DATA_MISSING;
    }

    private String defaultText(String value, String defaultValue) {
        if (StringUtils.isNotBlank(value)) {
            return value;
        }
        return StringUtils.isNotBlank(defaultValue) ? defaultValue : DATA_MISSING;
    }
}
