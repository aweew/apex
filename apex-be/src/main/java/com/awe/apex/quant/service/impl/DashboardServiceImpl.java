package com.awe.apex.quant.service.impl;

import com.awe.apex.quant.market.TradingCalendar;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.cache.RedisCacheService;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.bo.DashboardCommandContextBO;
import com.awe.apex.quant.domain.dto.DashboardCommandResp;
import com.awe.apex.quant.domain.dto.DashboardHomeResp;
import com.awe.apex.quant.domain.dto.DashboardResp;
import com.awe.apex.quant.domain.dto.DecisionItemResp;
import com.awe.apex.quant.domain.dto.DecisionTodayResp;
import com.awe.apex.quant.domain.dto.EquityPointResp;
import com.awe.apex.quant.domain.dto.IndustryPnlResp;
import com.awe.apex.quant.domain.dto.MarketBriefingResp;
import com.awe.apex.quant.domain.dto.MarketBreadthForecastResp;
import com.awe.apex.quant.domain.dto.MarketTipItem;
import com.awe.apex.quant.domain.dto.MorningBriefingResp;
import com.awe.apex.quant.domain.dto.OpeningAuctionResp;
import com.awe.apex.quant.domain.dto.ObservePoolResp;
import com.awe.apex.quant.domain.dto.PaperMetricsResp;
import com.awe.apex.quant.domain.dto.RiskOverviewResp;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.entity.LimitUpPool;
import com.awe.apex.quant.domain.entity.PaperAccount;
import com.awe.apex.quant.domain.entity.PaperOrder;
import com.awe.apex.quant.domain.entity.PaperPosition;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.domain.entity.StrategySignalEntity;
import com.awe.apex.quant.domain.entity.Watchlist;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.LimitUpPoolMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.mapper.StrategySignalMapper;
import com.awe.apex.quant.mapper.WatchlistMapper;
import com.awe.apex.quant.paper.PaperEquityCalculator;
import com.awe.apex.quant.service.IDailyActionService;
import com.awe.apex.quant.service.IDataSyncJobService;
import com.awe.apex.quant.service.IDashboardCommandService;
import com.awe.apex.quant.service.IDashboardService;
import com.awe.apex.quant.service.IDecisionService;
import com.awe.apex.quant.service.IMarketBriefingService;
import com.awe.apex.quant.service.IMarketBreadthForecastService;
import com.awe.apex.quant.service.IMorningBriefingService;
import com.awe.apex.quant.service.IObservePoolService;
import com.awe.apex.quant.service.IPaperService;
import com.awe.apex.quant.service.IRiskService;
import com.awe.apex.quant.market.OpeningAuctionQuoteClient;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 仪表盘实现
 */
@Slf4j
@Service
public class DashboardServiceImpl implements IDashboardService {

    private static final Duration HOME_CACHE_TTL = Duration.ofSeconds(15);

    @Resource
    private IPaperService paperService;

    @Resource
    private IRiskService riskService;

    @Resource
    private IDailyActionService dailyActionService;

    @Resource
    private StrategySignalMapper strategySignalMapper;

    @Resource
    private StockBasicMapper stockBasicMapper;

    @Resource
    private BarDailyMapper barDailyMapper;

    @Resource
    private IMarketBriefingService marketBriefingService;

    @Resource
    private IMorningBriefingService morningBriefingService;

    @Resource
    private IMarketBreadthForecastService marketBreadthForecastService;

    @Resource
    private OpeningAuctionQuoteClient openingAuctionQuoteClient;

    @Resource
    private IDashboardCommandService dashboardCommandService;

    @Resource
    private IDataSyncJobService dataSyncJobService;

    @Resource
    private LimitUpPoolMapper limitUpPoolMapper;

    @Resource
    private IDecisionService decisionService;

    @Resource
    private IObservePoolService observePoolService;

    @Resource
    private WatchlistMapper watchlistMapper;

    @Resource
    private RedisCacheService redisCacheService;

    @Resource
    private ApexUserContext userContext;

    private static final ExecutorService HOME_POOL = Executors.newFixedThreadPool(5, r -> {
        Thread t = new Thread(r, "dashboard-home");
        t.setDaemon(true);
        return t;
    });

    /**
     * 决策看板首页聚合（轻量路径：不做权益回放/全量观察池估值）
     *
     * @param accountId 账户，可空
     * @param groupName 自选分组，可空
     * @param forceRefresh 是否同步刷新实时行情
     * @return 首页
     */
    @Override
    public DashboardHomeResp home(Long accountId, String groupName, boolean forceRefresh) {
        Long currentUserId = userContext.currentUserId();
        String group = StringUtils.isNotBlank(groupName) ? groupName.trim() : "我的自选";
        String cacheKey = "apex:dashboard:home:" + currentUserId + ":"
                + (Objects.nonNull(accountId) ? accountId : "default") + ":" + group;
        if (!forceRefresh) {
            DashboardHomeResp cached = redisCacheService.get(cacheKey, DashboardHomeResp.class);
            if (Objects.nonNull(cached)) {
                return cached;
            }
        }
        if (forceRefresh) {
            marketBriefingService.invalidateCache();
        }

        // 1. 首页只读取已完成的简报；显式刷新才同步重建实时行情
        MarketBriefingResp briefing = null;
        try {
            briefing = forceRefresh ? marketBriefingService.briefing(true)
                    : marketBriefingService.loadCachedBriefing();
        } catch (Exception ex) {
            log.warn("看板简报加载失败: {}", ex.getMessage());
        }
        if (Objects.isNull(briefing)) {
            briefing = MarketBriefingResp.builder()
                    .asOf(LocalDate.now())
                    .stance("防守")
                    .stanceReason("暂无可用市场简报，请在同步中心刷新行情")
                    .positionAdvice("数据不足：暂缓开仓，先同步指数、板块和涨停池")
                    .dataLevel("RED")
                    .hotThemes(List.of())
                    .hotThemeItems(List.of())
                    .indexes(List.of())
                    .tips(List.of())
                    .build();
        }

        // 2. 并行拉取互不依赖的块：决策、观察告警、账户摘要、数据健康、盘前晨报
        final MarketBriefingResp cachedBriefing = briefing;
        CompletableFuture<DecisionTodayResp> todayFuture = CompletableFuture.supplyAsync(
                () -> userContext.runAsUser(currentUserId, () -> {
                    try {
                        return decisionService.today(LocalDate.now(), group, cachedBriefing);
                    } catch (Exception ex) {
                        return null;
                    }
                }), HOME_POOL);
        CompletableFuture<List<ObservePoolResp>> alertsFuture = CompletableFuture.supplyAsync(
                () -> userContext.runAsUser(currentUserId, () -> {
                    try {
                        return observePoolService.listReadyAlerts(6);
                    } catch (Exception ex) {
                        return List.of();
                    }
                }), HOME_POOL);
        CompletableFuture<DashboardHomeResp.AccountBlock> accountFuture = CompletableFuture.supplyAsync(
                () -> userContext.runAsUser(currentUserId, () -> buildLightAccount(accountId)), HOME_POOL);
        CompletableFuture<DashboardHomeResp.DataHealthBlock> healthFuture = CompletableFuture.supplyAsync(
                () -> userContext.runAsUser(currentUserId, () -> buildLightDataHealth(group)), HOME_POOL);
        CompletableFuture<MorningBriefingResp> morningBriefingFuture = CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return morningBriefingService.latest();
                    } catch (Exception ex) {
                        log.warn("看板盘前晨报加载失败，原因={}", ex.getMessage());
                        return null;
                    }
                }, HOME_POOL)
                .completeOnTimeout(null, 2, TimeUnit.SECONDS);
        CompletableFuture<OpeningAuctionResp> openingAuctionFuture = CompletableFuture
                .supplyAsync(() -> {
                    if (Objects.isNull(openingAuctionQuoteClient)) {
                        return null;
                    }
                    try {
                        return openingAuctionQuoteClient.fetch();
                    } catch (Exception ex) {
                        log.warn("看板集合竞价加载失败，原因={}", ex.getMessage());
                        return null;
                    }
                }, HOME_POOL)
                .completeOnTimeout(null, 2, TimeUnit.SECONDS);

        Integer limitUpCount = resolveLimitUpCount(briefing);
        List<String> tips = new ArrayList<>();
        if (Objects.nonNull(briefing) && CollUtil.isNotEmpty(briefing.getTips())) {
            for (MarketTipItem tip : briefing.getTips()) {
                if (Objects.nonNull(tip) && StringUtils.isNotBlank(tip.getText())) {
                    tips.add(tip.getText());
                    if (tips.size() >= 3) {
                        break;
                    }
                }
            }
        }
        DashboardHomeResp.MarketBlock market = DashboardHomeResp.MarketBlock.builder()
                .asOf(Objects.nonNull(briefing) ? briefing.getAsOf() : null)
                .stance(Objects.nonNull(briefing) ? briefing.getStance() : "防守")
                .stanceScore(Objects.nonNull(briefing) ? briefing.getStanceScore() : null)
                .stanceReason(Objects.nonNull(briefing) ? briefing.getStanceReason()
                        : "市场简报加载失败，请点「刷新行情」重试")
                .positionAdvice(Objects.nonNull(briefing) ? briefing.getPositionAdvice()
                        : "简报不可用：暂缓开仓，先刷新行情")
                .dataLevel(Objects.nonNull(briefing) ? briefing.getDataLevel() : "RED")
                .hotThemes(Objects.nonNull(briefing) && CollUtil.isNotEmpty(briefing.getHotThemes())
                        ? briefing.getHotThemes().subList(0, Math.min(6, briefing.getHotThemes().size()))
                        : List.of())
                .hotThemeItems(Objects.nonNull(briefing) && CollUtil.isNotEmpty(briefing.getHotThemeItems())
                        ? briefing.getHotThemeItems().subList(0, Math.min(6, briefing.getHotThemeItems().size()))
                        : List.of())
                .limitUpCount(limitUpCount)
                .limitDownCount(Objects.nonNull(briefing) ? briefing.getLimitDownCount() : null)
                .breadthUp(Objects.nonNull(briefing) ? briefing.getBreadthUp() : null)
                .breadthDown(Objects.nonNull(briefing) ? briefing.getBreadthDown() : null)
                .breadthFlat(Objects.nonNull(briefing) ? briefing.getBreadthFlat() : null)
                .indexLines(Objects.nonNull(briefing) ? briefing.getIndexLines() : List.of())
                .indexes(Objects.nonNull(briefing) && CollUtil.isNotEmpty(briefing.getIndexes())
                        ? briefing.getIndexes() : List.of())
                .volumeTrend(Objects.nonNull(briefing) ? briefing.getVolumeTrend() : null)
                .volumeVsMa5Pct(Objects.nonNull(briefing) ? briefing.getVolumeVsMa5Pct() : null)
                .volumeLabel(Objects.nonNull(briefing) ? briefing.getVolumeLabel() : null)
                .indexVolume(Objects.nonNull(briefing) ? briefing.getIndexVolume() : null)
                .indexVolumeChange(Objects.nonNull(briefing) ? briefing.getIndexVolumeChange() : null)
                .indexVolumeText(Objects.nonNull(briefing) ? briefing.getIndexVolumeText() : null)
                .tips(tips)
                .effect(Objects.nonNull(briefing) ? briefing.getEffect() : null)
                .build();

        DecisionTodayResp today = todayFuture.join();
        List<DashboardHomeResp.HomeActionItem> topBuys = new ArrayList<>();
        List<DashboardHomeResp.HomeActionItem> topSells = new ArrayList<>();
        int buyCount = 0;
        int sellCount = 0;
        int holdCount = 0;
        int executableCount = 0;
        int valuationCheapCount = 0;
        int valuationFairCount = 0;
        int valuationRichCount = 0;
        int mainlineMatchCount = 0;
        String riskNote = null;
        String stance = Objects.nonNull(briefing) ? briefing.getStance() : null;
        boolean hasToday = false;
        LocalDate actionDate = LocalDate.now();
        if (Objects.nonNull(today)) {
            actionDate = Objects.nonNull(today.getActionDate()) ? today.getActionDate() : actionDate;
            List<DecisionItemResp> buys = Objects.nonNull(today.getBuys()) ? today.getBuys() : List.of();
            List<DecisionItemResp> sells = Objects.nonNull(today.getSells()) ? today.getSells() : List.of();
            List<DecisionItemResp> holds = Objects.nonNull(today.getHolds()) ? today.getHolds() : List.of();
            buyCount = Objects.nonNull(today.getBuyCount()) ? today.getBuyCount() : buys.size();
            sellCount = Objects.nonNull(today.getSellCount()) ? today.getSellCount() : sells.size();
            holdCount = Objects.nonNull(today.getHoldCount()) ? today.getHoldCount() : holds.size();
            executableCount = Objects.nonNull(today.getExecutableCount()) ? today.getExecutableCount() : 0;
            valuationCheapCount = Objects.nonNull(today.getValuationCheapCount()) ? today.getValuationCheapCount() : 0;
            valuationFairCount = Objects.nonNull(today.getValuationFairCount()) ? today.getValuationFairCount() : 0;
            valuationRichCount = Objects.nonNull(today.getValuationRichCount()) ? today.getValuationRichCount() : 0;
            mainlineMatchCount = Objects.nonNull(today.getMainlineMatchCount()) ? today.getMainlineMatchCount() : 0;
            riskNote = today.getRiskNote();
            // 立场以看板实时简报为准，不覆盖为决策落库时的旧快照
            hasToday = Boolean.TRUE.equals(today.getGenerated()) || buyCount + sellCount + holdCount > 0;
            for (DecisionItemResp item : buys) {
                if (topBuys.size() >= 3) {
                    break;
                }
                topBuys.add(toHomeItem(item));
            }
            for (DecisionItemResp item : sells) {
                if (topSells.size() >= 5) {
                    break;
                }
                topSells.add(toHomeItem(item));
            }
        }
        String decisionSummary = hasToday
                ? ("买 " + buyCount + " / 卖 " + sellCount + " / 持有 " + holdCount
                + " · 可执行 " + executableCount
                + " · 低估 " + valuationCheapCount
                + " · 主线 " + mainlineMatchCount)
                : "今日尚无决策，可启动后台生成决策";
        DashboardHomeResp.DecisionBlock decision = DashboardHomeResp.DecisionBlock.builder()
                .actionDate(actionDate)
                .hasToday(hasToday)
                .buyCount(buyCount)
                .sellCount(sellCount)
                .holdCount(holdCount)
                .executableCount(executableCount)
                .valuationCheapCount(valuationCheapCount)
                .valuationFairCount(valuationFairCount)
                .valuationRichCount(valuationRichCount)
                .mainlineMatchCount(mainlineMatchCount)
                .stance(stance)
                .riskNote(riskNote)
                .topBuys(topBuys)
                .topSells(topSells)
                .summary(decisionSummary)
                .build();

        List<ObservePoolResp> observeAlerts = alertsFuture.join();
        if (Objects.isNull(observeAlerts)) {
            observeAlerts = List.of();
        }
        DashboardHomeResp.AccountBlock account = accountFuture.join();
        DashboardHomeResp.DataHealthBlock dataHealth = healthFuture.join();
        if (Objects.isNull(dataHealth)) {
            dataHealth = buildLightDataHealth(group);
        }

        MorningBriefingResp morningBriefing = morningBriefingFuture.join();
        OpeningAuctionResp openingAuction = openingAuctionFuture.join();
        MarketBreadthForecastResp breadthForecast = null;
        if (Objects.nonNull(marketBreadthForecastService)) {
            try {
                breadthForecast = marketBreadthForecastService.loadForDashboard(morningBriefing, briefing);
            } catch (Exception ex) {
                log.warn("看板盘前涨跌比预测加载失败，原因={}", ex.getMessage());
            }
        }
        boolean decisionRunning = false;
        try {
            decisionRunning = dataSyncJobService.isCurrentUserDecisionRunning();
        } catch (Exception ex) {
            log.warn("看板决策任务状态加载失败，用户编号={}，原因={}", currentUserId, ex.getMessage());
        }
        DashboardCommandResp command = null;
        try {
            DashboardCommandContextBO commandContext = DashboardCommandContextBO.builder()
                    .marketBriefing(briefing)
                    .morningBriefing(morningBriefing)
                    .decision(today)
                    .observeAlerts(observeAlerts)
                    .decisionRunning(decisionRunning)
                    .currentTime(LocalDateTime.now())
                    .build();
            command = dashboardCommandService.build(commandContext);
        } catch (Exception ex) {
            log.warn("看板盘前指挥聚合失败，用户编号={}，原因={}", currentUserId, ex.getMessage());
        }
        // 用简报等级校准数据健康（轻量统计可能滞后）
        String briefingLevel = market.getDataLevel();
        if (StringUtils.isNotBlank(briefingLevel) && !"RED".equals(dataHealth.getLevel())) {
            if ("RED".equals(briefingLevel)) {
                dataHealth.setLevel("RED");
                dataHealth.setSuggestion("数据不足，请先同步指数/板块/自选日线后再决策");
            } else if ("YELLOW".equals(briefingLevel) && "GREEN".equals(dataHealth.getLevel())) {
                dataHealth.setLevel("YELLOW");
                dataHealth.setSuggestion("部分数据过期，建议同步后使用");
            }
        }

        String homeMessage = Objects.isNull(briefing)
                ? "市场简报加载失败 · " + decisionSummary
                : market.getStance() + " · " + decisionSummary;
        DashboardHomeResp response = DashboardHomeResp.builder()
                .market(market)
                .morningBriefing(morningBriefing)
                .breadthForecast(breadthForecast)
                .openingAuction(openingAuction)
                .command(command)
                .decision(decision)
                .observeAlerts(observeAlerts)
                .account(account)
                .dataHealth(dataHealth)
                // 权益曲线改由前端延后拉取 overview，避免首屏卡死
                .equityCurve(List.of())
                .message(homeMessage)
                .build();
        redisCacheService.put(cacheKey, response, HOME_CACHE_TTL);
        return response;
    }

    /**
     * 轻量账户摘要：持仓市值 + 现金，跳过风控 N+1 与权益回放
     */
    private DashboardHomeResp.AccountBlock buildLightAccount(Long accountId) {
        try {
            PaperAccount account = paperService.defaultAccount();
            Long id = Objects.nonNull(accountId) ? accountId : account.getId();
            List<PaperPosition> positions = paperService.listPositions(id);
            BigDecimal initCash = Objects.nonNull(account.getInitCash()) ? account.getInitCash() : BigDecimal.valueOf(1000000);
            BigDecimal cash = Objects.nonNull(account.getCash()) ? account.getCash() : BigDecimal.ZERO;
            BigDecimal positionValue = BigDecimal.ZERO;
            if (CollUtil.isNotEmpty(positions)) {
                for (PaperPosition position : positions) {
                    if (Objects.nonNull(position.getMarketValue())) {
                        positionValue = positionValue.add(position.getMarketValue());
                    }
                }
            }
            BigDecimal totalAsset = cash.add(positionValue);
            BigDecimal totalReturn = initCash.signum() == 0 ? BigDecimal.ZERO
                    : totalAsset.subtract(initCash).divide(initCash, 4, RoundingMode.HALF_UP);
            BigDecimal positionRatio = totalAsset.signum() == 0 ? BigDecimal.ZERO
                    : positionValue.divide(totalAsset, 4, RoundingMode.HALF_UP);
            return DashboardHomeResp.AccountBlock.builder()
                    .totalAsset(totalAsset)
                    .totalReturn(totalReturn)
                    .positionRatio(positionRatio)
                    .maxDrawdown(null)
                    .winRate(null)
                    .criticalCount(0)
                    .warnCount(0)
                    .positionCount(CollUtil.isEmpty(positions) ? 0 : positions.size())
                    .build();
        } catch (Exception ex) {
            return DashboardHomeResp.AccountBlock.builder()
                    .criticalCount(0)
                    .warnCount(0)
                    .positionCount(0)
                    .build();
        }
    }

    /**
     * 涨停家数（单次 count，失败返回 null）
     */
    private Integer resolveLimitUpCount(MarketBriefingResp briefing) {
        if (Objects.nonNull(briefing) && Objects.nonNull(briefing.getLimitUpCount())) {
            return briefing.getLimitUpCount();
        }
        try {
            LocalDate luDate = Objects.nonNull(briefing) && Objects.nonNull(briefing.getAsOf())
                    ? briefing.getAsOf() : LocalDate.now();
            Long cnt = limitUpPoolMapper.selectCount(Wrappers.<LimitUpPool>lambdaQuery()
                    .eq(LimitUpPool::getTradeDate, luDate));
            if (Objects.isNull(cnt) || cnt == 0) {
                LimitUpPool latest = limitUpPoolMapper.selectOne(Wrappers.<LimitUpPool>lambdaQuery()
                        .orderByDesc(LimitUpPool::getTradeDate)
                        .last("LIMIT 1"));
                if (Objects.nonNull(latest)
                        && Objects.nonNull(latest.getTradeDate())
                        && !latest.getTradeDate().isBefore(LocalDate.now().minusDays(1))) {
                    cnt = limitUpPoolMapper.selectCount(Wrappers.<LimitUpPool>lambdaQuery()
                            .eq(LimitUpPool::getTradeDate, latest.getTradeDate()));
                }
            }
            return Objects.nonNull(cnt) ? cnt.intValue() : 0;
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 轻量数据健康：只查最新日线日期，不拉 140 日收盘与相对强弱
     */
    private DashboardHomeResp.DataHealthBlock buildLightDataHealth(String group) {
        int stale = 0;
        int empty = 0;
        int total = 0;
        try {
            List<Watchlist> list = watchlistMapper.selectList(Wrappers.<Watchlist>lambdaQuery()
                    .eq(Watchlist::getUserId, userContext.currentUserId())
                    .eq(StringUtils.isNotBlank(group), Watchlist::getGroupName, group)
                    .select(Watchlist::getCode));
            total = list.size();
            if (total > 0) {
                Set<String> codes = new HashSet<>();
                for (Watchlist item : list) {
                    if (Objects.nonNull(item) && StringUtils.isNotBlank(item.getCode())) {
                        codes.add(item.getCode());
                    }
                }
                Map<String, LocalDate> lastBarMap = new HashMap<>();
                if (!codes.isEmpty()) {
                    List<Map<String, Object>> stats = barDailyMapper.selectMaps(Wrappers.<BarDaily>query()
                            .select("code", "MAX(trade_date) AS tradeDate")
                            .in("code", codes)
                            .groupBy("code"));
                    for (Map<String, Object> row : stats) {
                        String code = String.valueOf(row.get("code"));
                        Object tradeDate = row.get("tradeDate");
                        if (Objects.nonNull(tradeDate)) {
                            lastBarMap.put(code, LocalDate.parse(String.valueOf(tradeDate).substring(0, 10)));
                        }
                    }
                }
                LocalDate staleBefore = TradingCalendar.latestTradingDayOnOrBefore(LocalDate.now());
                for (String code : codes) {
                    LocalDate lastBar = lastBarMap.get(code);
                    if (Objects.isNull(lastBar)) {
                        empty++;
                    } else if (lastBar.isBefore(staleBefore)) {
                        stale++;
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("轻量数据健康统计失败: {}", ex.getMessage());
            return DashboardHomeResp.DataHealthBlock.builder()
                    .level("YELLOW")
                    .suggestion("健康检查异常，请稍后刷新或同步数据")
                    .barsStaleCount(0)
                    .barsEmptyCount(0)
                    .watchlistCount(0)
                    .build();
        }
        String level = "GREEN";
        if (total > 0 && empty > total / 2) {
            level = "RED";
        } else if (total > 0 && stale > total / 3) {
            level = "YELLOW";
        } else if (total == 0) {
            level = "YELLOW";
        }
        String suggestion;
        if ("RED".equals(level)) {
            suggestion = "数据不足，请先同步指数/板块/自选日线后再决策";
        } else if ("YELLOW".equals(level)) {
            suggestion = "部分数据过期，建议同步后使用";
        } else {
            suggestion = "数据基本可信，可生成今日决策";
        }
        return DashboardHomeResp.DataHealthBlock.builder()
                .level(level)
                .suggestion(suggestion)
                .barsStaleCount(stale)
                .barsEmptyCount(empty)
                .watchlistCount(total)
                .build();
    }

    private DashboardHomeResp.HomeActionItem toHomeItem(DecisionItemResp item) {
        return DashboardHomeResp.HomeActionItem.builder()
                .code(item.getCode())
                .name(item.getName())
                .action(item.getAction())
                .strategyId(item.getStrategyId())
                .score(item.getScore())
                .suggestedWeight(item.getSuggestedWeight())
                .mainlineMatch(item.getMainlineMatch())
                .mainlineName(item.getMainlineName())
                .valuationLevel(item.getValuationLevel())
                .valuationLabel(item.getValuationLabel())
                .executableHint(item.getExecutableHint())
                .linkHint(item.getLinkHint())
                .reason(item.getReason())
                .exitRule(item.getExitRule())
                .build();
    }

    @Override
    public DashboardResp overview(Long accountId) {
        PaperAccount account = paperService.defaultAccount();
        Long id = Objects.nonNull(accountId) ? accountId : account.getId();
        RiskOverviewResp risk = riskService.overview(id);
        List<PaperPosition> positions = paperService.listPositions(id);
        List<PaperOrder> orders = paperService.listOrders(id);
        List<StrategySignalEntity> signals = strategySignalMapper.selectList(Wrappers.<StrategySignalEntity>lambdaQuery()
                .eq(StrategySignalEntity::getUserId, userContext.currentUserId())
                .ge(StrategySignalEntity::getSignalDate, LocalDate.now().minusDays(5))
                .orderByDesc(StrategySignalEntity::getId)
                .last("limit 50"));

        BigDecimal unrealized = BigDecimal.ZERO;
        for (PaperPosition position : positions) {
            if (Objects.nonNull(position.getPnl())) {
                unrealized = unrealized.add(position.getPnl());
            }
        }
        BigDecimal initCash = Objects.nonNull(account.getInitCash()) ? account.getInitCash() : BigDecimal.valueOf(1000000);
        BigDecimal totalAsset = risk.getTotalAsset();
        BigDecimal totalReturn = initCash.signum() == 0 ? BigDecimal.ZERO
                : totalAsset.subtract(initCash).divide(initCash, 4, RoundingMode.HALF_UP);

        TradeStats tradeStats = calcTradeStats(orders);
        BigDecimal turnoverRate = initCash.signum() == 0 ? BigDecimal.ZERO
                : tradeStats.totalTurnover.divide(initCash, 4, RoundingMode.HALF_UP);
        BigDecimal totalFee = BigDecimal.ZERO;
        for (PaperOrder order : orders) {
            if (Objects.nonNull(order.getFee())) {
                totalFee = totalFee.add(order.getFee());
            }
        }
        BigDecimal feeRate = tradeStats.totalTurnover.signum() == 0 ? BigDecimal.ZERO
                : totalFee.divide(tradeStats.totalTurnover, 6, RoundingMode.HALF_UP);

        PaperEquityCalculator.ReplayResult replay = buildEquityReplay(account, orders, totalAsset);

        PaperMetricsResp metrics = PaperMetricsResp.builder()
                .initCash(initCash)
                .totalAsset(totalAsset)
                .totalReturn(totalReturn)
                .orderCount(orders.size())
                .positionCount(positions.size())
                .unrealizedPnl(unrealized)
                .realizedPnl(tradeStats.realizedPnl)
                .winRate(tradeStats.winRate)
                .closedTradeCount(tradeStats.closedTradeCount)
                .totalTurnover(tradeStats.totalTurnover)
                .turnoverRate(turnoverRate)
                .maxDrawdown(replay.getMaxDrawdown())
                .sharpe(replay.getSharpe())
                .dailyVar95(replay.getDailyVar95())
                .dailyCvar95(replay.getDailyCvar95())
                .calmar(replay.getCalmar())
                .avgHoldDays(tradeStats.avgHoldDays)
                .winStreak(tradeStats.winStreak)
                .lossStreak(tradeStats.lossStreak)
                .drawdownRecoveryDays(replay.getDrawdownRecoveryDays())
                .underwaterRatio(replay.getUnderwaterRatio())
                .rollingSharpe20(calcRollingSharpe(replay.getPoints(), 20))
                .profitFactor(tradeStats.profitFactor)
                .payoffRatio(tradeStats.payoffRatio)
                .expectancy(tradeStats.expectancy)
                .sortino(calcSortino(replay.getPoints()))
                .ulcerIndex(calcUlcerIndex(replay.getPoints()))
                .totalFee(totalFee.setScale(2, RoundingMode.HALF_UP))
                .feeRate(feeRate)
                .omega(calcOmega(replay.getPoints()))
                .maxLosingDays(calcMaxLosingDays(replay.getPoints()))
                .cashDrag(Objects.nonNull(risk.getPositionRatio())
                        ? BigDecimal.ONE.subtract(risk.getPositionRatio()).max(BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP)
                        : BigDecimal.ONE)
                .build();

        MarketBriefingResp briefing = null;
        try {
            briefing = marketBriefingService.briefing();
        } catch (Exception ignored) {
            // 简报失败不影响看板主流程
        }
        Integer limitUpCount = null;
        try {
            LocalDate luDate = Objects.nonNull(briefing) && Objects.nonNull(briefing.getAsOf())
                    ? briefing.getAsOf() : LocalDate.now();
            Long cnt = limitUpPoolMapper.selectCount(Wrappers.<LimitUpPool>lambdaQuery()
                    .eq(LimitUpPool::getTradeDate, luDate));
            if (Objects.isNull(cnt) || cnt == 0) {
                LimitUpPool latest = limitUpPoolMapper.selectOne(Wrappers.<LimitUpPool>lambdaQuery()
                        .orderByDesc(LimitUpPool::getTradeDate)
                        .last("LIMIT 1"));
                if (Objects.nonNull(latest)) {
                    cnt = limitUpPoolMapper.selectCount(Wrappers.<LimitUpPool>lambdaQuery()
                            .eq(LimitUpPool::getTradeDate, latest.getTradeDate()));
                }
            }
            limitUpCount = Objects.nonNull(cnt) ? cnt.intValue() : 0;
        } catch (Exception ignored) {
            limitUpCount = null;
        }

        return DashboardResp.builder()
                .risk(risk)
                .positions(positions)
                .recentSignals(signals)
                .todayActions(dailyActionService.listByDate(LocalDate.now()))
                .paperMetrics(metrics)
                .equityCurve(replay.getPoints())
                .industryPnls(buildIndustryPnls(positions, totalAsset))
                .marketBriefing(briefing)
                .limitUpCount(limitUpCount)
                .build();
    }

    private TradeStats calcTradeStats(List<PaperOrder> orders) {
        BigDecimal totalTurnover = BigDecimal.ZERO;
        Map<String, List<Lot>> openLots = new HashMap<>();
        BigDecimal realized = BigDecimal.ZERO;
        BigDecimal grossWin = BigDecimal.ZERO;
        BigDecimal grossLoss = BigDecimal.ZERO;
        int wins = 0;
        int closed = 0;
        long holdDaysSum = 0;
        List<Boolean> closedWins = new ArrayList<>();
        List<PaperOrder> sorted = new ArrayList<>(orders);
        sorted.sort((a, b) -> {
            int byDate = a.getTradeDate().compareTo(b.getTradeDate());
            if (byDate != 0) {
                return byDate;
            }
            return Long.compare(a.getId(), b.getId());
        });
        for (PaperOrder order : sorted) {
            if (Objects.nonNull(order.getAmount())) {
                totalTurnover = totalTurnover.add(order.getAmount());
            }
            String code = order.getCode();
            int qty = Objects.nonNull(order.getQuantity()) ? order.getQuantity() : 0;
            if (qty <= 0 || Objects.isNull(order.getPrice())) {
                continue;
            }
            if ("BUY".equalsIgnoreCase(order.getSide())) {
                openLots.computeIfAbsent(code, k -> new ArrayList<>())
                        .add(new Lot(qty, order.getPrice(), order.getTradeDate()));
                continue;
            }
            if (!"SELL".equalsIgnoreCase(order.getSide())) {
                continue;
            }
            List<Lot> lots = openLots.computeIfAbsent(code, k -> new ArrayList<>());
            int remain = qty;
            BigDecimal sellFeeShare = Objects.nonNull(order.getFee()) ? order.getFee() : BigDecimal.ZERO;
            while (remain > 0 && !lots.isEmpty()) {
                Lot lot = lots.get(0);
                int matched = Math.min(remain, lot.qty);
                BigDecimal pnl = order.getPrice().subtract(lot.cost)
                        .multiply(BigDecimal.valueOf(matched));
                if (qty > 0) {
                    pnl = pnl.subtract(sellFeeShare.multiply(BigDecimal.valueOf(matched))
                            .divide(BigDecimal.valueOf(qty), 6, RoundingMode.HALF_UP));
                }
                realized = realized.add(pnl);
                closed++;
                boolean win = pnl.signum() >= 0;
                if (win) {
                    wins++;
                    grossWin = grossWin.add(pnl);
                } else {
                    grossLoss = grossLoss.add(pnl.abs());
                }
                closedWins.add(win);
                if (Objects.nonNull(lot.openDate) && Objects.nonNull(order.getTradeDate())) {
                    holdDaysSum += Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(lot.openDate, order.getTradeDate()));
                }
                lot.qty -= matched;
                remain -= matched;
                if (lot.qty <= 0) {
                    lots.remove(0);
                }
            }
        }
        BigDecimal winRate = closed == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(wins).divide(BigDecimal.valueOf(closed), 4, RoundingMode.HALF_UP);
        BigDecimal avgHold = closed == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(holdDaysSum).divide(BigDecimal.valueOf(closed), 2, RoundingMode.HALF_UP);
        int winStreak = 0;
        int lossStreak = 0;
        for (int i = closedWins.size() - 1; i >= 0; i--) {
            if (closedWins.get(i)) {
                if (lossStreak > 0) {
                    break;
                }
                winStreak++;
            } else {
                if (winStreak > 0) {
                    break;
                }
                lossStreak++;
            }
        }
        BigDecimal profitFactor = grossLoss.signum() == 0
                ? (grossWin.signum() > 0 ? BigDecimal.valueOf(99) : BigDecimal.ZERO)
                : grossWin.divide(grossLoss, 4, RoundingMode.HALF_UP);
        int lossCount = closed - wins;
        BigDecimal avgWin = wins == 0 ? BigDecimal.ZERO
                : grossWin.divide(BigDecimal.valueOf(wins), 6, RoundingMode.HALF_UP);
        BigDecimal avgLoss = lossCount == 0 ? BigDecimal.ZERO
                : grossLoss.divide(BigDecimal.valueOf(lossCount), 6, RoundingMode.HALF_UP);
        BigDecimal payoff = avgLoss.signum() == 0
                ? (avgWin.signum() > 0 ? BigDecimal.valueOf(99) : BigDecimal.ZERO)
                : avgWin.divide(avgLoss, 4, RoundingMode.HALF_UP);
        // E = p*W - (1-p)*L
        BigDecimal expectancy = closed == 0 ? BigDecimal.ZERO
                : winRate.multiply(avgWin)
                .subtract(BigDecimal.ONE.subtract(winRate).multiply(avgLoss))
                .setScale(2, RoundingMode.HALF_UP);
        return new TradeStats(realized.setScale(2, RoundingMode.HALF_UP), winRate, closed,
                totalTurnover.setScale(2, RoundingMode.HALF_UP), avgHold, winStreak, lossStreak,
                profitFactor, payoff, expectancy);
    }

    private Integer calcMaxLosingDays(List<EquityPointResp> points) {
        if (Objects.isNull(points) || points.size() < 2) {
            return 0;
        }
        int max = 0;
        int cur = 0;
        for (int i = 1; i < points.size(); i++) {
            BigDecimal prev = points.get(i - 1).getEquity();
            BigDecimal curr = points.get(i).getEquity();
            if (Objects.nonNull(prev) && Objects.nonNull(curr) && curr.compareTo(prev) < 0) {
                cur++;
                if (cur > max) {
                    max = cur;
                }
            } else {
                cur = 0;
            }
        }
        return max;
    }

    private BigDecimal calcOmega(List<EquityPointResp> points) {
        if (Objects.isNull(points) || points.size() < 6) {
            return BigDecimal.ZERO;
        }
        BigDecimal gains = BigDecimal.ZERO;
        BigDecimal losses = BigDecimal.ZERO;
        for (int i = 1; i < points.size(); i++) {
            BigDecimal prev = points.get(i - 1).getEquity();
            BigDecimal curr = points.get(i).getEquity();
            if (Objects.isNull(prev) || prev.signum() <= 0 || Objects.isNull(curr)) {
                continue;
            }
            BigDecimal r = curr.subtract(prev).divide(prev, 8, RoundingMode.HALF_UP);
            if (r.signum() >= 0) {
                gains = gains.add(r);
            } else {
                losses = losses.add(r.abs());
            }
        }
        if (losses.signum() == 0) {
            return gains.signum() > 0 ? BigDecimal.valueOf(99) : BigDecimal.ZERO;
        }
        return gains.divide(losses, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal calcUlcerIndex(List<EquityPointResp> points) {
        if (Objects.isNull(points) || points.size() < 3) {
            return BigDecimal.ZERO;
        }
        BigDecimal peak = BigDecimal.ZERO;
        BigDecimal sumSq = BigDecimal.ZERO;
        int n = 0;
        for (EquityPointResp point : points) {
            BigDecimal eq = point.getEquity();
            if (Objects.isNull(eq)) {
                continue;
            }
            if (eq.compareTo(peak) > 0) {
                peak = eq;
            }
            if (peak.signum() > 0) {
                BigDecimal ddPct = peak.subtract(eq).divide(peak, 8, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                sumSq = sumSq.add(ddPct.multiply(ddPct));
                n++;
            }
        }
        if (n == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(Math.sqrt(sumSq.divide(BigDecimal.valueOf(n), 8, RoundingMode.HALF_UP).doubleValue()))
                .setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal calcSortino(List<EquityPointResp> points) {
        if (Objects.isNull(points) || points.size() < 6) {
            return BigDecimal.ZERO;
        }
        List<BigDecimal> rets = new ArrayList<>();
        for (int i = 1; i < points.size(); i++) {
            BigDecimal prev = points.get(i - 1).getEquity();
            BigDecimal curr = points.get(i).getEquity();
            if (Objects.nonNull(prev) && prev.signum() > 0 && Objects.nonNull(curr)) {
                rets.add(curr.subtract(prev).divide(prev, 8, RoundingMode.HALF_UP));
            }
        }
        if (rets.size() < 5) {
            return BigDecimal.ZERO;
        }
        BigDecimal mean = rets.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(rets.size()), 8, RoundingMode.HALF_UP);
        BigDecimal downside = BigDecimal.ZERO;
        int n = 0;
        for (BigDecimal r : rets) {
            if (r.signum() < 0) {
                downside = downside.add(r.multiply(r));
                n++;
            }
        }
        if (n == 0) {
            return mean.signum() > 0 ? BigDecimal.valueOf(99) : BigDecimal.ZERO;
        }
        double dd = Math.sqrt(downside.divide(BigDecimal.valueOf(n), 8, RoundingMode.HALF_UP).doubleValue());
        if (dd == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(mean.doubleValue() / dd * Math.sqrt(252)).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal calcRollingSharpe(List<EquityPointResp> points, int window) {
        if (Objects.isNull(points) || points.size() < window + 1) {
            return BigDecimal.ZERO;
        }
        List<BigDecimal> rets = new ArrayList<>();
        int from = points.size() - window - 1;
        for (int i = from + 1; i < points.size(); i++) {
            BigDecimal prev = points.get(i - 1).getEquity();
            BigDecimal curr = points.get(i).getEquity();
            if (Objects.nonNull(prev) && prev.signum() > 0 && Objects.nonNull(curr)) {
                rets.add(curr.subtract(prev).divide(prev, 8, RoundingMode.HALF_UP));
            }
        }
        if (rets.size() < 5) {
            return BigDecimal.ZERO;
        }
        BigDecimal mean = rets.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(rets.size()), 8, RoundingMode.HALF_UP);
        BigDecimal variance = BigDecimal.ZERO;
        for (BigDecimal r : rets) {
            BigDecimal d = r.subtract(mean);
            variance = variance.add(d.multiply(d));
        }
        variance = variance.divide(BigDecimal.valueOf(rets.size() - 1), 8, RoundingMode.HALF_UP);
        if (variance.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        double std = Math.sqrt(variance.doubleValue());
        if (std == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(mean.doubleValue() / std * Math.sqrt(252)).setScale(4, RoundingMode.HALF_UP);
    }

    private static final class Lot {
        private int qty;
        private final BigDecimal cost;
        private final LocalDate openDate;

        private Lot(int qty, BigDecimal cost, LocalDate openDate) {
            this.qty = qty;
            this.cost = cost;
            this.openDate = openDate;
        }
    }

    private static final class TradeStats {
        private final BigDecimal realizedPnl;
        private final BigDecimal winRate;
        private final int closedTradeCount;
        private final BigDecimal totalTurnover;
        private final BigDecimal avgHoldDays;
        private final int winStreak;
        private final int lossStreak;
        private final BigDecimal profitFactor;
        private final BigDecimal payoffRatio;
        private final BigDecimal expectancy;

        private TradeStats(BigDecimal realizedPnl, BigDecimal winRate, int closedTradeCount,
                           BigDecimal totalTurnover, BigDecimal avgHoldDays, int winStreak, int lossStreak,
                           BigDecimal profitFactor, BigDecimal payoffRatio, BigDecimal expectancy) {
            this.realizedPnl = realizedPnl;
            this.winRate = winRate;
            this.closedTradeCount = closedTradeCount;
            this.totalTurnover = totalTurnover;
            this.avgHoldDays = avgHoldDays;
            this.winStreak = winStreak;
            this.lossStreak = lossStreak;
            this.profitFactor = profitFactor;
            this.payoffRatio = payoffRatio;
            this.expectancy = expectancy;
        }
    }

    private List<IndustryPnlResp> buildIndustryPnls(List<PaperPosition> positions, BigDecimal totalAsset) {
        Map<String, BigDecimal> mvMap = new HashMap<>();
        Map<String, BigDecimal> pnlMap = new HashMap<>();
        Set<String> codes = new HashSet<>();
        for (PaperPosition position : positions) {
            if (StringUtils.isNotBlank(position.getCode())) {
                codes.add(position.getCode());
            }
        }
        Map<String, StockBasic> basicMap = new HashMap<>();
        if (!codes.isEmpty()) {
            List<StockBasic> basics = stockBasicMapper.selectList(Wrappers.<StockBasic>lambdaQuery()
                    .in(StockBasic::getCode, codes));
            for (StockBasic basic : basics) {
                basicMap.put(basic.getCode(), basic);
            }
        }
        for (PaperPosition position : positions) {
            String industry = "未分类";
            StockBasic basic = basicMap.get(position.getCode());
            if (Objects.nonNull(basic) && StringUtils.isNotBlank(basic.getIndustry())) {
                industry = basic.getIndustry();
            }
            BigDecimal mv = Objects.nonNull(position.getMarketValue()) ? position.getMarketValue() : BigDecimal.ZERO;
            BigDecimal pnl = Objects.nonNull(position.getPnl()) ? position.getPnl() : BigDecimal.ZERO;
            mvMap.merge(industry, mv, BigDecimal::add);
            pnlMap.merge(industry, pnl, BigDecimal::add);
        }
        List<IndustryPnlResp> list = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : mvMap.entrySet()) {
            BigDecimal weight = totalAsset.signum() == 0 ? BigDecimal.ZERO
                    : entry.getValue().divide(totalAsset, 4, RoundingMode.HALF_UP);
            list.add(IndustryPnlResp.builder()
                    .industry(entry.getKey())
                    .marketValue(entry.getValue())
                    .pnl(pnlMap.getOrDefault(entry.getKey(), BigDecimal.ZERO))
                    .weight(weight)
                    .build());
        }
        list.sort(Comparator.comparing(IndustryPnlResp::getMarketValue).reversed());
        return list;
    }

    private PaperEquityCalculator.ReplayResult buildEquityReplay(PaperAccount account,
                                                                List<PaperOrder> orders,
                                                                BigDecimal terminalEquity) {
        BigDecimal initCash = Objects.nonNull(account.getInitCash()) ? account.getInitCash() : BigDecimal.valueOf(1000000);
        if (orders.isEmpty()) {
            return PaperEquityCalculator.replay(initCash, orders, Map.of(), terminalEquity);
        }
        Set<String> codes = new HashSet<>();
        LocalDate minDate = null;
        for (PaperOrder order : orders) {
            if (StringUtils.isNotBlank(order.getCode())) {
                codes.add(order.getCode());
            }
            if (Objects.nonNull(order.getTradeDate()) && (Objects.isNull(minDate) || order.getTradeDate().isBefore(minDate))) {
                minDate = order.getTradeDate();
            }
        }
        Map<String, Map<LocalDate, BigDecimal>> closesByCode = new HashMap<>();
        if (!codes.isEmpty() && Objects.nonNull(minDate)) {
            List<BarDaily> bars = barDailyMapper.selectList(Wrappers.<BarDaily>lambdaQuery()
                    .in(BarDaily::getCode, codes)
                    .ge(BarDaily::getTradeDate, minDate)
                    .orderByAsc(BarDaily::getTradeDate));
            for (BarDaily bar : bars) {
                if (Objects.isNull(bar.getClosePrice())) {
                    continue;
                }
                closesByCode.computeIfAbsent(bar.getCode(), k -> new HashMap<>())
                        .put(bar.getTradeDate(), bar.getClosePrice());
            }
        }
        return PaperEquityCalculator.replay(initCash, orders, closesByCode, terminalEquity);
    }
}
