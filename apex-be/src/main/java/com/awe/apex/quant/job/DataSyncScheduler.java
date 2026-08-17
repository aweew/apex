package com.awe.apex.quant.job;

import com.awe.apex.quant.domain.dto.BarSyncResp;
import com.awe.apex.quant.domain.dto.HotRefreshResp;
import com.awe.apex.quant.domain.dto.SectorRefreshResp;
import com.awe.apex.quant.domain.dto.SyncJobResp;
import com.awe.apex.quant.domain.dto.SyncStartReq;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.market.TradingCalendar;
import com.awe.apex.quant.service.ApexUserAuthService;
import com.awe.apex.quant.service.IBarDailyService;
import com.awe.apex.quant.service.IConfigService;
import com.awe.apex.quant.service.IDataSyncJobService;
import com.awe.apex.quant.service.IHotService;
import com.awe.apex.quant.service.IPortfolioService;
import com.awe.apex.quant.service.ISectorBoardService;
import com.awe.apex.quant.service.IObservePoolService;
import com.awe.apex.quant.service.IWatchlistService;

import java.time.LocalDate;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.List;

/**
 * 可选定时同步（默认关闭，配置 auto_sync_enabled=true 开启）
 */
@Slf4j
@Component
public class DataSyncScheduler {

    @Resource
    private IConfigService configService;

    @Resource
    private IBarDailyService barDailyService;

    @Resource
    private IWatchlistService watchlistService;

    @Resource
    private IHotService hotService;

    @Resource
    private ISectorBoardService sectorBoardService;

    @Resource
    private IDataSyncJobService dataSyncJobService;

    @Resource
    private IPortfolioService portfolioService;

    @Resource
    private IObservePoolService observePoolService;

    @Resource
    private ApexUserAuthService userAuthService;

    @Resource
    private ApexUserContext userContext;

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
                log.info("定时重估观察池完成 userId={} total={} near={} triggered={} archived={}",
                        userId, stats.get("total"), stats.get("near"), stats.get("triggered"), stats.get("archived"));
            } catch (Exception ex) {
                log.warn("定时重估观察池失败 userId={} reason={}", userId, ex.getMessage());
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
                log.info("定时同步日线完成 userId={} group={} success={} fail={}",
                        userId, group, resp.getSuccessCount(), resp.getFailCount());
            } catch (Exception ex) {
                log.warn("定时同步日线失败 userId={} group={} reason={}", userId, group, ex.getMessage());
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
        String group = configService.getString("auto_sync_group", "我的自选");
        for (Long userId : queryEnabledUserIds("定时刷新用户行情")) {
            try {
                Map<String, Object> resp = userContext.runAsUser(userId,
                        () -> watchlistService.refreshQuotes(group, 80, false));
                log.info("定时刷新自选行情完成 userId={} group={} success={}",
                        userId, group, resp.get("successCount"));
            } catch (Exception ex) {
                log.warn("定时刷新自选行情失败 userId={} group={} reason={}", userId, group, ex.getMessage());
            }
            try {
                Map<String, Object> resp = userContext.runAsUser(userId, () -> {
                    Map<String, Object> refreshResult = portfolioService.refreshQuotesAll(false);
                    portfolioService.snapshotAll();
                    return refreshResult;
                });
                log.info("定时刷新全部组合行情完成 userId={} portfolios={} success={} fail={}",
                        userId, resp.get("portfolioCount"), resp.get("success"), resp.get("fail"));
            } catch (Exception ex) {
                log.warn("定时刷新全部组合行情失败 userId={} reason={}", userId, ex.getMessage());
            }
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
            log.info("收盘包已提交统一任务 jobId={} status={}", job.getId(), job.getStatus());
        } catch (Exception ex) {
            log.warn("收盘包提交失败 reason={}", ex.getMessage());
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
        try {
            HotRefreshResp resp = hotService.refresh("eastmoney,baidu", 40);
            log.info("定时热点刷新完成 message={}", resp.getMessage());
        } catch (Exception ex) {
            log.warn("定时热点刷新失败: {}", ex.getMessage());
        }
    }

    /**
     * 交易时段快刷板块榜单（需 auto_sync_enabled=true）
     */
    @Scheduled(cron = "0 25 9,10,11,13,14 * * MON-FRI")
    public void refreshSectorIntraday() {
        if (!"true".equalsIgnoreCase(configService.getString("auto_sync_enabled", "false"))) {
            return;
        }
        try {
            SectorRefreshResp resp = sectorBoardService.refresh("INDUSTRY,CONCEPT,THEME");
            log.info("定时板块刷新完成 message={}", resp.getMessage());
        } catch (Exception ex) {
            log.warn("定时板块刷新失败: {}", ex.getMessage());
        }
    }

    private List<Long> queryEnabledUserIds(String taskName) {
        try {
            return userAuthService.listEnabledUserIds();
        } catch (Exception ex) {
            log.warn("{}读取启用用户失败 reason={}", taskName, ex.getMessage());
            return List.of();
        }
    }
}
