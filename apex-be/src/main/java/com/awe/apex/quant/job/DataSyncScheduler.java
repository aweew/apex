package com.awe.apex.quant.job;

import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.BarSyncResp;
import com.awe.apex.quant.domain.dto.SyncJobResp;
import com.awe.apex.quant.domain.dto.SyncStartReq;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.market.TradingCalendar;
import com.awe.apex.quant.service.ApexUserAuthService;
import com.awe.apex.quant.service.IBarDailyService;
import com.awe.apex.quant.service.IConfigService;
import com.awe.apex.quant.service.IDataSyncJobService;
import com.awe.apex.quant.service.IMyHoldingService;
import com.awe.apex.quant.service.IMarketBreadthForecastService;
import com.awe.apex.quant.service.IObservePoolService;
import com.awe.apex.quant.service.IPortfolioIntradayService;
import com.awe.apex.quant.service.IPortfolioService;
import com.awe.apex.quant.service.IWatchlistService;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 可选定时同步（默认关闭，配置 auto_sync_enabled=true 开启）
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "apex.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DataSyncScheduler {

    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");

    private final AtomicBoolean focusQuoteSyncRunning = new AtomicBoolean(false);

    private Clock clock = Clock.system(SHANGHAI_ZONE);

    @Resource
    private IConfigService configService;

    @Resource
    private IBarDailyService barDailyService;

    @Resource
    private IWatchlistService watchlistService;

    @Resource
    private IDataSyncJobService dataSyncJobService;

    @Resource
    private IPortfolioService portfolioService;

    @Resource
    private IPortfolioIntradayService portfolioIntradayService;

    @Resource
    private IObservePoolService observePoolService;

    @Resource
    private IMyHoldingService myHoldingService;

    @Resource
    private IMarketBreadthForecastService marketBreadthForecastService;

    @Resource
    private ApexUserAuthService userAuthService;

    @Resource
    private ApexUserContext userContext;

    /**
     * 交易时段每五分钟刷新组合持仓股和未归档观察股的轻量行情，并记录组合收益。
     */
    @Scheduled(cron = "0 */5 9-11,13-15 * * MON-FRI", zone = "Asia/Shanghai")
    public void refreshFocusQuotesIntraday() {
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(SHANGHAI_ZONE);
        refreshFocusQuotes(now.toLocalDate(), now.toLocalTime());
    }

    /**
     * 按指定交易时间刷新重点证券行情，并重估各用户观察池。
     *
     * @param tradeDate 交易日期
     * @param tradeTime 交易时间
     */
    public void refreshFocusQuotes(LocalDate tradeDate, LocalTime tradeTime) {
        if (!"true".equalsIgnoreCase(configService.getString("auto_sync_enabled", "false"))) {
            return;
        }
        boolean morningSession = !tradeTime.isBefore(LocalTime.of(9, 30))
                && !tradeTime.isAfter(LocalTime.of(11, 30));
        boolean afternoonSession = !tradeTime.isBefore(LocalTime.of(13, 0))
                && !tradeTime.isAfter(LocalTime.of(15, 0));
        if (!TradingCalendar.isTradingDay(tradeDate) || (!morningSession && !afternoonSession)) {
            return;
        }
        if (!focusQuoteSyncRunning.compareAndSet(false, true)) {
            log.info("盘中重点行情快刷跳过：上一轮仍在执行，交易日期={}，交易时间={}", tradeDate, tradeTime);
            return;
        }

        long startedAtNanos = System.nanoTime();
        List<Long> userIds = queryEnabledUserIds("盘中重点行情快刷");
        Set<String> focusCodes = new LinkedHashSet<>();
        try {
            // 1. 按用户读取私有持仓和观察池，再合并为共享行情代码。
            for (Long userId : userIds) {
                try {
                    List<String> userCodes = userContext.runAsUser(userId, () -> {
                        Set<String> codes = new LinkedHashSet<>(myHoldingService.listHoldingCodes());
                        codes.addAll(observePoolService.listActiveCodes());
                        codes.addAll(portfolioService.listActiveHoldingCodes());
                        return new ArrayList<>(codes);
                    });
                    focusCodes.addAll(userCodes);
                } catch (Exception ex) {
                    log.warn("盘中重点行情读取用户资产失败，用户编号={}，原因={}", userId, ex.getMessage());
                }
            }

            // 2. 全局代码去重后仅刷新 stock_basic，不同步日线。
            Map<String, Object> quoteResult = focusCodes.isEmpty()
                    ? Map.of("success", 0, "fail", 0)
                    : myHoldingService.refreshRealtimeQuotesForCodes(new ArrayList<>(focusCodes), false);

            // 3. 使用同一轮最新行情写入组合收益快照，再重估每个用户的观察池状态。
            int portfolioSnapshotCount = 0;
            for (Long userId : userIds) {
                try {
                    portfolioSnapshotCount += userContext.runAsUser(userId,
                            () -> portfolioIntradayService.snapshotAll(LocalDateTime.of(tradeDate, tradeTime)));
                } catch (Exception ex) {
                    log.warn("盘中组合快照写入失败，用户编号={}，原因={}", userId, ex.getMessage());
                }
                try {
                    userContext.runAsUser(userId, observePoolService::refresh);
                } catch (Exception ex) {
                    log.warn("盘中重点行情重估观察池失败，用户编号={}，原因={}", userId, ex.getMessage());
                }
            }
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
            log.info("盘中重点行情快刷完成，用户数量={}，证券数量={}，成功数量={}，失败数量={}，组合快照数量={}，耗时毫秒={}",
                    userIds.size(), focusCodes.size(), quoteResult.get("success"), quoteResult.get("fail"),
                    portfolioSnapshotCount, durationMs);
        } catch (Exception ex) {
            log.warn("盘中重点行情快刷失败，证券数量={}，原因={}", focusCodes.size(), ex.getMessage());
        } finally {
            focusQuoteSyncRunning.set(false);
        }
    }

    /**
     * 交易时段按本地行情快照重估观察池，不依赖外部同步开关
     */
    @Scheduled(cron = "0 5 10,11,14,15 * * MON-FRI", zone = "Asia/Shanghai")
    public void refreshObservePoolIntraday() {
        refreshObservePool(LocalDate.now());
    }

    /**
     * 按指定日期重估观察池
     *
     * @param tradeDate 待执行日期
     */
    public void refreshObservePool(LocalDate tradeDate) {
        if (!TradingCalendar.isTradingDay(tradeDate)) {
            return;
        }
        for (Long userId : queryEnabledUserIds("定时重估观察池")) {
            try {
                Map<String, Object> stats = userContext.runAsUser(userId, observePoolService::refresh);
                log.info("定时重估观察池完成，用户编号={}，总数={}，临近数量={}，触发数量={}，归档数量={}",
                        userId, stats.get("total"), stats.get("near"), stats.get("triggered"), stats.get("archived"));
            } catch (Exception ex) {
                log.warn("定时重估观察池失败，用户编号={}，原因={}", userId, ex.getMessage());
            }
        }
    }

    /**
     * 工作日傍晚尝试同步过期日线
     */
    @Scheduled(cron = "0 30 18 * * MON-FRI")
    public void syncStaleEvening() {
        if (!"true".equalsIgnoreCase(configService.getString("auto_sync_enabled", "false"))) {
            return;
        }
        String group = configService.getString("auto_sync_group", "我的自选");
        for (Long userId : queryEnabledUserIds("定时同步日线")) {
            try {
                BarSyncResp resp = userContext.runAsUser(userId,
                        () -> barDailyService.syncStaleWatchlist(group, 40));
                log.info("定时同步日线完成，用户编号={}，分组={}，成功数量={}，失败数量={}",
                        userId, group, resp.getSuccessCount(), resp.getFailCount());
            } catch (Exception ex) {
                log.warn("定时同步日线失败，用户编号={}，分组={}，原因={}", userId, group, ex.getMessage());
            }
        }
    }

    /**
     * 工作日收盘后刷新部分行情
     */
    @Scheduled(cron = "0 10 16 * * MON-FRI")
    public void refreshQuotesAfternoon() {
        if (!"true".equalsIgnoreCase(configService.getString("auto_sync_enabled", "false"))) {
            return;
        }
        if (dataSyncJobService.isTaskRunning("CLOSE_BUNDLE")) {
            log.info("收盘行情刷新跳过：一键收盘同步正在运行");
            return;
        }
        String group = configService.getString("auto_sync_group", "我的自选");
        List<Long> userIds = queryEnabledUserIds("定时刷新用户行情");
        Set<String> quoteCodes = new LinkedHashSet<>();
        long startedAtNanos = System.nanoTime();

        // 1. 收集全部用户关注代码，统一刷新共享行情。
        for (Long userId : userIds) {
            try {
                List<String> userCodes = userContext.runAsUser(userId, () -> {
                    Set<String> codes = new LinkedHashSet<>(watchlistService.listWatchlistCodes(group));
                    codes.addAll(myHoldingService.listHoldingCodes());
                    codes.addAll(portfolioService.listActiveHoldingCodes());
                    return new ArrayList<>(codes);
                });
                quoteCodes.addAll(userCodes);
            } catch (Exception ex) {
                log.warn("定时收集用户行情代码失败，用户编号={}，分组={}，原因={}", userId, group, ex.getMessage());
            }
        }
        Map<String, Object> quoteResult;
        try {
            quoteResult = quoteCodes.isEmpty()
                    ? Map.of("success", 0, "fail", 0)
                    : myHoldingService.refreshQuotesForCodes(new ArrayList<>(quoteCodes), false);
        } catch (Exception ex) {
            quoteResult = Map.of("success", 0, "fail", quoteCodes.size());
            log.warn("收盘行情刷新失败，证券数量={}，原因={}", quoteCodes.size(), ex.getMessage());
        }

        // 2. 使用同一批共享行情为各用户生成组合快照。
        int snapshotCount = 0;
        for (Long userId : userIds) {
            try {
                snapshotCount += userContext.runAsUser(userId, portfolioService::snapshotAll);
            } catch (Exception ex) {
                log.warn("定时生成组合快照失败，用户编号={}，原因={}", userId, ex.getMessage());
            }
        }
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
        log.info("收盘行情刷新完成，用户数量={}，证券数量={}，行情成功={}，行情失败={}，快照数量={}，耗时毫秒={}",
                userIds.size(), quoteCodes.size(), quoteResult.get("success"), quoteResult.get("fail"),
                snapshotCount, durationMs);
    }

    /**
     * 交易日开盘前固化一次涨跌比预测。
     */
    @Scheduled(cron = "0 20 9 * * MON-FRI", zone = "Asia/Shanghai")
    public void generateBreadthForecastBeforeOpen() {
        if (!"true".equalsIgnoreCase(configService.getString("auto_sync_enabled", "false"))) {
            return;
        }
        String message = marketBreadthForecastService.generateBeforeOpen();
        if (StringUtils.isNotBlank(message)) {
            log.warn("盘前涨跌比预测未生成，原因={}", message);
        }
    }

    /**
     * 收盘同步包：INDEX → SECTOR → LIMIT_UP → HOT → NEWS（16:35）
     */
    @Scheduled(cron = "0 35 16 * * MON-FRI")
    public void closeBundleAfternoon() {
        if (!"true".equalsIgnoreCase(configService.getString("auto_sync_enabled", "false"))) {
            return;
        }
        if (!TradingCalendar.isTradingDay(LocalDate.now())) {
            log.info("收盘包跳过：今日非交易日");
            return;
        }
        SyncStartReq request = new SyncStartReq();
        request.setTaskType("CLOSE_BUNDLE");
        request.setTypes("INDUSTRY,CONCEPT,THEME");
        try {
            SyncJobResp job = dataSyncJobService.startSystemTask(request);
            log.info("收盘包已提交统一任务，任务编号={}，状态={}", job.getId(), job.getStatus());
        } catch (Exception ex) {
            log.warn("收盘包提交失败，原因={}", ex.getMessage());
        }
    }

    /**
     * 日经225和韩国综合指数开盘后同步亚太早盘行情。
     */
    @Scheduled(cron = "0 5 8 * * MON-FRI", zone = "Asia/Shanghai")
    public void syncAsiaPacificMorning() {
        if (!"true".equalsIgnoreCase(configService.getString("auto_sync_enabled", "false"))) {
            return;
        }
        if (dataSyncJobService.isTaskRunning("INDEX")) {
            log.info("亚太早盘指数同步跳过：指数任务正在运行，指数代码=JP_N225,KR_KOSPI");
            return;
        }

        SyncStartReq request = new SyncStartReq();
        request.setTaskType("INDEX");
        request.setCodes("JP_N225,KR_KOSPI");
        try {
            SyncJobResp job = dataSyncJobService.startSystemTask(request);
            log.info("亚太早盘指数同步已提交，任务编号={}，指数代码={}，状态={}",
                    job.getId(), request.getCodes(), job.getStatus());
        } catch (Exception ex) {
            log.warn("亚太早盘指数同步提交失败，指数代码={}，原因={}", request.getCodes(), ex.getMessage());
        }
    }

    /**
     * 每天凌晨补齐全市场日线、公司资料和财务数据
     */
    @Scheduled(cron = "0 10 2 * * *", zone = "Asia/Shanghai")
    public void repairMarketDataNightly() {
        repairMarketDataNightly(LocalDate.now());
    }

    /**
     * 按运行日期提交凌晨数据补缺任务
     *
     * @param runDate 任务运行日期
     */
    public void repairMarketDataNightly(LocalDate runDate) {
        if (!"true".equalsIgnoreCase(configService.getString("auto_sync_enabled", "false"))) {
            return;
        }
        LocalDate expectedDate = TradingCalendar.latestTradingDayOnOrBefore(runDate.minusDays(1));
        SyncStartReq request = new SyncStartReq();
        request.setTaskType("NIGHTLY_REPAIR");
        request.setExpectedDate(expectedDate.toString());
        try {
            SyncJobResp job = dataSyncJobService.startSystemTask(request);
            log.info("凌晨数据补缺已提交，任务编号={}，预期交易日={}，状态={}",
                    job.getId(), expectedDate, job.getStatus());
        } catch (Exception ex) {
            log.warn("凌晨数据补缺提交失败，预期交易日={}，原因={}", expectedDate, ex.getMessage());
        }
    }

    /**
     * 交易时段快刷热点（跳过较慢雪球；需 auto_sync_enabled=true）
     */
    @Scheduled(cron = "0 20 9,10,11,13,14 * * MON-FRI")
    public void refreshHotIntraday() {
        if (!"true".equalsIgnoreCase(configService.getString("auto_sync_enabled", "false"))) {
            return;
        }
        SyncStartReq request = new SyncStartReq();
        request.setTaskType("HOT");
        request.setSources("eastmoney,baidu");
        request.setLimit(40);
        try {
            SyncJobResp job = dataSyncJobService.startSystemTask(request);
            log.info("定时热点刷新已提交统一任务，任务编号={}，状态={}", job.getId(), job.getStatus());
        } catch (Exception ex) {
            log.warn("定时热点刷新提交失败，原因={}", ex.getMessage());
        }
    }

    /**
     * 快刷板块榜单（需 auto_sync_enabled=true）。
     */
    public void refreshSectorIntraday() {
        if (!"true".equalsIgnoreCase(configService.getString("auto_sync_enabled", "false"))) {
            return;
        }
        SyncStartReq request = new SyncStartReq();
        request.setTaskType("SECTOR_QUOTE");
        request.setTypes("INDUSTRY,CONCEPT,THEME");
        try {
            SyncJobResp job = dataSyncJobService.startSystemTask(request);
            log.info("定时板块刷新已提交统一任务，任务编号={}，状态={}", job.getId(), job.getStatus());
        } catch (Exception ex) {
            log.warn("定时板块刷新提交失败，原因={}", ex.getMessage());
        }
    }

    /**
     * 交易时段每半小时刷新个股资金流和板块资金流。
     */
    @Scheduled(cron = "0 5,35 9-14 * * MON-FRI", zone = "Asia/Shanghai")
    public void refreshCapitalFlowIntraday() {
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(SHANGHAI_ZONE);
        LocalTime tradeTime = now.toLocalTime();
        boolean morningSession = !tradeTime.isBefore(LocalTime.of(9, 35))
                && !tradeTime.isAfter(LocalTime.of(11, 35));
        boolean afternoonSession = !tradeTime.isBefore(LocalTime.of(13, 5))
                && !tradeTime.isAfter(LocalTime.of(14, 35));
        if (!morningSession && !afternoonSession) {
            return;
        }
        refreshCapitalFlow(now.toLocalDate(), "stock");
    }

    /**
     * 15:05 刷新最后一轮盘中资金流。
     */
    @Scheduled(cron = "0 5 15 * * MON-FRI", zone = "Asia/Shanghai")
    public void refreshCapitalFlowIntradayClose() {
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(SHANGHAI_ZONE);
        refreshCapitalFlow(now.toLocalDate(), "stock");
    }

    /**
     * 15:10 刷新收盘资金流和北向资金披露状态。
     */
    @Scheduled(cron = "0 10 15 * * MON-FRI", zone = "Asia/Shanghai")
    public void refreshCapitalFlowClose() {
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(SHANGHAI_ZONE);
        refreshCapitalFlow(now.toLocalDate(), "flow");
    }

    /**
     * 17:30 首次刷新龙虎榜。
     */
    @Scheduled(cron = "0 30 17 * * MON-FRI", zone = "Asia/Shanghai")
    public void refreshDragonTiger() {
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(SHANGHAI_ZONE);
        refreshCapitalFlow(now.toLocalDate(), "lhb");
    }

    /**
     * 18:20 补刷全部资金面数据。
     */
    @Scheduled(cron = "0 20 18 * * MON-FRI", zone = "Asia/Shanghai")
    public void refreshCapitalFlowAll() {
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(SHANGHAI_ZONE);
        refreshCapitalFlow(now.toLocalDate(), "all");
    }

    /**
     * 按交易日和模式提交资金面同步任务。
     *
     * @param tradeDate 交易日
     * @param mode 同步模式stock、flow、lhb或all
     */
    public void refreshCapitalFlow(LocalDate tradeDate, String mode) {
        if (!"true".equalsIgnoreCase(configService.getString("auto_sync_enabled", "false"))) {
            return;
        }
        if (!TradingCalendar.isTradingDay(tradeDate)) {
            log.info("资金面同步跳过：非交易日，日期={}，模式={}", tradeDate, mode);
            return;
        }

        boolean dragonTigerOnly = "lhb".equals(mode);
        String taskType = dragonTigerOnly ? "DRAGON_TIGER" : "CAPITAL_FLOW";
        try {
            boolean taskRunning = dataSyncJobService.isTaskRunning(taskType);
            boolean dragonTigerRunning = "all".equals(mode)
                    && dataSyncJobService.isTaskRunning("DRAGON_TIGER");
            if (taskRunning || dragonTigerRunning) {
                log.info("资金面同步跳过：同类任务正在运行，任务类型={}，模式={}", taskType, mode);
            } else {
                SyncStartReq request = new SyncStartReq();
                request.setTaskType(taskType);
                request.setMode(mode);
                SyncJobResp job = dataSyncJobService.startSystemTask(request);
                log.info("资金面同步已提交，任务编号={}，任务类型={}，模式={}，状态={}",
                        job.getId(), taskType, mode, job.getStatus());
            }

            if (!dragonTigerOnly) {
                if (dataSyncJobService.isTaskRunning("SECTOR_QUOTE")) {
                    log.info("板块资金同步跳过：任务正在运行，模式={}", mode);
                } else {
                    SyncStartReq sectorRequest = new SyncStartReq();
                    sectorRequest.setTaskType("SECTOR_QUOTE");
                    sectorRequest.setTypes("INDUSTRY,CONCEPT,THEME");
                    SyncJobResp sectorJob = dataSyncJobService.startSystemTask(sectorRequest);
                    log.info("板块资金同步已提交，任务编号={}，模式={}，状态={}",
                            sectorJob.getId(), mode, sectorJob.getStatus());
                }
            }
        } catch (Exception ex) {
            log.warn("资金面同步提交失败，日期={}，模式={}，原因={}", tradeDate, mode, ex.getMessage());
        }
    }

    private List<Long> queryEnabledUserIds(String taskName) {
        try {
            return userAuthService.listEnabledUserIds();
        } catch (Exception ex) {
            log.warn("{}读取启用用户失败，原因={}", taskName, ex.getMessage());
            return List.of();
        }
    }
}
